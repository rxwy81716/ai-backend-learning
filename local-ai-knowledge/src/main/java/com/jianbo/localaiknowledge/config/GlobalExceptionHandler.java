package com.jianbo.localaiknowledge.config;

import com.jianbo.localaiknowledge.utils.R;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 全局异常统一处理 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** 参数校验异常 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<R<?>> handleValidation(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
    log.warn("参数校验失败: {}", message);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.badRequest(message));
  }

  /** 绑定异常 */
  @ExceptionHandler(BindException.class)
  public ResponseEntity<R<?>> handleBind(BindException e) {
    String message =
        e.getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
    log.warn("参数绑定失败: {}", message);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.badRequest(message));
  }

  /** 认证异常 */
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<R<?>> handleAuth(AuthenticationException e) {
    log.warn("认证失败: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(R.unauthorized("认证失败: " + e.getMessage()));
  }

  /** 权限不足 */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<R<?>> handleAccessDenied(AccessDeniedException e) {
    log.warn("权限不足: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(R.forbidden("权限不足"));
  }

  /** 业务层未认证（HTTP 401）。 */
  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<R<?>> handleUnauthorized(UnauthorizedException e) {
    log.warn("未认证拦截: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(R.unauthorized(e.getMessage()));
  }

  /** 业务层越权（HTTP 403）。 */
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<R<?>> handleForbidden(ForbiddenException e) {
    log.warn("越权拦截: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(R.forbidden(e.getMessage()));
  }

  /**
   * SecurityException 兜底：JDK / 第三方代码意外抛出时按 403 处理；项目自身业务请改抛
   * {@link UnauthorizedException} / {@link ForbiddenException}。
   */
  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<R<?>> handleSecurity(SecurityException e) {
    log.warn("SecurityException 兜底拦截 (建议改抛 Unauthorized/Forbidden): {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(R.forbidden(e.getMessage()));
  }

  /** 非法参数 */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<R<?>> handleIllegalArgument(IllegalArgumentException e) {
    log.warn("非法参数: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.badRequest(e.getMessage()));
  }

  /** 业务异常（状态冲突）：例如 "任务正在处理中"、"源文件已不存在" 等可预期错误。 返回 409 Conflict 而非 500，便于前端区分系统错误与业务约束冲突。 */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<R<?>> handleIllegalState(IllegalStateException e) {
    log.warn("业务状态冲突: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(R.error(e.getMessage()));
  }

  /** 其他异常 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<R<?>> handleException(Exception e) {
    log.error("系统异常", e);
    // 生产环境不返回详细错误信息，避免泄露敏感信息
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(R.serverError("系统繁忙，请稍后重试"));
  }
}
