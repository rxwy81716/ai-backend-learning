package com.jianbo.localaiknowledge.config;

/**
 * 业务层抛出的"未认证"异常（HTTP 401）。
 *
 * <p>替代原先用 {@code throw new SecurityException("未登录")} 然后在
 * {@link GlobalExceptionHandler} 里靠消息字符串 {@code contains("未登录")} 反推 401 的脆弱写法。
 * 异常类型即语义，避免文案改动导致状态码漂移。
 */
public class UnauthorizedException extends RuntimeException {
  public UnauthorizedException(String message) {
    super(message);
  }
}
