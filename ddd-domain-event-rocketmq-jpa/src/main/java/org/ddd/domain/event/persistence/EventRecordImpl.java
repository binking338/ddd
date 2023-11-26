package org.ddd.domain.event.persistence;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ddd.domain.event.EventRecord;
import org.ddd.share.annotation.Retry;
import org.ddd.domain.event.annotation.DomainEvent;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Objects;

/**
 * @author <template/>
 * @date
 */
@Entity
@Table(name = "`__event`")
@DynamicInsert
@DynamicUpdate

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Slf4j
public class EventRecordImpl implements EventRecord {

    public static final String F_SVC_NAME = "svcName";
    public static final String F_EVENT_TYPE = "eventType";
    public static final String F_DATA = "data";
    public static final String F_DATA_TYPE = "dataType";
    public static final String F_CREATE_AT = "createAt";
    public static final String F_EXPIRE_AT = "expireAt";
    public static final String F_EVENT_STATE = "eventState";
    public static final String F_TRY_TIMES = "tryTimes";
    public static final String F_TRIED_TIMES = "triedTimes";
    public static final String F_LAST_TRY_TIME = "lastTryTime";
    public static final String F_NEXT_TRY_TIME = "nextTryTime";

    public void init(Object payload, String svcName, LocalDateTime now, Duration expireAfter, int retryTimes) {
        this.svcName = svcName;
        this.createAt = now;
        this.expireAt = now.plusSeconds((int) expireAfter.getSeconds());
        this.eventState = EventState.INIT;
        this.tryTimes = retryTimes;
        this.triedTimes = 0;
        this.lastTryTime = now;
        this.loadPayload(payload);
    }

    @Transient
    private Object payload = null;

    public Object getPayload() {
        if (this.payload != null) {
            return this.payload;
        }
        if (StringUtils.isNotBlank(dataType)) {
            Class dataClass = null;
            try {
                dataClass = Class.forName(dataType);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                log.error("事件类型解析错误", e);
            }
            this.payload = JSON.parseObject(data, dataClass);
        }
        return this.payload;
    }

    private void loadPayload(Object payload) {
        this.payload = payload;
        this.data = JSON.toJSONString(payload);
        this.dataType = payload.getClass().getName();
        DomainEvent domainEvent = payload == null
                ? null
                : payload.getClass().getAnnotation(DomainEvent.class);
        if(domainEvent != null) {
            this.eventType = domainEvent.value();
        }
        Retry retry = payload == null
                ? null
                : payload.getClass().getAnnotation(Retry.class);
        if (retry != null) {
            this.tryTimes = retry.retryTimes();
            this.expireAt = this.createAt.plusSeconds(retry.expireAfter());
        }
    }

    public boolean beginDelivery(LocalDateTime now) {
        if (this.triedTimes >= this.tryTimes) {
            this.eventState = EventState.FAILED;
            return false;
        }
        if (now.isAfter(this.expireAt)) {
            this.eventState = EventState.EXPIRED;
            return false;
        }
        if (!EventState.INIT.equals(this.eventState)
                && !EventState.COMFIRMING.equals(this.eventState)) {
            return false;
        }
        if (this.nextTryTime.isAfter(now)) {
            return false;
        }
        this.eventState = EventState.COMFIRMING;
        this.lastTryTime = now;
        this.nextTryTime = calculateNextTryTime(now);
        this.triedTimes++;
        return true;
    }

    private LocalDateTime calculateNextTryTime(LocalDateTime now) {
        Retry retry = getPayload() == null
                ? null
                : getPayload().getClass().getAnnotation(Retry.class);
        if (Objects.isNull(retry) || retry.retryIntervals().length == 0) {
            if (this.triedTimes <= 3) {
                return now.plusSeconds(10);
            } else if (this.triedTimes <= 6) {
                return now.plusSeconds(30);
            } else if (this.triedTimes <= 10) {
                return now.plusSeconds(60);
            } else if (this.triedTimes <= 20) {
                return now.plusMinutes(5);
            } else {
                return now.plusMinutes(10);
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

    public void comfirmedDelivered(LocalDateTime now) {
        this.eventState = EventState.DELIVERED;
    }

    public void cancel(LocalDateTime now) {
        this.eventState = EventState.CANCEL;
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
     * 事件类型
     * varchar(100)
     */
    @Column(name = "`event_type`")
    private String eventType;

    /**
     * 事件数据
     * varchar(1000)
     */
    @Column(name = "`data`")
    private String data;

    /**
     * 事件数据类型
     * varchar(200)
     */
    @Column(name = "`data_type`")
    private String dataType;

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
    @Column(name = "`event_state`")
    @Convert(converter = EventState.Converter.class)
    private EventState eventState;

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
