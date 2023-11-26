package org.ddd.domain.event;

/**
 * @author qiaohe
 * @date 2023/8/13
 */
public abstract class RocketMqDomainEventSubscriber<Event> implements DomainEventSubscriber<Event> {
    public abstract Class<Event> forDomainEventClass();

    @Override
    public abstract void onEvent(Event o);
}
