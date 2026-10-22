# 拾光 AI 聊天 后端 API

> 所有接口根路径为 `/api`；后端默认运行在 `http://127.0.0.1:8080`。  
> 前端通过 Vite 代理转发 `/api` 到后端，请求直接使用相对路径。

## 基础信息

| 项 | 值 |
| --- | --- |
| Base URL | `http://127.0.0.1:8080/api` |
| 协议 | HTTP + JSON |
| 字符编码 | UTF-8 |
| 时间格式 | ISO-8601（`yyyy-MM-dd'T'HH:mm:ss.SSS`） |
| 大模型 | DeepSeek（固定，不支持切换） |

## 鉴权

| 路径前缀 | 鉴权要求 |
| --- | --- |
| `/api/auth/register`、`/api/auth/login` | 免鉴权 |
| `/api/auth/logout`、`/api/auth/profile`、`/api/auth/password` | 需要 token |
| `/api/sessions/**` | 需要 token（user_id 隔离，越权返回 404） |
| `/api/admin/**` | 需要 token 且角色为 admin（否则 403） |

**鉴权方式**：HTTP Header `Authorization: Bearer <token>`，token 由登录接口返回，保存在前端 localStorage。

**令牌规则**：30 分钟无操作自动失效（滑动过期）；封禁账号的现有令牌立即失效；修改密码后该用户所有令牌强制失效。

## 错误响应格式

所有非 2xx 响应统一返回：

```json
{
  "status": 400,
  "message": "密码需为 6-12 位，且须同时包含字母和数字",
  "path": "/api/auth/register",
  "timestamp": "2026-10-20T10:15:30Z"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| status | int | HTTP 状态码 |
| message | string | 错误原因，可直接展示给用户 |
| path | string | 出错的请求路径 |
| timestamp | string | 错误发生时间（UTC） |

## 状态码一览

| 状态码 | 含义 | 典型场景 |
| --- | --- | --- |
| 200 OK | 成功 | — |
| 201 Created | 创建成功 | POST `/api/auth/register`、POST `/api/sessions` |
| 204 No Content | 成功无返回体 | DELETE 会话、POST logout、PATCH 密码 |
| 400 Bad Request | 参数校验失败 | 注册密码不合规、敏感词命中 |
| 401 Unauthorized | 未登录 / 令牌失效 / 封禁账号 | 前端收到后应清除本地登录态并跳 `/login` |
| 403 Forbidden | 已登录但权限不足 | 普通用户访问 `/api/admin/**` |
| 404 Not Found | 资源不存在 / 越权访问他人数据 | 故意返回 404 防止泄露数据存在性 |
| 409 Conflict | 数据约束冲突 | 用户名已存在、外键约束失败 |
| 429 Too Many Requests | 超出每日对话上限 | 默认 100 次/日，可配置 |
| 503 Service Unavailable | AI 服务不可用 | 未配置 DEEPSEEK_API_KEY、大模型上游超时 |

---

## 一、认证接口

### 1.1 注册

- **方法**：`POST`
- **路径**：`/api/auth/register`
- **鉴权**：免

**请求体**：

| 字段 | 类型 | 必填 | 校验 |
| --- | --- | --- | --- |
| username | string | 是 | 2-50 字符，唯一 |
| password | string | 是 | 6-12 位，须同时包含字母和数字（支持大小写字母、数字、符号） |

```json
{ "username": "lyj", "password": "Lyj123456" }
```

**响应（201）**：返回新用户资料（不含每日限额统计）

```json
{
  "id": 2,
  "username": "lyj",
  "role": "user",
  "status": "active",
  "createdAt": "2026-10-20T10:00:00.000",
  "dailyLimit": null,
  "usedToday": null,
  "remainingToday": null
}
```

**典型错误**：
| 状态码 | message |
| --- | --- |
| 400 | 密码需为 6-12 位，且须同时包含字母和数字 |
| 409 | 用户名已存在 |

---

### 1.2 登录

- **方法**：`POST`
- **路径**：`/api/auth/login`
- **鉴权**：免

**请求体**：

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| username | string | 是 |
| password | string | 是 |

```json
{ "username": "admin", "password": "Admin@123" }
```

**响应（200）**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| token | string | 64 字符随机令牌，前端存 localStorage |
| user | UserProfileResponse | 当前用户资料（含 dailyLimit / usedToday / remainingToday） |

```json
{
  "token": "f29f0b3ca453cef10d78af5611af9230494594fe447381189783abbd66badbfb",
  "user": {
    "id": 1,
    "username": "admin",
    "role": "admin",
    "status": "active",
    "createdAt": "2026-10-19T21:10:46.032",
    "dailyLimit": 100,
    "usedToday": 0,
    "remainingToday": 100
  }
}
```

**典型错误**：
| 状态码 | message |
| --- | --- |
| 401 | 用户名或密码不正确 |
| 403 | 账号已被封禁 |

---

### 1.3 登出

- **方法**：`POST`
- **路径**：`/api/auth/logout`
- **鉴权**：token

**请求体**：无

**响应（204）**：无返回体。服务端立即删除该令牌，前端应同步清除 localStorage 中的 token/user。

---

### 1.4 修改密码

- **方法**：`PATCH`
- **路径**：`/api/auth/password`
- **鉴权**：token

**请求体**：

| 字段 | 类型 | 必填 | 校验 |
| --- | --- | --- | --- |
| oldPassword | string | 是 | — |
| newPassword | string | 是 | 6-12 位，须同时包含字母和数字 |

```json
{ "oldPassword": "Admin@123", "newPassword": "Admin@456" }
```

**响应（204）**：无返回体。修改成功后当前用户所有已有令牌立即失效，前端应强制跳登录页。

**典型错误**：400 密码规则不通过；401 旧密码不正确 / 令牌失效。

---

### 1.5 查看当前用户资料

- **方法**：`GET`
- **路径**：`/api/auth/profile`
- **鉴权**：token

**响应（200）**：UserProfileResponse

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 用户 ID |
| username | string | 用户名 |
| role | string | `user` / `admin` |
| status | string | `active` / `banned` |
| createdAt | datetime | 注册时间 |
| dailyLimit | int | 每日对话次数上限 |
| usedToday | int | 今日已发送的 user 消息数 |
| remainingToday | int | 今日剩余次数（= max(dailyLimit - usedToday, 0)） |

---

## 二、会话接口

全部需要 token，且自动注入当前登录用户，业务层强制 `user_id` 隔离。

### 2.1 会话列表

- **方法**：`GET`
- **路径**：`/api/sessions`
- **鉴权**：token

**响应（200）**：`ChatSession[]`，按 `updatedAt` 倒序，已逻辑删除的不返回。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 会话 ID |
| userId | long | 归属用户 ID（等于当前登录用户） |
| title | string | 会话标题 |
| deleted | bool | 逻辑删除标记（列表中均为 false） |
| deletedAt | datetime/null | 逻辑删除时间 |
| lastActiveAt | datetime | 最近活动时间（发消息/改名都会刷新） |
| createdAt | datetime | 创建时间 |
| updatedAt | datetime | 最后更新时间 |

---

### 2.2 创建会话

- **方法**：`POST`
- **路径**：`/api/sessions`
- **鉴权**：token

**请求体**（可省略）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| title | string | 否 | 自定义标题；为空时默认 "新对话"，首条消息后会自动更新 |

```json
{ "title": "关于毕业设计的讨论" }
```

**响应（201）**：ChatSession，Header `Location` 指向新会话 URI。

---

### 2.3 查看会话详情

- **方法**：`GET`
- **路径**：`/api/sessions/{sessionId}`
- **鉴权**：token

**响应（200）**：ChatSession。越权访问他人会话或会话已被逻辑删除 → **404**。

---

### 2.4 改名

- **方法**：`PATCH`
- **路径**：`/api/sessions/{sessionId}`
- **鉴权**：token

**请求体**：

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| title | string | 是 |

```json
{ "title": "新标题" }
```

**响应（200）**：更新后的 ChatSession。越权或不存在 → **404**。

---

### 2.5 删除会话（逻辑删除）

- **方法**：`DELETE`
- **路径**：`/api/sessions/{sessionId}`
- **鉴权**：token

**响应（204）**：无返回体。执行逻辑删除（`deleted=1`），数据保留 90 天，之后由定时任务物理清理。管理员可在管理端看到已逻辑删除的会话。

---

### 2.6 消息列表

- **方法**：`GET`
- **路径**：`/api/sessions/{sessionId}/messages`
- **鉴权**：token

**响应（200）**：`ChatMessage[]`，按 `createdAt` 升序。越权 / 会话不存在 / 会话已逻辑删除 → **404**。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 消息 ID |
| sessionId | long | 所属会话 |
| userId | long | 归属用户（冗余列） |
| role | string | `user` / `assistant` |
| content | string | 消息正文 |
| status | string | `complete` / `failed`（AI 调用失败时为 failed，即"未解决对话"） |
| errorMessage | string/null | AI 失败时的错误描述 |
| promptTokens | int/null | assistant 消息的输入 token 数 |
| completionTokens | int/null | assistant 消息的输出 token 数 |
| cost | decimal/null | assistant 消息的调用成本（元），按配置单价计算 |
| createdAt | datetime | 发送时间 |

---

### 2.7 发送一轮对话

- **方法**：`POST`
- **路径**：`/api/sessions/{sessionId}/messages`
- **鉴权**：token

**请求体**：

| 字段 | 类型 | 必填 | 校验 |
| --- | --- | --- | --- |
| content | string | 是 | 非空；长度上限由前端控制 |

```json
{ "content": "你好，介绍一下自己。" }
```

**处理流程**：
1. 敏感词检测 → 命中返回 **400**（不调用 AI，不存消息）
2. 每日次数校验 → 超限返回 **429**
3. 保存 user 消息
4. 调用大模型
5. 成功 → 存 status=complete 的 assistant 消息（含 token 数和 cost）
6. 失败 → 存 status=failed 的 assistant 消息（含 errorMessage），**user 消息仍保留不丢失**

**响应（200）**：ChatTurnResponse

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| userMessage | ChatMessage | 第 3 步已落库的 user 消息 |
| assistantMessage | ChatMessage | AI 回复（status=complete / failed 均可） |

```json
{
  "userMessage": {
    "id": 10, "sessionId": 52, "userId": 2, "role": "user",
    "content": "你好，介绍一下自己。", "status": "complete",
    "errorMessage": null, "promptTokens": null, "completionTokens": null,
    "cost": null, "createdAt": "2026-10-20T10:00:01.000"
  },
  "assistantMessage": {
    "id": 11, "sessionId": 52, "userId": 2, "role": "assistant",
    "content": "你好！我是拾光 AI 聊天助手，基于 DeepSeek 大模型……",
    "status": "complete", "errorMessage": null,
    "promptTokens": 28, "completionTokens": 42,
    "cost": 0.000112, "createdAt": "2026-10-20T10:00:03.500"
  }
}
```

**失败示例（AI 不可用）**：

```json
{
  "assistantMessage": {
    "status": "failed",
    "errorMessage": "AI 服务暂不可用，请稍后重试"
  }
}
```

**典型错误**：
| 状态码 | message |
| --- | --- |
| 400 | 敏感词提示（命中配置词库） |
| 404 | 会话不存在 / 越权 / 已逻辑删除 |
| 429 | 今日对话次数已达上限 |
| 503 | AI 服务暂不可用 |

---

## 三、管理员接口

所有路径前缀 `/api/admin/**`，需要 token 且用户 role 为 admin。越权 → **403**。

### 3.1 用户列表

- **方法**：`GET`
- **路径**：`/api/admin/users`
- **鉴权**：admin

**响应（200）**：`UserProfileResponse[]`，全体用户（含管理员自身），不含每日限额统计字段。

---

### 3.2 封禁用户

- **方法**：`POST`
- **路径**：`/api/admin/users/{userId}/ban`
- **鉴权**：admin

**请求体**：无

**响应（204）**：无返回体。封禁后该用户的现有令牌立即失效，下次登录返回 403。

---

### 3.3 解封用户

- **方法**：`POST`
- **路径**：`/api/admin/users/{userId}/unban`
- **鉴权**：admin

**请求体**：无

**响应（204）**。

---

### 3.4 重置密码

- **方法**：`POST`
- **路径**：`/api/admin/users/{userId}/reset-password`
- **鉴权**：admin

**请求体**：无

**响应（200）**：ResetPasswordResponse

```json
{ "newPassword": "R7k3P2n9" }
```

> 新密码为随机生成的 8 位字母数字组合，仅此一次返回给管理员，不复盘不留痕。

---

### 3.5 全平台会话列表

- **方法**：`GET`
- **路径**：`/api/admin/sessions`
- **鉴权**：admin

**响应（200）**：`AdminSessionView[]`，全体会话（含逻辑删除标记）。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 会话 ID |
| userId | long | 归属用户 ID |
| username | string | 归属用户名（联表 sys_user 带出） |
| title | string | 会话标题 |
| deleted | bool | 逻辑删除标记（true 表示用户已删，管理员仍可见） |
| deletedAt | datetime/null | 逻辑删除时间 |
| lastActiveAt | datetime | 最近活动时间 |
| createdAt | datetime | 创建时间 |
| updatedAt | datetime | 最后更新时间 |

---

### 3.6 查看任意会话消息

- **方法**：`GET`
- **路径**：`/api/admin/sessions/{sessionId}/messages`
- **鉴权**：admin

**响应（200）**：`ChatMessage[]`，顺序与用户端一致。管理员可查看任意会话（含已逻辑删除的）。

---

### 3.7 物理删除会话

- **方法**：`DELETE`
- **路径**：`/api/admin/sessions/{sessionId}`
- **鉴权**：admin

**请求体**：无

**响应（204）**。真正 DELETE，不可恢复。消息靠外键级联一并删除。操作会写入 operation_log（action=`ADMIN_DELETE_SESSION`）。

---

### 3.8 统计看板 · 总览

- **方法**：`GET`
- **路径**：`/api/admin/stats/overview`
- **鉴权**：admin

**请求参数**：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| range | string | 否 | `7d`（近 7 天）/ `30d`（近 30 天）/ `all`（全部，默认） |

**响应（200）**：StatsOverviewResponse

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| callCount | long | 大模型调用次数（assistant 消息数） |
| totalCost | decimal | 调用总成本（元），SUM(chat_message.cost) |
| unresolvedCount | long | 未解决对话数（assistant 消息中 status='failed' 的数量） |

```json
{ "callCount": 24, "totalCost": 0.012835, "unresolvedCount": 1 }
```

---

### 3.9 统计看板 · 高频提问

- **方法**：`GET`
- **路径**：`/api/admin/stats/top-questions`
- **鉴权**：admin

**请求参数**：

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| range | string | 否 | `all` | 同 overview |
| limit | int | 否 | `10` | 返回前 N 条 |

**响应（200）**：`TopQuestionView[]`，按出现次数降序。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| content | string | 用户提问内容（截前 100 字） |
| cnt | long | 出现次数 |

```json
[
  { "content": "你好", "cnt": 8 },
  { "content": "介绍一下自己", "cnt": 5 }
]
```

---

### 3.10 统计看板 · 未解决对话明细

- **方法**：`GET`
- **路径**：`/api/admin/stats/unresolved`
- **鉴权**：admin

**请求参数**：

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| range | string | 否 | `all` | 同 overview |
| limit | int | 否 | `50` | 返回前 N 条 |

**响应（200）**：`UnresolvedMessageView[]`，按时间倒序。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | assistant 消息 ID |
| sessionId | long | 所属会话 |
| userId | long | 归属用户 |
| username | string | 归属用户名（联表 sys_user 带出） |
| content | string | 失败的 assistant 回复内容（通常为空或占位） |
| errorMessage | string | AI 失败时的错误描述 |
| createdAt | datetime | 发送时间 |

---

### 3.11 操作日志

- **方法**：`GET`
- **路径**：`/api/admin/logs`
- **鉴权**：admin，**只读**（无修改/删除接口）

**请求参数**：

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| limit | int | 否 | `100` | 返回最近 N 条，上限 500 |

**响应（200）**：`OperationLog[]`，按 `createdAt` 倒序。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | long | 日志 ID |
| userId | long/null | 操作人 ID；定时任务为 null |
| username | string | 操作人用户名快照 |
| action | string | LOGIN / REGISTER / BAN_USER / UNBAN_USER / RESET_PASSWORD / DELETE_SESSION / ADMIN_DELETE_SESSION / PURGE_SESSION |
| targetType | string/null | 被操作对象类型（如 `user` / `session`） |
| targetId | string/null | 被操作对象 ID |
| detail | string/null | 补充说明（如重置密码、清理了多少条会话） |
| ip | string/null | 客户端 IP |
| createdAt | datetime | 记录时间 |

---

## 四、定时任务

**无对外接口**。由 Spring 内置 `@Scheduled` 触发。

| 任务 | Cron | 说明 |
| --- | --- | --- |
| SessionPurgeTask | `0 0 3 * * *`（每日凌晨 3:00） | 物理删除 `last_active_at < NOW() - INTERVAL 90 DAY` 的会话及关联消息，执行结果写入 operation_log（action=`PURGE_SESSION`） |

## 五、数据库配置说明（开发者）

| 环境变量 | 缺省值 | 说明 |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | 空 | 必填，缺失时 AI 调用返回 503 并提示 |
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/ai_chat_local?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true` | — |
| `DB_USERNAME` | `root` | — |
| `DB_PASSWORD` | 空 | — |
| `CHAT_DAILY_LIMIT` | `100` | 单用户每日对话次数上限 |
| `CHAT_SENSITIVE_WORDS` | 空 | 敏感词列表（英文逗号分隔） |
| `ADMIN_INITIAL_PASSWORD` | `Admin@123` | 首次启动自动创建的管理员密码 |
| `DEEPSEEK_PROMPT_PRICE` | `0.001` | 输入 token 单价（元/千 token） |
| `DEEPSEEK_COMPLETION_PRICE` | `0.002` | 输出 token 单价（元/千 token） |

> 本文件仅接口契约与开发者配置说明。完整功能描述、设计文档与验收对照见项目根目录 `PLAN.md`。
