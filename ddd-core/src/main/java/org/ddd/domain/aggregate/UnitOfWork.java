package org.ddd.domain.aggregate;

import org.springframework.transaction.annotation.Propagation;

/**
 * UnitOfWork模式
 * @author qiaohe
 * @date 2023/8/5
 */
public interface UnitOfWork {
    /**
     * 新增或更新持久化记录
     * @param entity
     */
    void persist(Object entity);

    /**
     * 移除持久化记录
     * @param entity
     */
    void remove(Object entity);

    /**
     * 提交事务
     */
    void save();

    /**
     * 提交事务
     * @param propagation
     */
    void save(Propagation propagation);

    /**
     * 重置上下文
     */
    void reset();
}
