package com.jianbo.mq.consumer;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = "stock_topic",
        consumerGroup = "stock_consumer_group"
)
public class RocketConsumer implements RocketMQListener<String> {

    @Override
    public void onMessage(String message) {
        System.out.println("MQ收到消息：" + message);
        // 业务逻辑：扣库存、下单处理
    }
}
