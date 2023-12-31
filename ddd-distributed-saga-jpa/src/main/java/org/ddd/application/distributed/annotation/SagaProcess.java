package org.ddd.application.distributed.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author <template/>
 * @date
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SagaProcess {

    /**
     * 处理名称，默认方法名称
     * @return
     */
    String name() default "";

    /**
     * 处理环节编码，区分SAGA不同处理环节，数值大小体现先后顺序（数值小优先执行）
     * @return
     */
    int code() default 0;
}
