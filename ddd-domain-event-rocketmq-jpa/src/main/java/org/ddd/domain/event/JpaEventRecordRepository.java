package org.ddd.domain.event;

import lombok.RequiredArgsConstructor;
import org.ddd.domain.event.persistence.Event;
import org.ddd.domain.event.persistence.EventJpaRepository;
import org.ddd.domain.event.persistence.EventRecordImpl;

/**
 * @author qiaohe
 * @date 2023/9/9
 */
@RequiredArgsConstructor
public class JpaEventRecordRepository implements EventRecordRepository {
    private final EventJpaRepository eventJpaRepository;

    @Override
    public EventRecord create() {
        return new EventRecordImpl();
    }

    @Override
    public void save(EventRecord eventRecord) {
        Event event = eventJpaRepository.saveAndFlush(((EventRecordImpl) eventRecord).getEvent());
        ((EventRecordImpl) eventRecord).resume(event);
    }
}
