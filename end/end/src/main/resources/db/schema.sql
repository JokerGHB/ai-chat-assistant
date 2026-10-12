-- Local single-user AI chat schema for MySQL 8.0+
CREATE DATABASE IF NOT EXISTS ai_chat_local
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE ai_chat_local;

CREATE TABLE IF NOT EXISTS ai_model_config (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT 'Display name shown in the UI',
    provider VARCHAR(50) NOT NULL COMMENT 'Provider identifier, e.g. openai-compatible',
    model_code VARCHAR(150) NOT NULL COMMENT 'Model identifier sent to the provider',
    base_url VARCHAR(500) NULL COMMENT 'Optional endpoint override; secrets belong in local environment config',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_model_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL DEFAULT '新对话',
    model_config_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_session_updated (updated_at, id),
    KEY idx_session_model (model_config_id),
    CONSTRAINT fk_session_model
        FOREIGN KEY (model_config_id) REFERENCES ai_model_config (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    role ENUM('system', 'user', 'assistant', 'tool') NOT NULL,
    content MEDIUMTEXT NOT NULL,
    status ENUM('complete', 'pending', 'failed') NOT NULL DEFAULT 'complete',
    error_message VARCHAR(1000) NULL,
    prompt_tokens INT UNSIGNED NULL,
    completion_tokens INT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_message_session_time (session_id, created_at, id),
    CONSTRAINT fk_message_session
        FOREIGN KEY (session_id) REFERENCES chat_session (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Optional local example model; replace model_code/base_url with the provider you use.
-- Never put API keys in this table or commit them to source control.
INSERT INTO ai_model_config (name, provider, model_code, base_url, enabled, sort_order)
SELECT '本地默认模型', 'openai-compatible', 'replace-with-your-model', NULL, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM ai_model_config WHERE provider = 'openai-compatible' AND model_code = 'replace-with-your-model'
);
