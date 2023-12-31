package org.ddd.application.distributed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ddd.application.distributed.persistence.TaskRecord;
import org.ddd.application.distributed.persistence.TaskRecordJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

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

    @Value(CONFIG_KEY_4_SVC_NAME)
    private String svcName;

    private String getSvcName() {
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

    private Optional<TaskRecord> queryTaskRecord(String uuid){
        Optional<TaskRecord> taskRecord = taskRecordJpaRepository.findAll((root, query, cb) -> {
            query.where(
                    cb.and(
                            cb.equal(root.get(TaskRecord.F_TASK_UUID), uuid),
                            cb.equal(root.get(TaskRecord.F_SVC_NAME), getSvcName())
                    )
            );
            return null;
        }, PageRequest.of(0,1)).stream().findFirst();
        return taskRecord;
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
        if (delay.getSeconds() == 0) {
            taskRecord.beginRun(schedule);
            taskRecord = taskRecordJpaRepository.save(taskRecord);
            internalTaskRunner.run(taskRecord, delay);
        } else {
            taskRecordJpaRepository.save(taskRecord);
        }
        return true;
    }

    public TaskRecord query(String uuid) {
        return queryTaskRecord(uuid).orElse(null);
    }


    public boolean copyAndRun(String sourceUuid, String uuid){
        LocalDateTime now = LocalDateTime.now();
        TaskRecord source = queryTaskRecord(sourceUuid).orElseThrow(() -> new RuntimeException(String.format("不存在的任务 source_uuid = %s", sourceUuid)));
        TaskRecord taskRecord = TaskRecord.builder().build();
        taskRecord.initFrom(source, uuid, now);
        taskRecord.beginRun(now);
        taskRecord = taskRecordJpaRepository.save(taskRecord);
        internalTaskRunner.run(taskRecord, Duration.ZERO);
        return true;
    }

    public boolean copyAndDelay(String sourceUuid, String uuid, Duration delay){
        LocalDateTime now = LocalDateTime.now();
        TaskRecord source = queryTaskRecord(sourceUuid).orElseThrow(() -> new RuntimeException(String.format("不存在的任务 source_uuid = %s", sourceUuid)));
        TaskRecord taskRecord = TaskRecord.builder().build();
        LocalDateTime schedule = now.plusSeconds(delay.getSeconds());
        taskRecord.initFrom(source, uuid, schedule);
        if (delay.getSeconds() == 0) {
            taskRecord.beginRun(schedule);
            taskRecord = taskRecordJpaRepository.save(taskRecord);
            internalTaskRunner.run(taskRecord, delay);
        } else {
            taskRecordJpaRepository.save(taskRecord);
        }
        return true;
    }
}
