package org.ddd.domain.event;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.ddd.share.DomainException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.ddd.share.Constants.CONFIG_KEY_4_SVC_NAME;

/**
 * @author qiaohe
 * @date 2023/8/13
 */
@Slf4j
public class RocketMqDomainEventPublisher implements DomainEventPublisher {
    private final RocketMqDomainEventSubscriberManager rocketMqDomainEventSubscriberManager;
    private final RocketMQTemplate rocketMQTemplate;
    private final EventRecordRepository eventRecordRepository;
    @Value(CONFIG_KEY_4_SVC_NAME)
    private String svcName;

    @Autowired
    Environment environment;

    /**
     * 如下配置需配置好，保障RocketMqTemplate被初始化
     * ## rocketmq
     * #rocketmq.name-server = myrocket.nameserver:9876
     * #rocketmq.producer.group=${spring.application.name}
     *
     * @param rocketMqDomainEventSubscriberManager
     * @param rocketMQTemplate
     * @param eventRecordRepository
     */
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public RocketMqDomainEventPublisher(
            @Autowired RocketMqDomainEventSubscriberManager rocketMqDomainEventSubscriberManager,
            @Autowired(required = false) RocketMQTemplate rocketMQTemplate,
            @Autowired(required = false) EventRecordRepository eventRecordRepository
    ) {
        this.rocketMqDomainEventSubscriberManager = rocketMqDomainEventSubscriberManager;
        this.rocketMQTemplate = rocketMQTemplate;
        this.eventRecordRepository = eventRecordRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(Object eventPayload) {
        EventRecord event = null;
        if (eventPayload instanceof EventRecord) {
            event = (EventRecord) eventPayload;
        } else {
            event = eventRecordRepository.create();
            // todo: 去除魔法数字
            event.init(eventPayload, svcName, LocalDateTime.now(), Duration.ofDays(1), 30);
            event.beginDelivery(LocalDateTime.now());
            eventRecordRepository.save(event);
        }
        try {
            String destination = event.getEventType();
            destination = environment.resolvePlaceholders(destination);
            if (destination != null && !destination.isEmpty()) {
                rocketMQTemplate.asyncSend(destination, event.getPayload(), new DomainEventSendCallback(event, eventRecordRepository));
            } else {
                rocketMqDomainEventSubscriberManager.trigger(event.getPayload());
                event.confirmedDelivered(LocalDateTime.now());
                eventRecordRepository.save(event);
            }
        } catch (Exception ex) {
            log.error(String.format("集成事件发布失败: %s", event.toString()), ex);
        }
    }
    @Slf4j
    public static class DomainEventSendCallback implements SendCallback {
        private EventRecord event;
        private final EventRecordRepository eventRecordRepository;

        public DomainEventSendCallback(EventRecord event, EventRecordRepository eventRecordRepository) {
            this.event = event;
            this.eventRecordRepository = eventRecordRepository;
        }

        @Override
        public void onSuccess(SendResult sendResult) {
            // 修改事件消费状态
            if (event == null) {
                throw new DomainException(String.format("集成事件不存在 event = %s", event.toString()));
            }
            try {
                LocalDateTime now = LocalDateTime.now();
                event.confirmedDelivered(now);
                eventRecordRepository.save(event);
                log.info(String.format("集成事件发送成功, destination=%s, body=%s", event.getEventType(), JSON.toJSONString(event.getPayload())));
            } catch (Exception ex) {
                log.error("本地事件库持久化失败", ex);
            }
        }

        @Override
        public void onException(Throwable throwable) {
            if (event == null) {
                throw new DomainException(String.format("集成事件不存在 event = %s", event.toString()));
            }
            try {
                log.error(String.format("集成事件发送失败, destination=%s, body=%s", event.getEventType(), JSON.toJSONString(event.getPayload())), throwable);
            } catch (Exception ex) {
                log.error("本地事件库持久化失败", ex);
            }
        }
    }
}
