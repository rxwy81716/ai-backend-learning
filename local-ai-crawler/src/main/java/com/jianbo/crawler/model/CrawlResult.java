package com.jianbo.crawler.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 爬虫采集结果（不可变 record）
 *
 * @param source     数据来源
 * @param items      采集到的热榜条目列表
 * @param success    是否采集成功
 * @param errorMsg   失败时的错误信息
 * @param crawlTime  本次采集时间
 * @param costMillis 本次采集耗时（毫秒）
 */
public record CrawlResult(
        CrawlSource source,
        List<HotItem> items,
        boolean success,
        String errorMsg,
        LocalDateTime crawlTime,
        long costMillis
) {

    /** 构造成功结果 */
    public static CrawlResult success(CrawlSource source, List<HotItem> items, long costMillis) {
        return new CrawlResult(source, items, true, null, LocalDateTime.now(), costMillis);
    }

    /** 构造失败结果 */
    public static CrawlResult fail(CrawlSource source, String errorMsg, long costMillis) {
        return new CrawlResult(source, List.of(), false, errorMsg, LocalDateTime.now(), costMillis);
    }

    /** 采集到的条目数量 */
    public int itemCount() {
        return items == null ? 0 : items.size();
    }
}
