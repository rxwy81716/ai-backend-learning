package com.jianbo.localaiknowledge.utils;

public class TextCleanUtil {

  /** 清洗文本,去空行 去多余空格 去特殊符号 */
  public static String clean(String text) {
    if (text == null) {
      return "";
    }

    // 1. 替换制表符,全角空格
    text = text.replace("\t", " ").replace((char) 12288, ' ');

    // 2 去除所有多余空格
    text = text.replaceAll(" +", " ");

    // 3. 合并多个换行 -> 标准换行
    text = text.replaceAll("\\n+", "\n");

    // 4. 去除首尾空格
    text = text.trim();

    return text;
  }

  public static String cleanText(String text) {
    if (text == null) return "";
    return text.replaceAll("\\s+", " ") // 所有空白→单个空格
        .replaceAll(" +", " ") // 多空格→单空格
        .replaceAll("\\n+", "\n") // 多换行→单换行
        .trim();
  }
}
