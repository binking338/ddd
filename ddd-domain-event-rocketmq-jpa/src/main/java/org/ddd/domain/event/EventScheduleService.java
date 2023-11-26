package org.ddd.domain.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.ddd.application.distributed.Locker;
import org.ddd.domain.event.persistence.EventRecordImpl;
import org.ddd.domain.event.persistence.EventRecordImplJpaRepository;
import org.ddd.domain.event.persistence.EventState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.SystemPropertyUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

    private final Locker locker;
    private final DomainEventPublisher domainEventPublisher;
    private final EventRecordImplJpaRepository eventRecordImplJpaRepository;

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
                    Page<EventRecordImpl> events = eventRecordImplJpaRepository.findAll((root, cq, cb) -> {
                        cq.where(cb.or(
                                cb.and(
                                        // 【初始状态】
                                        cb.equal(root.get(EventRecordImpl.F_EVENT_STATE), EventState.INIT),
                                        cb.lessThan(root.get(EventRecordImpl.F_NEXT_TRY_TIME), now.plusSeconds(interval.getSeconds() / 2)),
                                        cb.equal(root.get(EventRecordImpl.F_SVC_NAME), svcName)
                                ), cb.and(
                                        // 【未知状态】
                                        cb.equal(root.get(EventRecordImpl.F_EVENT_STATE), EventState.COMFIRMING),
                                        cb.lessThan(root.get(EventRecordImpl.F_NEXT_TRY_TIME), now.plusSeconds(interval.getSeconds() / 2)),
                                        cb.equal(root.get(EventRecordImpl.F_SVC_NAME), svcName)
                                )));
                        return null;
                    }, PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, EventRecordImpl.F_CREATE_AT)));
                    if (!events.hasContent()) {
                        noneEvent = true;
                        continue;
                    }
                    for (EventRecordImpl eventRecordImpl : events.getContent()) {
                        LocalDateTime nextTryTime = eventRecordImpl.getNextTryTime();
                        long delay = 0;
                        if (nextTryTime.isAfter(now)) {
                            delay = Duration.between(now, nextTryTime).getSeconds();
                        }
                        eventRecordImpl.beginDelivery(nextTryTime);
                        eventRecordImpl = eventRecordImplJpaRepository.saveAndFlush(eventRecordImpl);
                        final EventRecordImpl fEventRecordImpl = eventRecordImpl;
                        executor.schedule(() -> domainEventPublisher.publish(fEventRecordImpl), delay, TimeUnit.SECONDS);
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
}
