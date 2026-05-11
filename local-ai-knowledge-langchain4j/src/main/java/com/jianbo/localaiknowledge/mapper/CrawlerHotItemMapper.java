package com.jianbo.localaiknowledge.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 爬虫热榜数据 Mapper（与 Spring AI 版完全一致，无 AI 框架依赖）。
 */
@Mapper
public interface CrawlerHotItemMapper {

    @Select("""
        SELECT id, source, title, content, url, rank, hot_score, metadata::text as metadata, crawl_time
        FROM crawler_hot_item WHERE crawl_date = #{date} AND source = #{source} ORDER BY rank ASC
    """)
    List<Map<String, Object>> findByDateAndSource(@Param("date") LocalDate date, @Param("source") String source);

    @Select("""
        SELECT id, source, title, content, url, rank, hot_score, metadata::text as metadata, crawl_time
        FROM crawler_hot_item WHERE crawl_date = #{date} ORDER BY source, rank ASC
    """)
    List<Map<String, Object>> findByDate(@Param("date") LocalDate date);

    @Select("""
        SELECT source, title, hot_score, url, rank
        FROM crawler_hot_item WHERE crawl_date = #{date} AND rank <= #{topN} ORDER BY source, rank ASC
    """)
    List<Map<String, Object>> topNByDate(@Param("date") LocalDate date, @Param("topN") int topN);

    @Select("""
        SELECT source, COUNT(*) AS item_count, MIN(crawl_time) AS first_crawl, MAX(crawl_time) AS last_crawl
        FROM crawler_hot_item WHERE crawl_date = #{date} GROUP BY source ORDER BY item_count DESC
    """)
    List<Map<String, Object>> dailyStats(@Param("date") LocalDate date);
}
