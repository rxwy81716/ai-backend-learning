package com.jianbo.localaiknowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class LocalAiKnowledgeLangchain4jApplication {
    public static void main(String[] args) {
        SpringApplication.run(LocalAiKnowledgeLangchain4jApplication.class, args);
    }
}
