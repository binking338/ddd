package org.ddd.domain.repo;

import lombok.RequiredArgsConstructor;
import org.ddd.share.ListOrder;
import org.ddd.share.PageData;
import org.ddd.share.PageParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author qiaohe
 * @date 2023/8/13
 */
@RequiredArgsConstructor
public abstract class AbstractJpaRepository<Entity, ID> implements Repository<Entity> {
    private final JpaSpecificationExecutor<Entity> jpaSpecificationExecutor;

    public Optional<Entity> getBy(Object condition) {
        return jpaSpecificationExecutor.findOne((org.springframework.data.jpa.domain.Specification<Entity>) condition);
    }

    public List<Entity> listBy(Object condition, List<ListOrder> orders) {
        Sort sort = Sort.unsorted();
        if (orders != null && !orders.isEmpty()) {
            sort = convertSort(orders);
        }
        List<Entity> entities = jpaSpecificationExecutor.findAll((org.springframework.data.jpa.domain.Specification<Entity>) condition, sort);
        return entities;
    }

    public PageData pageBy(Object condition, PageParam pageParam) {
        Page<Entity> page = jpaSpecificationExecutor.findAll((org.springframework.data.jpa.domain.Specification<Entity>) condition, convertPageable(pageParam));
        return convertPageData(page);
    }

    public long count(Object condition) {
        long result = jpaSpecificationExecutor.count((org.springframework.data.jpa.domain.Specification<Entity>) condition);
        return result;
    }

    private Sort convertSort(List<ListOrder> orders) {
        Sort sort = Sort.unsorted();
        if (orders != null && !orders.isEmpty()) {
            Sort.by(orders.stream().map(order -> {
                if (order.getDesc()) {
                    return Sort.Order.desc(order.getField());
                } else {
                    return Sort.Order.asc(order.getField());
                }
            }).collect(Collectors.toList()));
        }
        return sort;
    }

    private Pageable convertPageable(PageParam pageParam) {
        PageRequest pageRequest = null;

        Sort orders = convertSort(pageParam.getSort());
        pageRequest = PageRequest.of(pageParam.getPageNum() - 1, pageParam.getPageSize(), orders);
        return pageRequest;
    }

    private <T> PageData<T> convertPageData(Page<T> page) {
        return PageData.create(page.getPageable().getPageSize(), page.getPageable().getPageNumber() + 1, page.getTotalElements(), page.getContent());
    }
}
