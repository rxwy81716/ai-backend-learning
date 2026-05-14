package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.crypto.UserApiKeyCrypto;
import com.jianbo.localaiknowledge.mapper.UserChatModelConfigMapper;
import com.jianbo.localaiknowledge.model.UserChatModelConfig;
import com.jianbo.localaiknowledge.model.dto.UserChatModelSaveDto;
import com.jianbo.localaiknowledge.model.dto.UserChatModelTryDto;
import com.jianbo.localaiknowledge.model.dto.UserChatModelVo;
import com.jianbo.localaiknowledge.service.agent.ChatClientResolver;
import com.jianbo.localaiknowledge.service.agent.OpenAiCompatibleChatModelFactory;
import com.jianbo.localaiknowledge.service.agent.UserChatModelKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 用户自备 Chat 配置的增删改查、连通性探测，以及与 {@link ChatClientResolver}、Redis 失效广播的衔接。
 *
 * <p>不涉及 Embedding / 向量维度；对话仍走本服务已有 RAG 链，仅替换 LLM Chat 客户端来源。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserChatModelService {

  private static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{2,64}$");

  private final UserChatModelConfigMapper mapper;
  private final UserApiKeyCrypto crypto;
  private final OpenAiCompatibleChatModelFactory chatModelFactory;
  private final ChatClientResolver chatClientResolver;
  private final UserChatModelEvictPublisher evictPublisher;

  public List<UserChatModelVo> list(Long userId) {
    return mapper.selectByUserId(userId).stream().map(this::toVo).toList();
  }

  public List<String> listPrefixedModelKeys(Long userId) {
    return mapper.selectByUserId(userId).stream()
        .map(r -> UserChatModelKeys.prefixed(r.getAlias()))
        .toList();
  }

  /** 新建（无 id）或更新（有 id）；写库后本地失效并 Redis 广播，便于多实例一致。 */
  @Transactional
  public UserChatModelVo save(Long userId, UserChatModelSaveDto dto) {
    if (dto.getId() == null) {
      validateAlias(dto.getAlias(), null);
      return insert(userId, dto);
    }
    validateAlias(dto.getAlias(), dto.getId());
    return update(userId, dto);
  }

  private UserChatModelVo insert(Long userId, UserChatModelSaveDto dto) {
    if (dto.getApiKey() == null || dto.getApiKey().isBlank()) {
      throw new IllegalArgumentException("新建时必须填写 apiKey");
    }
    UserChatModelConfig row = new UserChatModelConfig();
    fillRow(userId, dto, row);
    row.setApiKeyCipher(crypto.encrypt(dto.getApiKey().trim()));
    mapper.insert(row);
    evictLocalAndBroadcast(userId, row.getAlias());
    return toVo(mapper.selectByIdAndUser(row.getId(), userId));
  }

  private UserChatModelVo update(Long userId, UserChatModelSaveDto dto) {
    UserChatModelConfig existing = mapper.selectByIdAndUser(dto.getId(), userId);
    if (existing == null) {
      throw new IllegalArgumentException("记录不存在或无权访问");
    }
    String alias = existing.getAlias();
    if (dto.getAlias() != null && !dto.getAlias().isBlank() && !dto.getAlias().equals(alias)) {
      throw new IllegalArgumentException("不允许修改 alias");
    }
    existing.setLabel(trimToNull(dto.getLabel()));
    existing.setBaseUrl(requireText(dto.getBaseUrl(), "baseUrl"));
    existing.setCompletionsPath(trimToNull(dto.getCompletionsPath()));
    existing.setModel(requireText(dto.getModel(), "model"));
    existing.setTemperature(dto.getTemperature() != null ? dto.getTemperature() : 0.3);
    existing.setMaxTokens(dto.getMaxTokens() != null ? dto.getMaxTokens() : 2048);
    if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) {
      existing.setApiKeyCipher(crypto.encrypt(dto.getApiKey().trim()));
    }
    mapper.update(existing);
    evictLocalAndBroadcast(userId, alias);
    return toVo(mapper.selectByIdAndUser(dto.getId(), userId));
  }

  @Transactional
  public void delete(Long userId, Long id) {
    UserChatModelConfig row = mapper.selectByIdAndUser(id, userId);
    if (row == null) {
      throw new IllegalArgumentException("记录不存在或无权访问");
    }
    mapper.deleteByIdAndUser(id, userId);
    evictLocalAndBroadcast(userId, row.getAlias());
  }

  /** 使用请求体中的明文参数调用一次最小对话，不落库 */
  public String tryConnection(UserChatModelTryDto dto) {
    requireText(dto.getBaseUrl(), "baseUrl");
    requireText(dto.getApiKey(), "apiKey");
    requireText(dto.getModel(), "model");
    ChatClient client =
        chatModelFactory.createChatClient(
            dto.getBaseUrl().trim(),
            dto.getApiKey().trim(),
            trimToNull(dto.getCompletionsPath()),
            dto.getModel().trim(),
            dto.getTemperature(),
            dto.getMaxTokens());
    return probeChat(client);
  }

  /** 对已保存的配置做连通性测试 */
  public String trySaved(Long userId, Long id) {
    UserChatModelConfig row = mapper.selectByIdAndUser(id, userId);
    if (row == null) {
      throw new IllegalArgumentException("记录不存在或无权访问");
    }
    String apiKey = crypto.decrypt(row.getApiKeyCipher());
    ChatClient client =
        chatModelFactory.createChatClient(
            row.getBaseUrl(),
            apiKey,
            row.getCompletionsPath(),
            row.getModel(),
            row.getTemperature(),
            row.getMaxTokens());
    return probeChat(client);
  }

  private static String probeChat(ChatClient client) {
    try {
      String reply =
          client
              .prompt()
              .messages(List.of(new UserMessage("Reply with exactly one word: pong")))
              .call()
              .content();
      if (reply == null || reply.isBlank()) {
        return "(empty reply)";
      }
      return reply.trim();
    } catch (Exception e) {
      log.warn("[UserChatModelService] 探测失败: {}", e.getMessage());
      throw new IllegalArgumentException("连通性测试失败: " + e.getMessage());
    }
  }

  private void fillRow(Long userId, UserChatModelSaveDto dto, UserChatModelConfig row) {
    row.setUserId(userId);
    row.setAlias(dto.getAlias().trim());
    row.setLabel(trimToNull(dto.getLabel()));
    row.setBaseUrl(requireText(dto.getBaseUrl(), "baseUrl"));
    row.setCompletionsPath(trimToNull(dto.getCompletionsPath()));
    row.setModel(requireText(dto.getModel(), "model"));
    row.setTemperature(dto.getTemperature() != null ? dto.getTemperature() : 0.3);
    row.setMaxTokens(dto.getMaxTokens() != null ? dto.getMaxTokens() : 2048);
  }

  private void validateAlias(String alias, Long idForUpdate) {
    if (idForUpdate != null) {
      return;
    }
    if (alias == null || alias.isBlank()) {
      throw new IllegalArgumentException("alias 不能为空");
    }
    String a = alias.trim();
    if (!ALIAS_PATTERN.matcher(a).matches()) {
      throw new IllegalArgumentException("alias 仅允许 2~64 位字母、数字、下划线、中划线");
    }
    if (a.equalsIgnoreCase("default")) {
      throw new IllegalArgumentException("alias 不能使用 default");
    }
  }

  private static String requireText(String s, String field) {
    if (s == null || s.isBlank()) {
      throw new IllegalArgumentException(field + " 不能为空");
    }
    return s.trim();
  }

  private static String trimToNull(String s) {
    if (s == null) {
      return null;
    }
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private UserChatModelVo toVo(UserChatModelConfig r) {
    if (r == null) {
      return null;
    }
    boolean configured = r.getApiKeyCipher() != null && !r.getApiKeyCipher().isBlank();
    String hint = "";
    if (configured) {
      try {
        hint = maskPlainKey(crypto.decrypt(r.getApiKeyCipher()));
      } catch (Exception e) {
        hint = "(已加密，无法展示)";
      }
    }
    return UserChatModelVo.builder()
        .id(r.getId())
        .alias(r.getAlias())
        .label(r.getLabel())
        .baseUrl(r.getBaseUrl())
        .completionsPath(r.getCompletionsPath())
        .model(r.getModel())
        .temperature(r.getTemperature())
        .maxTokens(r.getMaxTokens())
        .apiKeyConfigured(configured)
        .apiKeyHint(hint)
        .build();
  }

  private static String maskPlainKey(String plain) {
    if (plain == null || plain.isEmpty()) {
      return "";
    }
    String t = plain.trim();
    if (t.length() <= 8) {
      return "****";
    }
    return t.substring(0, 4) + "…" + t.substring(t.length() - 4);
  }

  private void evictLocalAndBroadcast(Long userId, String alias) {
    // 1) 本 JVM 立即去掉旧 ChatClient
    chatClientResolver.evictUserModel(userId, alias);
    // 2) 其它实例通过 Redis Topic 收到后同样 evict（ChatClient 本身不进 Redis）
    evictPublisher.publish(userId, alias);
  }
}
