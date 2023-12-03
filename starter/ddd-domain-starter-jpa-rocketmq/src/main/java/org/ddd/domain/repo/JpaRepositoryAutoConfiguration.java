package org.ddd.domain.repo;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.ddd.domain.event.DomainEventPublisher;
import org.ddd.domain.event.DomainEventSupervisor;
import org.ddd.domain.event.EventRecordRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @author qiaohe
 * @date 2023/9/10
 */
@Configuration
@ConditionalOnBean(RocketMQTemplate.class)
@RequiredArgsConstructor
public class JpaRepositoryAutoConfiguration {
    private final ApplicationEventPublisher applicationEventPublisher;
    private final DomainEventSupervisor domainEventSupervisor;
    private final DomainEventPublisher domainEventPublisher;
    private final EventRecordRepository eventRecordRepository;

    @Bean
    public JpaSpecificationManager jpaSpecificationManager(List<AbstractJpaSpecification> specifications){
        JpaSpecificationManager specificationManager = new JpaSpecificationManager(specifications);
        return specificationManager;
    }

    @Bean
    public JpaUnitOfWork jpaUnitOfWork(JpaSpecificationManager jpaSpecificationManager){
        JpaUnitOfWork unitOfWork = new JpaUnitOfWork(applicationEventPublisher, domainEventSupervisor, domainEventPublisher, eventRecordRepository, jpaSpecificationManager);
        return unitOfWork;
    }


}
