package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.mapper.CrawlerHotItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 热搜智能查询服务（RAG Agent 路由用）
 *
 * 功能：
 *   1. 检测用户问题是否与热榜相关
 *   2. 从 crawler_hot_item 表查询最新热榜数据
 *   3. 格式化为 LLM 可理解的上下文
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotSearchService {

    private final CrawlerHotItemMapper hotItemMapper;

    /** 热榜相关关键词 */
    private static final String[] HOT_KEYWORDS = {
            "热榜", "热搜", "热门", "trending", "热点",
            "排行", "排名", "最火", "最热", "火了",
            "上热搜", "今日", "今天",
            "推荐", "榜单", "流行", "爆款", "视频", "话题",
    };

    /** 来源关键词 → source 枚举名 映射 */
    private static final Map<String, String> SOURCE_KEYWORD_MAP = Map.ofEntries(
            Map.entry("b站", "BILIBILI_HOT"),
            Map.entry("bilibili", "BILIBILI_HOT"),
            Map.entry("哔哩哔哩", "BILIBILI_HOT"),
            Map.entry("微博", "WEIBO_HOT"),
            Map.entry("weibo", "WEIBO_HOT"),
            Map.entry("知乎", "ZHIHU_HOT"),
            Map.entry("zhihu", "ZHIHU_HOT"),
            Map.entry("github", "GITHUB_TRENDING"),
            Map.entry("抖音", "DOUYIN"),
            Map.entry("douyin", "DOUYIN"),
            Map.entry("小红书", "XIAOHONGSHU"),
            Map.entry("红书", "XIAOHONGSHU")
    );

    /** 来源枚举名 → 中文展示名 */
    private static final Map<String, String> SOURCE_DISPLAY_MAP = Map.of(
            "BILIBILI_HOT", "B站热门",
            "WEIBO_HOT", "微博热搜",
            "ZHIHU_HOT", "知乎热榜",
            "GITHUB_TRENDING", "GitHub Trending",
            "DOUYIN", "抖音热搜",
            "XIAOHONGSHU", "小红书热门"
    );

    /**
     * 检测用户问题是否与热榜相关
     */
    public boolean isHotQuery(String question) {
        if (question == null || question.isBlank()) return false;
        String lower = question.toLowerCase();

        // 包含来源关键词 + 热榜关键词 → 高置信度
        boolean hasSource = SOURCE_KEYWORD_MAP.keySet().stream()
                .anyMatch(lower::contains);
        boolean hasHotWord = false;
        for (String kw : HOT_KEYWORDS) {
            if (lower.contains(kw)) {
                hasHotWord = true;
                break;
            }
        }

        // 来源+热词 或 纯热榜关键词组合
        return (hasSource && hasHotWord) || containsStrongHotPattern(lower);
    }

    /**
     * 强热榜模式匹配（不需要来源关键词也能命中）
     */
    private boolean containsStrongHotPattern(String lower) {
        // "今天热搜" "今日热榜" "最新热门" "xxx排行榜" 等
        return lower.matches(".*(?:今[天日]|最新|最近).*(?:热[搜榜门点]|排[行名]|trending).*")
                || lower.matches(".*(?:热[搜榜门点]|排行榜).*(?:是什么|有哪些|看看|推荐).*");
    }

    /**
     * 查询热榜数据并格式化为 LLM 上下文
     *
     * @param question 用户问题（用于识别来源）
     * @return 格式化的热榜上下文文本
     */
    public String queryAndFormat(String question) {
        String lower = question.toLowerCase();

        // 识别用户想查哪个来源
        String targetSource = null;
        for (Map.Entry<String, String> entry : SOURCE_KEYWORD_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                targetSource = entry.getValue();
                break;
            }
        }

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> items;

        if (targetSource != null) {
            // 查指定来源
            items = hotItemMapper.findByDateAndSource(today, targetSource);
            if (items.isEmpty()) {
                // 今天没数据，查昨天
                items = hotItemMapper.findByDateAndSource(today.minusDays(1), targetSource);
            }
        } else {
            // 未指定来源 → 各来源 Top 10
            items = hotItemMapper.topNByDate(today, 10);
            if (items.isEmpty()) {
                items = hotItemMapper.topNByDate(today.minusDays(1), 10);
            }
        }

        if (items.isEmpty()) {
            return "暂无热榜数据。爬虫可能还未采集今日数据。";
        }

        return formatAsContext(items, targetSource);
    }

    /**
     * 格式化热榜数据为 LLM 上下文
     */
    private String formatAsContext(List<Map<String, Object>> items, String source) {
        StringBuilder sb = new StringBuilder();

        if (source != null) {
            String displayName = SOURCE_DISPLAY_MAP.getOrDefault(source, source);
            sb.append("以下是最新的【").append(displayName).append("】热榜数据：\n\n");
        } else {
            sb.append("以下是各平台最新热榜数据（每个来源 Top 10）：\n\n");
        }

        String currentSource = null;
        for (Map<String, Object> item : items) {
            String itemSource = String.valueOf(item.get("source"));

            // 来源分组标题
            if (!itemSource.equals(currentSource)) {
                currentSource = itemSource;
                String displayName = SOURCE_DISPLAY_MAP.getOrDefault(itemSource, itemSource);
                sb.append("## ").append(displayName).append("\n");
            }

            int rank = item.get("rank") != null ? ((Number) item.get("rank")).intValue() : 0;
            String title = String.valueOf(item.getOrDefault("title", ""));
            String hotScore = String.valueOf(item.getOrDefault("hot_score", ""));
            String content = item.get("content") != null ? String.valueOf(item.get("content")) : "";
            String url = item.get("url") != null ? String.valueOf(item.get("url")) : "";

            sb.append(rank).append(". ").append(title);
            if (!hotScore.isBlank() && !"null".equals(hotScore)) {
                sb.append(" (热度: ").append(hotScore).append(")");
            }
            sb.append("\n");

            if (!content.isBlank() && !"null".equals(content)) {
                // 截取摘要前100字
                String brief = content.length() > 100 ? content.substring(0, 100) + "..." : content;
                sb.append("   ").append(brief).append("\n");
            }
            if (!url.isBlank() && !"null".equals(url)) {
                sb.append("   链接: ").append(url).append("\n");
            }
        }

        return sb.toString();
    }
}
