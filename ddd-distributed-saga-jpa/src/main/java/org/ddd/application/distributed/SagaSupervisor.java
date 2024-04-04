package org.ddd.application.distributed;

import lombok.extern.slf4j.Slf4j;
import org.ddd.application.distributed.persistence.Saga;
import org.ddd.application.distributed.persistence.SagaJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.ddd.share.Constants.CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_THREADPOOLSIIZE;
import static org.ddd.share.Constants.CONFIG_KEY_4_SVC_NAME;

/**
 * @author <template/>
 * @date
 */
@Slf4j
public class SagaSupervisor {
    private Map<Class, List<SagaStateMachine>> sagaStateMachineContextClassMap;
    private Map<String, SagaStateMachine> sagaStateMachineBizTypeMap;

    @Value(CONFIG_KEY_4_SVC_NAME)
    protected String svcName;
    @Autowired
    private SagaJpaRepository sagaJpaRepository;

    @Value(CONFIG_KEY_4_DISTRIBUTED_SAGA_SCHEDULE_THREADPOOLSIIZE)
    private int threadPoolsize;
    private ThreadPoolExecutor executor = null;
    @PostConstruct
    public void init(){
        executor = new ThreadPoolExecutor(threadPoolsize, threadPoolsize, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    public SagaSupervisor(List<SagaStateMachine> sagaStateMachineContextClassMap) {
        if (sagaStateMachineContextClassMap != null && !sagaStateMachineContextClassMap.isEmpty()) {
            this.sagaStateMachineContextClassMap = sagaStateMachineContextClassMap.stream().collect(Collectors.groupingBy(ssm -> ssm.getContextClass()));
            this.sagaStateMachineBizTypeMap = sagaStateMachineContextClassMap.stream().collect(Collectors.toMap(ssm -> ssm.getBizType(), ssm -> ssm));
        } else {
            this.sagaStateMachineContextClassMap = Collections.emptyMap();
            this.sagaStateMachineBizTypeMap = Collections.emptyMap();
        }
    }

    /**
     * 获取支持的bizType
     *
     * @return
     */
    public Set<String> getSupportedBizTypes() {
        return this.sagaStateMachineBizTypeMap.keySet();
    }

    /**
     * 获取支持的上下文类型
     *
     * @return
     */
    public Set<Class> getSupportedContextClasses() {
        return this.sagaStateMachineContextClassMap.keySet();
    }

    /**
     * 执行Saga流程
     *
     * @param context
     * @param <Context>
     * @return
     */
    public <Context> Saga run(Context context) {
        return run(context, true, null);
    }

    /**
     * 执行Saga流程
     *
     * @param context
     * @param runImmediately
     * @param uuid
     * @param <Context>
     * @return
     */
    public <Context> Saga run(Context context, boolean runImmediately, String uuid) {
        Assert.notNull(context, "context 参数不得为空");
        if (!this.sagaStateMachineContextClassMap.containsKey(context.getClass())) {
            throw new IllegalArgumentException("context 传入参数不支持: " + context.getClass().getName());
        }
        if (this.sagaStateMachineContextClassMap.get(context.getClass()).size() != 1) {
            throw new IllegalArgumentException("存在多个saga流程支持该context类型: " + context.getClass().getName());
        }
        SagaStateMachine<Context> sagaStateMachine = sagaStateMachineContextClassMap.get(context.getClass()).get(0);
        Saga saga = sagaStateMachine.init(context, uuid);
        if(runImmediately){
            saga = sagaStateMachine.run(saga);
        }
        return saga;
    }

    /**
     * 执行Saga流程
     *
     * @param bizType
     * @param context
     * @return
     */
    public Saga run(String bizType, Object context) {
        return run(bizType, context, true, null);
    }

    /**
     * 执行Saga流程
     *
     * @param bizType
     * @param context
     * @param runImmediately
     * @param uuid
     * @return
     */
    public Saga run(String bizType, Object context, boolean runImmediately, String uuid) {
        Assert.notNull(context, "context 参数不得为空");
        if (!this.sagaStateMachineBizTypeMap.containsKey(bizType)) {
            throw new IllegalArgumentException("bizType 传入参数不支持: " + bizType);
        }
        SagaStateMachine sagaStateMachine = sagaStateMachineBizTypeMap.get(bizType);
        Saga saga = sagaStateMachine.init(context, uuid);
        if(runImmediately){
            saga = sagaStateMachine.run(saga);
        }
        return saga;
    }

    /**
     * 查询Saga
     *
     * @param uuid
     * @return
     */
    public Saga query(String uuid) {
        Optional<Saga> saga = sagaJpaRepository.findAll(((root, query, cb) -> {
            query.where(cb.and(
                    cb.equal(root.get(Saga.F_SAGA_UUID), uuid),
                    cb.equal(root.get(Saga.F_SVC_NAME), svcName)
            ));
            return null;
        }), PageRequest.of(0, 1)).stream().findFirst();
        return saga.orElse(null);
    }


    /**
     * 重试Saga
     *
     * @param saga
     * @return
     */
    public Saga resume(Saga saga) {
        Assert.notNull(saga, "saga 参数不得为空");
        SagaStateMachine sagaStateMachine = sagaStateMachineBizTypeMap.get(saga.getBizType());
        if (!sagaStateMachine.getContextClass().getName().equalsIgnoreCase(saga.getContextDataType())) {
            throw new UnsupportedOperationException("bizType不匹配 sagaId=" + saga.getId());
        }
        LocalDateTime now = LocalDateTime.now();

        final Saga newSaga = sagaStateMachine.holdState4Run(saga, now);
        if(newSaga.isRunnning(now)) {
            executor.submit(() -> sagaStateMachine.run(newSaga));
        } else if(newSaga.isFailed()) {
            rollback(newSaga);
        }
        return newSaga;
    }

    /**
     * 回滚Saga流程
     *
     * @param saga
     * @return
     */
    public Saga rollback(Saga saga) {
        Assert.notNull(saga, "saga 参数不得为空");
        SagaStateMachine sagaStateMachine = sagaStateMachineBizTypeMap.get(saga.getBizType());
        if (!sagaStateMachine.getContextClass().getName().equalsIgnoreCase(saga.getContextDataType())) {
            throw new UnsupportedOperationException("bizType不匹配 sagaId=" + saga.getId());
        }
        LocalDateTime now = LocalDateTime.now();
        final Saga newSaga = sagaStateMachine.holdState4Rollback(saga, now);
        if(newSaga.isRollbacking(now)) {
            executor.submit(() -> sagaStateMachine.rollback(newSaga));
        }
        return newSaga;
    }
}
