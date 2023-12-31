package org.ddd.application.distributed.persistence;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * @author <template/>
 * @date
 */
@Entity
@Table(name = "`__saga`")
@DynamicInsert
@DynamicUpdate

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class Saga {
    public static final String F_SAGA_UUID = "sagaUuid";
    public static final String F_SVC_NAME = "svcName";
    public static final String F_BIZ_TYPE = "bizType";
    public static final String F_CONTEXT_DATA = "contextData";
    public static final String F_CONTEXT_DATA_TYPE = "contextDataType";
    public static final String F_SAGA_STATE = "sagaState";
    public static final String F_CREATE_AT = "createAt";
    public static final String F_EXPIRE_AT = "expireAt";
    public static final String F_TRY_TIMES = "tryTimes";
    public static final String F_TRIED_TIMES = "triedTimes";
    public static final String F_LAST_TRY_TIME = "lastTryTime";
    public static final String F_NEXT_TRY_TIME = "nextTryTime";

    public void init(LocalDateTime now, String svcName, String bizType, Object context, String uuid, LocalDateTime nextTryTime, int expireInSeconds, int retryTimes, List<SagaProcess> sagaProcesses) {
        this.sagaUuid = StringUtils.isNotBlank(uuid) ? uuid : UUID.randomUUID().toString();
        this.svcName = svcName;
        this.bizType = bizType;
        this.context = context;
        this.contextData = (JSON.toJSONString(context));
        this.contextDataType = context == null ? Object.class.getName() : context.getClass().getName();
        this.sagaState = SagaState.INIT;
        this.createAt = now;
        this.expireAt = now.plusSeconds(expireInSeconds);
        this.tryTimes = retryTimes;
        this.triedTimes = 0;
        this.lastTryTime = LocalDateTime.of(1, 1, 1, 0, 0, 0);
        this.nextTryTime = nextTryTime;
        this.processes = sagaProcesses;
    }

    public boolean startRunning(LocalDateTime now, LocalDateTime nextTryTime) {
        if (triedTimes >= tryTimes) {
            this.sagaState = SagaState.FAILED;
            return false;
        }
        if (expireAt.isBefore(now)) {
            this.sagaState = SagaState.EXPIRED;
            return false;
        }
        if (SagaState.RUNNING.equals(this.sagaState) && (this.nextTryTime != null && this.nextTryTime.isAfter(now))) {
            return true;
        }
        if (!SagaState.INIT.equals(this.sagaState)
                && !SagaState.RUNNING.equals(this.sagaState)) {
            return false;
        }
        this.sagaState = SagaState.RUNNING;
        this.triedTimes++;
        this.lastTryTime = now;
        this.nextTryTime = nextTryTime;
        return true;
    }

    public void finishRunning() {
        syncContextData();
        this.sagaState = SagaState.DONE;
    }

    public void cancel() {
        syncContextData();
        this.sagaState = SagaState.CANCEL;
    }

    public void fail() {
        syncContextData();
        if (triedTimes >= tryTimes) {
            this.sagaState = SagaState.FAILED;
        } else {
            this.sagaState = SagaState.RUNNING;
        }
    }

    public boolean startRollback(LocalDateTime now, LocalDateTime nextTryTime) {
        if (expireAt.isBefore(now)) {
            this.sagaState = SagaState.EXPIRED;
            return false;
        }
        if (SagaState.ROLLBACKING.equals(this.sagaState)
                && (this.nextTryTime != null && this.nextTryTime.isAfter(now))) {
            return true;
        }
        if (!SagaState.FAILED.equals(this.sagaState)) {
            return false;
        }
        this.nextTryTime = nextTryTime;
        this.sagaState = SagaState.ROLLBACKING;
        return true;
    }

    public void finishRollback() {
        syncContextData();
        this.sagaState = SagaState.ROLLBACKED;
    }

    public SagaProcess findProcess(Integer processCode) {
        return  processes == null
                ? null
                :  processes.stream().filter(p -> (Objects.equals(p.processCode, processCode))).findFirst().orElse(null);
    }

    public void addProcess(Saga.SagaProcess process) {
        if(this.processes == null){
            this.processes = new ArrayList<>();
        }
        this.processes.add(process);
    }

    @Transient
    private Object context = null;

    public void syncContextData(){
        this.contextData = JSON.toJSONString(getContext());
    }

    public Object getContext() {
        if(this.context != null){
            return this.context;
        }
        Class ctxClass = null;
        try {
            ctxClass = Class.forName(getContextDataType());
        } catch (Exception _) {
            /* don't care */
            return null;
        }
        this.context = JSON.parseObject(contextData, ctxClass);
        return this.context;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    private Long id;

    /**
     * uuid
     * varchar(64)
     */
    @Column(name = "`saga_uuid`")
    private String sagaUuid;

    /**
     * 服务
     * varchar
     */
    @Column(name = "`svc_name`")
    private String svcName;

    /**
     * 业务类型
     * int
     */
    @Column(name = "`biz_type`")
    private String bizType;

    /**
     * 上下文
     * varchar
     */
    @Column(name = "`context_data`")
    private String contextData;

    /**
     * 上下文对象类型
     * varchar
     */
    @Column(name = "`context_data_type`")
    private String contextDataType;

    /**
     * 执行状态
     * int
     */
    @Column(name = "`saga_state`")
    @Convert(converter = SagaState.Converter.class)
    private SagaState sagaState;

    /**
     * 过期时间
     * datetime
     */
    @Column(name = "`expire_at`")
    private LocalDateTime expireAt;

    /**
     * 创建时间
     * datetime
     */
    @Column(name = "`create_at`")
    private LocalDateTime createAt;

    /**
     * 尝试次数
     * int
     */
    @Column(name = "`try_times`")
    private Integer tryTimes;

    /**
     * 已尝试次数
     * int
     */
    @Column(name = "`tried_times`")
    private Integer triedTimes;

    /**
     * 上次尝试时间
     * int
     */
    @Column(name = "`last_try_time`")
    private LocalDateTime lastTryTime;

    /**
     * 下次尝试时间
     * datetime
     */
    @Column(name = "`next_try_time`")
    private LocalDateTime nextTryTime;

    /**
     * 处理环节
     */
    @OneToMany(cascade = {CascadeType.ALL}, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "`saga_id`", nullable = false)
    private List<SagaProcess> processes;

    /**
     * 乐观锁
     * int
     */
    @Version
    @Column(name = "`version`")
    private Integer version;

    /**
     * 创建时间
     * datetime
     */
    @Column(name = "`db_created_at`", insertable = false, updatable = false)
    private Date dbCreatedAt;

    /**
     * 更新时间
     * datetime
     */
    @Column(name = "`db_updated_at`", insertable = false, updatable = false)
    private Date dbUpdatedAt;


    @Entity
    @Table(name = "`__saga_process`")
    @DynamicInsert
    @DynamicUpdate

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Getter
    public static class SagaProcess {
        public void init(LocalDateTime now, Integer code, String name) {
            this.processCode = code;
            this.processName = name;
            this.processState = SagaState.INIT;
            this.inputData = "null";
            this.inputDataType = Object.class.getName();
            this.outputData = "null";
            this.outputDataType = Object.class.getName();
            this.triedTimes = 0;
            this.lastTryTime = now;
            this.createAt = now;
            this.exception = "";
        }

        /**
         * @param now
         * @return
         */
        public boolean startRunning(LocalDateTime now, Object input) {
            if (SagaState.INIT.equals(this.processState)
                    || SagaState.FAILED.equals(this.processState)
                    || SagaState.RUNNING.equals(this.processState)) {
                this.inputData = JSON.toJSONString(input);
                this.inputDataType = input == null
                        ? Object.class.getName()
                        : input.getClass().getName();
                this.processState = SagaState.RUNNING;
                this.lastTryTime = now;
                this.triedTimes++;
                return true;
            }
            return false;
        }

        public void finishRunning(Object output) {
            if (SagaState.RUNNING.equals(this.processState)) {
                this.outputData = JSON.toJSONString(output);
                this.outputDataType = output == null
                        ? Object.class.getName()
                        : output.getClass().getName();
                this.processState = SagaState.DONE;
            }
        }

        public void startRollback() {
            this.processState = SagaState.ROLLBACKING;
        }

        public void finishRollback() {
            this.processState = SagaState.ROLLBACKED;
        }

        public void fail(Exception ex) {
            this.processState = SagaState.FAILED;
            this.exception = StringUtils.isEmpty(ex.getMessage()) ? "" : ex.getMessage();
        }

        public Object getInput() {
            Class inputClass = null;
            try {
                inputClass = Class.forName(getInputDataType());
            } catch (Exception _) {
                /* don't care */
                return null;
            }
            Object input = JSON.parseObject(getInputData(), inputClass);
            return input;
        }

        public Object getOutput() {
            Class outputClass = null;
            try {
                outputClass = Class.forName(getOutputDataType());
            } catch (Exception _) {
                /* don't care */
                return null;
            }
            Object output = JSON.parseObject(getInputData(), outputClass);
            return output;
        }

        @Override
        public String toString() {
            return JSON.toJSONString(this);
        }

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "`id`")
        private Long id;

        /**
         * 处理编码
         * int
         */
        @Column(name = "`process_code`")
        private Integer processCode;

        /**
         * 处理名称
         * varchar
         */
        @Column(name = "`process_name`")
        private String processName;

        /**
         * 创建时间
         * datetime
         */
        @Column(name = "`create_at`")
        private LocalDateTime createAt;

        /**
         * 输入数据
         * varchar
         */
        @Column(name = "`input_data`")
        private String inputData;

        /**
         * 输入类型
         * varchar
         */
        @Column(name = "`input_data_type`")
        private String inputDataType;

        /**
         * 输出数据
         * varchar
         */
        @Column(name = "`output_data`")
        private String outputData;

        /**
         * 输出类型
         * varchar
         */
        @Column(name = "`output_data_type`")
        private String outputDataType;

        /**
         * 处理执行状态
         * int
         */
        @Column(name = "`process_state`")
        @Convert(converter = SagaState.Converter.class)
        private SagaState processState;

        /**
         * 已尝试次数
         * int
         */
        @Column(name = "`tried_times`")
        private Integer triedTimes;

        /**
         * 上次尝试时间
         * int
         */
        @Column(name = "`last_try_time`")
        private LocalDateTime lastTryTime;

        /**
         * 异常信息
         * varchar
         */
        @Column(name = "`exception`")
        private String exception;

        /**
         * 创建时间
         * datetime
         */
        @Column(name = "`db_created_at`", insertable = false, updatable = false)
        private LocalDateTime dbCreatedAt;

        /**
         * 更新时间
         * datetime
         */
        @Column(name = "`db_updated_at`", insertable = false, updatable = false)
        private LocalDateTime dbUpdatedAt;
    }

    @AllArgsConstructor
    public enum SagaState {
        /**
         * 初始状态
         */
        INIT(0, "init"),
        /**
         * 执行中
         */
        RUNNING(-1, "running"),
        /**
         * 业务主动取消
         */
        CANCEL(-2, "cancel"),
        /**
         * 过期
         */
        EXPIRED(-3, "expired"),
        /**
         * 用完重试次数
         */
        FAILED(-4, "failed"),
        /**
         * 回滚中
         */
        ROLLBACKING(-5, "rollbacking"),
        /**
         * 已回滚
         */
        ROLLBACKED(-6, "rollbacked"),
        /**
         * 已完成
         */
        DONE(1, "done");
        @Getter
        private final Integer value;
        @Getter
        private final String name;

        public static SagaState valueOf(Integer value) {
            for (SagaState val : SagaState.values()) {
                if (Objects.equals(val.value, value)) {
                    return val;
                }
            }
            throw new RuntimeException("枚举类型DeliveryState枚举值转换异常，不存在的值" + value);
        }

        public static class Converter implements AttributeConverter<SagaState, Integer> {

            @Override
            public Integer convertToDatabaseColumn(SagaState attribute) {
                return attribute.value;
            }

            @Override
            public SagaState convertToEntityAttribute(Integer dbData) {
                return SagaState.valueOf(dbData);
            }
        }
    }
}
