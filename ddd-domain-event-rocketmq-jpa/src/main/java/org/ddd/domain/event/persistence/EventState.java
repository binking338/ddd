package org.ddd.domain.event.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.persistence.AttributeConverter;
import java.util.Objects;

/**
 * @author qiaohe
 * @date 2023/8/13
 */
@AllArgsConstructor
public enum EventState {
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

    public static EventState valueOf(Integer value) {
        for (EventState val : EventState.values()) {
            if (Objects.equals(val.value, value)) {
                return val;
            }
        }
        return null;
    }

    public static class Converter implements AttributeConverter<EventState, Integer> {

        @Override
        public Integer convertToDatabaseColumn(EventState attribute) {
            return attribute.value;
        }

        @Override
        public EventState convertToEntityAttribute(Integer dbData) {
            return EventState.valueOf(dbData);
        }
    }
}
