package com.jianbo.springai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.autoconfigure.vectorstore.elasticsearch.ElasticsearchVectorStoreAutoConfiguration;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

@SpringBootApplication(exclude = {ElasticsearchVectorStoreAutoConfiguration.class})
@Slf4j
public class DemoSpringaiApplication {
  @Autowired
  public static void main(String[] args) {
    SpringApplication.run(DemoSpringaiApplication.class, args);
  }
}
