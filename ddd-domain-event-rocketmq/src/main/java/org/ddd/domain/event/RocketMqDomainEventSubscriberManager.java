package org.ddd.domain.event;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddd.share.DomainException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Map;

/**
 * @author qiaohe
 * @date 2023/8/13
 */
@RequiredArgsConstructor
@Slf4j
public class RocketMqDomainEventSubscriberManager implements DomainEventSubscriberManager {
    private final List<RocketMqDomainEventSubscriber> subscribers;
    private final ApplicationEventPublisher applicationEventPublisher;
    private Map<Class, List<RocketMqDomainEventSubscriber>> subscriberMap = null;

    @Override
    public <Event> void trigger(Event eventPayload) {
        try {
            applicationEventPublisher.publishEvent(eventPayload);
        } catch (Exception e) {
            log.error("领域事件处理失败 eventPayload=" + JSON.toJSONString(eventPayload), e);
            throw new DomainException("领域事件处理失败 eventPayload=" + JSON.toJSONString(eventPayload), e);
        }
        if (subscriberMap == null) {
            synchronized (this) {
                if (subscriberMap == null) {
                    subscriberMap = new java.util.HashMap<Class, List<RocketMqDomainEventSubscriber>>();
                    subscribers.sort((a, b) ->
                            a.getClass().getAnnotation(Order.class).value() - b.getClass().getAnnotation(Order.class).value()
                    );
                    for (RocketMqDomainEventSubscriber subscriber : subscribers) {
                        if (subscriberMap.get(subscriber.forDomainEventClass()) == null) {
                            subscriberMap.put(subscriber.forDomainEventClass(), new java.util.ArrayList<RocketMqDomainEventSubscriber>());
                        }
                        subscriberMap.get(subscriber.forDomainEventClass()).add(subscriber);
                    }
                }
            }
        }
        List<RocketMqDomainEventSubscriber> subscribersForEvent = subscriberMap.get(eventPayload.getClass());
        if (subscribersForEvent == null || subscribersForEvent.isEmpty()) {
            return;
        }
        for (RocketMqDomainEventSubscriber<Event> subscriber : subscribersForEvent) {
            try {
                subscriber.onEvent(eventPayload);
            } catch (Exception e) {
                log.error("领域事件处理失败 eventPayload=" + JSON.toJSONString(eventPayload), e);
                throw new DomainException("领域事件处理失败 eventPayload=" + JSON.toJSONString(eventPayload), e);
            }
        }
    }

    public boolean hasSubscriber(Class eventClass) {
        return subscriberMap.containsKey(eventClass) && subscriberMap.get(eventClass).size() > 0;
    }
}
