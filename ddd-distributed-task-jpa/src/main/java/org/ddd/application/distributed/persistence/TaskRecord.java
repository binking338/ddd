package org.ddd.application.distributed.persistence;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ddd.share.annotation.Retry;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * @author qiaohe
 * @date 2023/8/28
 */
@Entity
@Table(name = "`__task`")
@DynamicInsert
@DynamicUpdate

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Slf4j
public class TaskRecord {
    public static final String F_SVC_NAME = "svcName";
    public static final String F_TASK_TYPE = "taskType";
    public static final String F_DATA = "data";
    public static final String F_DATA_TYPE = "dataType";
    public static final String F_RESULT = "result";
    public static final String F_RESULT_TYPE = "resultType";
    public static final String F_CREATE_AT = "createAt";
    public static final String F_EXPIRE_AT = "expireAt";
    public static final String F_TASK_STATE = "taskState";
    public static final String F_TRY_TIMES = "tryTimes";
    public static final String F_TRIED_TIMES = "triedTimes";
    public static final String F_LAST_TRY_TIME = "lastTryTime";
    public static final String F_NEXT_TRY_TIME = "nextTryTime";

    public void init(Class<?> taskClass, Object param, String svcName, LocalDateTime schedule, Duration expireAfter, int retryTimes) {
        this.svcName = svcName;
        this.taskType = taskClass.getName();
        this.createAt = schedule;
        this.expireAt = schedule.plusSeconds((int) expireAfter.getSeconds());
        this.taskState = TaskState.INIT;
        this.tryTimes = retryTimes;
        this.triedTimes = 0;
        this.lastTryTime = schedule;
        this.loadParam(param);
        Retry retry = taskClass.getAnnotation(Retry.class);
        if (retry != null) {
            this.tryTimes = retry.retryTimes();
            this.expireAt = this.createAt.plusSeconds(retry.expireAfter());
        }
    }

    @Transient
    private Object param = null;

    public Object getParam() {
        if (this.param != null) {
            return this.param;
        }
        if (StringUtils.isNotBlank(dataType)) {
            Class dataClass = null;
            try {
                dataClass = Class.forName(dataType);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                log.error("任务参数类型解析错误", e);
            }
            this.param = JSON.parseObject(this.data, dataClass);
        }
        return this.param;
    }

    private void loadParam(Object param) {
        this.param = param;
        this.data = JSON.toJSONString(param);
        this.dataType = param.getClass().getName();
    }

    public boolean beginRun(LocalDateTime now) {
        if (this.triedTimes >= this.tryTimes) {
            this.taskState = TaskState.FAILED;
            return false;
        }
        if (now.isAfter(this.expireAt)) {
            this.taskState = TaskState.EXPIRED;
            return false;
        }
        if (!TaskState.INIT.equals(this.taskState)
                && !TaskState.COMFIRMING.equals(this.taskState)) {
            return false;
        }
        if (this.nextTryTime.isAfter(now)) {
            return false;
        }
        this.taskState = TaskState.COMFIRMING;
        this.lastTryTime = now;
        this.nextTryTime = calculateNextTryTime(now);
        this.triedTimes++;
        return true;
    }

    private LocalDateTime calculateNextTryTime(LocalDateTime now) {
        Retry retry = getParam() == null
                ? null
                : getParam().getClass().getAnnotation(Retry.class);
        if (retry == null || retry.retryIntervals().length == 0) {
            if (this.triedTimes <= 3) {
                return now.plusMinutes(10);
            } else if (this.triedTimes <= 6) {
                return now.plusMinutes(30);
            } else if (this.triedTimes <= 10) {
                return now.plusMinutes(60);
            } else {
                return now.plusMinutes(60);
            }
        }
        int index = this.triedTimes - 1;
        if (index >= retry.retryIntervals().length) {
            index = retry.retryIntervals().length - 1;
        } else if (index < 0) {
            index = 0;
        }
        return now.plusSeconds(retry.retryIntervals()[index]);
    }

    public void confirmedCompeleted(Object result, LocalDateTime now) {
        this.result = JSON.toJSONString(result);
        this.resultType = result == null ? "" : result.getClass().getName();
        this.taskState = TaskState.DELIVERED;
    }

    public void cancel(LocalDateTime now) {
        this.taskState = TaskState.CANCEL;
    }

    public Class getTaskClass() {
        Class taskClass = null;
        try {
            taskClass = Class.forName(taskType);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            log.error("任务类型解析错误", e);
        }
        return taskClass;
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
     * 服务
     * varchar
     */
    @Column(name = "`svc_name`")
    private String svcName;

    /**
     * 任务类型
     * varchar(100)
     */
    @Column(name = "`task_type`")
    private String taskType;

    /**
     * 任务数据
     * varchar(1000)
     */
    @Column(name = "`data`")
    private String data;

    /**
     * 任务数据类型
     * varchar(200)
     */
    @Column(name = "`data_type`")
    private String dataType;

    /**
     * 任务结果数据
     * text
     */
    @Column(name = "`result`")
    private String result;

    /**
     * 任务结果类型
     * varchar(200)
     */
    @Column(name = "`result_type`")
    private String resultType;

    /**
     * 创建时间
     * datetime
     */
    @Column(name = "`create_at`")
    private LocalDateTime createAt;

    /**
     * 过期时间
     * datetime
     */
    @Column(name = "`expire_at`")
    private LocalDateTime expireAt;

    /**
     * 分发状态
     * int
     */
    @Column(name = "`task_state`")
    @Convert(converter = TaskState.Converter.class)
    private TaskState taskState;

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
     * datetime
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
    private LocalDateTime dbCreatedAt;

    /**
     * 更新时间
     * datetime
     */
    @Column(name = "`db_updated_at`", insertable = false, updatable = false)
    private Date dbUpdatedAt;
}
