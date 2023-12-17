package org.ddd.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.ddd.application.distributed.Locker;
import org.ddd.domain.event.persistence.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.SystemPropertyUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.ddd.share.Constants.CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_THREADPOOLSIIZE;
import static org.ddd.share.Constants.CONFIG_KEY_4_SVC_NAME;

/**
 * 事件调度服务
 * 失败定时重试
 *
 * @author qiaohe
 * @date 2023/8/13
 */
@RequiredArgsConstructor
@Slf4j
public class EventScheduleService {
    private static final String KEY_COMPENSATION_LOCKER = "event_compensation[" + CONFIG_KEY_4_SVC_NAME + "]";
    private static final String KEY_ARCHIVE_LOCKER = "event_archive[" + CONFIG_KEY_4_SVC_NAME + "]";

    private final Locker locker;
    private final DomainEventPublisher domainEventPublisher;
    private final EventJpaRepository eventJpaRepository;
    private final ArchivedEventJpaRepository archivedEventJpaRepository;

    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(Integer.parseInt(SystemPropertyUtils.resolvePlaceholders(CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_THREADPOOLSIIZE)));

    private String svcName = null;

    private String getSvcName() {
        if (svcName == null) {
            svcName = SystemPropertyUtils.resolvePlaceholders(CONFIG_KEY_4_SVC_NAME);
        }
        return svcName;
    }

    private String compensationLockerKey = null;

    private String getCompensationLockerKey() {
        if (compensationLockerKey == null) {
            compensationLockerKey = SystemPropertyUtils.resolvePlaceholders(KEY_COMPENSATION_LOCKER);
        }
        return compensationLockerKey;
    }

    private boolean compensationRunning = false;
    private int compensationDelayMillis = 0;

    public void compensation(int batchSize, int maxConcurrency, Duration interval, Duration maxLockDuration) {
        if (compensationRunning) {
            log.info("事件发送补偿:上次事件发送补偿仍未结束，跳过");
            return;
        }
        compensationRunning = true;
        trySleep(compensationDelayMillis);

        String pwd = RandomStringUtils.random(8, true, true);
        String svcName = getSvcName();
        String lockerKey = getCompensationLockerKey();
        try {
            boolean noneEvent = false;
            while (!noneEvent) {
                LocalDateTime now = LocalDateTime.now();
                try {
                    if (!locker.acquire(lockerKey, pwd, maxLockDuration)) {
                        trySleep(interval.getSeconds() * 1000 / maxConcurrency);
                        compensationDelayMillis = (int) ((compensationDelayMillis + (interval.getSeconds() * 1000 / maxConcurrency)) % (interval.getSeconds() * 1000));
                        return;
                    }
                    Page<Event> events = eventJpaRepository.findAll((root, cq, cb) -> {
                        cq.where(cb.or(
                                cb.and(
                                        // 【初始状态】
                                        cb.equal(root.get(Event.F_EVENT_STATE), Event.EventState.INIT),
                                        cb.lessThan(root.get(Event.F_NEXT_TRY_TIME), now.plusSeconds(interval.getSeconds() / 2)),
                                        cb.equal(root.get(Event.F_SVC_NAME), svcName)
                                ), cb.and(
                                        // 【未知状态】
                                        cb.equal(root.get(Event.F_EVENT_STATE), Event.EventState.COMFIRMING),
                                        cb.lessThan(root.get(Event.F_NEXT_TRY_TIME), now.plusSeconds(interval.getSeconds() / 2)),
                                        cb.equal(root.get(Event.F_SVC_NAME), svcName)
                                )));
                        return null;
                    }, PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, Event.F_CREATE_AT)));
                    if (!events.hasContent()) {
                        noneEvent = true;
                        continue;
                    }
                    for (Event event : events.getContent()) {
                        log.info("事件发送补偿: {}", event.toString());
                        LocalDateTime nextTryTime = event.getNextTryTime();
                        long delay = 0;
                        if (nextTryTime.isAfter(now)) {
                            delay = Duration.between(now, nextTryTime).getSeconds();
                        }
                        event.beginDelivery(nextTryTime);
                        event = eventJpaRepository.saveAndFlush(event);
                        EventRecordImpl eventRecordImpl = new EventRecordImpl();
                        eventRecordImpl.resume(event);
                        executor.schedule(() -> domainEventPublisher.publish(eventRecordImpl), delay, TimeUnit.SECONDS);
                    }
                } catch (Exception ex) {
                    log.error("事件发送补偿:异常失败", ex);
                } finally {
                    locker.release(lockerKey, pwd);
                }
            }
        } finally {
            compensationRunning = false;
        }
    }

    private void trySleep(long mills) {
        try {
            if (mills > 0) {
                Thread.sleep(mills);
            }
        } catch (InterruptedException e) {
            /* ignore */
        }
    }

    private String archiveLockerKey = null;

    private String getArchiveLockerKey() {
        if (archiveLockerKey == null) {
            archiveLockerKey = SystemPropertyUtils.resolvePlaceholders(KEY_ARCHIVE_LOCKER);
        }
        return archiveLockerKey;
    }

    /**
     * 本地事件库归档
     */
    public void archive(int expireDays, int batchSize, Duration maxLockDuration) {
        String pwd = RandomStringUtils.random(8, true, true);
        String svcName = getSvcName();
        String lockerKey = getArchiveLockerKey();

        if (!locker.acquire(lockerKey, pwd, maxLockDuration)) {
            return;
        }
        log.info("事件归档");

        Date now = new Date();
        int failCount = 0;
        while (true) {
            try {
                Page<Event> events = eventJpaRepository.findAll((root, cq, cb) -> {
                    cq.where(
                            cb.and(
                                    // 【状态】
                                    cb.or(
                                            cb.equal(root.get(Event.F_EVENT_STATE), Event.EventState.CANCEL),
                                            cb.equal(root.get(Event.F_EVENT_STATE), Event.EventState.EXPIRED),
                                            cb.equal(root.get(Event.F_EVENT_STATE), Event.EventState.FAILED),
                                            cb.equal(root.get(Event.F_EVENT_STATE), Event.EventState.DELIVERED)
                                    ),
                                    cb.lessThan(root.get(Event.F_EXPIRE_AT), DateUtils.addDays(now, -7))
                            ));
                    return null;
                }, PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, Event.F_CREATE_AT)));
                if (!events.hasContent()) {
                    break;
                }
                List<ArchivedEvent> archivedEvents = events.stream().map(e -> ArchivedEvent.builder()
                        .id(e.getId())
                        .dataType(e.getDataType())
                        .data(e.getData())
                        .eventType(e.getEventType())
                        .eventState(e.getEventState())
                        .createAt(e.getCreateAt())
                        .expireAt(e.getExpireAt())
                        .nextTryTime(e.getNextTryTime())
                        .lastTryTime(e.getLastTryTime())
                        .tryTimes(e.getTryTimes())
                        .triedTimes(e.getTriedTimes())
                        .version(e.getVersion())
                        .build()
                ).collect(Collectors.toList());
                migrate(events.toList(), archivedEvents);
            } catch (Exception ex) {
                failCount++;
                log.error("事件归档:失败", ex);
                if (failCount >= 3) {
                    log.info("事件归档:累计3次退出任务");
                    break;
                }
            }
        }
        locker.release(lockerKey, pwd);
    }

    @Transactional
    public void migrate(List<Event> events, List<ArchivedEvent> archivedEvents) {
        archivedEventJpaRepository.saveAll(archivedEvents);
        eventJpaRepository.deleteInBatch(events);
    }

    public void addPartition() {
        Date now  = new Date();
        addPartition("__event", DateUtils.addMonths(now, 1));
        addPartition("__archived_event", DateUtils.addMonths(now, 1));
    }

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建date日期所在月下个月的分区
     * @param table
     * @param date
     */
    private void addPartition(String table, Date date) {
        String sql = "alter table `" + table + "` add partition (partition p" + DateFormatUtils.format(date, "yyyyMM") + " values less than (to_days('" + DateFormatUtils.format(DateUtils.addMonths(date, 1), "yyyy-MM") + "-01')) ENGINE=InnoDB)";
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            if (!ex.getMessage().contains("Duplicate partition")) {
                log.error("分区创建异常 table = " + table + " partition = p" + DateFormatUtils.format(date, "yyyyMM"), ex);
            }
        }
    }
}
