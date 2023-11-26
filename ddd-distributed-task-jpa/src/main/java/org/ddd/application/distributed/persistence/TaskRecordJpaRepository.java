package org.ddd.application.distributed.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * @author qiaohe
 * @date 2023/8/28
 */
public interface TaskRecordJpaRepository extends JpaRepository<TaskRecord, Long>, JpaSpecificationExecutor<TaskRecord> {
}
