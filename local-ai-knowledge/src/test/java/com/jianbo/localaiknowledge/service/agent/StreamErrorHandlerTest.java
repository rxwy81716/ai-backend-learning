package com.jianbo.localaiknowledge.service.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link StreamErrorHandler} 错误分类与文案渲染单测，无 Spring 上下文依赖。 */
class StreamErrorHandlerTest {

  private final StreamErrorHandler handler = new StreamErrorHandler();

  @Test
  @DisplayName("超时异常按是否已收到首字节区分两种错误码")
  void classifyTimeout() {
    TimeoutException e = new TimeoutException();
    assertThat(handler.classify(e, false))
        .isEqualTo(StreamErrorHandler.CODE_TIMEOUT_FIRST_BYTE);
    assertThat(handler.classify(e, true))
        .isEqualTo(StreamErrorHandler.CODE_TIMEOUT_IDLE);
  }

  @Test
  @DisplayName("HTTP 429 → rate_limit；401/403 → auth")
  void classifyHttpClientCodes() {
    assertThat(handler.classify(
        HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many", null, null, null),
        false))
        .isEqualTo(StreamErrorHandler.CODE_RATE_LIMIT);

    assertThat(handler.classify(
        HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauth", null, null, null),
        false))
        .isEqualTo(StreamErrorHandler.CODE_AUTH);

    assertThat(handler.classify(
        HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", null, null, null),
        false))
        .isEqualTo(StreamErrorHandler.CODE_AUTH);
  }

  @Test
  @DisplayName("HTTP 4xx 但 body 含 content_filter / safety / moderation → content_policy")
  void classifyContentPolicy() {
    HttpClientErrorException e = HttpClientErrorException.create(
        HttpStatus.BAD_REQUEST, "Bad", null,
        "{\"error\":\"content_filter triggered\"}".getBytes(),
        java.nio.charset.StandardCharsets.UTF_8);
    assertThat(handler.classify(e, false))
        .isEqualTo(StreamErrorHandler.CODE_CONTENT_POLICY);
  }

  @Test
  @DisplayName("普通 4xx → client_error；5xx → server_error；网络 → network")
  void classifyOtherHttp() {
    assertThat(handler.classify(
        HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad", null, null, null),
        false))
        .isEqualTo(StreamErrorHandler.CODE_CLIENT_ERROR);

    assertThat(handler.classify(
        HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "ISE", null, null, null),
        false))
        .isEqualTo(StreamErrorHandler.CODE_SERVER_ERROR);

    assertThat(handler.classify(new ResourceAccessException("conn refused"), false))
        .isEqualTo(StreamErrorHandler.CODE_NETWORK);

    assertThat(handler.classify(new IOException("broken pipe"), false))
        .isEqualTo(StreamErrorHandler.CODE_NETWORK);

    assertThat(handler.classify(new RuntimeException("?"), false))
        .isEqualTo(StreamErrorHandler.CODE_UNKNOWN);
  }

  @Test
  @DisplayName("render：每个错误码都有非空中文兜底文案")
  void renderAllCodes() {
    String[] codes = {
        StreamErrorHandler.CODE_TIMEOUT_FIRST_BYTE,
        StreamErrorHandler.CODE_TIMEOUT_IDLE,
        StreamErrorHandler.CODE_RATE_LIMIT,
        StreamErrorHandler.CODE_AUTH,
        StreamErrorHandler.CODE_CONTENT_POLICY,
        StreamErrorHandler.CODE_CLIENT_ERROR,
        StreamErrorHandler.CODE_SERVER_ERROR,
        StreamErrorHandler.CODE_NETWORK,
        StreamErrorHandler.CODE_UNKNOWN,
    };
    for (String code : codes) {
      String msg = handler.render(code, false);
      assertThat(msg).as("code=" + code).isNotBlank();
    }
  }

  @Test
  @DisplayName("server_error / network / unknown 在已发首字节场景下使用『回答已中断』短文案")
  void renderInflightVariant() {
    String inflight = handler.render(StreamErrorHandler.CODE_SERVER_ERROR, true);
    String firstByte = handler.render(StreamErrorHandler.CODE_SERVER_ERROR, false);
    assertThat(inflight).contains("中断");
    assertThat(firstByte).contains("不可用");
  }
}
