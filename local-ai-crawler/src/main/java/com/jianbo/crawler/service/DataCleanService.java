package com.jianbo.crawler.service;

import com.jianbo.crawler.model.CrawlSource;
import com.jianbo.crawler.model.HotItem;
import com.jianbo.crawler.util.TextCleanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据清洗与格式化服务
 *
 * 处理流水线中的第二步：
 *   采集 → 去重 → 【清洗格式化】 → 结构化 → 向量化封装 → 入库
 *
 * 清洗内容：
 *   - 文本标准化（去 HTML 标签、控制字符、Emoji、多余空白）
 *   - 标题/摘要长度裁剪
 *   - 空内容条目过滤
 *   - 将多条 HotItem 组装为结构化文档文本（供向量化入库）
 */
@Slf4j
@Service
public class DataCleanService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 标题最大长度 */
    private static final int MAX_TITLE_LEN = 200;
    /** 摘要最大长度 */
    private static final int MAX_CONTENT_LEN = 500;

    /**
     * 清洗单条 HotItem（返回清洗后的新实例）
     */
    public HotItem cleanItem(HotItem item) {
        return new HotItem(
                TextCleanUtil.truncate(TextCleanUtil.clean(item.title()), MAX_TITLE_LEN),
                TextCleanUtil.truncate(TextCleanUtil.clean(item.content()), MAX_CONTENT_LEN),
                item.url(),
                item.source(),
                item.rank(),
                item.hotScore(),
                item.metadata(),
                item.crawlTime()
        );
    }

    /**
     * 批量清洗（过滤掉标题为空的无效条目）
     */
    public List<HotItem> cleanAll(List<HotItem> items) {
        if (items == null || items.isEmpty()) return List.of();

        List<HotItem> cleaned = items.stream()
                .map(this::cleanItem)
                .filter(item -> !item.title().isBlank())
                .toList();

        int removed = items.size() - cleaned.size();
        if (removed > 0) {
            log.info("数据清洗：原始 {} 条 → 有效 {} 条，过滤无效 {} 条",
                    items.size(), cleaned.size(), removed);
        }
        return cleaned;
    }

    /**
     * 将多条 HotItem 组装为一份结构化文档文本
     *
     * 格式示例：
     *   ═══════════════════════════════
     *   GitHub Trending 热榜数据
     *   采集时间：2025-04-29 10:00
     *   共 25 条
     *   ═══════════════════════════════
     *
     *   【GitHub Trending #1】
     *   标题：xxx
     *   摘要：xxx
     *   链接：xxx
     *   ...
     *
     * @param items  清洗后的条目列表
     * @param source 数据来源
     * @return 结构化文档文本
     */
    public String buildDocument(List<HotItem> items, CrawlSource source) {
        if (items == null || items.isEmpty()) return "";

        var sb = new StringBuilder();

        // 文档头部
        sb.append("═".repeat(40)).append("\n");
        sb.append(source.getDisplayName()).append(" 热榜数据\n");
        sb.append("采集时间：").append(LocalDateTime.now().format(FMT)).append("\n");
        sb.append("数据分类：").append(source.getCategory()).append("\n");
        sb.append("共 ").append(items.size()).append(" 条\n");
        sb.append("═".repeat(40)).append("\n\n");

        // 逐条拼接结构化文本
        for (HotItem item : items) {
            sb.append(item.toStructuredText()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 为文档生成文件名
     * 格式：来源_yyyyMMdd_HHmm.txt
     */
    public String buildFileName(CrawlSource source) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        return source.name().toLowerCase() + "_" + timestamp + ".txt";
    }
}
