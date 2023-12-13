package org.ddd.application.distributed;

import lombok.RequiredArgsConstructor;
import org.ddd.application.distributed.persistence.ArchivedTaskRecordJpaRepository;
import org.ddd.application.distributed.persistence.TaskRecordJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final ArchivedTaskRecordJpaRepository archivedTaskRecordJpaRepository;
    private final List<Task> tasks;
    private final Locker locker;
    private final JdbcTemplate jdbcTemplate;

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
        scheduleService = new TaskScheduleService(locker, taskRecordJpaRepository, archivedTaskRecordJpaRepository, taskRunner, jdbcTemplate);
        scheduleService.addPartition();
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

    @Value(CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_ARCHIVE_BATCHSIZE)
    private int archiveBatchSize;
    @Value(CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_ARCHIVE_EXPIREDAYS)
    private int archiveExpireDays;
    @Value(CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_ARCHIVE_MAXLOCKSECONDS)
    private int archiveMaxLockSeconds;
    @Scheduled(cron = CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_ARCHIVE_CRON)
    public void archive() {
        if (scheduleService == null) return;
        scheduleService.archive(archiveExpireDays, archiveBatchSize, Duration.ofSeconds(archiveMaxLockSeconds));
    }

    @Scheduled(cron = CONFIG_KEY_4_DISTRIBUTED_TASK_SCHEDULE_ADDPARTITION_CRON)
    public void addTablePartition(){
        scheduleService.addPartition();
    }

}
