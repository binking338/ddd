package org.ddd.application.distributed.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * @author qiaohe
 * @date 2023/12/13
 */
public interface ArchivedSagaJpaRepository  extends JpaRepository<ArchivedSaga, Long>, JpaSpecificationExecutor<ArchivedSaga> {
}
