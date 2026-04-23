package com.jianbo.mq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class DemoMqApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoMqApplication.class, args);
    }

}
