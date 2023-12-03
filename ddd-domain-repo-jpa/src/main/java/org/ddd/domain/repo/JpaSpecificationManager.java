package org.ddd.domain.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Map;

/**
 * @author qiaohe
 * @date 2023/8/13
 */
@RequiredArgsConstructor
@Slf4j
public class JpaSpecificationManager implements SpecificationManager {
    private final List<AbstractJpaSpecification> specifications;
    private Map<Class, List<AbstractJpaSpecification>> specificationMap;

    @Override
    public <Entity> Specification.Result specify(Entity entity) {
        if(specificationMap == null){
            synchronized (this){
                if(specificationMap == null){
                    specificationMap = new java.util.HashMap<Class, List<AbstractJpaSpecification>>();
                    specifications.sort((a,b)->
                        a.getClass().getAnnotation(Order.class).value() - b.getClass().getAnnotation(Order.class).value()
                    );
                    for (AbstractJpaSpecification specification : specifications) {
                        if(!specificationMap.containsKey(specification.forEntityClass())){
                            specificationMap.put(specification.forEntityClass(), new java.util.ArrayList<AbstractJpaSpecification>());
                        }
                        List<AbstractJpaSpecification> specificationList = specificationMap.get(specification.forEntityClass());
                        specificationList.add(specification);
                    }
                }
            }
        }
        List<AbstractJpaSpecification> specifications = specificationMap.get(entity.getClass());
        if(specifications != null) {
            for (AbstractJpaSpecification specification : specifications) {
                Specification.Result result = specification.specify(entity);
                if (!result.isPassed()) {
                    return result;
                }
            }
        }
        return Specification.Result.pass();
    }
}
