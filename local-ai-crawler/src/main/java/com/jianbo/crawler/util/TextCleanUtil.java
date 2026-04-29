package com.jianbo.crawler.util;

import java.util.regex.Pattern;

/**
 * 文本清洗工具类
 *
 * 对爬虫采集的原始文本进行标准化处理：
 *   - 去除 HTML 标签残留
 *   - 去除特殊控制字符
 *   - 压缩连续空白
 *   - 去除 Emoji 和不可见字符
 */
public final class TextCleanUtil {

    private TextCleanUtil() {}

    /** HTML 标签正则 */
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    /** 连续空白（含换行）压缩为单个空格 */
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");
    /** 控制字符（保留换行和制表） */
    private static final Pattern CONTROL_CHAR = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    /** Emoji 和特殊符号（Unicode Supplementary Planes） */
    private static final Pattern EMOJI = Pattern.compile("[\\x{10000}-\\x{10FFFF}]");

    /**
     * 执行全套清洗流程
     *
     * @param raw 原始文本
     * @return 清洗后的文本（null 输入返回空字符串）
     */
    public static String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String text = raw;
        // 1. 去除 HTML 标签
        text = HTML_TAG.matcher(text).replaceAll("");
        // 2. 去除控制字符
        text = CONTROL_CHAR.matcher(text).replaceAll("");
        // 3. 去除 Emoji
        text = EMOJI.matcher(text).replaceAll("");
        // 4. HTML 实体解码（常见）
        text = text.replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"")
                   .replace("&nbsp;", " ")
                   .replace("&#39;", "'");
        // 5. 压缩连续空白
        text = MULTI_SPACE.matcher(text).replaceAll(" ");

        return text.strip();
    }

    /**
     * 截取摘要（前 maxLen 个字符，超出部分用 ... 代替）
     *
     * @param text   原文
     * @param maxLen 最大长度
     * @return 截取后的摘要
     */
    public static String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
