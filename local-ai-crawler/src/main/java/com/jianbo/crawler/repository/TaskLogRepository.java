package com.jianbo.crawler.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 爬虫任务执行日志 Repository
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TaskLogRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 保存一条任务执行日志
     */
    public void save(String source, String triggerType, boolean success,
                     int crawledCount, int storedCount, long costMs, String errorMsg) {
        String sql = """
                INSERT INTO crawler_task_log (source, trigger_type, success, crawled_count, stored_count, cost_ms, error_msg, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, source, triggerType, success,
                crawledCount, storedCount, costMs, errorMsg,
                Timestamp.valueOf(LocalDateTime.now()));
    }

    /**
     * 查询最近 N 条日志
     */
    public List<Map<String, Object>> findRecent(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM crawler_task_log ORDER BY created_at DESC LIMIT ?", limit);
    }

    /**
     * 按来源查询最近 N 条日志
     */
    public List<Map<String, Object>> findRecentBySource(String source, int limit) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM crawler_task_log WHERE source = ? ORDER BY created_at DESC LIMIT ?",
                source, limit);
    }

    /**
     * 查询今日各来源执行统计
     */
    public List<Map<String, Object>> todayStats() {
        return jdbcTemplate.queryForList("""
                SELECT source,
                       COUNT(*)                          AS total_runs,
                       SUM(CASE WHEN success THEN 1 ELSE 0 END) AS success_count,
                       SUM(CASE WHEN NOT success THEN 1 ELSE 0 END) AS fail_count,
                       SUM(stored_count)                 AS total_stored,
                       AVG(cost_ms)::INT                 AS avg_cost_ms,
                       MAX(created_at)                   AS last_run
                FROM crawler_task_log
                WHERE created_at::DATE = CURRENT_DATE
                GROUP BY source
                ORDER BY source
                """);
    }
}
