package com.jianbo.localaiknowledge.config;

/**
 * 业务层抛出的"越权 / 权限不足"异常（HTTP 403）。
 *
 * <p>与 Spring Security 的 {@link org.springframework.security.access.AccessDeniedException}
 * 区分：那一个由 Security 框架在过滤器链里抛出，这一个由业务代码主动抛出（如"不是你的会话"）。
 * 在 {@link GlobalExceptionHandler} 里二者都映射到 403。
 */
public class ForbiddenException extends RuntimeException {
  public ForbiddenException(String message) {
    super(message);
  }
}
