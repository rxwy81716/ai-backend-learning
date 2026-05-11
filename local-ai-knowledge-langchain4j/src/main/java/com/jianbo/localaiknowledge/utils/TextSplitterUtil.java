package com.jianbo.localaiknowledge.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切片工具（与 Spring AI 版一致，无 AI 框架依赖）。
 */
public class TextSplitterUtil {

    public static List<String> split(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) return List.of();

        List<String> chunks = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(pos + chunkSize, text.length());
            chunks.add(text.substring(pos, end));
            pos += chunkSize - overlap;
        }
        return chunks;
    }
}
