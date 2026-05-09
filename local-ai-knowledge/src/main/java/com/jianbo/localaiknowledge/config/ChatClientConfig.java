package com.jianbo.localaiknowledge.config;

import com.jianbo.localaiknowledge.service.agent.ChatModelRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 默认 {@link ChatClient} Bean 配置。
 *
 * <p>统一由 {@link ChatModelRegistry} 维护多模型 ChatClient 实例（每个 provider 一份），
 * 这里只把 {@code DEFAULT_KEY} 对应的实例暴露成 Spring 容器里的 @Primary Bean，
 * 让 Agent / Service 能通过普通 {@code @Autowired ChatClient} 注入到默认实例，
 * 同时避免出现"业务里持有的 ChatClient" 与 "registry 里的 ChatClient" 两份不同实例
 * 在以后挂 advisor（日志 / token 计费 / 敏感词）时漏掉一份的隐患。
 *
 * <p>需要切换运行时模型的场景（带 modelKey 参数）仍然通过 {@link ChatModelRegistry#getClient(String)}
 * 取对应实例。
 */
@Configuration
public class ChatClientConfig {

  @Bean
  @Primary
  public ChatClient defaultChatClient(ChatModelRegistry chatModelRegistry) {
    // ChatModelRegistry 在 @PostConstruct 阶段已注册好 DEFAULT_KEY，
    // Spring 保证 @Bean 工厂方法在依赖（registry）完成初始化之后才被调用。
    return chatModelRegistry.getClient(ChatModelRegistry.DEFAULT_KEY);
  }
}
