package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.config.RagMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * SSE 流式调用的错误分类 & 兜底文案生成。
 *
 * <p>从 {@code MultiAgentOrchestrator} 抽出，原因：
 *
 * <ul>
 *   <li>错误分类逻辑和 Orchestrator 的消息编排职责无关
 *   <li>抽出后更易单元测试（无 Spring 上下文即可 new）
 *   <li>后续 ChatAgent / PlannerAgent 也能直接复用同一套错误映射
 * </ul>
 *
 * <p>本类保留超时阈值常量，Orchestrator 的超时 {@code Mono.delay(Duration)} 仍用 Orchestrator 自身常量
 * 以免 SSE 关闭时机受这里改动影响；本类仅负责"已经抛出的 Throwable 到用户话术"的映射。
 */
@Component
@Slf4j
public class StreamErrorHandler {

  /** 与 Orchestrator 保持一致的首字节超时阈值，用于渲染文案 */
  public static final int FIRST_BYTE_TIMEOUT_SECONDS = 15;
  /** 与 Orchestrator 保持一致的静默超时阈值，用于渲染文案 */
  public static final int IDLE_TIMEOUT_SECONDS = 25;

  /** 错误码枚举（字符串形式，写入 META 便于前端/日志检索） */
  public static final String CODE_TIMEOUT_FIRST_BYTE = "timeout_first_byte";
  public static final String CODE_TIMEOUT_IDLE = "timeout_idle";
  public static final String CODE_RATE_LIMIT = "rate_limit";
  public static final String CODE_AUTH = "auth";
  public static final String CODE_CONTENT_POLICY = "content_policy";
  public static final String CODE_CLIENT_ERROR = "client_error";
  public static final String CODE_SERVER_ERROR = "server_error";
  public static final String CODE_NETWORK = "network";
  public static final String CODE_UNKNOWN = "unknown";

  /**
   * Round 4 新增：可选注入 {@link RagMetrics}。
   * 测试场景（new StreamErrorHandler()）下为 null，{@link #classify} 里做空判兜底，不影响单测。
   */
  @Autowired(required = false)
  private RagMetrics ragMetrics;

  /**
   * 根据异常类型 + 是否已收到首字节，分类到稳定的错误码。
   *
   * @param e 异常
   * @param firstByteReceived 流是否已经吐过至少一个 token
   */
  public String classify(Throwable e, boolean firstByteReceived) {
    String code = classifyInternal(e, firstByteReceived);
    if (ragMetrics != null) {
      ragMetrics.incrementErrorCode(code);
    }
    return code;
  }

  private String classifyInternal(Throwable e, boolean firstByteReceived) {
    if (e instanceof TimeoutException) {
      return firstByteReceived ? CODE_TIMEOUT_IDLE : CODE_TIMEOUT_FIRST_BYTE;
    }
    if (e instanceof HttpClientErrorException ce) {
      int code = ce.getStatusCode().value();
      if (code == 429) return CODE_RATE_LIMIT;
      if (code == 401 || code == 403) return CODE_AUTH;
      String body = ce.getResponseBodyAsString().toLowerCase();
      if (body.contains("content_filter") || body.contains("safety") || body.contains("moderation")) {
        return CODE_CONTENT_POLICY;
      }
      return CODE_CLIENT_ERROR;
    }
    if (e instanceof HttpServerErrorException) return CODE_SERVER_ERROR;
    if (e instanceof ResourceAccessException || e instanceof IOException) return CODE_NETWORK;
    return CODE_UNKNOWN;
  }

  /**
   * 根据错误码生成给终端用户看的一段兜底文案（中文）。
   *
   * @param code              {@link #classify(Throwable, boolean)} 产出的稳定错误码
   * @param firstByteReceived 流是否已经吐过至少一个 token；决定文案措辞
   */
  public String render(String code, boolean firstByteReceived) {
    return switch (code) {
      case CODE_TIMEOUT_FIRST_BYTE ->
          "抱歉，AI 服务响应超时（首字节 " + FIRST_BYTE_TIMEOUT_SECONDS + "s 未到），请稍后重试。";
      case CODE_TIMEOUT_IDLE ->
          "\n\n_[流式中断：连续 " + IDLE_TIMEOUT_SECONDS + "s 未收到新内容]_";
      case CODE_RATE_LIMIT -> "请求过于频繁，请稍后再试。";
      case CODE_AUTH -> "AI 服务认证失败，请联系管理员检查 API Key 配置。";
      case CODE_CONTENT_POLICY -> "抱歉，您的问题或上下文触发了内容安全策略，无法回答。";
      case CODE_CLIENT_ERROR -> "抱歉，请求被服务端拒绝（参数或配额问题），请稍后重试。";
      case CODE_SERVER_ERROR, CODE_NETWORK, CODE_UNKNOWN ->
          firstByteReceived
              ? "\n\n_[AI 服务连接异常，回答已中断]_"
              : "抱歉，AI 服务暂时不可用，请稍后重试。";
      default -> "抱歉，AI 服务暂时不可用，请稍后重试。";
    };
  }
}
