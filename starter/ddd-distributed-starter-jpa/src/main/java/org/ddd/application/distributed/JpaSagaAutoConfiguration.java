package org.ddd.application.distributed;

import lombok.RequiredArgsConstructor;
import org.ddd.application.distributed.persistence.SagaJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
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
@EnableJpaRepositories(basePackages = {"org.ddd.application.distributed.persistence"})
@EntityScan(basePackages = {"org.ddd.application.distributed.persistence"})
@EnableScheduling
public class JpaSagaAutoConfiguration {
    private final List<SagaStateMachine> sagaStateMachines;
    private final SagaJpaRepository sagaJpaRepository;
    private final Locker locker;

    @Bean
    public SagaSupervisor sagaSupervisor() {
        SagaSupervisor supervisor = new SagaSupervisor(sagaStateMachines);
        return supervisor;
    }

    @Bean
    public SagaScheduleService sagaScheduleService(SagaSupervisor sagaSupervisor) {
        scheduleService = new SagaScheduleService(sagaJpaRepository, sagaSupervisor, locker);
        return scheduleService;
    }

    private SagaScheduleService scheduleService = null;
    @Value(CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_BATCHSIZE)
    private int batchSize;
    @Value(CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_MAXCONCURRENT)
    private int maxConcurrency;
    @Value(CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_INTERVALSECONDS)
    private int intervalSeconds;
    @Value(CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_MAXLOCKSECONDS)
    private int maxLockSeconds;

    @Scheduled(cron = CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_COMPENSATION_CRON)
    public void compensation() {
        if (scheduleService == null) return;
        scheduleService.compensation(batchSize, maxConcurrency, Duration.ofSeconds(intervalSeconds), Duration.ofSeconds(maxLockSeconds));
    }

    @Scheduled(cron = CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_ROLLBACK_CRON)
    public void rollback() {
        if (scheduleService == null) return;
        scheduleService.rollback(batchSize, maxConcurrency, Duration.ofSeconds(intervalSeconds), Duration.ofSeconds(maxLockSeconds));
    }
}
