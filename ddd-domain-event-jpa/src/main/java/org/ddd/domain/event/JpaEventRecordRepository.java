package org.ddd.domain.event;

import lombok.RequiredArgsConstructor;
import org.ddd.domain.event.persistence.Event;
import org.ddd.domain.event.persistence.EventRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author qiaohe
 * @date 2023/9/9
 */
@RequiredArgsConstructor
public class JpaEventRecordRepository implements EventRecordRepository {
    private final EventRepository eventRepository;

    @Override
    public EventRecord create() {
        return new EventRecordImpl();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void save(EventRecord eventRecord) {
        EventRecordImpl eventRecordImpl = (EventRecordImpl) eventRecord;
        Event event = eventRepository.saveAndFlush(eventRecordImpl.getEvent());
        eventRecordImpl.resume(event);
    }
}
