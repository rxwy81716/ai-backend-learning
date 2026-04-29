package com.jianbo.localaiknowledge.utils;

import java.util.regex.Pattern;

/**
 * 账户校验工具
 *
 * <p>用户名规则：
 *
 * <ul>
 *   <li>长度 4-20 位
 *   <li>必须以字母开头
 *   <li>只允许字母、数字、下划线
 * </ul>
 *
 * <p>密码规则：
 *
 * <ul>
 *   <li>长度 8-32 位
 *   <li>不允许包含空白字符
 *   <li>必须同时包含字母和数字
 *   <li>不能与用户名相同
 * </ul>
 *
 * <p>昵称规则（可选字段）：
 *
 * <ul>
 *   <li>允许为空（null / 空串 / 全空白），由业务方走默认值
 *   <li>非空时去除首尾空白后长度不超过 20 位
 * </ul>
 */
public final class AccountValidator {

  private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{3,19}$");

  private static final Pattern PASSWORD_HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
  private static final Pattern PASSWORD_HAS_DIGIT = Pattern.compile(".*\\d.*");
  private static final Pattern PASSWORD_HAS_WHITESPACE = Pattern.compile(".*\\s.*");

  private AccountValidator() {}

  /** 校验用户名，不合法时抛 {@link IllegalArgumentException} */
  public static void validateUsername(String username) {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("用户名不能为空");
    }
    if (!USERNAME_PATTERN.matcher(username).matches()) {
      throw new IllegalArgumentException("用户名必须以字母开头，仅允许字母/数字/下划线，长度 4-20 位");
    }
  }

  /** 校验密码（结合用户名做相等性检查），不合法时抛 {@link IllegalArgumentException} */
  public static void validatePassword(String password, String username) {
    if (password == null || password.isEmpty()) {
      throw new IllegalArgumentException("密码不能为空");
    }
    if (password.length() < 8 || password.length() > 32) {
      throw new IllegalArgumentException("密码长度需为 8-32 位");
    }
    if (PASSWORD_HAS_WHITESPACE.matcher(password).matches()) {
      throw new IllegalArgumentException("密码不允许包含空白字符");
    }
    if (!PASSWORD_HAS_LETTER.matcher(password).matches()
        || !PASSWORD_HAS_DIGIT.matcher(password).matches()) {
      throw new IllegalArgumentException("密码必须同时包含字母和数字");
    }
    if (username != null && password.equals(username)) {
      throw new IllegalArgumentException("密码不能与用户名相同");
    }
  }

  /** 校验昵称（允许 null / 空 / 全空白，均视为不填） */
  public static void validateNickname(String nickname) {
    if (nickname == null || nickname.isBlank()) {
      return;
    }
    if (nickname.trim().length() > 20) {
      throw new IllegalArgumentException("昵称去除空白后长度不能超过 20 位");
    }
  }
}
