package org.ddd.domain.repo;

/**
 * @author qiaohe
 * @date 2024/1/31
 */
public interface PersistListener<Entity> {

    void onPersist(Entity entity);

    void onCreate(Entity entity);

    void onUpdate(Entity entity);

    void onDelete(Entity entity);

}
