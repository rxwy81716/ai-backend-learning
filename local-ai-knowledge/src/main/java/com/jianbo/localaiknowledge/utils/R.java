package com.jianbo.localaiknowledge.utils;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结构
 *
 * @param <T> 数据类型
 */
@Data
public class R<T> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 状态码：0=成功，其他=失败 */
  private int code;

  /** 消息 */
  private String message;

  /** 数据 */
  private T data;

  private R() {}

  private R(int code, String message, T data) {
    this.code = code;
    this.message = message;
    this.data = data;
  }

  // ==================== 成功响应 ====================

  public static <T> R<T> ok() {
    return new R<>(0, "success", null);
  }

  public static <T> R<T> ok(T data) {
    return new R<>(0, "success", data);
  }

  public static <T> R<T> ok(String message, T data) {
    return new R<>(0, message, data);
  }

  public static <T> R<T> ok(String message) {
    return new R<>(0, message, null);
  }

  // ==================== 失败响应 ====================

  public static <T> R<T> error(String message) {
    return new R<>(1, message, null);
  }

  public static <T> R<T> error(int code, String message) {
    return new R<>(code, message, null);
  }

  public static <T> R<T> error(String message, T data) {
    return new R<>(1, message, data);
  }

  // ==================== 快捷失败 ====================

  public static <T> R<T> badRequest(String message) {
    return new R<>(400, message, null);
  }

  public static <T> R<T> unauthorized(String message) {
    return new R<>(401, message, null);
  }

  public static <T> R<T> forbidden(String message) {
    return new R<>(403, message, null);
  }

  public static <T> R<T> notFound(String message) {
    return new R<>(404, message, null);
  }

  public static <T> R<T> serverError(String message) {
    return new R<>(500, message, null);
  }

  // ==================== 辅助方法 ====================

  public boolean isSuccess() {
    return code == 0;
  }

  public boolean isError() {
    return code != 0;
  }
}
