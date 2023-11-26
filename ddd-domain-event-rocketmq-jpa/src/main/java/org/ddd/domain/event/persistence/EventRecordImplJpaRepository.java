package org.ddd.domain.event.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * @author qiaohe
 * @date 2023/8/15
 */
public interface EventRecordImplJpaRepository extends JpaRepository<EventRecordImpl, Long>, JpaSpecificationExecutor<EventRecordImpl> {
}
