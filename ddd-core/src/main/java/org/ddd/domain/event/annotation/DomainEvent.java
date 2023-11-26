package org.ddd.domain.event.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 领域事件
 *
 * @author <template/>
 * @date
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DomainEvent {
    /**
     * 领域事件名称
     * 只有集成事件需要定义领域事件名称，集成事件将使用mq向外部系统发出
     *
     * @return
     */
    String value() default "";

    /**
     * 订阅者
     * @return
     */
    String subscriber() default "";
}
