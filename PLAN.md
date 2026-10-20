# 多用户 AI 智能对话系统改造计划书

## 1. 现状与差距分析

当前系统为**单用户本地版**：无登录鉴权、无用户体系，会话和消息直连 DeepSeek 存储于 MySQL。对照需求，差距如下：

| 需求模块 | 现状 | 差距 |
| --- | --- | --- |
| 用户账号管理 | 无 | 全部新增：注册/登录/改密/封禁/重置密码 |
| 登录状态管理 | 无 | 新增令牌体系 + 30 分钟闲置超时 |
| 会话/消息管理 | 单用户全量读写 | 增加用户隔离、逻辑删除、90 天定时物理清理 |
| AI 对话 | 基础多轮对话已有 | 增加敏感词过滤、每日次数限制、未解决对话标记 |
| 管理员功能 | 无 | 全部新增：用户管理、全平台对话管理、统计看板 |
| 操作日志 | 无 | 全部新增 |
| 密码安全 | 无 | BCrypt 加密存储 |

## 2. 总体技术方案

保持现有技术栈：Spring Boot + MyBatis + MySQL + Vue 3 + Vite，不引入重型框架。

| 决策点 | 方案 | 理由 |
| --- | --- | --- |
| 身份认证 | 服务端令牌表 `login_token`（随机 token + 滑动过期） | 封禁立即生效、可强制下线；JWT 无法服务端即时失效 |
| 会话超时 | 每次请求刷新 `last_active_at`，超过 30 分钟未活动令牌失效 | 满足"闲置 30 分钟自动注销" |
| 密码加密 | `spring-security-crypto` 的 BCrypt（不引入完整 Spring Security） | 轻量，一个依赖即可 |
| 权限控制 | 自定义 `AuthInterceptor` + `@CurrentUser` 参数解析器；`/api/admin/**` 校验管理员角色 | 简单直接，便于课程讲解 |
| 模型配置 | 固定 `deepseek-flash`，模型名/地址/token 单价写在 `application.properties` | 不做模型切换，砍掉 `ai_model_config` 表 |
| 敏感词过滤 | `chat.sensitive-words` 配置列表，启动加载进内存匹配，调用 AI 前拦截 | 需求未要求管理员维护词库，砍掉词库表 |
| 每日次数限制 | 实时统计 `chat_message` 当日 user 消息数，上限配置化 | 无需额外表，口径准确 |
| 调用统计 | 复用 `chat_message`：assistant 消息记录 token 数和 `cost` | 调用次数/成本/未解决全部从消息表聚合，砍掉调用日志表 |
| 未解决对话 | AI 调用失败时写入 `status='failed'` 的 assistant 消息 | 与现有 status 枚举兼容，统计口径清晰 |
| 定时清理 | `@Scheduled` 每日任务，物理删除 90 天无活动会话 | 满足过期清理需求 |
| 前端路由 | 新增 `vue-router`（登录/注册/聊天/管理后台四个视图） | 当前单页无路由，多角色页面必须拆分 |
| 统计图表 | ECharts | 看板可视化标准方案 |

## 3. 数据库设计（详见第 8 节 SQL）

共 **5 张表**。

新增 3 张：`sys_user`（用户）、`login_token`（登录令牌）、`operation_log`（操作日志）。
改造 2 张：`chat_session`（+user_id、逻辑删除、最后活动时间）、`chat_message`（+user_id、+cost）。
删除 1 张：`ai_model_config`（模型固定，配置入 properties）。

## 4. 后端改造清单

### 4.1 用户与认证模块（新增）
- `SysUser` 实体、`SysUserMapper`、`LoginToken` 实体、`LoginTokenMapper`
- `AuthService`：注册（用户名唯一校验、密码 6-12 位复杂度校验、BCrypt）、登录（校验封禁状态、签发 token）、登出、修改密码
- `AuthController`：`POST /api/auth/register`、`POST /api/auth/login`、`POST /api/auth/logout`、`GET /api/auth/profile`、`PATCH /api/auth/password`
- `AuthInterceptor`：校验 token、滑动刷新过期时间、解析当前用户；封禁用户请求直接 401
- 配置：令牌有效期 30 分钟，密码复杂度正则

### 4.2 聊天业务改造
- `ChatSessionService` / `ChatMessageService`：所有查询增加 `user_id` 条件，越权访问返回 404（不暴露他人数据存在性）
- 会话创建：首条消息发送后自动生成标题（取提问前 24 字）；`PATCH` 改名保留
- 会话删除改为逻辑删除（`deleted=1`）；列表查询过滤已删除
- `ChatTurnService` 发送流程调整为：
  1. 敏感词检测 → 命中则 400 拦截，不调 AI、不存消息
  2. 每日次数校验 → 超限 429 拒绝
  3. 存用户消息 → 调 AI → 成功存 assistant(complete, 含 token 数和 cost)；失败存 assistant(failed)，用户消息仍保留
- `SensitiveWordService`：启动时从 `chat.sensitive-words` 配置加载词库到内存，提供 `containsSensitive(content)`
- `DeepSeekClient`：回复后按配置的单价计算 cost 一并返回
- 配置项：`chat.daily-limit`（默认 100）、`deepseek.prompt-price-per-1k`、`deepseek.completion-price-per-1k`

### 4.3 管理员模块（新增）
- `AdminUserController`（`/api/admin/users`）：用户列表、封禁/解封、重置密码（生成随机密码返回给管理员）
- `AdminChatController`（`/api/admin/sessions`）：全平台会话列表（含已逻辑删除标记）、查看任意会话消息、物理删除会话
- `AdminStatsController`（`/api/admin/stats`），全部从 `chat_message` 聚合：
  - `GET /overview?range=7d|30d|all`：调用次数（assistant 消息数）、总成本（`SUM(cost)`）、未解决对话数
  - `GET /top-questions?range=...`：高频提问 Top N（按 user 消息内容分组计数）
  - `GET /unresolved?range=...`：未解决对话明细（`role='assistant' AND status='failed'`）
- 删除 `AiModelConfigController` / `AiModelConfigService` / `AiModelConfigMapper`（模型表已砍）

### 4.4 操作日志（新增）
- `OperationLogService` + `OperationLogMapper`：只插入，不提供任何修改/删除接口
- 记录点：登录、注册、封禁/解封、重置密码、用户删会话、管理员删会话、定时清理执行结果
- 查询接口仅管理员可用：`GET /api/admin/logs`

### 4.5 定时清理任务（新增）
- `SessionPurgeTask`：`@Scheduled(cron = "0 0 3 * * *")` 每日凌晨 3 点
- 物理删除 `last_active_at < NOW() - INTERVAL 90 DAY` 的会话（消息靠外键级联删除）
- 删除结果写入 `operation_log`

## 5. 前端改造清单

### 5.1 基础框架
- 新增 `vue-router`：`/login`、`/register`、`/chat`、`/admin`（含子页：用户管理/对话管理/统计看板/操作日志）
- token 存 `localStorage`，`fetch` 封装自动带 `Authorization: Bearer <token>`
- 401 响应统一跳转登录页；管理员/普通用户按角色路由守卫

### 5.2 登录注册页
- 注册：用户名 + 密码（前端同步校验 6-12 位复杂度）+ 确认密码
- 登录：用户名 + 密码；封禁账号提示"账号已被封禁"

### 5.3 聊天页改造
- 所有 API 调用带 token；会话列表、消息均只含本人数据
- 删除会话提示"删除后 90 天内数据保留，之后自动清除"
- 敏感词拦截 / 超出每日次数时展示后端返回的提示
- 显示当日剩余对话次数
- 移除顶部模型选择菜单（模型已固定）

### 5.4 管理后台（新增）
- 用户管理：表格展示用户，操作列：封禁/解封、重置密码
- 对话管理：全平台会话列表，可按用户筛选，查看消息、物理删除
- 统计看板：时间范围切换（近 7 天/近 30 天/全部），卡片展示调用次数/成本/未解决数，ECharts 柱状图展示高频问题 Top 10，表格展示未解决明细
- 操作日志：只读表格

## 6. 实施阶段

| 阶段 | 内容 | 产出 |
| --- | --- | --- |
| P1 | 数据库迁移 + 用户认证后端 | 新 schema、注册/登录/改密接口、令牌拦截器 |
| P2 | 聊天业务改造 | 用户隔离、逻辑删除、敏感词、每日限额、未解决标记、成本记录 |
| P3 | 管理员后端 | 用户管理、对话管理、统计接口、操作日志 |
| P4 | 定时清理任务 | 90 天物理清理 + 日志 |
| P5 | 前端改造 | 路由、登录注册、聊天页适配、管理后台 + 看板 |
| P6 | 联调验收 | 对照第 7 节验收标准逐项测试 |

## 7. 需求验收对照

| 验收项 | 实现位置 |
| --- | --- |
| 注册唯一性/密码约束 | `AuthService.register` + 前端表单校验 |
| 登录/30 分钟超时/封禁禁登/改密 | `login_token` 滑动过期 + `AuthInterceptor` + 封禁状态校验 |
| 自动生成标题/多轮对话/敏感内容拦截/每日上限 | `ChatTurnService` 新流程 |
| 逻辑删除保留数据/管理员物理删除/禁删单条消息 | `deleted` 标记 + 管理端物理删除接口（不提供单消息删除接口） |
| 统计看板四项指标/未解决口径 | `AdminStatsController` 聚合 `chat_message`（assistant 行 / `SUM(cost)` / `status='failed'`） |
| AI 不可用友好提示不丢消息 | 现有 `DeepSeekClient` 异常处理 + 用户消息先落库（沿用） |
| 越权访问拦截 | 所有查询带 `user_id`，管理接口校验角色 |
| 操作日志不可篡改 | 只插入、仅管理员可查、无更新删除接口 |

## 8. 数据库设计 SQL

```sql
CREATE DATABASE IF NOT EXISTS ai_chat_local CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
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

-- ========== 3. 会话（+用户隔离、逻辑删除、过期判定） ==========
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
    action VARCHAR(50) NOT NULL COMMENT 'LOGIN/REGISTER/BAN_USER/RESET_PASSWORD/DELETE_SESSION/ADMIN_DELETE_SESSION/PURGE_SESSION 等',
    target_type VARCHAR(30) NULL,
    target_id VARCHAR(64) NULL,
    detail VARCHAR(1000) NULL,
    ip VARCHAR(45) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_log_created (created_at),
    KEY idx_log_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 初始管理员（密码需在首次部署后通过重置修改，此处为 BCrypt 哈希占位）
INSERT INTO sys_user (username, password_hash, role)
SELECT 'admin', '$2a$10$REPLACE_WITH_REAL_BCRYPT_HASH', 'admin'
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'admin');
```

配套 `application.properties` 新增配置：

```properties
# 每日对话次数上限
chat.daily-limit=100
# 敏感词列表（逗号分隔，启动加载到内存）
chat.sensitive-words=
# 成本统计单价（元/千 token，按 deepseek-flash 实际定价填写）
deepseek.prompt-price-per-1k=0.001
deepseek.completion-price-per-1k=0.002
```

## 9. 迁移说明

当前库为单用户数据，无 `user_id`，无法归属。**直接重建数据库**：drop 旧库后执行新 schema，本地开发数据不保留。`ai_model_config` 表一并废弃。
