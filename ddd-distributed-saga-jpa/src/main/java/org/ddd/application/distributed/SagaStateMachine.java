package org.ddd.application.distributed;

import com.alibaba.fastjson.JSON;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.ddd.application.distributed.annotation.SagaProcess;
import org.ddd.application.distributed.persistence.Saga;
import org.ddd.application.distributed.persistence.SagaJpaRepository;
import org.ddd.share.annotation.Retry;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.SystemPropertyUtils;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.ddd.share.Constants.CONFIG_KEY_4_SVC_NAME;

/**
 * @author <template/>
 * @date
 */
@Slf4j
public abstract class SagaStateMachine<Context> {
    @Autowired
    private SagaJpaRepository sagaJpaRepository;

    @Value(CONFIG_KEY_4_SVC_NAME)
    protected String svcName;
    protected Process<Context> process;

    @PostConstruct
    public void init() {
        this.process = config();
    }

    /**
     * 业务类型标志
     *
     * @return
     */
    protected String getBizType(){
        return this.getClass().getName();
    }

    /**
     * 上下文类型
     *
     * @return
     */
    protected abstract Class<Context> getContextClass();

    /**
     * 事务过期时长, (单位：分)
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
     * @param triedTimes 输入 >= 0
     * @return
     */
    protected int getNextTryIdleInSeconds(int triedTimes) {
        Retry retry = this.getClass().getAnnotation(Retry.class);
        if (retry != null && retry.retryIntervals() != null && retry.retryIntervals().length > 0) {
            int index = triedTimes - 1;
            if (index >= retry.retryIntervals().length) {
                index = retry.retryIntervals().length - 1;
            } else if (index < 0) {
                index = 0;
            }
            return retry.retryIntervals()[index];
        }
        return 600;
    }

    /**
     * 配置saga流程
     *
     * @return
     */
    protected Process<Context> config() {
        Object sagaStateMachine = this;
        Class clazz = this.getClass();
        Process<Context> process = null;
        List<Method> sagaProcessMethods = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getAnnotation(SagaProcess.class) != null)
                .collect(Collectors.toList());
        if (sagaProcessMethods.size() == 0) {
            log.error("SAGA type=[" + clazz.getTypeName() + "]没有声明任何SagaProcess方法！");
            throw new RuntimeException("没有声明任何SagaProcess方法！");
        }
        List<Method> startSagaProcessMethods = sagaProcessMethods.stream()
                .filter(m -> {
                    SagaProcess annotation = m.getAnnotation(SagaProcess.class);
                    return StringUtils.isEmpty(annotation.parent()) && StringUtils.isEmpty(annotation.preview());
                })
                .collect(Collectors.toList());
        if (startSagaProcessMethods.size() == 0) {
            log.error("SAGA type=[" + clazz.getTypeName() + "]没有声明起始SagaProcess方法！");
            throw new RuntimeException("没有声明起始SagaProcess方法！");
        } else if (startSagaProcessMethods.size() == 1) {
            process = transformProcess(startSagaProcessMethods.get(0), sagaProcessMethods, sagaStateMachine);
        } else {
            startSagaProcessMethods.sort(this::sagaCompare);
            Process<Context> currentProcess = null;
            for (Method m : startSagaProcessMethods) {
                if (currentProcess == null) {
                    currentProcess = transformProcess(m, sagaProcessMethods, sagaStateMachine);
                    process = currentProcess;
                } else {
                    currentProcess.addSub(transformProcess(m, sagaProcessMethods, sagaStateMachine));
                }
            }
        }
        return process;
    }

    protected Process<Context> transformProcess(Method processMethod, List<Method> allSagaProcessMethods, Object sagaStateMachine) {
        SagaProcess anno = processMethod.getAnnotation(SagaProcess.class);
        String processName = StringUtils.isNotEmpty(anno.name()) ? anno.name() : processMethod.getName();
        Process<Context> process = Process.of(anno.code(), processName, context -> {
            try {
                processMethod.invoke(sagaStateMachine, context);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        List<Method> subProcessMethods = allSagaProcessMethods.stream()
                .filter(m -> {
                    SagaProcess annoSub = m.getAnnotation(SagaProcess.class);
                    return StringUtils.equals(annoSub.parent(), processName);
                })
                .collect(Collectors.toList());

        subProcessMethods.sort(this::sagaCompare);
        for (Method subProcessMethod : subProcessMethods) {
            process.addSub(transformProcess(subProcessMethod, allSagaProcessMethods, sagaStateMachine));
        }
        Method nextProcessMethod = allSagaProcessMethods.stream()
                .filter(m -> {
                    SagaProcess annoNext = m.getAnnotation(SagaProcess.class);
                    return StringUtils.equals(annoNext.preview(), processName);
                })
                .findFirst()
                .orElse(null);
        if (nextProcessMethod != null) {
            process.then(transformProcess(nextProcessMethod, allSagaProcessMethods, sagaStateMachine));
        }
        return process;
    }

    protected Integer sagaCompare(Method a, Method b) {
        SagaProcess annoA = a.getAnnotation(SagaProcess.class);
        SagaProcess annoB = b.getAnnotation(SagaProcess.class);
        int codeComp = Integer.compare(annoA.code(), annoB.code());
        if (codeComp != 0) {
            return codeComp;
        } else {
            String processAName = StringUtils.isNotEmpty(annoA.name()) ? annoA.name() : a.getName();
            String processBName = StringUtils.isNotEmpty(annoB.name()) ? annoB.name() : b.getName();
            return StringUtils.compare(processAName, processBName);
        }
    }

    public Optional<Saga> queryByUuid(String uuid){
        Optional<Saga> saga = sagaJpaRepository.findOne(((root, query, cb) -> {
            query.where(cb.and(
                    cb.equal(root.get(Saga.F_SAGA_UUID), uuid),
                    cb.equal(root.get(Saga.F_BIZ_TYPE), getBizType()),
                    cb.equal(root.get(Saga.F_SVC_NAME), svcName)
            ));
            return null;
        }));
        return saga;
    }

    /**
     * 创建saga流程
     *
     * @param context
     * @return saga
     */
    public Saga run(Context context) {
        return run(context, true, null);
    }

    /**
     * 创建并执行saga流程
     *
     * @param context
     * @param runImmediately
     * @param uuid
     * @return
     */
    public Saga run(Context context, boolean runImmediately, String uuid) {
        if(StringUtils.isNotBlank(uuid)){
            Saga existSaga = queryByUuid(uuid).orElse(null);
            if (existSaga != null) {
                log.warn("Saga已提交，勿重复提交: " + uuid);
                return existSaga;
            }
        }
        Saga saga = build(context, runImmediately, uuid);
        if (runImmediately) {
            LocalDateTime now = LocalDateTime.now();
            saga = beginResume(saga, now);
            saga = resume(saga);
        } else {
            saga = sagaJpaRepository.saveAndFlush(saga);
        }
        return saga;
    }

    /**
     * 开始复原saga流程
     *
     * @param uuid
     * @param now
     * @return
     */
    public Saga beginResume(String uuid, LocalDateTime now) {
        Saga saga = queryByUuid(uuid)
                .orElseThrow(() -> new RuntimeException("[Saga Resume]saga不存在: " + uuid));
        return beginResume(saga, now);
    }

    /**
     * 开始复原saga流程
     *
     * @param saga
     * @param now
     * @return
     */
    public Saga beginResume(Saga saga, LocalDateTime now) {
        boolean started = saga.startRunning(now, now.plusSeconds(getNextTryIdleInSeconds(saga.getTriedTimes())));
        if (!started) {
            if (Saga.SagaState.EXPIRED.equals(saga.getSagaState()) || Saga.SagaState.FAILED.equals(saga.getSagaState())) {
                saga = sagaJpaRepository.saveAndFlush(saga);
                return saga;
            }
        }
        saga = sagaJpaRepository.saveAndFlush(saga);
        return saga;
    }

    /**
     * 复原saga流程
     *
     * @param uuid
     * @param now
     * @return
     */
    public Saga resume(String uuid, LocalDateTime now) {
        Saga saga = queryByUuid(uuid)
                .orElseThrow(() -> new RuntimeException("[Saga Resume]saga不存在: " + uuid));
        return resume(saga);
    }

    /**
     * 复原saga流程
     *
     * @param saga
     * @return
     */
    public Saga resume(Saga saga) {
        if (!this.getBizType().equals(saga.getBizType())) {
            log.error("bizType不匹配 sagaId=" + saga.getId());
            return null;
        }
        if (!Saga.SagaState.RUNNING.equals(saga.getSagaState())) {
            log.error("运行中的saga才可以复原 sagaId=" + saga.getId());
            return null;
        }
        Context context = JSON.parseObject(saga.getContextData(), getContextClass());
        saga = internalRun(saga, context, process);
        if (saga.getProcesses().stream().anyMatch(p -> !Saga.SagaState.DONE.equals(p.getProcessState()))) {
            return saga;
        }
        saga.finishRunning(context);
        saga = sagaJpaRepository.saveAndFlush(saga);
        return saga;
    }

    /**
     * @param context
     * @param runningState
     * @param uuid
     * @return
     */
    protected Saga build(Context context, boolean runningState, String uuid) {
        // 持久化
        LocalDateTime now = LocalDateTime.now();
        List<Saga.SagaProcess> sagaProcesses = process.flattenProcessList().stream().map(p -> {
            Saga.SagaProcess sagaProcess = new Saga.SagaProcess();
            sagaProcess.init(now, p.code, p.name);
            return sagaProcess;
        }).collect(Collectors.toList());
        Saga saga = new Saga();
        LocalDateTime nextTryTime = runningState ? now.plusSeconds(getNextTryIdleInSeconds(0)) : now;
        saga.init(now, svcName, getBizType(), context, uuid, nextTryTime, expireInSeconds(), retryTimes(), sagaProcesses);
        return saga;
    }

    protected Saga internalRun(Saga saga, Context context, Process<Context> currentProcess) {
        LocalDateTime now = LocalDateTime.now();
        // 判断是否能执行
        if (saga.findProcess(currentProcess.code) == null) {
            log.error("[Saga Running]saga process丢失 saga_id = " + saga.getId() + " code = " + currentProcess.code);
            saga.fail(context);
            saga = sagaJpaRepository.saveAndFlush(saga);
            return saga;
        }
        try {
            if (saga.findProcess(currentProcess.code).startRunning(now, context)) {
                saga = sagaJpaRepository.saveAndFlush(saga);
                int tryCount = 3;
                while (tryCount-- > 0) {
                    try {
                        currentProcess.process.accept(context);
                        break;
                    } catch (Exception e) {
                        log.error("[Saga Running]saga执行失败 saga = " + saga.toString(), e);
                        if (tryCount == 0) {
                            throw e;
                        }
                    }
                }

                saga.findProcess(currentProcess.code).finishRunning(context);
                saga = sagaJpaRepository.saveAndFlush(saga);
            } else if (Saga.SagaState.DONE.equals(saga.findProcess(currentProcess.code).getProcessState())) {
                Context temp = saga.findProcess(currentProcess.code).getContext(getContextClass());
                BeanUtils.copyProperties(temp, context);
            } else {
                return saga;
            }
        } catch (Exception ex) {
            saga.fail(context);
            saga.findProcess(currentProcess.code).fail(ex);
            if (Saga.SagaState.FAILED.equals(saga.getSagaState()) && currentProcess.rollback != null) {
                saga = beginRollback(saga, now);
                return saga;
            }
            saga = sagaJpaRepository.saveAndFlush(saga);
            return saga;
        }
        // sub processes
        if (CollectionUtils.isNotEmpty(currentProcess.subProcesses)) {
            for (int i = 0; i < currentProcess.subProcesses.size(); i++) {
                Process process = currentProcess.subProcesses.get(i);
                saga = internalRun(saga, context, process);
                if (!Saga.SagaState.DONE.equals(saga.findProcess(process.code).getProcessState())) {
                    return saga;
                }
            }
        }
        // next process
        if (currentProcess.nextProcess != null) {
            return internalRun(saga, context, currentProcess.nextProcess);
        }
        return saga;
    }

    /**
     * 开始回滚Saga
     *
     * @param uuid
     * @param now
     * @return
     */
    public Saga beginRollback(String uuid, LocalDateTime now) {
        Saga saga = queryByUuid(uuid)
                .orElseThrow(() -> new RuntimeException("[Saga Resume]saga不存在: " + uuid));
        return beginRollback(saga, now);
    }

    /**
     * 开始回滚Saga
     *
     * @param saga
     * @param now
     * @return
     */
    public Saga beginRollback(Saga saga, LocalDateTime now) {
        if (!this.getBizType().equals(saga.getBizType())) {
            log.error("bizType不匹配 sagaId=" + saga.getId());
            return null;
        }
        saga.startRollback(now, now.plusSeconds(getNextTryIdleInSeconds(0)));
        saga = sagaJpaRepository.saveAndFlush(saga);
        return saga;
    }

    /**
     * 回滚saga
     *
     * @param uuid
     * @return
     */
    public Saga rollback(String uuid) {
        Saga saga = queryByUuid(uuid)
                .orElseThrow(() -> new RuntimeException("[Saga Resume]saga不存在: " + uuid));
        return rollback(saga);
    }

    /**
     *  回滚saga
     *
     * @param saga
     * @return
     */
    public Saga rollback(Saga saga) {
        if (!this.getBizType().equals(saga.getBizType())) {
            log.error("bizType不匹配 sagaId=" + saga.getId());
            return null;
        }
        Context context = JSON.parseObject(saga.getContextData(), getContextClass());
        saga = internalRollback(saga, context, process);
        if (!saga.getProcesses().stream().allMatch(p -> Saga.SagaState.ROLLBACKED.equals(p.getProcessState()) || Saga.SagaState.INIT.equals(p.getProcessState()))) {
            saga.fail(context);
            saga = sagaJpaRepository.saveAndFlush(saga);
            return saga;
        }
        saga.finishRollback(context);
        saga = sagaJpaRepository.saveAndFlush(saga);
        return saga;
    }

    protected Saga internalRollback(Saga saga, Context context, Process<Context> currentProcess) {
        Date now = new Date();
        // 判断是否能执行
        if (saga.findProcess(currentProcess.code) == null) {
            log.error("[Saga Rollback]saga process丢失 saga_id = " + saga.getId() + " code = " + currentProcess.code);
            saga.fail(context);
            saga = sagaJpaRepository.saveAndFlush(saga);
            return saga;
        }
        switch (saga.findProcess(currentProcess.code).getProcessState()) {
            case INIT:
            case ROLLBACKED:
                return saga;
            case RUNNING:
            case FAILED:
            case CANCEL:
            case EXPIRED:
            case DONE:
            case ROLLBACKING:
            default:
                break;
        }
        // next process
        if (currentProcess.nextProcess != null) {
            saga = internalRollback(saga, context, currentProcess.nextProcess);
        }

        // sub processes
        if (CollectionUtils.isNotEmpty(currentProcess.subProcesses)) {
            for (int i = currentProcess.subProcesses.size() - 1; i >= 0; i--) {
                Process process = currentProcess.subProcesses.get(i);
                saga = internalRollback(saga, context, process);
                if (!Saga.SagaState.ROLLBACKED.equals(saga.findProcess(process.code).getProcessState())) {
                    return saga;
                }
            }
        }

        // current
        try {
            if (currentProcess.rollback == null) {
                saga.fail(context);
                saga.findProcess(currentProcess.code).fail(new NullPointerException("rollback回滚处理缺失"));
                saga = sagaJpaRepository.saveAndFlush(saga);
            } else {
                saga.findProcess(currentProcess.code).startRollback(context);
                saga = sagaJpaRepository.saveAndFlush(saga);

                currentProcess.rollback.accept(context);

                saga.findProcess(currentProcess.code).finishRollback(context);
                saga = sagaJpaRepository.saveAndFlush(saga);
            }
        } catch (Exception ex) {
            log.error("[Saga Rollback]saga执行失败 saga = " + saga.toString(), ex);
            return saga;
        }

        return saga;
    }

    public static class Process<Context> {

        protected Process(Integer code, String name, Consumer<Context> process, Consumer<Context> rollback) {
            this.code = code;
            this.name = name;
            this.process = process;
            this.rollback = rollback;
        }

        @Getter
        private Integer code;
        @Getter
        private String name;

        /**
         * 满足幂等性
         */
        @Getter
        private final Consumer<Context> process;
        /**
         * 回滚（逆向处理）
         */
        @Getter
        private final Consumer<Context> rollback;

        /**
         * 根处理环节
         */
        private Process<Context> rootProcess;

        /**
         * 下个处理环节
         */
        @Getter
        private Process<Context> nextProcess;

        /**
         * 子处理环节
         */
        @Getter
        private List<Process<Context>> subProcesses = new ArrayList<>();

        public List<Process<Context>> getSubProcesses() {
            return ListUtils.unmodifiableList(subProcesses);
        }

        public List<Process<Context>> flattenProcessList() {
            List<Process<Context>> processes = new ArrayList<>();
            processes.add(this);
            for (Process<Context> subProcess : subProcesses) {
                processes.addAll(subProcess.flattenProcessList());
            }
            if (!Objects.isNull(nextProcess)) {
                processes.addAll(nextProcess.flattenProcessList());
            }
            return ListUtils.unmodifiableList(processes);
        }

        protected int maxCode() {
            int maxCode = this.flattenProcessList().stream().mapToInt(p -> p.code).max().getAsInt();
            return maxCode;
        }

        public Process<Context> root() {
            if (Objects.isNull(rootProcess)) {
                return this;
            } else {
                return rootProcess;
            }
        }

        public Process<Context> sub(Consumer<Context>... subProcesses) {
            for (Consumer<Context> process : subProcesses) {
                addSub(process);
            }
            return this;
        }

        public Process<Context> sub(Process<Context>... subProcesses) {
            for (Process<Context> process : subProcesses) {
                addSub(process);
            }
            return this;
        }

        public Process<Context> addSub(Consumer<Context> process) {
            return addSub(0, process);
        }

        public Process<Context> addSub(Integer code, Consumer<Context> process) {
            return addSub(code, "", process, null);
        }

        public Process<Context> addSub(Integer code, String name, Consumer<Context> process) {
            return addSub(code, name, process, null);
        }

        public Process<Context> addSub(Integer code, Consumer<Context> process, Consumer<Context> rollback) {
            return addSub(code, "", process, rollback, null);
        }

        public Process<Context> addSub(Integer code, String name, Consumer<Context> process, Consumer<Context> rollback) {
            return addSub(code, name, process, rollback, null);
        }

        public Process<Context> addSub(Integer code, Consumer<Context> process, Consumer<Context> rollback, Consumer<Process<Context>> subProcessConfig) {
            return addSub(code, "", process, rollback, null);
        }

        public Process<Context> addSub(Integer code, String name, Consumer<Context> process, Consumer<Context> rollback, Consumer<Process<Context>> subProcessConfig) {
            Process<Context> subProcess = of(code, name, process, rollback);
            addSub(subProcess);
            if (subProcessConfig != null) {
                subProcessConfig.accept(subProcess);
            }
            return this;
        }

        public Process<Context> addSub(Process<Context> subProcess) {
            if (subProcess.code == 0 || subProcess.code == null) {
                int next = maxCode() + 10;
                for (Process<Context> process : subProcess.flattenProcessList()) {
                    process.code = next;
                    next += 10;
                }
            }
            subProcess.rootProcess = root();
            this.subProcesses.add(subProcess);
            return this;
        }

        public Process<Context> then(Consumer<Context> process) {
            return then(0, process);
        }

        public Process<Context> then(Integer code, Consumer<Context> process) {
            return then(code, "", process, null);
        }

        public Process<Context> then(Integer code, String name, Consumer<Context> process) {
            return then(code, name, process, null);
        }

        public Process<Context> then(Integer code, Consumer<Context> process, Consumer<Context> rollback) {
            return then(code, "", process, rollback);
        }

        public Process<Context> then(Integer code, String name, Consumer<Context> process, Consumer<Context> rollback) {
            code = code > 0 ? code : maxCode() + 10;
            Process<Context> next = of(code, name, process, rollback);
            next.rootProcess = root();
            this.nextProcess = next;
            return next;
        }

        public Process<Context> then(Process<Context> nextProcess) {
            if (nextProcess.code == 0 || nextProcess.code == null) {
                int next = maxCode() + 10;
                for (Process<Context> process : nextProcess.flattenProcessList()) {
                    process.code = next;
                    next += 10;
                }
            }
            nextProcess.rootProcess = root();
            this.nextProcess = nextProcess;
            return nextProcess;
        }

        public static <Context> Process<Context> of(Consumer<Context> process) {
            return new Process<>(0, "", process, null);
        }

        public static <Context> Process<Context> of(Integer code, Consumer<Context> process) {
            return new Process<>(code, "", process, null);
        }

        public static <Context> Process<Context> of(Integer code, String name, Consumer<Context> process) {
            return new Process<>(code, name, process, null);
        }

        public static <Context> Process<Context> of(Integer code, Consumer<Context> process, Consumer<Context> rollback) {
            return new Process<>(code, "", process, rollback);
        }

        public static <Context> Process<Context> of(Integer code, String name, Consumer<Context> process, Consumer<Context> rollback) {
            return new Process<>(code, name, process, rollback);
        }
    }

}
