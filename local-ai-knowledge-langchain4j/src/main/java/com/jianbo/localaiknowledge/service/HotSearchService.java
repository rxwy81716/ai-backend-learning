package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.mapper.CrawlerHotItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 热搜智能查询服务（与 Spring AI 版完全一致，无 AI 框架依赖）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotSearchService {

    private final CrawlerHotItemMapper hotItemMapper;

    private static final Map<String, String> SOURCE_KEYWORD_MAP = Map.ofEntries(
            Map.entry("b站", "BILIBILI_HOT"), Map.entry("bilibili", "BILIBILI_HOT"),
            Map.entry("哔哩哔哩", "BILIBILI_HOT"), Map.entry("微博", "WEIBO_HOT"),
            Map.entry("weibo", "WEIBO_HOT"), Map.entry("知乎", "ZHIHU_HOT"),
            Map.entry("zhihu", "ZHIHU_HOT"), Map.entry("github", "GITHUB_TRENDING"),
            Map.entry("抖音", "DOUYIN"), Map.entry("douyin", "DOUYIN"),
            Map.entry("小红书", "XIAOHONGSHU"), Map.entry("红书", "XIAOHONGSHU"));

    private static final Map<String, String> SOURCE_DISPLAY_MAP = Map.of(
            "BILIBILI_HOT", "B站热门", "WEIBO_HOT", "微博热搜",
            "ZHIHU_HOT", "知乎热榜", "GITHUB_TRENDING", "GitHub Trending",
            "DOUYIN", "抖音热搜", "XIAOHONGSHU", "小红书热门");

    private static final int PER_SOURCE_TOP_N = 5;
    private static final int GLOBAL_MAX_ITEMS = 20;

    public String queryAndFormat(String question) {
        String lower = question.toLowerCase();
        String targetSource = null;
        for (Map.Entry<String, String> entry : SOURCE_KEYWORD_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) { targetSource = entry.getValue(); break; }
        }

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> items;

        if (targetSource != null) {
            items = hotItemMapper.findByDateAndSource(today, targetSource);
            if (items.isEmpty()) items = hotItemMapper.findByDateAndSource(today.minusDays(1), targetSource);
        } else {
            items = hotItemMapper.topNByDate(today, PER_SOURCE_TOP_N);
            if (items.isEmpty()) items = hotItemMapper.topNByDate(today.minusDays(1), PER_SOURCE_TOP_N);
            if (items.size() > GLOBAL_MAX_ITEMS) items = items.subList(0, GLOBAL_MAX_ITEMS);
        }

        if (items.isEmpty()) return "暂无热榜数据。爬虫可能还未采集今日数据。";

        items = dedup(items);
        return formatAsContext(items, targetSource);
    }

    private String formatAsContext(List<Map<String, Object>> items, String source) {
        StringBuilder sb = new StringBuilder();
        if (source != null) {
            sb.append("以下是最新的【").append(SOURCE_DISPLAY_MAP.getOrDefault(source, source)).append("】热榜数据：\n\n");
        } else {
            sb.append("以下是各平台最新热榜数据（每个来源 Top ").append(PER_SOURCE_TOP_N).append("）：\n\n");
        }

        String currentSource = null;
        for (Map<String, Object> item : items) {
            String itemSource = String.valueOf(item.get("source"));
            if (!itemSource.equals(currentSource)) {
                currentSource = itemSource;
                sb.append("## ").append(SOURCE_DISPLAY_MAP.getOrDefault(itemSource, itemSource)).append("\n");
            }
            int rank = item.get("rank") != null ? ((Number) item.get("rank")).intValue() : 0;
            String title = String.valueOf(item.getOrDefault("title", ""));
            String hotScore = String.valueOf(item.getOrDefault("hot_score", ""));
            String content = item.get("content") != null ? String.valueOf(item.get("content")) : "";
            String url = item.get("url") != null ? String.valueOf(item.get("url")) : "";

            sb.append(rank).append(". ").append(title);
            if (!hotScore.isBlank() && !"null".equals(hotScore)) sb.append(" (热度: ").append(hotScore).append(")");
            sb.append("\n");
            if (!content.isBlank() && !"null".equals(content)) {
                sb.append("   ").append(content.length() > 100 ? content.substring(0, 100) + "..." : content).append("\n");
            }
            if (!url.isBlank() && !"null".equals(url)) sb.append("   链接: ").append(url).append("\n");
        }
        return sb.toString();
    }

    private List<Map<String, Object>> dedup(List<Map<String, Object>> items) {
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : items) {
            if (seen.add(String.valueOf(item.getOrDefault("title", "")))) result.add(item);
        }
        return result;
    }
}
