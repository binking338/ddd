package org.ddd.domain.event.persistence;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.ddd.domain.event.EventRecord;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * @author <template/>
 * @date
 */
@Slf4j
public class EventRecordImpl implements EventRecord {
    private Event event;

    public EventRecordImpl(){
        event = Event.builder().build();
    }

    public void resume(Event event){
        this.event = event;
    }

    public Event getEvent(){
        return event;
    }

    @Override
    public void init(Object payload, String svcName, LocalDateTime now, Duration expireAfter, int retryTimes) {
        Event.builder().build().init(payload, svcName, now, expireAfter, retryTimes);
    }

    @Override
    public boolean beginDelivery(LocalDateTime now) {
        return event.beginDelivery(now);
    }

    @Override
    public void confirmedDelivered(LocalDateTime now) {
        event.confirmedDelivered(now);
    }

    @Override
    public String getEventType() {
        return event.getEventType();
    }

    @Override
    public Object getPayload() {
        return event.getPayload();
    }
}
