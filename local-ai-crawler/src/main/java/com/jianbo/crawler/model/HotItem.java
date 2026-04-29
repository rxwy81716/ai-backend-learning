package com.jianbo.crawler.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 热榜条目（单条采集结果），使用 JDK16+ record 不可变语义
 *
 * @param title     标题
 * @param content   内容摘要 / 描述
 * @param url       原始链接
 * @param source    数据来源枚举
 * @param rank      排名序号
 * @param hotScore  热度值（如搜索量、播放量等）
 * @param metadata  扩展元数据（语言、标签、作者等）
 * @param crawlTime 采集时间
 */
public record HotItem(
        String title,
        String content,
        String url,
        CrawlSource source,
        int rank,
        String hotScore,
        Map<String, String> metadata,
        LocalDateTime crawlTime
) {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 生成用于布隆过滤器去重的唯一指纹
     * 规则：来源名 + ":" + 标题，同一来源同标题视为重复
     */
    public String fingerprint() {
        return source.name() + ":" + title;
    }

    /**
     * 格式化为结构化文本，供后续向量化入库
     * 输出示例：
     *   【GitHub Trending #1】
     *   标题：xxx
     *   摘要：xxx
     *   链接：xxx
     *   热度：xxx
     */
    public String toStructuredText() {
        var sb = new StringBuilder();
        sb.append("【").append(source.getDisplayName()).append(" #").append(rank).append("】\n");
        sb.append("标题：").append(title).append("\n");
        if (content != null && !content.isBlank()) {
            sb.append("摘要：").append(content).append("\n");
        }
        if (url != null && !url.isBlank()) {
            sb.append("链接：").append(url).append("\n");
        }
        if (hotScore != null && !hotScore.isBlank()) {
            sb.append("热度：").append(hotScore).append("\n");
        }
        if (metadata != null && !metadata.isEmpty()) {
            metadata.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    sb.append(k).append("：").append(v).append("\n");
                }
            });
        }
        sb.append("采集时间：").append(crawlTime.format(FMT)).append("\n");
        return sb.toString();
    }
}
