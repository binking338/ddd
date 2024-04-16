package org.ddd.domain.repo;

/**
 * @author qiaohe
 * @date 2024/1/31
 */
public interface PersistListener<Entity> {

    void onPersist(Entity entity);

    /**
     * 新增实体时
     * @param entity
     */
    void onCreate(Entity entity);

    /**
     * 更新实体时
     * @param entity
     */
    void onUpdate(Entity entity);

    /**
     * 删除实体时
     * @param entity
     */
    void onDelete(Entity entity);

}
