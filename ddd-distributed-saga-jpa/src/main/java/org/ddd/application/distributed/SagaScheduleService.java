package org.ddd.application.distributed;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.ddd.application.distributed.persistence.ArchivedSaga;
import org.ddd.application.distributed.persistence.ArchivedSagaJpaRepository;
import org.ddd.application.distributed.persistence.SagaJpaRepository;
import org.ddd.application.distributed.persistence.Saga;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.SystemPropertyUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.ddd.share.Constants.CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_THREADPOOLSIIZE;
import static org.ddd.share.Constants.CONFIG_KEY_4_SVC_NAME;

/**
 * @author <template/>
 * @date
 */
@RequiredArgsConstructor
@Slf4j
public class SagaScheduleService {
    private static final String KEY_COMPENSATION_LOCKER = "saga_compensation[" + CONFIG_KEY_4_SVC_NAME + "]";
    private static final String KEY_ROLLBACK_LOCKER = "saga_rollback[" + CONFIG_KEY_4_SVC_NAME + "]";
    private static final String KEY_ARCHIVE_LOCKER = "saga_archive[" + CONFIG_KEY_4_SVC_NAME + "]";

    private final SagaJpaRepository sagaJpaRepository;
    private final ArchivedSagaJpaRepository archivedSagaJpaRepository;
    private final SagaSupervisor sagaSupervisor;
    private final Locker locker;

    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(Integer.parseInt(SystemPropertyUtils.resolvePlaceholders(CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_THREADPOOLSIIZE)));

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
            log.info("SAGA事务补偿:上次saga事务补偿仍未结束，跳过");
            return;
        }
        compensationRunning = true;
        trySleep(compensationDelayMillis);

        String pwd = RandomStringUtils.random(8, true, true);
        String svcName = getSvcName();
        String lockerKey = getCompensationLockerKey();
        try {
            boolean noneSaga = false;
            if (CollectionUtils.isEmpty(sagaSupervisor.getSupportedBizTypes())) {
                return;
            }
            while (!noneSaga) {
                try {
                    LocalDateTime now = LocalDateTime.now();
                    if (!this.locker.acquire(lockerKey, pwd, maxLockDuration)) {
                        trySleep(interval.getSeconds() * 1000 / maxConcurrency);
                        compensationDelayMillis = (int) ((compensationDelayMillis + (interval.getSeconds() * 1000 / maxConcurrency)) % (interval.getSeconds() * 1000));
                        return;
                    }
                    Page<Saga> sagas = sagaJpaRepository.findAll((root, cq, cb) -> {
                        cq.where(cb.or(
                                cb.and(
                                        // 【初始状态】
                                        cb.equal(root.get(Saga.F_SAGA_STATE), Saga.SagaState.INIT),
                                        cb.lessThan(root.get(Saga.F_NEXT_TRY_TIME), now.plusSeconds(interval.getSeconds() / 2)),
                                        root.get(Saga.F_BIZ_TYPE).in(sagaSupervisor.getSupportedBizTypes()),
                                        cb.equal(root.get(Saga.F_SVC_NAME), svcName)
                                ), cb.and(
                                        // 【未知状态】
                                        cb.equal(root.get(Saga.F_SAGA_STATE), Saga.SagaState.RUNNING),
                                        cb.lessThan(root.get(Saga.F_NEXT_TRY_TIME), now.plusSeconds(interval.getSeconds() / 2)),
                                        root.get(Saga.F_BIZ_TYPE).in(sagaSupervisor.getSupportedBizTypes()),
                                        cb.equal(root.get(Saga.F_SVC_NAME), svcName)
                                )));
                        return null;
                    }, PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, Saga.F_CREATE_AT)));
                    if (!sagas.hasContent()) {
                        noneSaga = true;
                        continue;
                    }
                    for (Saga saga : sagas.toList()) {
                        log.info("SAGA事务补偿: %s", saga.toString());
                        LocalDateTime nextTryTime = saga.getNextTryTime();
                        long delay = 0;
                        if (nextTryTime.isAfter(now)) {
                            delay = Duration.between(now, nextTryTime).getSeconds();
                        }

                        saga = sagaSupervisor.beginResume(saga, nextTryTime);
                        final Saga fSaga = saga;
                        executor.schedule(() -> sagaSupervisor.resume(fSaga), delay, TimeUnit.SECONDS);
                    }
                } catch (Exception ex) {
                    log.error("SAGA事务补偿:异常失败", ex);
                } finally {
                    this.locker.release(lockerKey, pwd);
                }
            }
        } finally {
            compensationRunning = false;
        }
    }

    private String rollbackLockerKey = null;

    private String getRollbackLockerKey() {
        if (rollbackLockerKey == null) {
            rollbackLockerKey = SystemPropertyUtils.resolvePlaceholders(KEY_ROLLBACK_LOCKER);
        }
        return rollbackLockerKey;
    }

    private boolean rollbackRunning = false;
    private int rollbackDelayMillis = 0;

    public void rollback(int batchSize, int maxConcurrency, Duration interval, Duration maxLockDuration) {
        if (rollbackRunning) {
            log.info("SAGA事务回滚补偿:上次saga事务回滚仍补偿未结束，跳过");
            return;
        }
        rollbackRunning = true;
        trySleep(rollbackDelayMillis);

        String pwd = RandomStringUtils.random(8, true, true);
        String svcName = getSvcName();
        String lockerKey = getRollbackLockerKey();
        try {
            boolean noneSaga = false;
            if (CollectionUtils.isEmpty(sagaSupervisor.getSupportedBizTypes())) {
                return;
            }
            while (!noneSaga) {
                try {
                    if (!this.locker.acquire(lockerKey, pwd, maxLockDuration)) {
                        trySleep(interval.getSeconds() * 1000 / maxConcurrency);
                        rollbackDelayMillis = (int) ((rollbackDelayMillis + (interval.getSeconds() * 1000 / maxConcurrency)) % (interval.getSeconds() * 1000));
                        return;
                    }
                    LocalDateTime now = LocalDateTime.now();
                    Page<Saga> sagas = sagaJpaRepository.findAll((root, cq, cb) -> {
                        cq.where(
                                cb.and(
                                        // 【回滚状态】
                                        cb.equal(root.get(Saga.F_SAGA_STATE), Saga.SagaState.ROLLBACKING),
                                        root.get(Saga.F_BIZ_TYPE).in(sagaSupervisor.getSupportedBizTypes()),
                                        cb.lessThan(root.get(Saga.F_NEXT_TRY_TIME), now.plusSeconds(interval.getSeconds() / 2)),
                                        cb.equal(root.get(Saga.F_SVC_NAME), svcName)
                                ));
                        return null;
                    }, PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, Saga.F_CREATE_AT)));
                    if (!sagas.hasContent()) {
                        noneSaga = true;
                        continue;
                    }

                    for (Saga saga : sagas.toList()) {
                        log.info("SAGA事务回滚补偿: %s", JSON.toJSONString(saga));
                        LocalDateTime nextTryTime = saga.getNextTryTime();
                        long delay = 0;
                        if (nextTryTime.isAfter(now)) {
                            delay = Duration.between(now, nextTryTime).getSeconds();
                        }
                        sagaSupervisor.beginnRollback(saga, nextTryTime);
                        executor.schedule(() -> sagaSupervisor.rollback(saga), delay, TimeUnit.SECONDS);
                    }
                } catch (Exception ex) {
                    log.error("SAGA事务回滚补偿:异常失败", ex);
                } finally {
                    this.locker.release(lockerKey, pwd);
                }
            }
        } finally {
            rollbackRunning = false;
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
     * SAGA事务归档
     */
    public void archive(int expireDays, int batchSize, Duration maxLockDuration) {
        String pwd = RandomStringUtils.random(8, true, true);
        String svcName = getSvcName();
        String lockerKey = getArchiveLockerKey();

        if (!locker.acquire(lockerKey, pwd, maxLockDuration)) {
            return;
        }
        log.info("SAGA事务归档");

        Date now = new Date();
        while (true) {
            try {
                Page<Saga> sagas = sagaJpaRepository.findAll((root, cq, cb) -> {
                    cq.where(
                            cb.and(
                                    // 【状态】
                                    cb.or(
                                            cb.equal(root.get(Saga.F_SAGA_STATE), Saga.SagaState.CANCEL),
                                            cb.equal(root.get(Saga.F_SAGA_STATE), Saga.SagaState.EXPIRED),
                                            cb.equal(root.get(Saga.F_SAGA_STATE), Saga.SagaState.FAILED),
                                            cb.equal(root.get(Saga.F_SAGA_STATE), Saga.SagaState.ROLLBACKED),
                                            cb.equal(root.get(Saga.F_SAGA_STATE), Saga.SagaState.DONE)
                                    ),
                                    cb.lessThan(root.get(Saga.F_EXPIRE_AT), DateUtils.addDays(now, expireDays)))
                    );
                    return null;
                }, PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, Saga.F_CREATE_AT)));
                if (!sagas.hasContent()) {
                    break;
                }
                List<ArchivedSaga> archivedSagas = sagas.stream().map(s -> ArchivedSaga.builder()
                        .id(s.getId())
                        .svcName(s.getSvcName())
                        .bizType(s.getBizType())
                        .contextDataType(s.getContextDataType())
                        .contextData(s.getContextData())
                        .sagaState(s.getSagaState())
                        .createAt(s.getCreateAt())
                        .expireAt(s.getExpireAt())
                        .nextTryTime(s.getNextTryTime())
                        .lastTryTime(s.getLastTryTime())
                        .tryTimes(s.getTryTimes())
                        .triedTimes(s.getTriedTimes())
                        .version(s.getVersion())
                        .processes(s.getProcesses().stream().map(p -> ArchivedSaga.SagaProcess.builder()
                                .id(p.getId())
                                .processCode(p.getProcessCode())
                                .processName(p.getProcessName())
                                .processState(p.getProcessState())
                                .contextData(p.getContextData())
                                .exception(p.getException())
                                .lastTryTime(p.getLastTryTime())
                                .triedTimes(p.getTriedTimes())
                                .createAt(p.getCreateAt())
                                .build()).collect(Collectors.toList()))
                        .build()
                ).collect(Collectors.toList());
                migrate(sagas.toList(), archivedSagas);
            } catch (Exception ex) {
                log.error("Saga事务归档:异常失败", ex);
            }
        }
        locker.release(lockerKey, pwd);
    }
    @Transactional
    public void migrate(List<Saga> sagas, List<ArchivedSaga> archivedSagas) {
        archivedSagaJpaRepository.saveAll(archivedSagas);
        sagaJpaRepository.deleteInBatch(sagas);
    }

    public void addPartition() {
        Date now = new Date();
        addPartition("__saga", DateUtils.addMonths(now, 1));
        addPartition("__saga_process", DateUtils.addMonths(now, 1));
        addPartition("__archived_saga", DateUtils.addMonths(now, 1));
        addPartition("__archived_saga_process", DateUtils.addMonths(now, 1));
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
