package org.ddd.domain.event;


import lombok.RequiredArgsConstructor;
import org.ddd.domain.event.persistence.EventRecordImpl;
import org.ddd.domain.event.persistence.EventRecordImplJpaRepository;

/**
 * @author qiaohe
 * @date 2023/9/9
 */
@RequiredArgsConstructor
public class JpaEventRecordRepository implements EventRecordRepository {
    private final EventRecordImplJpaRepository eventRecordImplJpaRepository;

    @Override
    public EventRecord create() {
        return new EventRecordImpl();
    }

    @Override
    public EventRecord save(EventRecord event) {
        return eventRecordImplJpaRepository.saveAndFlush(((EventRecordImpl) event));
    }
}
