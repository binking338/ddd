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
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * @author <template/>
 * @date
 */
@Entity
@Table(name = "`__archived_saga`")
@DynamicInsert
@DynamicUpdate

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class ArchivedSaga {
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
    @Convert(converter = Saga.SagaState.Converter.class)
    private Saga.SagaState sagaState;

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
    @Table(name = "`__archived_saga_process`")
    @DynamicInsert
    @DynamicUpdate

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Getter
    public static class SagaProcess {
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
        @Convert(converter = Saga.SagaState.Converter.class)
        private Saga.SagaState processState;

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
}
