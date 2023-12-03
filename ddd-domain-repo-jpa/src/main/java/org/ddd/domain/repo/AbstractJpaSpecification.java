package org.ddd.domain.repo;

/**
 * @author qiaohe
 * @date 2023/8/13
 */
public abstract class AbstractJpaSpecification<Entity> implements Specification<Entity> {
    public abstract Class<Entity> forEntityClass();

    public abstract Result specify(Entity entity);
}
