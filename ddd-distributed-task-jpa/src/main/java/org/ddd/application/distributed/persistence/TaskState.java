package org.ddd.application.distributed.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.persistence.AttributeConverter;
import java.util.Objects;

/**
 * @author qiaohe
 * @date 2023/8/13
 */
@AllArgsConstructor
public enum TaskState {
    /**
     * 初始状态
     */
    INIT(0, "init"),
    /**
     * 待确认发送结果
     */
    COMFIRMING(-1, "comfirming"),
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
     * 已发送
     */
    DELIVERED(1, "delivered");
    @Getter
    private final Integer value;
    @Getter
    private final String name;

    public static TaskState valueOf(Integer value) {
        for (TaskState val : TaskState.values()) {
            if (Objects.equals(val.value, value)) {
                return val;
            }
        }
        return null;
    }

    public static class Converter implements AttributeConverter<TaskState, Integer> {

        @Override
        public Integer convertToDatabaseColumn(TaskState attribute) {
            return attribute.value;
        }

        @Override
        public TaskState convertToEntityAttribute(Integer dbData) {
            return TaskState.valueOf(dbData);
        }
    }
}
