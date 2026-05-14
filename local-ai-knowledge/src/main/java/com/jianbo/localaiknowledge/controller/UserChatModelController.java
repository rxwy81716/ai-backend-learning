package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.model.dto.UserChatModelSaveDto;
import com.jianbo.localaiknowledge.model.dto.UserChatModelTryDto;
import com.jianbo.localaiknowledge.model.dto.UserChatModelVo;
import com.jianbo.localaiknowledge.service.UserChatModelService;
import com.jianbo.localaiknowledge.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户自备 OpenAI 兼容 Chat 配置（运行时 SSE 请求体 {@code model=user:{alias}}）。
 *
 * <p>需登录；路径挂载在 {@code /api/user/chat-models}，与 {@link com.jianbo.localaiknowledge.config.SecurityConfig}
 * 中已认证的 {@code /api/user/**} 规则一致。API Key 使用 AES-GCM 加密入库。
 */
@RestController
@RequestMapping("/api/user/chat-models")
@Slf4j
@RequiredArgsConstructor
public class UserChatModelController {

  private final UserChatModelService userChatModelService;

  private Long requireUserId() {
    Long userId = SecurityUtil.getCurrentUserId();
    if (userId == null) {
      throw new IllegalArgumentException("用户未登录");
    }
    return userId;
  }

  /** 当前用户全部配置（含脱敏 apiKeyHint）。 */
  @GetMapping
  public List<UserChatModelVo> list() {
    return userChatModelService.list(requireUserId());
  }

  /** 新建或更新一条配置；更新时 apiKey 可空表示保留原密文。 */
  @PostMapping
  public UserChatModelVo save(@RequestBody UserChatModelSaveDto dto) {
    return userChatModelService.save(requireUserId(), dto);
  }

  /** 按主键删除，且校验 userId 归属。 */
  @DeleteMapping("/{id}")
  public Map<String, Boolean> delete(@PathVariable Long id) {
    userChatModelService.delete(requireUserId(), id);
    return Map.of("ok", true);
  }

  /** 使用请求体明文参数测试连通性，不落库。 */
  @PostMapping("/try")
  public Map<String, String> tryInline(@RequestBody UserChatModelTryDto dto) {
    String preview = userChatModelService.tryConnection(dto);
    return Map.of("preview", preview);
  }

  /** 对已保存密文配置解密后做一次最小对话探测。 */
  @PostMapping("/{id}/try")
  public Map<String, String> trySaved(@PathVariable Long id) {
    String preview = userChatModelService.trySaved(requireUserId(), id);
    return Map.of("preview", preview);
  }
}
