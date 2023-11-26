package org.ddd.application.distributed.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * @author <template/>
 * @date
 */
public interface SagaJpaRepository extends JpaRepository<Saga, Long>, JpaSpecificationExecutor<Saga> {
}
