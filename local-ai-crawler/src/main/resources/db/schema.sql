-- ====================================================
-- 爬虫热榜数据持久化表
-- 存储每日各来源的热榜条目，支持历史查询和统计展示
-- ====================================================

CREATE TABLE IF NOT EXISTS crawler_hot_item (
    id              BIGSERIAL       PRIMARY KEY,
    source          VARCHAR(50)     NOT NULL,           -- 数据来源（GITHUB_TRENDING / WEIBO_HOT / ...）
    title           VARCHAR(500)    NOT NULL,           -- 标题
    content         TEXT,                               -- 内容摘要
    url             VARCHAR(1000),                      -- 原始链接
    rank            INT             NOT NULL DEFAULT 0, -- 排名
    hot_score       VARCHAR(100),                       -- 热度值
    metadata        JSONB,                              -- 扩展元数据（JSON 格式）
    crawl_date      DATE            NOT NULL DEFAULT CURRENT_DATE,  -- 采集日期（按天聚合）
    crawl_time      TIMESTAMP       NOT NULL DEFAULT NOW(),         -- 精确采集时间
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- 按日期+来源查询索引（每日热搜展示核心查询）
CREATE INDEX IF NOT EXISTS idx_hot_item_date_source ON crawler_hot_item (crawl_date, source);

-- 按来源查询索引
CREATE INDEX IF NOT EXISTS idx_hot_item_source ON crawler_hot_item (source);

-- 去重索引：同一天同一来源同一标题不重复入库
CREATE UNIQUE INDEX IF NOT EXISTS uk_hot_item_dedup ON crawler_hot_item (crawl_date, source, title);

COMMENT ON TABLE  crawler_hot_item IS '爬虫每日热榜数据';
COMMENT ON COLUMN crawler_hot_item.source     IS '数据来源枚举名';
COMMENT ON COLUMN crawler_hot_item.crawl_date IS '采集日期（按天聚合，用于每日统计）';
COMMENT ON COLUMN crawler_hot_item.metadata   IS '扩展信息，如语言/作者/标签等';

-- ====================================================
-- 爬虫任务执行日志
-- 记录每次流水线执行的详细结果，便于问题排查和统计
-- ====================================================

CREATE TABLE IF NOT EXISTS crawler_task_log (
    id              BIGSERIAL       PRIMARY KEY,
    source          VARCHAR(50)     NOT NULL,           -- 数据来源
    trigger_type    VARCHAR(20)     NOT NULL DEFAULT 'SCHEDULED',  -- 触发方式：SCHEDULED / MANUAL
    success         BOOLEAN         NOT NULL DEFAULT FALSE,
    crawled_count   INT             NOT NULL DEFAULT 0, -- 采集到的原始条数
    stored_count    INT             NOT NULL DEFAULT 0, -- 最终入库条数
    cost_ms         BIGINT          NOT NULL DEFAULT 0, -- 耗时（毫秒）
    error_msg       TEXT,                               -- 错误信息
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_task_log_source ON crawler_task_log (source, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_task_log_time ON crawler_task_log (created_at DESC);

COMMENT ON TABLE  crawler_task_log IS '爬虫任务执行日志';
COMMENT ON COLUMN crawler_task_log.trigger_type IS '触发方式：SCHEDULED=定时 / MANUAL=手动';
