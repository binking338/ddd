package org.ddd.domain.aggregate;

/**
 * 实体规格约束管理器
 * @author qiaohe
 * @date 2023/8/5
 */
public interface SpecificationManager {
    /**
     * 校验实体是否符合规格约束
     * @param entity
     * @return
     * @param <Entity>
     */
    <Entity> Specification.Result specify(Entity entity);
}
