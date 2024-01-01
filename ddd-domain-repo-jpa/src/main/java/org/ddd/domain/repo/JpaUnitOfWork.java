package org.ddd.domain.repo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ddd.domain.event.*;
import org.ddd.domain.event.annotation.DomainEvent;
import org.ddd.share.DomainException;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.ddd.share.Constants.CONFIG_KEY_4_SVC_NAME;

/**
 * @author qiaohe
 * @date 2023/8/13
 */
@RequiredArgsConstructor
@Slf4j
public class JpaUnitOfWork implements UnitOfWork {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final DomainEventSupervisor domainEventSupervisor;
    private final DomainEventPublisher domainEventPublisher;
    private final DomainEventSubscriberManager domainEventSubscriberManager;
    private final EventRecordRepository eventRecordRepository;
    private final JpaSpecificationManager jpaSpecificationManager;

    private ThreadLocal<Set<Object>> persistedEntitiesThreadLocal = new ThreadLocal<>();
    private ThreadLocal<Set<Object>> removedEntitiesThreadLocal = new ThreadLocal<>();

    public void persist(Object entity) {
        if (persistedEntitiesThreadLocal.get() == null) {
            persistedEntitiesThreadLocal.set(new HashSet<>());
        } else if (persistedEntitiesThreadLocal.get().contains(entity)) {
            return;
        }
        persistedEntitiesThreadLocal.get().add(entity);
    }

    public void remove(Object entity) {
        if (removedEntitiesThreadLocal.get() == null) {
            removedEntitiesThreadLocal.set(new HashSet<>());
        } else if (removedEntitiesThreadLocal.get().contains(entity)) {
            return;
        }
        removedEntitiesThreadLocal.get().add(entity);
    }

    public void save() {
        save(Propagation.REQUIRED);
    }

    public void save(Propagation propagation) {
        Set<Object> persistEntityList = null;
        if (persistedEntitiesThreadLocal.get() != null) {
            persistEntityList = persistedEntitiesThreadLocal.get().stream().collect(Collectors.toSet());
            persistedEntitiesThreadLocal.get().clear();
        } else {
            persistEntityList = new HashSet<>();
        }
        Set<Object> deleteEntityList = null;
        if (removedEntitiesThreadLocal.get() != null) {
            deleteEntityList = removedEntitiesThreadLocal.get().stream().collect(Collectors.toSet());
            removedEntitiesThreadLocal.get().clear();
        } else {
            deleteEntityList = new HashSet<>();
        }
        for (Object entity : persistenceContextEntities()) {
            // 如果不在删除列表中，则加入保存列表
            if (!deleteEntityList.contains(entity)) {
                persistEntityList.add(entity);
            }
        }
        Set<Object>[] saveAndDeleteEntityList = new Set[]{persistEntityList, deleteEntityList};
        save(input -> {
            Set<Object> persistEntities = input[0];
            Set<Object> deleteEntities = input[1];
            specifyPersistedEntites(persistEntities);
            boolean flush = false;
            List<Object> refreshEntityList = null;
            if (persistEntities != null && !persistEntities.isEmpty()) {
                flush = true;
                for (Object entity : persistEntities) {
                    if (!getEntityManager().contains(entity)) {
                        Object id = null;
                        try {
                            id = entity.getClass().getMethod("getId").invoke(entity);
                        } catch (Exception _ex) {
                            /* we don't care */
                        }
                        if (id != null) {
                            getEntityManager().merge(entity);
                        } else {
                            getEntityManager().persist(entity);
                            if (refreshEntityList == null) {
                                refreshEntityList = new ArrayList<>();
                            }
                            refreshEntityList.add(entity);
                        }
                    }
                }
            }
            if (deleteEntities != null && !deleteEntities.isEmpty()) {
                flush = true;
                for (Object entity : deleteEntities) {
                    if (getEntityManager().contains(entity)) {
                        getEntityManager().remove(entity);
                    } else {
                        getEntityManager().remove(getEntityManager().merge(entity));
                    }
                }
            }
            if (flush) {
                getEntityManager().flush();
                if (refreshEntityList != null && !refreshEntityList.isEmpty()) {
                    for (Object entity : refreshEntityList) {
                        getEntityManager().refresh(entity);
                    }
                }
            }

            publishTransactionEvent(domainEventSupervisor.getEvents());
            return null;
        }, saveAndDeleteEntityList, propagation);
    }

    public void reset() {
        persistedEntitiesThreadLocal.remove();
        removedEntitiesThreadLocal.remove();
    }

    @Getter
    @PersistenceContext
    protected EntityManager entityManager;
    protected static JpaUnitOfWork instance;

    @Value("${ddd.domain.JpaUnitOfWork.retrieveCountWarnThreshold:3000}")
    private int RETRIEVE_COUNT_WARN_THRESHOLD;

    public interface QueryBuilder<R, F> {
        void build(CriteriaBuilder cb, CriteriaQuery<R> cq, Root<F> root);
    }

    /**
     * 自定义查询
     * 期待返回一条记录，数据异常返回0条或多条记录将抛出异常
     *
     * @param resultClass
     * @param fromEntityClass
     * @param queryBuilder
     * @param <R>
     * @param <F>
     * @return
     */
    public <R, F> R one(Class<R> resultClass, Class<F> fromEntityClass, QueryBuilder<R, F> queryBuilder) {
        CriteriaBuilder criteriaBuilder = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<R> criteriaQuery = criteriaBuilder.createQuery(resultClass);
        Root<F> root = criteriaQuery.from(fromEntityClass);
        queryBuilder.build(criteriaBuilder, criteriaQuery, root);
        R result = getEntityManager().createQuery(criteriaQuery).getSingleResult();
        return result;
    }

    /**
     * 自定义查询
     * 返回0条或多条记录
     *
     * @param resultClass
     * @param fromEntityClass
     * @param queryBuilder
     * @param <R>
     * @param <F>
     * @return
     */
    public <R, F> List<R> list(Class<R> resultClass, Class<F> fromEntityClass, QueryBuilder<R, F> queryBuilder) {
        CriteriaBuilder criteriaBuilder = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<R> criteriaQuery = criteriaBuilder.createQuery(resultClass);
        Root<F> root = criteriaQuery.from(fromEntityClass);
        queryBuilder.build(criteriaBuilder, criteriaQuery, root);
        List<R> results = getEntityManager().createQuery(criteriaQuery).getResultList();
        if (results.size() > RETRIEVE_COUNT_WARN_THRESHOLD) {
            log.warn("查询记录数过多: retrieve_count=" + results.size());
        }
        return results;
    }

    /**
     * 自定义查询
     * 如果存在符合筛选条件的记录，返回第一条记录
     *
     * @param resultClass
     * @param fromEntityClass
     * @param queryBuilder
     * @param <R>
     * @param <F>
     * @return
     */
    public <R, F> Optional<R> firstOne(Class<R> resultClass, Class<F> fromEntityClass, QueryBuilder<R, F> queryBuilder) {
        CriteriaBuilder criteriaBuilder = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<R> criteriaQuery = criteriaBuilder.createQuery(resultClass);
        Root<F> root = criteriaQuery.from(fromEntityClass);
        queryBuilder.build(criteriaBuilder, criteriaQuery, root);
        List<R> results = getEntityManager().createQuery(criteriaQuery)
                .setFirstResult(0)
                .setMaxResults(1)
                .getResultList();
        return results.stream().findFirst();
    }

    /**
     * 自定义查询
     * 获取分页列表
     *
     * @param resultClass
     * @param fromEntityClass
     * @param queryBuilder
     * @param pageIndex
     * @param pageSize
     * @param <R>
     * @param <F>
     * @return
     */
    public <R, F> List<R> page(Class<R> resultClass, Class<F> fromEntityClass, QueryBuilder<R, F> queryBuilder, int pageIndex, int pageSize) {
        CriteriaBuilder criteriaBuilder = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<R> criteriaQuery = criteriaBuilder.createQuery(resultClass);
        Root<F> root = criteriaQuery.from(fromEntityClass);
        queryBuilder.build(criteriaBuilder, criteriaQuery, root);
        List<R> results = getEntityManager().createQuery(criteriaQuery)
                .setFirstResult(pageSize * pageIndex).setMaxResults(pageSize)
                .getResultList();
        return results;
    }

    /**
     * 自定义查询
     * 返回查询计数
     *
     * @param fromEntityClass
     * @param queryBuilder
     * @param <F>
     * @return
     */
    public <F> long count(Class<F> fromEntityClass, QueryBuilder<Long, F> queryBuilder) {
        CriteriaBuilder criteriaBuilder = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Long> criteriaQuery = criteriaBuilder.createQuery(Long.class);
        Root<F> root = criteriaQuery.from(fromEntityClass);
        queryBuilder.build(criteriaBuilder, criteriaQuery, root);
        long total = getEntityManager().createQuery(criteriaQuery).getSingleResult().longValue();
        return total;
    }


    /**
     * 事务执行句柄
     */
    public interface TransactionHandler<I, O> {
        O exec(I input);
    }

    /**
     * 事务保存，自动发送领域事件
     *
     * @param transactionHandler
     * @param propagation
     * @param <I>
     * @param <O>
     * @return
     */
    public <I, O> O save(TransactionHandler<I, O> transactionHandler, I i, Propagation propagation) {
        O result = null;
        switch (propagation) {
            case SUPPORTS:
                result = instance.supports(transactionHandler, i);
                break;
            case NOT_SUPPORTED:
                result = instance.notSupported(transactionHandler, i);
                break;
            case REQUIRES_NEW:
                result = instance.requiresNew(transactionHandler, i);
                break;
            case MANDATORY:
                result = instance.mandatory(transactionHandler, i);
                break;
            case NEVER:
                result = instance.never(transactionHandler, i);
                break;
            case NESTED:
                result = instance.nested(transactionHandler, i);
                break;
            case REQUIRED:
            default:
                result = instance.required(transactionHandler, i);
                break;
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public <I, O> O required(TransactionHandler<I, O> transactionHandler, I in) {
        return transactionWrapper(transactionHandler, in);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public <I, O> O requiresNew(TransactionHandler<I, O> transactionHandler, I in) {
        return transactionWrapper(transactionHandler, in);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.SUPPORTS)
    public <I, O> O supports(TransactionHandler<I, O> transactionHandler, I in) {
        return transactionWrapper(transactionHandler, in);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.NOT_SUPPORTED)
    public <I, O> O notSupported(TransactionHandler<I, O> transactionHandler, I in) {
        return transactionWrapper(transactionHandler, in);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.MANDATORY)
    public <I, O> O mandatory(TransactionHandler<I, O> transactionHandler, I in) {
        return transactionWrapper(transactionHandler, in);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.NEVER)
    public <I, O> O never(TransactionHandler<I, O> transactionHandler, I in) {
        return transactionWrapper(transactionHandler, in);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.NESTED)
    public <I, O> O nested(TransactionHandler<I, O> transactionHandler, I in) {
        return transactionWrapper(transactionHandler, in);
    }

    protected <I, O> O transactionWrapper(TransactionHandler<I, O> transactionHandler, I in) {
        O result = null;
        if (transactionHandler != null) {
            result = transactionHandler.exec(in);
        }
        return result;
    }

    protected List<Object> persistenceContextEntities() {
        try {
            if (!((SessionImplementor) getEntityManager().getDelegate()).isClosed()) {
                org.hibernate.engine.spi.PersistenceContext persistenceContext = ((SessionImplementor) getEntityManager().getDelegate()).getPersistenceContext();
                Stream<Object> entitiesInPersistenceContext = Arrays.stream(persistenceContext.reentrantSafeEntityEntries()).map(e -> e.getKey());
                return entitiesInPersistenceContext.collect(Collectors.toList());
            }
        } catch (Exception ex) {
            log.debug("跟踪实体获取失败", ex);
        }
        return Collections.emptyList();
    }

    protected void specifyPersistedEntites(Set<Object> entities) {
        if (entities != null && !entities.isEmpty()) {
            for (Object entity : entities) {
                Specification.Result result = jpaSpecificationManager.specify(entity);
                if (!result.isPassed()) {
                    throw new DomainException(result.getMessage());
                }
            }
        }
    }

    /**
     * UoW事务成功提交事件
     */
    public static class TransactionCommittedEvent extends ApplicationEvent {
        @Getter
        List<Object> events;

        /**
         * Create a new {@code ApplicationEvent}.
         *
         * @param source the object on which the event initially occurred or with
         *               which the event is associated (never {@code null})
         */
        public TransactionCommittedEvent(Object source, List<Object> events) {
            super(source);
            this.events = events;
        }
    }

    /**
     * UoW事务正在提交事件
     */
    public static class TransactionCommitingEvent extends ApplicationEvent {
        @Getter
        List<Object> events;

        /**
         * Create a new {@code ApplicationEvent}.
         *
         * @param source the object on which the event initially occurred or with
         *               which the event is associated (never {@code null})
         */
        public TransactionCommitingEvent(Object source, List<Object> events) {
            super(source);
            this.events = events;
        }
    }

    @Value(CONFIG_KEY_4_SVC_NAME)
    private String svcName = null;

    protected void publishTransactionEvent(List<Object> eventPayloads) {
        List<Object> persistedEvents = new ArrayList<>(eventPayloads.size());
        List<Object> transientEvents = new ArrayList<>(eventPayloads.size());
        for (Object eventPayload : eventPayloads) {
            EventRecord event = eventRecordRepository.create();
            event.init(eventPayload, this.svcName, LocalDateTime.now(), Duration.ofMinutes(15), 13);
            event.beginDelivery(LocalDateTime.now());
            if (subscribeInTransaction(eventPayload)) {
                transientEvents.add(event);
            } else {
                eventRecordRepository.save(event);
                persistedEvents.add(event);
            }
        }
        domainEventSupervisor.reset();
        applicationEventPublisher.publishEvent(new TransactionCommitingEvent(this, transientEvents));
        applicationEventPublisher.publishEvent(new TransactionCommittedEvent(this, persistedEvents));
    }

    public boolean subscribeInTransaction(Object payload) {
        DomainEvent domainEvent = payload == null
                ? null
                : payload.getClass().getAnnotation(DomainEvent.class);
        if (domainEvent != null) {
            return !domainEvent.forceSubscribeAfterTransaction();
        } else {
            return true;
        }
    }

    @TransactionalEventListener(fallbackExecution = true, classes = TransactionCommittedEvent.class)
    public void onTransactionCommitted(TransactionCommittedEvent transactionCommittedEvent) {
        List<Object> events = transactionCommittedEvent.getEvents();
        if (events != null && !events.isEmpty()) {
            events.forEach(event -> {
                domainEventPublisher.publish(event);
            });
        }
    }

    @EventListener(classes = TransactionCommitingEvent.class)
    public void onTransactionCommiting(TransactionCommitingEvent transactionCommitingEvent) {
        List<Object> events = transactionCommitingEvent.getEvents();
        if (events != null && !events.isEmpty()) {
            events.forEach(event -> {
                domainEventSubscriberManager.trigger(event);
            });
        }
    }
}
