package org.ddd.application.distributed;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ddd.application.distributed.persistence.Saga;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author <template/>
 * @date
 */
@Slf4j
public class SagaSupervisor {
    private Map<Class, List<SagaStateMachine>> sagaStateMachineContextClassMap;
    private Map<String, SagaStateMachine> sagaStateMachineBizTypeMap;

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
        if(this.sagaStateMachineContextClassMap.get(context.getClass()).size() != 1){
            throw new IllegalArgumentException("存在多个saga流程支持该context类型: " + context.getClass().getName());
        }
        SagaStateMachine<Context> sagaStateMachine = sagaStateMachineContextClassMap.get(context.getClass()).get(0);
        Saga saga = sagaStateMachine.run(context, runImmediately, uuid);
        return saga;
    }

    /**
     * 执行Saga流程
     *
     * @param bizType
     * @param context
     * @return
     */
    public Saga run(String bizType, Object context){
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
        if(!this.sagaStateMachineBizTypeMap.containsKey(bizType)){
            throw new IllegalArgumentException("bizType 传入参数不支持: "+ bizType);
        }
        SagaStateMachine sagaStateMachine = sagaStateMachineBizTypeMap.get(bizType);
        Saga saga = sagaStateMachine.run(context, runImmediately, uuid);
        return saga;
    }


    /**
     * 开始复原Saga
     *
     * @param saga
     * @param now
     * @return
     */
    public Saga beginResume(Saga saga, LocalDateTime now) {
        Assert.notNull(saga, "saga 参数不得为空");
        SagaStateMachine sagaStateMachine = sagaStateMachineBizTypeMap.get(saga.getBizType());
        if (!sagaStateMachine.getContextClass().getName().equalsIgnoreCase(saga.getContextDataType())) {
            throw new UnsupportedOperationException("bizType不匹配 sagaId=" + saga.getId());
        }
        Saga newSaga = sagaStateMachine.beginResume(saga, now);
        return newSaga;
    }

    /**
     * 恢复Saga流程
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
        Saga newSaga = sagaStateMachine.resume(saga);
        return newSaga;
    }

    /**
     * 回滚Saga流程
     *
     * @param saga
     * @return
     */
    public Saga beginnRollback(Saga saga, LocalDateTime now) {
        Assert.notNull(saga, "saga 参数不得为空");
        SagaStateMachine sagaStateMachine = sagaStateMachineBizTypeMap.get(saga.getBizType());
        if (!sagaStateMachine.getContextClass().getName().equalsIgnoreCase(saga.getContextDataType())) {
            throw new UnsupportedOperationException("bizType不匹配 sagaId=" + saga.getId());
        }
        Saga newSaga = sagaStateMachine.beginRollback(saga, now);
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
        Saga newSaga = sagaStateMachine.rollback(saga);
        return newSaga;
    }
}
