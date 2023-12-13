package org.ddd.domain.event.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * @author qiaohe
 * @date 2023/8/15
 */
public interface EventJpaRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {
}
