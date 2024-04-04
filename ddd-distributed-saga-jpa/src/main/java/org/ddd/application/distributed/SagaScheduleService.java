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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import javax.annotation.PostConstruct;
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

    @Value(CONFIG_KEY_4_SVC_NAME)
    private String svcName = null;

    private String getSvcName() {
        return svcName;
    }

    @Value(KEY_COMPENSATION_LOCKER)
    private String compensationLockerKey = null;

    private String getCompensationLockerKey() {
        return compensationLockerKey;
    }

    private boolean compensationRunning = false;

    public void compensation(int batchSize, int maxConcurrency, Duration interval, Duration maxLockDuration) {
        if (compensationRunning) {
            log.info("SAGA事务补偿:上次saga事务补偿仍未结束，跳过");
            return;
        }
        compensationRunning = true;

        String pwd = RandomStringUtils.random(8, true, true);
        String svcName = getSvcName();
        String lockerKey = getCompensationLockerKey();
        try {
            boolean noneSaga = false;
            if (CollectionUtils.isEmpty(sagaSupervisor.getSupportedBizTypes())) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            while (!noneSaga) {
                try {
                    if (!this.locker.acquire(lockerKey, pwd, maxLockDuration)) {
                        return;
                    }
                    Page<Saga> sagas = sagaJpaRepository.findAll((root, cq, cb) -> {
                        cq.where(cb.or(
                                cb.and(
                                        // 【初始状态】
                                        cb.equal(root.get(Saga.F_SAGA_STATE), Saga.SagaState.INIT),
                                        cb.lessThan(root.get(Saga.F_NEXT_TRY_TIME), now.plusSeconds(0)),
                                        root.get(Saga.F_BIZ_TYPE).in(sagaSupervisor.getSupportedBizTypes()),
                                        cb.equal(root.get(Saga.F_SVC_NAME), svcName)
                                ), cb.and(
                                        // 【未知状态】
                                        cb.equal(root.get(Saga.F_SAGA_STATE), Saga.SagaState.RUNNING),
                                        cb.lessThan(root.get(Saga.F_NEXT_TRY_TIME), now.plusSeconds(0)),
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
                        log.info("SAGA事务补偿: {}", saga.toString());
                        sagaSupervisor.resume(saga);
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

    @Value(KEY_ROLLBACK_LOCKER)
    private String rollbackLockerKey = null;

    private String getRollbackLockerKey() {
        return rollbackLockerKey;
    }

    private boolean rollbackRunning = false;

    public void rollback(int batchSize, int maxConcurrency, Duration interval, Duration maxLockDuration) {
        if (rollbackRunning) {
            log.info("SAGA事务回滚补偿:上次saga事务回滚仍补偿未结束，跳过");
            return;
        }
        rollbackRunning = true;

        String pwd = RandomStringUtils.random(8, true, true);
        String svcName = getSvcName();
        String lockerKey = getRollbackLockerKey();
        try {
            boolean noneSaga = false;
            if (CollectionUtils.isEmpty(sagaSupervisor.getSupportedBizTypes())) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            while (!noneSaga) {
                try {
                    if (!this.locker.acquire(lockerKey, pwd, maxLockDuration)) {
                        return;
                    }
                    Page<Saga> sagas = sagaJpaRepository.findAll((root, cq, cb) -> {
                        cq.where(
                                cb.and(
                                        // 【回滚状态】
                                        cb.equal(root.get(Saga.F_SAGA_STATE), Saga.SagaState.ROLLBACKING),
                                        root.get(Saga.F_BIZ_TYPE).in(sagaSupervisor.getSupportedBizTypes()),
                                        cb.lessThan(root.get(Saga.F_NEXT_TRY_TIME), now.plusSeconds(0)),
                                        cb.equal(root.get(Saga.F_SVC_NAME), svcName)
                                ));
                        return null;
                    }, PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, Saga.F_CREATE_AT)));
                    if (!sagas.hasContent()) {
                        noneSaga = true;
                        continue;
                    }

                    for (Saga saga : sagas.toList()) {
                        log.info("SAGA事务回滚补偿: {}", JSON.toJSONString(saga));
                        sagaSupervisor.rollback(saga);
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

    @Value(KEY_ARCHIVE_LOCKER)
    private String archiveLockerKey = null;

    private String getArchiveLockerKey() {
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
                                .inputData(p.getInputData())
                                .inputDataType(p.getInputDataType())
                                .outputData(p.getOutputData())
                                .outputDataType(p.getOutputDataType())
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
