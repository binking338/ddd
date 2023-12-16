package org.ddd.application.distributed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ddd.application.distributed.persistence.TaskRecord;
import org.ddd.application.distributed.persistence.TaskRecordJpaRepository;
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
public class JpaTaskSupervisor implements TaskSupervisor {
    private final TaskRecordJpaRepository taskRecordJpaRepository;
    private final InternalTaskRunner internalTaskRunner;

    private String svcName;

    private String getSvcName() {
        if (this.svcName == null) {
            this.svcName = SystemPropertyUtils.resolvePlaceholders(CONFIG_KEY_4_SVC_NAME);
        }
        return this.svcName;
    }

    private boolean isExists(String uuid){
        long count = taskRecordJpaRepository.count((root, query, cb) -> {
            query.where(
                    cb.and(
                            cb.equal(root.get(TaskRecord.F_TASK_UUID), uuid),
                            cb.equal(root.get(TaskRecord.F_SVC_NAME), getSvcName())
                    )
            );
            return null;
        });
        return count > 0;
    }

    @Override
    public <Param, Result, T extends Task<Param, Result>> boolean run(Class<T> taskClass, Param param, String uuid, Duration expire, int retryTimes) {
        // 检查是否有相同uuid的任务存在，如果已经成功执行，不重复执行
        if (StringUtils.isNotBlank(uuid) && isExists(uuid)) {
            log.warn("异步任务已提交，勿重复提交: " + uuid);
            return false;
        }
        TaskRecord taskRecord = new TaskRecord();
        LocalDateTime now = LocalDateTime.now();
        taskRecord.init(uuid, taskClass, param, getSvcName(), now, now, expire, retryTimes);
        taskRecord.beginRun(now);
        taskRecord = taskRecordJpaRepository.save(taskRecord);
        internalTaskRunner.run(taskRecord, Duration.ZERO);
        return true;
    }

    @Override
    public <Param, Result, T extends Task<Param, Result>> boolean delay(Class<T> taskClass, Param param, String uuid, Duration delay, java.time.Duration expire, int retryTimes) {
        // 检查是否有相同uuid的任务存在，如果已经成功执行，不重复执行
        if (StringUtils.isNotBlank(uuid) && isExists(uuid)) {
            log.warn("异步任务已提交，勿重复提交: " + uuid);
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime schedule = now.plusSeconds(delay.getSeconds());
        TaskRecord taskRecord = new TaskRecord();
        taskRecord.init(uuid, taskClass, param, getSvcName(), now, schedule, expire, retryTimes);
        if (delay.getSeconds() < 60) {
            taskRecord.beginRun(schedule);
            taskRecord = taskRecordJpaRepository.save(taskRecord);
            internalTaskRunner.run(taskRecord, delay);
        } else {
            taskRecordJpaRepository.save(taskRecord);
        }
        return true;
    }
}
