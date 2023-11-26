package org.ddd.application.distributed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.ddd.application.distributed.persistence.SagaJpaRepository;
import org.ddd.application.distributed.persistence.Saga;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.SystemPropertyUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.ddd.share.Constants.CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_THREADPOOLSIIZE;
import static org.ddd.share.Constants.CONFIG_KEY_4_SVC_NAME;

/**
 * @author <template/>
 * @date
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaScheduleService {
    private static final String KEY_COMPENSATION_LOCKER = "saga_compensation[" + CONFIG_KEY_4_SVC_NAME + "]";
    private static final String KEY_ROLLBACK_LOCKER = "saga_rollback[" + CONFIG_KEY_4_SVC_NAME + "]";

    private final SagaJpaRepository sagaJpaRepository;
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
}
