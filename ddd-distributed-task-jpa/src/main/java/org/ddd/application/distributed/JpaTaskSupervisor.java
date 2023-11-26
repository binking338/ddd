package org.ddd.application.distributed;

import lombok.RequiredArgsConstructor;
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
public class JpaTaskSupervisor implements TaskSupervisor {
    private final TaskRecordJpaRepository taskRecordJpaRepository;
    private final InternalTaskRunner internalTaskRunner;

    private String svcName;

    private String getSvcName(){
        if(this.svcName == null){
            this.svcName = SystemPropertyUtils.resolvePlaceholders(CONFIG_KEY_4_SVC_NAME);
        }
        return this.svcName;
    }

    @Override
    public <Param, Result> void run(Class<Task<Param, Result>> taskClass, Param param, java.time.Duration expire, int retryTimes) {
        TaskRecord taskRecord = new TaskRecord();
        taskRecord.init(taskClass, param, getSvcName(), LocalDateTime.now(), expire, retryTimes);
        taskRecord.beginRun(LocalDateTime.now());
        taskRecord = taskRecordJpaRepository.save(taskRecord);
        internalTaskRunner.run(taskRecord, Duration.ZERO);
    }

    @Override
    public <Param, Result> void delay(Class<Task<Param, Result>> taskClass, Param param, Duration delay, java.time.Duration expire, int retryTimes) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime schedule = now.plusSeconds(delay.getSeconds());
        TaskRecord taskRecord = new TaskRecord();
        taskRecord.init(taskClass, param, getSvcName(), schedule, expire, retryTimes);
        if (delay.getSeconds() < 60) {
            taskRecord.beginRun(schedule);
            taskRecord = taskRecordJpaRepository.save(taskRecord);
            internalTaskRunner.run(taskRecord, delay);
        } else {
            taskRecordJpaRepository.save(taskRecord);
        }
    }
}
