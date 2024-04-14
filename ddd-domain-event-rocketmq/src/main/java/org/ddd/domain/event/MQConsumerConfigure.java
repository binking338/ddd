package org.ddd.domain.event;

import org.apache.rocketmq.client.consumer.MQPushConsumer;

/**
 * 配置领域事件的MQ配置
 * @author qiaohe
 * @date 2024/3/28
 */
public interface MQConsumerConfigure {
    MQPushConsumer get(Class domainEventClass);
}
