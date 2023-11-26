package org.ddd.application.distributed;

import lombok.extern.slf4j.Slf4j;
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
    private Map<Class, SagaStateMachine> sagaStateMachineContextClassMap;
    private Map<String, SagaStateMachine> sagaStateMachineBizTypeMap;

    public SagaSupervisor(List<SagaStateMachine> sagaStateMachineContextClassMap) {
        if (sagaStateMachineContextClassMap != null && !sagaStateMachineContextClassMap.isEmpty()) {
            this.sagaStateMachineContextClassMap = sagaStateMachineContextClassMap.stream().collect(Collectors.toMap(ssm -> ssm.getContextClass(), ssm -> ssm));
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
        return run(context, true);
    }

    /**
     * 执行Saga流程
     *
     * @param context
     * @param runImmediately
     * @param <Context>
     * @return
     */
    public <Context> Saga run(Context context, boolean runImmediately) {
        Assert.notNull(context, "coontext 参数不得为空");
        if (!this.sagaStateMachineContextClassMap.containsKey(context.getClass())) {
            throw new IllegalArgumentException("context 传入参数类型不支持");
        }
        SagaStateMachine<Context> sagaStateMachine = sagaStateMachineContextClassMap.get(context.getClass());
        Saga saga = sagaStateMachine.run(context, runImmediately);
        return saga;
    }

    public <Context> Saga beginResume(Saga saga, LocalDateTime now) {
        Assert.notNull(saga, "saga 参数不得为空");
        SagaStateMachine<Context> sagaStateMachine = sagaStateMachineBizTypeMap.get(saga.getBizType());
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
     * @param <Context>
     * @return
     */
    public <Context> Saga resume(Saga saga) {
        Assert.notNull(saga, "saga 参数不得为空");
        SagaStateMachine<Context> sagaStateMachine = sagaStateMachineBizTypeMap.get(saga.getBizType());
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
     * @param <Context>
     * @return
     */
    public <Context> Saga beginnRollback(Saga saga, LocalDateTime now) {
        Assert.notNull(saga, "saga 参数不得为空");
        SagaStateMachine<Context> sagaStateMachine = sagaStateMachineBizTypeMap.get(saga.getBizType());
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
     * @param <Context>
     * @return
     */
    public <Context> Saga rollback(Saga saga) {
        Assert.notNull(saga, "saga 参数不得为空");
        SagaStateMachine<Context> sagaStateMachine = sagaStateMachineBizTypeMap.get(saga.getBizType());
        if (!sagaStateMachine.getContextClass().getName().equalsIgnoreCase(saga.getContextDataType())) {
            throw new UnsupportedOperationException("bizType不匹配 sagaId=" + saga.getId());
        }
        Saga newSaga = sagaStateMachine.rollback(saga);
        return newSaga;
    }
}
