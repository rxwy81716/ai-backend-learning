-- ==================== System Prompt 配置表 ====================
CREATE TABLE IF NOT EXISTS system_prompt
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    content     TEXT         NOT NULL,
    description VARCHAR(500),
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE system_prompt IS 'System Prompt 配置表（支持多套 Prompt 切换）';
COMMENT ON COLUMN system_prompt.name IS 'Prompt 名称（唯一标识，如 rag_default / rag_strict）';
COMMENT ON COLUMN system_prompt.is_default IS '是否为默认 Prompt（全局只有一个 true）';

-- 插入默认 RAG Prompt
INSERT INTO system_prompt (name, content, description, is_default)
VALUES ('rag_default',
        '你是一个专业的知识库问答助手。请严格根据以下【参考资料】回答用户问题。

        规则：
        1. 只能基于参考资料中的内容回答，不要编造或推测任何信息
        2. 如果参考资料中没有相关内容，请明确回答"根据现有知识库，暂未找到相关信息"
        3. 回答时请标注信息来源，格式为 [来源: 文档名]
        4. 如果多个文档包含相关信息，请综合回答并分别标注来源
        5. 保持回答简洁、准确、有条理

        【参考资料】
        {context}',
        '默认 RAG 问答 Prompt（严格引用模式，减少幻觉）', TRUE),

       ('rag_creative',
        '你是一个智能知识库助手。请参考以下资料回答问题，可以适当补充你的专业知识进行扩展说明。

        【参考资料】
        {context}

        注意：
        - 优先使用参考资料中的信息，引用时标注 [来源: 文档名]
        - 如果资料不足，可以基于专业知识补充，但需标注"（补充说明）"
        - 如果完全无关，请如实告知',
        '创意模式 Prompt（允许适度补充，适合探索型问答）', FALSE);

CREATE INDEX IF NOT EXISTS idx_prompt_default ON system_prompt (is_default) WHERE is_default = TRUE;

-- ==================== document_task 表扩展（用户归属） ====================
ALTER TABLE document_task
    ADD COLUMN IF NOT EXISTS user_id VARCHAR(64);
ALTER TABLE document_task
    ADD COLUMN IF NOT EXISTS doc_scope VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';

CREATE INDEX IF NOT EXISTS idx_doc_task_user ON document_task (user_id);
CREATE INDEX IF NOT EXISTS idx_doc_task_scope ON document_task (doc_scope);

COMMENT ON COLUMN document_task.user_id IS '上传用户ID（NULL=公共/爬虫）';
COMMENT ON COLUMN document_task.doc_scope IS '文档范围：PRIVATE=用户私有 / PUBLIC=公共';
