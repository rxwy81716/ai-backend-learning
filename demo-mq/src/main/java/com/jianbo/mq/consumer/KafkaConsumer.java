package com.jianbo.mq.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaConsumer {

    @KafkaListener(topics = "kafka_topic")
    public void consumer(String msg){
        System.out.println("kafka消费："+msg);
    }
}
