-- Multi-user AI chat schema for MySQL 8.0+
-- 5 tables: sys_user / login_token / chat_session / chat_message / operation_log
CREATE DATABASE IF NOT EXISTS ai_chat_local
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE ai_chat_local;

-- ========== 1. 用户 ==========
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL COMMENT '登录用户名，唯一',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    role ENUM('user', 'admin') NOT NULL DEFAULT 'user',
    status ENUM('active', 'banned') NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ========== 2. 登录令牌（30 分钟滑动过期） ==========
CREATE TABLE IF NOT EXISTS login_token (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    token VARCHAR(64) NOT NULL COMMENT '随机令牌',
    user_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_active_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近活动时间，超过 30 分钟未活动则失效',
    PRIMARY KEY (id),
    UNIQUE KEY uk_token (token),
    KEY idx_token_user (user_id),
    CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ========== 3. 会话（用户隔离 + 逻辑删除 + 90 天过期判定） ==========
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL COMMENT '归属用户',
    title VARCHAR(200) NOT NULL DEFAULT '新对话',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    deleted_at DATETIME(3) NULL,
    last_active_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近访问/发消息时间，用于 90 天过期判定',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_session_user_list (user_id, deleted, updated_at),
    KEY idx_session_purge (last_active_at),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ========== 4. 消息（+user_id 限额统计、+cost 成本统计） ==========
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL COMMENT '归属用户（冗余，便于按人统计）',
    role ENUM('system', 'user', 'assistant', 'tool') NOT NULL,
    content MEDIUMTEXT NOT NULL,
    status ENUM('complete', 'pending', 'failed') NOT NULL DEFAULT 'complete',
    error_message VARCHAR(1000) NULL,
    prompt_tokens INT UNSIGNED NULL,
    completion_tokens INT UNSIGNED NULL,
    cost DECIMAL(12,6) NULL COMMENT 'assistant 消息的大模型调用成本（元），写入时按配置单价计算',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_message_session_time (session_id, created_at, id),
    KEY idx_message_user_daily (user_id, role, created_at),
    KEY idx_message_unresolved (role, status, created_at),
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES chat_session (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_message_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ========== 5. 操作日志（只插不改） ==========
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NULL COMMENT '操作人，系统任务为 NULL',
    username VARCHAR(50) NOT NULL COMMENT '操作人快照',
    action VARCHAR(50) NOT NULL COMMENT 'LOGIN/REGISTER/BAN_USER/UNBAN_USER/RESET_PASSWORD/DELETE_SESSION/ADMIN_DELETE_SESSION/PURGE_SESSION 等',
    target_type VARCHAR(30) NULL,
    target_id VARCHAR(64) NULL,
    detail VARCHAR(1000) NULL,
    ip VARCHAR(45) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_log_created (created_at),
    KEY idx_log_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 说明：
-- 1. 初始管理员账号由后端启动时自动创建（见 auth 模块初始化器），
--    用户名 admin，初始密码从环境变量 ADMIN_INITIAL_PASSWORD 读取（默认 Admin@123），
--    首次登录后请立即修改。
-- 2. 大模型配置（模型名/地址/token 单价）固定在 application.properties，不再使用 ai_model_config 表。
-- 3. 旧库数据无 user_id 无法归属，本次升级直接 drop 旧库后执行本脚本重建。
