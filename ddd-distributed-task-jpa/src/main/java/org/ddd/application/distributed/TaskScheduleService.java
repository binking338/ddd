package org.ddd.application.distributed;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.ddd.application.distributed.persistence.TaskRecord;
import org.ddd.application.distributed.persistence.TaskRecordJpaRepository;
import org.ddd.application.distributed.persistence.TaskState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.SystemPropertyUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.ddd.share.Constants.CONFIG_KEY_4_SVC_NAME;

/**
 * @author qiaohe
 * @date 2023/8/19
 */
@RequiredArgsConstructor
@Slf4j
public class TaskScheduleService {
    private static final String KEY_COMPENSATION_LOCKER = "task_compensation[" + CONFIG_KEY_4_SVC_NAME + "]";

    private final Locker locker;
    private final TaskRecordJpaRepository taskRecordJpaRepository;
    private final InternalTaskRunner internalTaskRunner;

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
            log.info("异步任务补偿:上次任务补偿仍未结束，跳过");
            return;
        }
        compensationRunning = true;
        trySleep(compensationDelayMillis);

        String pwd = RandomStringUtils.random(8, true, true);
        String svcName = getSvcName();
        String lockerKey = getCompensationLockerKey();
        try {
            boolean noneTask = false;
            while (!noneTask) {
                LocalDateTime now = LocalDateTime.now();
                try {
                    if (!locker.acquire(lockerKey, pwd, maxLockDuration)) {
                        trySleep(interval.getSeconds() * 1000 / maxConcurrency);
                        compensationDelayMillis = (int) ((compensationDelayMillis + (interval.getSeconds() * 1000 / maxConcurrency)) % (interval.getSeconds() * 1000));
                        return;
                    }
                    Page<TaskRecord> tasks = taskRecordJpaRepository.findAll((root, cq, cb) -> {
                        cq.where(cb.or(
                                cb.and(
                                        // 【初始状态】
                                        cb.equal(root.get(TaskRecord.F_TASK_STATE), TaskState.INIT),
                                        cb.lessThan(root.get(TaskRecord.F_NEXT_TRY_TIME), now.plusSeconds(interval.getSeconds() / 2)),
                                        cb.equal(root.get(TaskRecord.F_SVC_NAME), svcName)
                                ), cb.and(
                                        // 【未知状态】
                                        cb.equal(root.get(TaskRecord.F_TASK_STATE), TaskState.COMFIRMING),
                                        cb.lessThan(root.get(TaskRecord.F_NEXT_TRY_TIME), now.plusSeconds(interval.getSeconds() / 2)),
                                        cb.equal(root.get(TaskRecord.F_SVC_NAME), svcName)
                                )));
                        return null;
                    }, PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, TaskRecord.F_CREATE_AT)));
                    if (!tasks.hasContent()) {
                        noneTask = true;
                        continue;
                    }
                    for (TaskRecord taskRecord : tasks.getContent()) {
                        log.info("异步任务补偿: %s", JSON.toJSONString(taskRecord));
                        LocalDateTime nextTryTime = taskRecord.getNextTryTime();
                        taskRecord.beginRun(nextTryTime);
                        taskRecord = taskRecordJpaRepository.saveAndFlush(taskRecord);
                        Duration delay = nextTryTime.isAfter(now) ? Duration.between(now, nextTryTime) : Duration.ZERO;
                        internalTaskRunner.run(taskRecord, delay);
                    }
                } catch (Exception ex) {
                    log.error("异步任务补偿:异常失败", ex);
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
