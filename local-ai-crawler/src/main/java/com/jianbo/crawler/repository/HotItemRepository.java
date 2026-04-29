package com.jianbo.crawler.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.crawler.model.CrawlSource;
import com.jianbo.crawler.model.HotItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 热榜数据持久化仓库（只写）
 *
 * 使用 JdbcTemplate 操作 crawler_hot_item 表：
 *   - 批量保存每日热榜数据（ON CONFLICT 去重）
 *
 * 查询/统计由 local-ai-knowledge 服务负责（职责分离）。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class HotItemRepository {

    private final JdbcTemplate jdbcTemplate;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 批量保存热榜条目（冲突时跳过，不重复入库）
     *
     * @param items  清洗后的条目列表
     * @param source 数据来源
     * @return 实际新增行数
     */
    public int batchSave(List<HotItem> items, CrawlSource source) {
        if (items == null || items.isEmpty()) return 0;

        String sql = """
                INSERT INTO crawler_hot_item (source, title, content, url, rank, hot_score, metadata, crawl_date, crawl_time)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                ON CONFLICT (crawl_date, source, title) DO NOTHING
                """;

        int inserted = 0;
        for (HotItem item : items) {
            try {
                int rows = jdbcTemplate.update(sql,
                        source.name(),
                        item.title(),
                        item.content(),
                        item.url(),
                        item.rank(),
                        item.hotScore(),
                        toJson(item.metadata()),
                        Date.valueOf(LocalDate.now()),
                        Timestamp.valueOf(item.crawlTime())
                );
                inserted += rows;
            } catch (Exception e) {
                log.warn("保存热榜条目失败：source={}, title={}, error={}",
                        source.name(), item.title(), e.getMessage());
            }
        }

        log.info("热榜持久化：{} | 提交 {} 条 → 新增 {} 条", source.getDisplayName(), items.size(), inserted);
        return inserted;
    }

    /** Map → JSON 字符串 */
    private String toJson(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
