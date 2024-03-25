package org.ddd.domain.repo;

/**
 * @author qiaohe
 * @date 2024/3/9
 */
public abstract class AbstractJpaPersistListener<Entity> implements PersistListener<Entity> {
    public abstract Class<Entity> forEntityClass();
}
