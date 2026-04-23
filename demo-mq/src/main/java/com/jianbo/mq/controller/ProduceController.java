package com.jianbo.mq.controller;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * 生产者
 * 发送消息
 *
 */
@RestController
@RequestMapping("/mq")
public class ProduceController {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;


    // 发送普通消息
    @GetMapping("/rocketmq/send")
    public String sendMsg() {
        String topic = "stock_topic";
        String msg = "商品扣库存消息";

        rocketMQTemplate.convertAndSend(topic, msg);
        return "消息发送成功";
    }

    @GetMapping("/kafka/send")
    public String sendKafka(){
        kafkaTemplate.send("kafka_topic","扣库存消息");
        return "消息发送成功";
    }
}
