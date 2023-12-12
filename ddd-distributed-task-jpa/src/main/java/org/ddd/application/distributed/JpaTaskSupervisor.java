package org.ddd.application.distributed;

import lombok.RequiredArgsConstructor;
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
    public <Param, Result> void run(String uuid, Class<Task<Param, Result>> taskClass, Param param, java.time.Duration expire, int retryTimes) {
        if(StringUtils.isNotBlank(uuid)){
            // TODO 检查是否有相同uuid的任务存在，如果已经成功执行，不重复执行
        }
        TaskRecord taskRecord = new TaskRecord();
        taskRecord.init(uuid, taskClass, param, getSvcName(), LocalDateTime.now(), expire, retryTimes);
        taskRecord.beginRun(LocalDateTime.now());
        taskRecord = taskRecordJpaRepository.save(taskRecord);
        internalTaskRunner.run(taskRecord, Duration.ZERO);
    }

    @Override
    public <Param, Result> void delay(String uuid, Class<Task<Param, Result>> taskClass, Param param, Duration delay, java.time.Duration expire, int retryTimes) {
        if(StringUtils.isNotBlank(uuid)){
            // TODO 检查是否有相同uuid的任务存在，如果已经成功执行，不重复执行
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime schedule = now.plusSeconds(delay.getSeconds());
        TaskRecord taskRecord = new TaskRecord();
        taskRecord.init(uuid, taskClass, param, getSvcName(), schedule, expire, retryTimes);
        if (delay.getSeconds() < 60) {
            taskRecord.beginRun(schedule);
            taskRecord = taskRecordJpaRepository.save(taskRecord);
            internalTaskRunner.run(taskRecord, delay);
        } else {
            taskRecordJpaRepository.save(taskRecord);
        }
    }
}
