package org.ddd.application.distributed;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.ddd.application.distributed.annotation.SagaProcess;
import org.ddd.application.distributed.annotation.SagaRollback;
import org.ddd.application.distributed.persistence.Saga;
import org.ddd.application.distributed.persistence.SagaJpaRepository;
import org.ddd.share.annotation.Retry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.ddd.share.Constants.CONFIG_KEY_4_SVC_NAME;

/**
 * @author qiaohe
 * @date 2023/12/30
 */
@Slf4j
public abstract class SagaStateMachine<Context> {
    @Autowired
    private SagaJpaRepository sagaJpaRepository;

    @Value(CONFIG_KEY_4_SVC_NAME)
    protected String svcName;

    private Optional<Saga> querySaga(String uuid) {
        Optional<Saga> saga = sagaJpaRepository.findAll((root, query, cb) -> {
            query.where(
                    cb.and(
                            cb.equal(root.get(Saga.F_SAGA_UUID), uuid),
                            cb.equal(root.get(Saga.F_SVC_NAME), svcName)
                    )
            );
            return null;
        }, PageRequest.of(0, 1)).stream().findFirst();
        return saga;
    }

    public Saga init(Context context, String uuid) {
        if (!StringUtils.isEmpty(uuid)) {
            Saga saga = querySaga(uuid).orElse(null);
            if (saga != null) {
                return saga;
            }
        }
        Saga saga = Saga.builder().build();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextTryTime = getNextTryTime(now, 0);
        saga.init(now, svcName, getBizType(), context, uuid, nextTryTime, expireInSeconds(), retryTimes(), new ArrayList<>());
        SagaWrapper sagaWrapper = new SagaWrapper();
        sagaWrapper.setSaga(saga);
        sagaWrapper.saveAndFlush(sagaJpaRepository);
        saga.startRunning(now, nextTryTime);
        return sagaWrapper.getSaga();
    }

    public Saga holdState4Run(Saga saga, LocalDateTime time) {
        LocalDateTime now = time;
        LocalDateTime nextTryTime = getNextTryTime(now, saga.getTriedTimes());
        saga.startRunning(now, nextTryTime);
        SagaWrapper sagaWrapper = new SagaWrapper();
        sagaWrapper.setSaga(saga);
        sagaWrapper.saveAndFlush(sagaJpaRepository);
        return sagaWrapper.getSaga();
    }


    public Saga run(Saga saga) {
        if (saga.isRunnning(LocalDateTime.now())) {
            SagaWrapper sagaWrapper = new SagaWrapper();
            sagaWrapper.setSaga(saga);
            configProcess((Context) saga.getContext(), sagaWrapper);
            sagaWrapper.getSaga().finishRunning();
            if (sagaWrapper.getSaga().isFailed()) {
                return rollback(holdState4Rollback(sagaWrapper.getSaga(), LocalDateTime.now()));
            }
            sagaWrapper.saveAndFlush(sagaJpaRepository);
            return sagaWrapper.getSaga();
        } else {
            return saga;
        }
    }

    public Saga holdState4Rollback(Saga saga, LocalDateTime time) {
        LocalDateTime now = time;
        LocalDateTime nextTryTime = getNextTryTime(now, saga.getTriedTimes());
        saga.startRollback(now, nextTryTime);
        SagaWrapper sagaWrapper = new SagaWrapper();
        sagaWrapper.setSaga(saga);
        sagaWrapper.saveAndFlush(sagaJpaRepository);
        return sagaWrapper.getSaga();
    }

    public Saga rollback(Saga saga) {
        if (saga.isRollbacking(LocalDateTime.now())) {
            SagaWrapper sagaWrapper = new SagaWrapper();
            sagaWrapper.setSaga(saga);
            configRollback(sagaWrapper);
            sagaWrapper.getSaga().finishRollback();
            sagaWrapper.saveAndFlush(sagaJpaRepository);
            return sagaWrapper.getSaga();
        } else {
            return saga;
        }
    }

    /**
     * 业务类型标志
     *
     * @return
     */
    protected String getBizType() {
        return this.getClass().getName();
    }

    /**
     * 上下文类型
     *
     * @return
     */
    protected abstract Class<Context> getContextClass();

    /**
     * 事务过期时长, (单位：秒)
     *
     * @return
     */
    protected int expireInSeconds() {
        Retry retry = this.getClass().getAnnotation(Retry.class);
        if (retry != null && retry.expireAfter() > 0) {
            return retry.expireAfter();
        }
        // 默认1天
        return 60 * 60 * 24 * 1;
    }

    /**
     * 重试次数
     *
     * @return
     */
    protected int retryTimes() {
        Retry retry = this.getClass().getAnnotation(Retry.class);
        if (retry != null && retry.retryTimes() > 0) {
            return retry.retryTimes();
        }
        return 3;
    }

    /**
     * 获取下次尝试间隔时间（单位：秒）
     *
     * @param now
     * @param triedTimes 输入 >= 0
     * @return
     */
    protected LocalDateTime getNextTryTime(LocalDateTime now, int triedTimes) {
        Retry retry = this.getClass().getAnnotation(Retry.class);
        if (retry != null && retry.retryIntervals() != null && retry.retryIntervals().length > 0) {
            int index = triedTimes - 1;
            if (index >= retry.retryIntervals().length) {
                index = retry.retryIntervals().length - 1;
            } else if (index < 0) {
                index = 0;
            }
            return now.plusSeconds(retry.retryIntervals()[index]);
        }
        return now.plusSeconds(600);
    }

    protected void configProcess(Context context, SagaWrapper saga) {
        Class clazz = this.getClass();
        List<Method> sagaProcessMethods = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getAnnotation(SagaProcess.class) != null)
                .sorted((m1, m2) -> m1.getAnnotation(SagaProcess.class).code() - m2.getAnnotation(SagaProcess.class).code())
                .collect(Collectors.toList());
        if (sagaProcessMethods.size() == 0) {
            log.error("SAGA type=[" + clazz.getTypeName() + "]没有声明任何SagaProcess方法！");
            throw new RuntimeException("没有声明任何SagaProcess方法！");
        }
        Object input = context;
        Object output = null;
        for (Method method : sagaProcessMethods) {
            output = process(method, input, saga);
            input = output;
        }
    }

    protected void configRollback(SagaWrapper saga) {
        Class clazz = this.getClass();
        List<Method> sagaRollbackMethods = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getAnnotation(SagaRollback.class) != null)
                .sorted((m1, m2) -> m2.getAnnotation(SagaRollback.class).code() - m1.getAnnotation(SagaRollback.class).code())
                .collect(Collectors.toList());
        if (sagaRollbackMethods.size() == 0) {
            return;
        }
        for (Method method : sagaRollbackMethods) {
            if (!rollback(method, saga)) {
                return;
            }
        }
    }


    protected <Input, Output> Output process(ProcessHandler<Input, Output, Context> handler, Input input, int code, String name, SagaWrapper saga) {
        Saga.SagaProcess process = saga.getSaga().findProcess(code);
        LocalDateTime now = LocalDateTime.now();
        if (process == null) {
            process = Saga.SagaProcess.builder().build();
            process.init(now, code, name);
            process.startRunning(now, input);
            saga.getSaga().addProcess(process);
            saga.saveAndFlush(sagaJpaRepository);
        } else {
            if (Saga.SagaState.DONE.equals(process.getProcessState())) {
                return (Output) process.getOutput();
            } else {
                process.startRunning(now, input);
                saga.saveAndFlush(sagaJpaRepository);
            }
        }
        process = saga.getSaga().findProcess(code);
        Output output = null;
        try {
            output = handler.process(input, (Context) saga.getSaga().getContext());
            process.finishRunning(output);
            saga.saveAndFlush(sagaJpaRepository);
        } catch (Exception ex) {
            process.fail(ex);
            saga.saveAndFlush(sagaJpaRepository);
        }
        return output;
    }

    protected <Input, Output> boolean rollback(RollbackHandler<Input, Output, Context> handler, int code, SagaWrapper saga) {
        Saga.SagaProcess process = saga.getSaga().findProcess(code);
        if (process == null) {
            return false;
        } else if (Saga.SagaState.ROLLBACKED.equals(process.getProcessState())) {
            return true;
        }
        process.startRollback();
        saga.saveAndFlush(sagaJpaRepository);

        Context context = (Context) saga.getSaga().getContext();
        try {
            if(handler.rollback((Input) process.getInput(), (Output) process.getOutput(), context)) {
                process = saga.getSaga().findProcess(code);
                process.finishRollback();
                saga.saveAndFlush(sagaJpaRepository);
                return true;
            }
        } catch (Exception ex) {
            process.fail(ex);
            saga.saveAndFlush(sagaJpaRepository);
        }
        return false;
    }

    protected <Input, Output> boolean rollback(Method method, SagaWrapper saga) {
        SagaRollback annotation = method.getAnnotation(SagaRollback.class);
        Object _this = this;

        Class<?>[] parameterTypes = method.getParameterTypes();
        RollbackHandler<Input, Output, Context> handler = (i, o, c) -> {
            Object[] params = null;
            if (parameterTypes.length == 0) {
                params = new Object[0];
            } else if (parameterTypes.length == 1) {
                if (c != null && c.getClass().equals(parameterTypes[0])) {
                    params = new Object[]{c};
                } else if (i != null && i.getClass().equals(parameterTypes[0])) {
                    params = new Object[]{i};
                } else if (o != null && o.getClass().equals(parameterTypes[0])) {
                    params = new Object[]{o};
                } else {
                    params = new Object[]{null};
                }
            } else if (parameterTypes.length == 2) {
                params = new Object[]{i, o};
            } else if (parameterTypes.length == 3) {
                params = new Object[]{i, o, c};
            }
            Object result = null;
            try {
                result = method.invoke(_this, params);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            if(result instanceof Boolean){
                return ((Boolean) result).booleanValue();
            }
            return true;
        };
        return rollback(handler, annotation.code(), saga);
    }

    protected <Input, Output> Output process(Method method, Input input, SagaWrapper saga) {
        SagaProcess annotation = method.getAnnotation(SagaProcess.class);
        Object _this = this;

        Class<?>[] parameterTypes = method.getParameterTypes();
        ProcessHandler<Input, Output, Context> handler = (Input i, Context c) -> {
            Object[] params = null;
            if (parameterTypes.length == 1) {
                if (c != null && c.getClass().equals(parameterTypes[0])) {
                    params = new Object[]{c};
                } else {
                    params = new Object[]{i};
                }
            } else if (parameterTypes.length == 2) {
                if (c != null && c.getClass().equals(parameterTypes[0])) {
                    params = new Object[]{c, i};
                } else {
                    params = new Object[]{i, c};
                }
            }
            Output output = null;
            try {
                output = (Output) method.invoke(_this, params);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            return output;
        };
        int code = annotation.code();
        String name = StringUtils.isEmpty(annotation.name()) ? method.getName() : annotation.name();
        return process(handler, input, code, name, saga);
    }

    @Data
    public static class SagaWrapper {
        Saga saga;

        protected void saveAndFlush(SagaJpaRepository sagaJpaRepository) {
            saga.syncContextData();
            saga = sagaJpaRepository.saveAndFlush(saga);
        }
    }

    public static interface ProcessHandler<Input, Output, Context> {
        Output process(Input input, Context context);
    }

    public static interface RollbackHandler<Input, Output, Context> {
        boolean rollback(Input input, Output output, Context context);
    }
}
