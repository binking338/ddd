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
import java.util.UUID;

/**
 * @author qiaohe
 * @date 2023/8/28
 */
@Entity
@Table(name = "`__archived_task`")
@DynamicInsert
@DynamicUpdate

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Slf4j
public class ArchivedTaskRecord {
    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "`id`")
    private Long id;

    /**
     * 任务uuid
     * varchar(64)
     */
    @Column(name = "`task_uuid`")
    private String taskUuid;

    /**
     * 服务
     * varchar
     */
    @Column(name = "`svc_name`")
    private String svcName;

    /**
     * 任务类型
     * varchar(255)
     */
    @Column(name = "`task_type`")
    private String taskType;

    /**
     * 任务数据
     * text
     */
    @Column(name = "`data`")
    private String data;

    /**
     * 任务数据类型
     * varchar(255)
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
     * varchar(255)
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
    @Convert(converter = TaskRecord.TaskState.Converter.class)
    private TaskRecord.TaskState taskState;

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
