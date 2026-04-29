package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.mapper.CrawlerHotItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 每日热榜数据查询控制器
 *
 * 查询 crawler_hot_item 表中由爬虫采集的热榜数据，
 * 提供每日热搜展示、历史查询、统计分析等 API。
 *
 * 接口列表：
 *   GET  /api/hot/today                        → 今日全部热榜
 *   GET  /api/hot/today/{source}               → 今日指定来源热榜
 *   GET  /api/hot/date/{date}                  → 指定日期全部热榜
 *   GET  /api/hot/date/{date}/{source}         → 指定日期+来源热榜
 *   GET  /api/hot/stats/today                  → 今日各来源采集统计
 *   GET  /api/hot/stats/date/{date}            → 指定日期统计
 *   GET  /api/hot/stats/trend?days=7           → 最近N天趋势
 *   GET  /api/hot/top?date=xxx&topN=10         → 各来源 Top N
 *
 * 所有接口无需认证（公开数据）。
 */
@Slf4j
@RestController
@RequestMapping("/api/hot")
@RequiredArgsConstructor
public class HotItemController {

    private final CrawlerHotItemMapper hotItemMapper;

    // ==================== 热榜数据查询 ====================

    /**
     * 今日全部热榜数据
     */
    @GetMapping("/today")
    public Map<String, Object> today() {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> items = hotItemMapper.findByDate(today);
        return Map.of(
                "date", today.toString(),
                "total", items.size(),
                "items", items
        );
    }

    /**
     * 今日指定来源的热榜数据
     *
     * @param source 来源名称（如 GITHUB_TRENDING、WEIBO_HOT 等）
     */
    @GetMapping("/today/{source}")
    public Map<String, Object> todayBySource(@PathVariable String source) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> items = hotItemMapper.findByDateAndSource(today, source.toUpperCase());
        return Map.of(
                "date", today.toString(),
                "source", source.toUpperCase(),
                "total", items.size(),
                "items", items
        );
    }

    /**
     * 指定日期全部热榜数据
     *
     * @param date 日期，格式 yyyy-MM-dd
     */
    @GetMapping("/date/{date}")
    public Map<String, Object> byDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<Map<String, Object>> items = hotItemMapper.findByDate(date);
        return Map.of(
                "date", date.toString(),
                "total", items.size(),
                "items", items
        );
    }

    /**
     * 指定日期 + 来源的热榜数据
     */
    @GetMapping("/date/{date}/{source}")
    public Map<String, Object> byDateAndSource(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable String source) {
        List<Map<String, Object>> items = hotItemMapper.findByDateAndSource(date, source.toUpperCase());
        return Map.of(
                "date", date.toString(),
                "source", source.toUpperCase(),
                "total", items.size(),
                "items", items
        );
    }

    // ==================== 统计分析 ====================

    /**
     * 今日各来源采集统计（条目数、首次/末次采集时间）
     */
    @GetMapping("/stats/today")
    public Map<String, Object> statsToday() {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> stats = hotItemMapper.dailyStats(today);
        return Map.of(
                "date", today.toString(),
                "sources", stats
        );
    }

    /**
     * 指定日期的采集统计
     */
    @GetMapping("/stats/date/{date}")
    public Map<String, Object> statsByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<Map<String, Object>> stats = hotItemMapper.dailyStats(date);
        return Map.of(
                "date", date.toString(),
                "sources", stats
        );
    }

    /**
     * 最近 N 天的采集趋势（默认7天）
     *
     * 返回每天每个来源的采集条目数，适合折线图/柱状图展示。
     */
    @GetMapping("/stats/trend")
    public Map<String, Object> trend(@RequestParam(defaultValue = "7") int days) {
        List<Map<String, Object>> trend = hotItemMapper.trendStats(days);
        return Map.of(
                "days", days,
                "trend", trend
        );
    }

    /**
     * 各来源 Top N 热榜（默认今天，Top 10）
     *
     * 取每个来源排名前 N 的条目。
     */
    @GetMapping("/top")
    public Map<String, Object> topN(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "10") int topN) {
        if (date == null) date = LocalDate.now();
        List<Map<String, Object>> items = hotItemMapper.topNByDate(date, topN);
        return Map.of(
                "date", date.toString(),
                "topN", topN,
                "items", items
        );
    }
}
