package org.ddd.domain.event.impl;

import org.ddd.domain.event.DomainEventSupervisor;

import java.util.Collections;
import java.util.List;

/**
 * @author qiaohe
 * @date 2023/8/13
 */
public class DefaultDomainEventSupervisor implements DomainEventSupervisor {
    public static DomainEventSupervisor Instance = new DefaultDomainEventSupervisor();
    private final ThreadLocal<List<Object>> TL_EVENT_PAYLOADS = new ThreadLocal<List<Object>>();
    private final List<Object> EMPTY_EVENT_PAYLOADS = Collections.emptyList();

    public void attach(Object eventPayload) {
        List<Object> eventPayloads = TL_EVENT_PAYLOADS.get();
        if(eventPayloads == null){
            eventPayloads = new java.util.ArrayList<Object>();
            TL_EVENT_PAYLOADS.set(eventPayloads);
        }
        eventPayloads.add(eventPayload);
    }

    public void detach(Object eventPayload) {
        List<Object> eventPayloads = TL_EVENT_PAYLOADS.get();
        if(eventPayloads != null){
            eventPayloads.remove(eventPayload);
        }
    }

    public void reset() {
        TL_EVENT_PAYLOADS.remove();
    }

    public List<Object> getEvents() {
        List<Object> eventPayloads = TL_EVENT_PAYLOADS.get();
        return eventPayloads != null ? eventPayloads : EMPTY_EVENT_PAYLOADS;
    }
}
