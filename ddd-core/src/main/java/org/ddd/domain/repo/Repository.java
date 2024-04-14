package org.ddd.domain.repo;

import org.ddd.share.PageData;
import org.ddd.share.ListOrder;
import org.ddd.share.PageParam;

import java.util.List;
import java.util.Optional;

/**
 * 聚合仓储
 * @author qiaohe
 * @date 2023/8/12
 */
public interface Repository<Entity> {
    /**
     * 根据条件获取实体
     * @param condition
     * @return
     */
    Optional<Entity> getBy(Object condition);
    /**
     * 根据条件获取实体列表
     * @param condition
     * @param orders
     * @return
     */
    List<Entity> listBy(Object condition, List<ListOrder> orders);
    /**
     * 根据条件获取实体分页列表
     * @param condition
     * @param pageParam
     * @return
     */
    PageData<Entity> pageBy(Object condition, PageParam pageParam);

    /**
     * 根据条件获取实体计数
     * @param condition
     * @return
     */
    long count(Object condition);
}
