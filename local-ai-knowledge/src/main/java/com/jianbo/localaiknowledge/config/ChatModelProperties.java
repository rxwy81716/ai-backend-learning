package com.jianbo.localaiknowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多 ChatModel 注册配置。
 *
 * <p>为 {@link com.jianbo.localaiknowledge.service.agent.ChatModelRegistry} 提供数据源，
 * 允许在 application.yml 中声明多个 OpenAI 兼容 provider（glm / deepseek / qwen / …），
 * 运行时通过 key 切换模型，而不是依赖启动 profile。
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * app:
 *   chat-models:
 *     default-key: glm
 *     providers:
 *       glm:
 *         base-url: https://open.bigmodel.cn/api/paas
 *         api-key: ${ZHIPU_API_KEY:}
 *         completions-path: /v4/chat/completions
 *         model: glm-4-flash
 *         temperature: 0.3
 *         max-tokens: 2048
 *       deepseek:
 *         base-url: https://api.deepseek.com
 *         api-key: ${DEEPSEEK_API_KEY:}
 *         model: deepseek-chat
 *         temperature: 0.3
 *         max-tokens: 2048
 * }</pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.chat-models")
public class ChatModelProperties {

  /** 默认模型 key（注册表查找 null/未知 key 时的 fallback） */
  private String defaultKey;

  /** 各模型 provider 的连接配置，key 即运行时切换用的 model id */
  private Map<String, Provider> providers = new LinkedHashMap<>();

  @Data
  public static class Provider {
    /** OpenAI 兼容 base-url（不要带 /v1） */
    private String baseUrl;
    /** API Key（建议走环境变量占位符） */
    private String apiKey;
    /** chat completions 路径；null = 走 OpenAiApi 默认的 /v1/chat/completions */
    private String completionsPath;
    /** 模型名（如 glm-4-flash / deepseek-chat） */
    private String model;
    /** 采样温度 */
    private Double temperature = 0.3;
    /** 最大输出 token */
    private Integer maxTokens = 2048;
  }
}
