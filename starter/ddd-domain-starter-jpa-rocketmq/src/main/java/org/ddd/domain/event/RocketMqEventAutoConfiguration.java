package org.ddd.domain.event;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.ddd.application.distributed.Locker;
import org.ddd.domain.event.persistence.EventRecordImplJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationEventPublisher;
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
@ConditionalOnProperty(name = "rocketmq.name-server")
@RequiredArgsConstructor
@EnableJpaRepositories(basePackages = {"org.ddd.domain.event.persistence"})
@EntityScan(basePackages = {"org.ddd.domain.event.persistence"})
@EnableScheduling
public class RocketMqEventAutoConfiguration {
    private final Locker locker;
    private final EventRecordImplJpaRepository eventRecordImplJpaRepository;
    private final RocketMQTemplate rocketMQTemplate;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final List<RocketMqDomainEventSubscriber> subscribers;

    @Bean
    @ConditionalOnMissingBean(EventRecordRepository.class)
    public JpaEventRecordRepository jpaEventRecordRepository() {
        JpaEventRecordRepository eventRecordRepository = new JpaEventRecordRepository(eventRecordImplJpaRepository);
        return eventRecordRepository;
    }

    @Bean
    public RocketMqDomainEventSubscriberManager rocketMqDomainEventSubscriberManager() {
        RocketMqDomainEventSubscriberManager domainEventSubscriberManager = new RocketMqDomainEventSubscriberManager(subscribers, applicationEventPublisher);
        return domainEventSubscriberManager;
    }

    @Bean
    public RocketMqDomainEventPublisher rocketMqDomainEventPublisher(RocketMqDomainEventSubscriberManager rocketMqDomainEventSubscriberManager, JpaEventRecordRepository jpaEventRecordRepository) {
        RocketMqDomainEventPublisher rocketMqDomainEventPublisher = new RocketMqDomainEventPublisher(rocketMqDomainEventSubscriberManager, rocketMQTemplate, jpaEventRecordRepository);
        return rocketMqDomainEventPublisher;
    }

    @Bean
    public RocketMqDomainEventSupervisor rocketMqDomainEventSupervisor() {
        RocketMqDomainEventSupervisor rocketMqDomainEventSupervisor = new RocketMqDomainEventSupervisor();
        return rocketMqDomainEventSupervisor;
    }

    @Bean
    public RocketMqDomainEventSubscriberAdapter rocketMqDomainEventSubscriberAdapter(RocketMqDomainEventSubscriberManager rocketMqDomainEventSubscriberManager) {
        RocketMqDomainEventSubscriberAdapter rocketMqDomainEventSubscriberAdapter = new RocketMqDomainEventSubscriberAdapter(rocketMqDomainEventSubscriberManager);
        return rocketMqDomainEventSubscriberAdapter;
    }

    @Bean
    public EventScheduleService eventScheduleService(DomainEventPublisher domainEventPublisher) {
        scheduleService = new EventScheduleService(locker, domainEventPublisher, eventRecordImplJpaRepository);
        return scheduleService;
    }

    private EventScheduleService scheduleService = null;
    @Value(CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_BATCHSIZE)
    private int batchSize;
    @Value(CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_MAXCONCURRENT)
    private int maxConcurrency;
    @Value(CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_INTERVALSECONDS)
    private int intervalSeconds;
    @Value(CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_MAXLOCKSECONDS)
    private int maxLockSeconds;

    @Scheduled(cron = CONFIG_KEY_4_DISTRIBUTED_EVENT_SCHEDULE_CRON)
    public void compensation() {
        if (scheduleService == null) return;
        scheduleService.compensation(batchSize, maxConcurrency, Duration.ofSeconds(intervalSeconds), Duration.ofSeconds(maxLockSeconds));
    }

}
