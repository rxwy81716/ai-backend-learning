package com.jianbo.localaiknowledge.utils;

/**
 * 文本清洗工具（与 Spring AI 版完全一致，无 AI 框架依赖）。
 */
public class TextCleanUtil {

    public static String clean(String text) {
        if (text == null) return "";
        return text
                .replaceAll("\\s+", " ")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .trim();
    }

    public static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
