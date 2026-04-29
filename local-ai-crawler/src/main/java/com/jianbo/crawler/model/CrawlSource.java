package com.jianbo.crawler.model;

/**
 * 爬虫数据来源枚举
 *
 * 每个来源对应一种采集策略：
 *   - static  → Jsoup 静态页面解析
 *   - api     → 公开 JSON 接口调用
 *   - dynamic → Playwright 无头浏览器渲染
 */
public enum CrawlSource {

    GITHUB_TRENDING("GitHub Trending", "static", "技术趋势"),
    WEIBO_HOT("微博热搜", "static", "社会热点"),
    ZHIHU_HOT("知乎热榜", "api", "深度讨论"),
    BILIBILI_HOT("B站热门", "api", "视频热点"),
    XIAOHONGSHU("小红书热门", "dynamic", "生活方式"),
    DOUYIN("抖音热点", "dynamic", "短视频热点");

    /** 中文展示名 */
    private final String displayName;
    /** 采集类型：static / api / dynamic */
    private final String crawlType;
    /** 内容分类标签 */
    private final String category;

    CrawlSource(String displayName, String crawlType, String category) {
        this.displayName = displayName;
        this.crawlType = crawlType;
        this.category = category;
    }

    public String getDisplayName() { return displayName; }
    public String getCrawlType() { return crawlType; }
    public String getCategory() { return category; }
}
