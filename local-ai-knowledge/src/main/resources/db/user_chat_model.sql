-- =============================================================================
-- 用户自备对话模型（OpenAI 兼容 Chat）表
-- 与后端 UserChatModelConfig / UserChatModelController 对应；执行前请在目标库运行本脚本。
-- =============================================================================
CREATE TABLE IF NOT EXISTS user_chat_model_config(
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT       NOT NULL REFERENCES sys_user (id) ON DELETE CASCADE,
    alias              VARCHAR(64)  NOT NULL,
    label              VARCHAR(100),
    base_url           TEXT         NOT NULL,
    api_key_cipher     TEXT         NOT NULL,
    completions_path   VARCHAR(200),
    model              VARCHAR(200) NOT NULL,
    temperature        REAL         NOT NULL DEFAULT 0.3,
    max_tokens         INT          NOT NULL DEFAULT 2048,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, alias)
);

COMMENT ON TABLE user_chat_model_config IS '用户自备 OpenAI 兼容 Chat 配置（运行时 model=user:{alias}）';
CREATE INDEX IF NOT EXISTS idx_user_chat_model_user ON user_chat_model_config (user_id);
