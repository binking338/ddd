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
    public static final String NONE_SUBSCRIBER = "[none]";

    /**
     * 领域事件名称
     * 只有集成事件需要定义领域事件名称，集成事件将使用mq向外部系统发出
     *
     * @return
     */
    String value() default "";

    /**
     * 订阅者
     * 集成事件的场景下，该值将会成为消费分组名称的默认值
     * @return
     */
    String subscriber() default "";

    /**
     * 强制订阅逻辑在事务后执行
     * 领域事件场景下，该值决定订阅逻辑的执行时机是在事务中还是事务后，如果在事务后执行，事件记录将会先持久化。
     * @return
     */
    boolean forceSubscribeAfterTransaction() default false;
}
