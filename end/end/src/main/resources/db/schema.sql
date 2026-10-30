-- =====================================================================
-- 拾光 AI 聊天助手 · 数据库 Schema
-- MySQL 8.0+，utf8mb4 支持 emoji 与全量 Unicode
-- 共 6 张表：sys_user / login_token / chat_session / chat_message
--         / operation_log / chat_question_stat
-- =====================================================================
CREATE DATABASE IF NOT EXISTS ai_chat_local
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE ai_chat_local;

-- =====================================================================
-- 表 1：sys_user —— 用户账号表
-- 用途：存储登录凭证与角色，不存明文密码（BCrypt 哈希）
-- 约束：username 唯一，role 分 user/admin，status 分 active/banned
-- =====================================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username VARCHAR(50) NOT NULL COMMENT '登录用户名，业务唯一键',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希，永不存明文',
    role ENUM('user', 'admin') NOT NULL DEFAULT 'user' COMMENT '角色：普通用户 / 管理员',
    status ENUM('active', 'banned') NOT NULL DEFAULT 'active' COMMENT '账号状态：活跃 / 已封禁（封禁后立即无法登录）',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，毫秒精度',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间（自动维护）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username) COMMENT '用户名唯一约束，注册时查重用',
    KEY idx_user_status (status) COMMENT '按状态索引，管理后台封禁列表查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================
-- 表 2：login_token —— 登录令牌表
-- 用途：实现"30 分钟闲置自动失效"的滑动过期会话管理
-- 过期判定逻辑：每次请求刷新 last_active_at，30 分钟未刷新则自动删除
-- 业务场景：登出、改用户名、封禁时通过删除本记录实现"强制下线"
-- =====================================================================
CREATE TABLE IF NOT EXISTS login_token (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    token VARCHAR(64) NOT NULL COMMENT '随机令牌（UUID/Hex），前端存 localStorage 后每次请求携带',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '令牌所属用户（外键 → sys_user）',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '令牌创建时间',
    last_active_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近活动时间，超过 30 分钟未刷新则判定失效',
    PRIMARY KEY (id),
    UNIQUE KEY uk_token (token) COMMENT 'token 唯一，请求时按此字段快速定位所属用户',
    KEY idx_token_user (user_id) COMMENT '按用户查令牌，改用户名/封禁时批量删除',
    CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON DELETE CASCADE ON UPDATE CASCADE COMMENT '用户删除时级联清理令牌'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================
-- 表 3：chat_session —— 会话表
-- 用途：用户的聊天会话，一条会话包含多条消息
-- 隔离策略：查询必带 user_id + deleted=0，从 Mapper 层强制越权返回 404
-- 删除策略：逻辑删除（deleted=1），数据保留；last_active_at 超 90 天定时任务物理清理
-- =====================================================================
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '归属用户（外键 → sys_user）',
    title VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '会话标题，首条消息后自动截取生成',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 正常 / 1 已删除（用户侧不可见，管理端仍可追溯）',
    deleted_at DATETIME(3) NULL COMMENT '删除时间，物理清理任务依据此时间判断',
    last_active_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近活动时间，发消息/查看列表都会刷新；超过 90 天触发物理清理',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间（自动维护）',
    PRIMARY KEY (id),
    KEY idx_session_user_list (user_id, deleted, updated_at) COMMENT '用户查自己的会话列表：按用户+未删除过滤，按更新时间倒序',
    KEY idx_session_purge (last_active_at) COMMENT '定时清理任务：按活动时间范围扫描 90 天未活跃会话',
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON DELETE CASCADE ON UPDATE CASCADE COMMENT '用户删除时级联清理其会话'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================
-- 表 4：chat_message —— 消息表（核心业务表）
-- 用途：存储每一条用户提问和 AI 回复
-- 关键设计：
--   ① 冗余 user_id —— 避免 JOIN 会话表即可按人统计（每日限额、成本、高频提问）
--   ② cost 写入时落库 —— 按当时 DeepSeek 配置的 prompt/completion 单价计算，
--      管理看板直接 SUM 无需实时计算（即使后续单价改了历史也不变）
--   ③ role 区分 user/assistant —— 限额统计只数 role='user'，成本只看 role='assistant'
--   ④ status 标记 failed —— AI 调用失败时消息标记 failed，不单独建表存"未解决对话"
-- 死锁教训：外键 fk_message_session 导致"先插消息再 touch 会话"时两事务互持共享锁 → 死锁，
--          修复方案：业务层先 touch（排他锁）再 insert（共享锁），所有事务锁顺序统一
-- =====================================================================
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id BIGINT UNSIGNED NOT NULL COMMENT '所属会话（外键 → chat_session）',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '归属用户（冗余字段，便于按人统计，避免 JOIN chat_session）',
    role ENUM('system', 'user', 'assistant', 'tool') NOT NULL COMMENT '消息角色：user=用户提问 / assistant=AI回复',
    content MEDIUMTEXT NOT NULL COMMENT '消息正文（user 存用户提问，assistant 存 AI 回复）',
    status ENUM('complete', 'pending', 'failed') NOT NULL DEFAULT 'complete' COMMENT 'AI 回复状态：complete=正常 / pending=处理中 / failed=调用失败',
    error_message VARCHAR(1000) NULL COMMENT 'AI 调用失败时的错误原因（仅 assistant 消息有值）',
    prompt_tokens INT UNSIGNED NULL COMMENT '本次调用消耗的 prompt token 数（仅 assistant 消息有值）',
    completion_tokens INT UNSIGNED NULL COMMENT '本次调用消耗的 completion token 数（仅 assistant 消息有值）',
    cost DECIMAL(12,6) NULL COMMENT '本次 AI 调用成本（元）= prompt_tokens × 单价 + completion_tokens × 单价；写入时计算并落库',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消息创建时间',
    PRIMARY KEY (id),
    KEY idx_message_session_time (session_id, created_at, id) COMMENT '会话消息列表：按会话过滤 + 时间正序',
    KEY idx_message_user_daily (user_id, role, created_at) COMMENT '每日限额统计：按用户 + role=user + 当日范围 COUNT',
    KEY idx_message_unresolved (role, status, created_at) COMMENT '未解决对话看板：role=assistant + status=failed',
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES chat_session (id)
        ON DELETE CASCADE ON UPDATE CASCADE COMMENT '会话删除时级联清理消息',
    CONSTRAINT fk_message_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON DELETE CASCADE ON UPDATE CASCADE COMMENT '用户删除时级联清理消息'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================
-- 表 5：operation_log —— 操作日志表
-- 用途：审计追溯，只 INSERT，不提供 UPDATE/DELETE 接口
-- action 枚举（部分）：
--   LOGIN / LOGOUT / REGISTER / CHANGE_USERNAME / CHANGE_PASSWORD
--   BAN_USER / UNBAN_USER / DELETE_SESSION / ADMIN_DELETE_SESSION
--   PURGE_SESSION（定时任务清理 90 天未活跃会话）
-- =====================================================================
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT UNSIGNED NULL COMMENT '操作人用户 ID（系统定时任务为 NULL）',
    username VARCHAR(50) NOT NULL COMMENT '操作人用户名快照（冗余存储，即使改用户名也能查到当时是谁）',
    action VARCHAR(50) NOT NULL COMMENT '操作类型，见上方枚举',
    target_type VARCHAR(30) NULL COMMENT '操作目标类型：USER / SESSION / SYSTEM 等',
    target_id VARCHAR(64) NULL COMMENT '操作目标 ID（字符串兼容非数字类型）',
    detail VARCHAR(1000) NULL COMMENT '操作详情描述',
    ip VARCHAR(45) NULL COMMENT '操作来源 IP（兼容 IPv6 128 bit 文本最长 45 字符）',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_log_created (created_at) COMMENT '管理端日志按时间范围查询',
    KEY idx_log_user (user_id, created_at) COMMENT '查询某用户的历史操作'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================
-- 表 6：chat_question_stat —— 高频提问预聚合表
-- 用途：管理看板"高频提问 Top 10"的数据源
-- 工作原理：用户每发一条消息，应用侧对文本做 trim + 转小写取 MD5，
--          执行 INSERT ON DUPLICATE KEY UPDATE ask_count = ask_count + 1
--          看板"全部"口径直接查本表，无需 GROUP BY 全表 chat_message
--          7d / 30d 时间窗口仍走 chat_message 实时聚合（本表无时间窗口字段）
-- 字段 user_id=0 是全局哨兵值，不指向 sys_user，因此本表不建外键
-- =====================================================================
CREATE TABLE IF NOT EXISTS chat_question_stat (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    question_md5 CHAR(32) NOT NULL COMMENT '问题文本归一化（trim + 小写）后的 MD5 指纹，作为去重键',
    question VARCHAR(500) NOT NULL COMMENT '问题样例文本，保留最新一条提问内容便于看板展示',
    user_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '归属用户 ID；0=全局汇总（哨兵值），非 0=按用户统计（预留）',
    ask_count BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '该问题累计被提问次数（预聚合计数，写入时 +1）',
    last_asked_at DATETIME(3) NOT NULL COMMENT '最近一次提问时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次出现时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间（自动维护）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_question_stat (user_id, question_md5) COMMENT '同一用户同一问题只能有一行（或 user_id=0 的全局行），ON DUPLICATE KEY UPDATE 依赖此唯一键',
    KEY idx_question_stat_top (ask_count) COMMENT '按提问次数降序查 Top 10'
    -- 注意：user_id=0 是"全局"哨兵值，不指向 sys_user，因此本表不建外键
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================
-- 补充说明
-- =====================================================================
-- 1. 初始管理员账号由后端启动时自动创建（见 AdminInitializer），
--    用户名 admin，初始密码从环境变量 ADMIN_INITIAL_PASSWORD 读取（默认 Admin@123）。
--
-- 2. 大模型配置（模型名 / API 地址 / token 单价）固定写在 application.properties，
--    不建 ai_model_config 表。模型固定 deepseek-flash，不做动态模型切换。
--
-- 3. 旧库升级：若之前已有旧版 ai_chat_local，建议先 drop 旧库再执行本脚本重建，
--    因旧表结构缺少 user_id 字段，数据无法归属到新表。
--
-- 4. chat_question_stat 统计口径说明：
--    - 会话物理删除（管理员删除 / 90 天清理）后 ask_count 不回退——"问过即计入"；
--    - 看板"全部"口径直接查本表，7d/30d 时间窗口仍走 chat_message 实时聚合；
--    - 不回填历史数据（原因：本机 MySQL 26.7 已移除 MD5() 函数，SQL 层无法计算指纹，
--      应用侧 Java DigestUtils 不受影响；如确需回填须写 Java 一次性任务）。
--
-- 5. 死锁修复教训（BUG-002）：
--    chat_message.session_id 有外键指向 chat_session.id。
--    原实现「先 INSERT message 再 UPDATE session.last_active_at」：
--      INSERT 时 MySQL 给会话行加共享锁（外键检查），
--      UPDATE 时要升级排他锁 —— 两事务各持共享锁互等对方释放 → 死锁。
--    修复：先 UPDATE session（排他锁）再 INSERT message（共享锁），
--          所有事务锁顺序统一为"先父表排他、后子表共享"，并发请求从互等变排队。
