package com.jianbo.localaiknowledge;

import org.springframework.ai.autoconfigure.ollama.OllamaAutoConfiguration;
import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
        OllamaAutoConfiguration.class,
        PgVectorStoreAutoConfiguration.class
})
public class LocalAiKnowledgeApplication {

	public static void main(String[] args) {
		SpringApplication.run(LocalAiKnowledgeApplication.class, args);
	}

}
