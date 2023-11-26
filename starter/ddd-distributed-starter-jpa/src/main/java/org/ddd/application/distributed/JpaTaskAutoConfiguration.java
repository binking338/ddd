package org.ddd.application.distributed;

import lombok.RequiredArgsConstructor;
import org.ddd.application.distributed.persistence.TaskRecordJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;

import static org.ddd.share.Constants.*;

/**
 * @author qiaohe
 * @date 2023/9/10
 */
@Configuration
@RequiredArgsConstructor
@EnableScheduling
public class JpaTaskAutoConfiguration {
    private final TaskRecordJpaRepository taskRecordJpaRepository;
    private final List<Task> tasks;
    private final Locker locker;

    @Bean
    public InternalTaskRunner internalTaskRunner(){
        InternalTaskRunner taskRunner = new InternalTaskRunner(taskRecordJpaRepository, tasks);
        return taskRunner;
    }

    @Bean
    public JpaTaskSupervisor jpaTaskSupervisor(InternalTaskRunner taskRunner){
        JpaTaskSupervisor taskSupervisor = new JpaTaskSupervisor(taskRecordJpaRepository, taskRunner);
        return taskSupervisor;
    }

    @Bean
    public TaskScheduleService taskScheduleService(InternalTaskRunner taskRunner){
        scheduleService = new TaskScheduleService(locker, taskRecordJpaRepository, taskRunner);
        return scheduleService;
    }

    TaskScheduleService scheduleService = null;
    @Value(CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_BATCHSIZE)
    private int batchSize;
    @Value(CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_MAXCONCURRENT)
    private int maxConcurrency;
    @Value(CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_INTERVALSECONDS)
    private int intervalSeconds;
    @Value(CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_MAXLOCKSECONDS)
    private int maxLockSeconds;
    @Scheduled( cron = CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_CRON)
    public void compensation(){
        if(scheduleService == null) return;
        scheduleService.compensation(batchSize, maxConcurrency, Duration.ofSeconds(intervalSeconds), Duration.ofSeconds(maxLockSeconds));
    }

}
