-- 文档解析任务表（持久化任务状态 + 操作日志）
CREATE TABLE IF NOT EXISTS document_task
(
    id              BIGSERIAL PRIMARY KEY,
    task_id         VARCHAR(64)  NOT NULL UNIQUE,
    file_name       VARCHAR(255) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    file_size       BIGINT       NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED',
    total_chunks    INT          NOT NULL DEFAULT 0,
    imported_chunks INT          NOT NULL DEFAULT 0,
    error_msg       TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    finished_at     TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_doc_task_status ON document_task (status);
CREATE INDEX IF NOT EXISTS idx_doc_task_created ON document_task (created_at DESC);

COMMENT ON TABLE document_task IS '文档解析任务表';
COMMENT ON COLUMN document_task.task_id IS '任务ID（UUID）';
COMMENT ON COLUMN document_task.status IS 'UPLOADED/PARSING/IMPORTING/DONE/FAILED';

-- 文档操作日志表（记录每一步操作）
CREATE TABLE IF NOT EXISTS document_task_log
(
    id         BIGSERIAL PRIMARY KEY,
    task_id    VARCHAR(64) NOT NULL,
    action     VARCHAR(50) NOT NULL,
    detail     TEXT,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_doc_log_task ON document_task_log (task_id);

COMMENT ON TABLE document_task_log IS '文档任务操作日志';
COMMENT ON COLUMN document_task_log.action IS '操作类型：UPLOAD/PARSE_START/PARSE_DONE/IMPORT_START/IMPORT_DONE/FAILED';


create table chat_conversation
(
    id         bigserial
        primary key,
    session_id varchar(64) not null,
    user_id    varchar(64),
    role       varchar(16) not null,
    content    text        not null,
    metadata   jsonb,
    created_at timestamp default now()
);

alter table chat_conversation
    owner to jianbo;

create index idx_session
    on chat_conversation (session_id, created_at);

COMMENT ON TABLE chat_conversation IS '聊天会话表';
COMMENT ON COLUMN chat_conversation.session_id IS '会话ID';
COMMENT ON COLUMN chat_conversation.user_id IS '用户ID';
COMMENT ON COLUMN chat_conversation.role IS '角色';
COMMENT ON COLUMN chat_conversation.content IS '内容';
COMMENT ON COLUMN chat_conversation.metadata IS '元数据';
COMMENT ON COLUMN chat_conversation.created_at IS '创建时间';
