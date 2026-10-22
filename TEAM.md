# 拾光 AI 聊天 · 人员分工与代码归属

> 后端所有 Java 类位于 `com.lrj.end.*` 包下；前端所有文件位于 `front/src/*`。
> MyBatis 使用注解式 Mapper，无 XML 映射文件。数据库实际为 5 张表（sys_user / login_token / chat_session / chat_message / operation_log）。

## 分工总览

| 成员 | 角色 | 核心模块 |
| --- | --- | --- |
| 李一甲 | 后端开发 | 会话与消息业务、多轮对话、大模型对接 |
| 李然伋 | 后端开发 | 注册登录、令牌鉴权、越权拦截 |
| 张子豪 | 数据库 | 表结构设计、建表 SQL、设计文档、Mapper SQL |
| 周鹏 | 测试 | 接口功能/异常测试、Bug 回归、测试报告 |
| 刘政声 | 前端开发 | 聊天主页面 |
| 宋金健 | 前端开发 | 管理后台页面、表单校验、前后端联调 |
| 吴昊 | 答辩负责人 | 统一响应/异常/错误码、统计与敏感词后端、接口文档 |
| 余庆年 | 前端开发 | 登录注册页、请求封装与鉴权、统计看板前端 |

---

### 李一甲 · 会话消息与大模型

| 包 | 类 | 说明 |
| --- | --- | --- |
| controller | ChatSessionController | 会话列表/创建/改名/删除 REST 接口 |
| controller | AdminChatController | 管理员视角的全平台会话与消息查询、物理删除 |
| service | ChatSessionService | 会话 CRUD、创建时自动生成标题、逻辑删除 |
| service | ChatMessageService | 消息存储与查询、读取时按 user_id 隔离 |
| service | ChatTurnService | 多轮对话编排：敏感词检测 → 每日限额 → 存提问 → 调大模型 → 存回复；AI 失败时保留提问并标记未解决 |
| service | DeepSeekClient | 大模型 HTTP 调用封装、按配置单价计算 cost、统一异常转 AiServiceException |
| service | AdminChatService | 管理员对话管理业务逻辑 |
| entity | ChatSession | 会话实体（含 user_id、deleted、last_active_at） |
| entity | ChatMessage | 消息实体（含 user_id、status、cost、token 数） |
| mapper | ChatSessionMapper | 会话 SQL（列表按 user_id+deleted 过滤） |
| mapper | ChatMessageMapper | 消息 SQL（按 session_id+时间排序、按 user_id 聚合日活） |
| dto | CreateSessionRequest | 创建会话入参 |
| dto | UpdateSessionRequest | 改名入参 |
| dto | CreateMessageRequest | 发送消息入参 |
| dto | ChatTurnResponse | 一轮对话的完整返回（userMessage + assistantMessage） |

---

### 李然伋 · 认证与权限

| 包 | 类 | 说明 |
| --- | --- | --- |
| service | AuthService | 注册（用户名唯一 + BCrypt + 密码复杂度校验）、登录（封禁检查 + 签发 30 分钟滑动过期令牌）、登出、改密、当前用户资料 |
| service | AdminUserService | 管理员封禁/解封、重置密码（生成随机密码） |
| controller | AuthController | `/api/auth/{register,login,logout,profile,password}` |
| controller | AdminUserController | `/api/admin/users` 列表 + 封禁/解封/重置密码 |
| config | AuthInterceptor | 拦截除 register/login 外的请求，校验令牌、刷新滑动过期时间、封禁用户直接 401、`/api/admin/**` 要求管理员角色 |
| config | CurrentUser | `@CurrentUser` 参数注解 |
| config | CurrentUserArgumentResolver | 将当前登录用户注入 Controller 方法参数 |
| config | WebConfig | 注册拦截器与参数解析器 |
| config | AdminInitializer | 应用启动时自动创建初始管理员账号（admin / Admin@123） |
| entity | SysUser | 用户实体（含 role、status） |
| entity | LoginToken | 令牌实体（含 user_id、last_active_at 滑动过期判断） |
| mapper | SysUserMapper | 用户 SQL（按用户名查、按状态筛选） |
| mapper | LoginTokenMapper | 令牌 SQL（查有效令牌、刷新 last_active_at、清理过期） |
| dto | RegisterRequest | 注册入参（前端已校验 6-12 位 + 字母数字） |
| dto | LoginRequest | 登录入参 |
| dto | LoginResponse | 登录返回（token + user） |
| dto | UpdatePasswordRequest | 改密入参 |
| dto | UserProfileResponse | 当前用户资料（含 dailyLimit / usedToday / remainingToday） |
| dto | ResetPasswordResponse | 重置密码返回（新密码明文展示给管理员） |
| — | ChatSessionService / ChatMessageService 中的 user_id 过滤条件 | 所有数据访问强制携带归属用户条件，越权返回 404

---

### 张子豪 · 数据库与文档

| 位置 | 内容 | 说明 |
| --- | --- | --- |
| end/end/src/main/resources/db/schema.sql | 5 张表建表 SQL + 索引 + 外键 | sys_user / login_token / chat_session / chat_message / operation_log，全部 `IF NOT EXISTS` 可重复执行 |
| entity | SysUser、LoginToken、ChatSession、ChatMessage、OperationLog | 5 张表的 Java 实体，字段与 DDL 一一对应 |
| mapper | SysUserMapper、LoginTokenMapper、ChatSessionMapper、ChatMessageMapper、OperationLogMapper | 全部基础 CRUD 与条件查询 SQL，使用 @Select / @Update 注解 |
| PLAN.md | 需求分析、概要设计、数据库设计、接口验收对照 | 课程改造计划书，完整验收标准 |

---

### 周鹏 · 测试

| 位置 | 内容 | 说明 |
| --- | --- | --- |
| src/test/java/com/lrj/end/EndApplicationTests.java | Spring 上下文加载测试 + DeepSeekClient 无密钥异常测试 | 验证未配置 API Key 时抛 AiServiceException(503) 且不调用外部接口 |
| 另行产出 | 测试报告、Bug 记录 | 全部后端接口功能与异常测试记录 |

---

### 刘政声 · 聊天主页

| 位置 | 内容 | 说明 |
| --- | --- | --- |
| front/src/views/ChatView.vue | 聊天主页面 | 侧边栏会话列表、顶栏剩余次数徽标、消息气泡、底部发送区、剩余次数刷新、多轮对话切换、逻辑删除确认提示 |
| front/src/App.vue | 应用壳 | 仅 `<router-view />`，路由挂载点 |
| front/src/style.css | 全局样式 | 聊天页全部 CSS（含多用户改造新增的 `.failed`、`.remaining-badge`、`.logout-button` 等） |

---

### 宋金健 · 管理后台与联调

| 位置 | 内容 | 说明 |
| --- | --- | --- |
| front/src/views/AdminView.vue | 管理员后台整体框架 | 顶部品牌栏（返回对话 / 退出登录）+ 4 个 tab：**用户管理**（列表 + 封禁/解封/重置密码弹窗）、**对话管理**（全平台会话列表 + 查看消息弹窗 + 物理删除）、**统计看板**（ECharts + 卡片 + 未解决表格，与余庆年协作）、**操作日志**（只读表格）。含 toast 轻提示组件 |
| front/src/views/RegisterView.vue（校验部分） | 前端表单校验 | 密码 6-12 位含字母数字校验（与后端 RegisterRequest 同步） |
| — | 前后端联调 | 修复前后端数据交互问题、字段命名与格式统一 |

---

### 吴昊 · 统一封装、统计与敏感词

| 包 | 类 | 说明 |
| --- | --- | --- |
| dto | ApiError | 统一错误返回体：`{status, message, path, timestamp}` |
| exception | ApiExceptionHandler | 全局异常处理器：兜底所有 Controller 异常、映射自定义异常到 HTTP 状态码、统一 ApiError 格式 |
| exception | AiServiceException | AI 服务调用失败异常（带 HTTP 状态码与友好提示） |
| exception | ResourceNotFoundException | 资源不存在（越权访问会话时返回 404 而不是 403） |
| exception | ForbiddenException | 权限不足（封禁账号、普通用户访问 admin 接口） |
| exception | UnauthorizedException | 未登录（token 无效或过期） |
| exception | TooManyRequestsException | 每日对话超限（429） |
| config | CorsConfig | 全局 CORS 配置 |
| controller | AdminStatsController | `/api/admin/stats/{overview,top-questions,unresolved}?range=7d` |
| controller | AdminLogController | `/api/admin/logs?limit=100` 只读查询 |
| service | SensitiveWordService | 启动时从 `chat.sensitive-words` 配置加载词库到内存，提供 `containsSensitive(content)` 方法，在 ChatTurnService 调用 AI 前拦截 |
| service | OperationLogService | 统一操作日志写入（登录 / 注册 / 封禁 / 重置密码 / 删除会话 / 定时清理结果），只插不改 |
| mapper | OperationLogMapper | 操作日志 SQL（插入 + 按时间倒序查询） |
| entity | OperationLog | 操作日志实体 |
| task | SessionPurgeTask | `@Scheduled(cron = "0 0 3 * * *")` 每日凌晨 3 点物理删除连续 90 天无活动的会话（消息靠外键级联删除），执行结果写入 operation_log |
| dto | StatsOverviewResponse | 统计概览返回体（callCount / totalCost / unresolvedCount） |
| dto | TopQuestionView | 高频提问 Top N（question / cnt） |
| dto | UnresolvedMessageView | 未解决对话明细 |
| — | 接口文档 | 统一 /api/** 接口参数、返回格式与错误码 |
| — | 答辩讲稿与代码讲解材料 | 熟悉项目业务与核心源码 |

---

### 余庆年 · 登录注册与看板前端

| 位置 | 内容 | 说明 |
| --- | --- | --- |
| front/src/api.js | 统一 fetch 封装 + 认证助手 | 自动带 `Authorization: Bearer <token>`、Content-Type JSON、相对路径走 Vite 代理；401 清 localStorage 跳 `/login`；非 2xx 抛带 status 的 Error。导出 saveAuth / clearAuth / getToken / getUser |
| front/src/router/index.js | 路由与全局守卫 | 路由 `/login`、`/register`、`/chat`、`/admin`；未登录访问业务页 → `/login`；已登录访问 `/login`/`/register` → `/chat`；`/admin` 要求 `role === 'admin'`，否则弹回 `/chat` |
| front/src/main.js | 应用入口 | `createApp(App).use(router).mount('#app')` |
| front/src/views/LoginView.vue | 登录页 | 居中卡片、品牌"拾光"、登录成功按角色跳转（admin → `/admin`，user → `/chat`）、403 显示"账号已被封禁" |
| front/src/views/RegisterView.vue | 注册页 | 注册成功自动登录并跳 `/chat` |
| front/src/views/AdminView.vue（统计看板 tab） | 统计看板前端 | range 切换（7d / 30d / all）、调用次数/成本/未解决数卡片、ECharts 柱状图展示高频问题 Top 10、未解决对话明细表（与宋金健协作，页面整体框架归宋金健） |

---
