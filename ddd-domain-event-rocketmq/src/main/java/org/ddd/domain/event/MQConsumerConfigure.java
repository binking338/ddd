package org.ddd.domain.event;

import org.apache.rocketmq.client.consumer.MQPushConsumer;

/**
 * @author qiaohe
 * @date 2024/3/28
 */
public interface MQConsumerConfigure {
    MQPushConsumer get(Class domainEventClass);
}
