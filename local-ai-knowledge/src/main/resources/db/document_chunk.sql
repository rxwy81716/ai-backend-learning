-- 文档分段表（同步存储分段原文+元数据，向量同时存入 vector_store 表）
CREATE TABLE IF NOT EXISTS document_chunk (
    id            BIGSERIAL PRIMARY KEY,
    task_id       VARCHAR(64)  NOT NULL,          -- 关联 document_task.task_id
    chunk_index   INT          NOT NULL,           -- 分段序号（从0开始）
    content       TEXT         NOT NULL,            -- 分段原文
    source        VARCHAR(255) NOT NULL,            -- 文档来源名（文件名）
    user_id       VARCHAR(64),                      -- 上传用户ID
    doc_scope     VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',  -- PRIVATE/PUBLIC
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chunk_task ON document_chunk(task_id);
CREATE INDEX IF NOT EXISTS idx_chunk_source ON document_chunk(source);
CREATE INDEX IF NOT EXISTS idx_chunk_user ON document_chunk(user_id);
CREATE INDEX IF NOT EXISTS idx_chunk_scope ON document_chunk(doc_scope);

COMMENT ON TABLE  document_chunk IS '文档分段表（同步存储分段原文+元数据）';
COMMENT ON COLUMN document_chunk.task_id IS '关联文档任务ID';
COMMENT ON COLUMN document_chunk.chunk_index IS '分段序号';
COMMENT ON COLUMN document_chunk.content IS '分段原文';
COMMENT ON COLUMN document_chunk.source IS '文档来源名（文件名）';
COMMENT ON COLUMN document_chunk.user_id IS '上传用户ID';
COMMENT ON COLUMN document_chunk.doc_scope IS '文档范围：PRIVATE/PUBLIC';
