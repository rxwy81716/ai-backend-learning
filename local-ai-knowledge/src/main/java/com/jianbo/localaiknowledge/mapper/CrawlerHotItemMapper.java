package com.jianbo.localaiknowledge.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 爬虫热榜数据 Mapper
 *
 * 查询 crawler_hot_item 表（由 local-ai-crawler 写入），
 * 提供每日热榜展示和统计分析查询。
 */
@Mapper
public interface CrawlerHotItemMapper {

    /**
     * 查询指定日期 + 指定来源的热榜数据
     */
    @Select("""
        SELECT id, source, title, content, url, rank, hot_score, 
               metadata::text as metadata, crawl_time
        FROM crawler_hot_item
        WHERE crawl_date = #{date} AND source = #{source}
        ORDER BY rank ASC
    """)
    List<Map<String, Object>> findByDateAndSource(@Param("date") LocalDate date,
                                                   @Param("source") String source);

    /**
     * 查询指定日期的所有热榜数据（按来源+排名排序）
     */
    @Select("""
        SELECT id, source, title, content, url, rank, hot_score,
               metadata::text as metadata, crawl_time
        FROM crawler_hot_item
        WHERE crawl_date = #{date}
        ORDER BY source, rank ASC
    """)
    List<Map<String, Object>> findByDate(@Param("date") LocalDate date);

    /**
     * 分页查询指定日期的热榜数据（可选来源筛选）
     */
    @Select("""
        <script>
        SELECT id, source, title, content, url, rank, hot_score,
               metadata::text as metadata, crawl_time
        FROM crawler_hot_item
        WHERE crawl_date = #{date}
        <if test="source != null and source != ''"> AND source = #{source}</if>
        ORDER BY source, rank ASC
        LIMIT #{limit} OFFSET #{offset}
        </script>
    """)
    List<Map<String, Object>> findByDatePaged(@Param("date") LocalDate date,
                                               @Param("source") String source,
                                               @Param("offset") int offset,
                                               @Param("limit") int limit);

    /**
     * 统计指定日期的热榜总条数（可选来源筛选）
     */
    @Select("""
        <script>
        SELECT COUNT(*) FROM crawler_hot_item
        WHERE crawl_date = #{date}
        <if test="source != null and source != ''"> AND source = #{source}</if>
        </script>
    """)
    int countByDate(@Param("date") LocalDate date, @Param("source") String source);

    /**
     * 统计指定日期各来源的条目数量
     */
    @Select("""
        SELECT source, COUNT(*) AS item_count,
               MIN(crawl_time) AS first_crawl,
               MAX(crawl_time) AS last_crawl
        FROM crawler_hot_item
        WHERE crawl_date = #{date}
        GROUP BY source
        ORDER BY item_count DESC
    """)
    List<Map<String, Object>> dailyStats(@Param("date") LocalDate date);

    /**
     * 最近 N 天的每日采集趋势（按天+来源分组）
     */
    @Select("""
        SELECT crawl_date, source, COUNT(*) AS item_count
        FROM crawler_hot_item
        WHERE crawl_date >= CURRENT_DATE - #{days}
        GROUP BY crawl_date, source
        ORDER BY crawl_date DESC, source
    """)
    List<Map<String, Object>> trendStats(@Param("days") int days);

    /**
     * 查询指定来源的最近一次采集时间
     */
    @Select("SELECT MAX(crawl_time) FROM crawler_hot_item WHERE source = #{source}")
    java.sql.Timestamp lastCrawlTime(@Param("source") String source);

    /**
     * 查询指定日期各来源的热度 Top N
     */
    @Select("""
        SELECT source, title, hot_score, url, rank
        FROM crawler_hot_item
        WHERE crawl_date = #{date} AND rank <= #{topN}
        ORDER BY source, rank ASC
    """)
    List<Map<String, Object>> topNByDate(@Param("date") LocalDate date,
                                          @Param("topN") int topN);
}
