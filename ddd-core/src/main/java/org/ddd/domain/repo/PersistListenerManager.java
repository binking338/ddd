package org.ddd.domain.repo;

/**
 * @author qiaohe
 * @date 2024/1/31
 */
public interface PersistListenerManager {
    <Entity> void onPersist(Entity entity);

    <Entity> void onCreate(Entity entity);

    <Entity> void onUpdate(Entity entity);

    <Entity> void onDelete(Entity entity);
}
