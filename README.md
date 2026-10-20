# 拾光 AI 聊天（多用户版）

本地运行的多用户 AI 智能对话系统。前端使用 Vue 3 + Vite + vue-router + ECharts，后端使用 Spring Boot + MyBatis，数据保存在 MySQL。

用户注册登录后与 DeepSeek 大模型多轮对话；管理员可在后台管理用户、查看全平台对话、查看调用统计与操作日志。

## 目录

- `front/`：Vue 前端（登录/注册/聊天/管理后台四个视图）
- `end/end/`：Spring Boot 后端
- `end/end/src/main/resources/db/schema.sql`：建库建表脚本（5 张表）

## 功能特性

### 用户端
- 注册：用户名唯一，密码 6-12 位且同时包含字母和数字，BCrypt 哈希存储
- 登录：令牌 30 分钟无操作自动失效；被封禁账号立即无法登录
- 会话：数据按用户隔离（越权访问返回 404）；删除为逻辑删除，数据保留；首条提问自动生成标题
- 对话：敏感词拦截（命中不调用大模型）；每日对话次数限额（默认 100 次，可配置）；调用失败时用户提问不丢失，回复标记为"未解决"状态
- 聊天页显示当日剩余对话次数；退出登录

### 管理端（`/admin`，仅管理员角色可访问）
- 用户管理：封禁/解封、重置密码（随机密码回显）
- 对话管理：全平台会话列表（含已逻辑删除）、查看消息、物理删除
- 统计看板：按近 7 天/近 30 天/全部切换，展示调用次数、调用成本、未解决对话数，ECharts 柱状图展示高频提问 Top 10
- 操作日志：只读查询（登录/注册/封禁/重置密码/删除会话/定时清理等），日志只插不改

### 其他
- 定时任务：每天凌晨 3 点物理删除连续 90 天无活动的会话（含消息）
- 大模型固定使用 DeepSeek，不做模型切换；单次调用成本按配置单价在消息写入时计算落库

## 数据库初始化

执行 `end/end/src/main/resources/db/schema.sql`（自带 `CREATE DATABASE`，库名 `ai_chat_local`，可重复执行）。

PowerShell 下推荐：

```powershell
cmd /c 'mysql -uroot -p < d:\work\java\yijia-ai-chat-assistant\end\end\src\main\resources\db\schema.sql'
```

或进入 `mysql -uroot -p` 交互客户端后执行 `source d:/work/java/yijia-ai-chat-assistant/end/end/src/main/resources/db/schema.sql`。

执行后验证：

```powershell
mysql -uroot -p -e "use ai_chat_local; show tables;"
```

应看到 5 张表：`sys_user`、`login_token`、`chat_session`、`chat_message`、`operation_log`。

## 运行前配置

不要将数据库密码或模型 API Key 写入源码、前端环境变量或 Git。以下凭据一律通过环境变量提供，配置方式任选其一：

- **终端启动**：先在当前 PowerShell 窗口 `$env:DEEPSEEK_API_KEY="sk-xxx"`（仅当前窗口有效）
- **编辑器启动**（Trae/VSCode/IDEA）：写入启动配置（VSCode 系 `.vscode/launch.json` 的 `env`、IDEA 的 Run Configuration → Environment variables）
- **长期方案**：设为 Windows 用户环境变量 `[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY","sk-xxx","User")`，设置后需重启编辑器

| 环境变量 | 说明 | 缺省值 |
| --- | --- | --- |
| `DEEPSEEK_API_KEY` | DeepSeek API Key（必填，缺失时 AI 调用返回友好提示） | 空 |
| `DB_URL` | MySQL JDBC 地址 | `jdbc:mysql://127.0.0.1:3306/ai_chat_local?...` |
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | 空 |
| `CHAT_DAILY_LIMIT` | 单用户每日对话次数上限 | `100` |
| `CHAT_SENSITIVE_WORDS` | 敏感词列表（英文逗号分隔） | 空 |
| `ADMIN_INITIAL_PASSWORD` | 初始管理员密码（仅首次启动创建 admin 时使用） | `Admin@123` |
| `DEEPSEEK_PROMPT_PRICE` / `DEEPSEEK_COMPLETION_PRICE` | token 单价（元/千 token），用于成本统计 | `0.001` / `0.002` |

API Key 仅在后端配置，不能配置为 `VITE_` 开头的变量暴露给前端。

## 启动后端

用编辑器打开 `end/end/pom.xml` 作为 Java/Maven 项目，配置上述环境变量后运行 `com.lrj.end.EndApplication`。后端默认监听 `http://127.0.0.1:8080`。

也可以在 `end/end/` 目录运行：

```powershell
./mvnw.cmd spring-boot:run
```

首次启动会自动创建管理员账号（用户名 `admin`，密码取 `ADMIN_INITIAL_PASSWORD`，默认 `Admin@123`），请在演示后修改。

> 排错提示：后端启动后立即退出时，看日志最后一行 `Caused by:`——`Connection refused` 表示 MySQL 未启动；`Access denied` 表示数据库密码不对；`Table doesn't exist` 表示未执行 schema.sql。若 DeepSeek 官方 API 报模型不存在，将 `application.properties` 中 `deepseek.model` 改为 `deepseek-chat`。

## 启动前端

在 `front/` 目录执行：

```powershell
npm install
npm run dev
```

浏览器打开 Vite 输出的地址（默认 `http://127.0.0.1:5173`）。Vite 会将 `/api` 请求代理到本地后端 `8080` 端口，前端请求统一使用相对路径 `/api`。

## 演示账号与验收要点

- 管理员：`admin / Admin@123`，登录后自动进入 `/admin`
- 普通用户：注册页自助创建，登录后进入 `/chat`
- 路由守卫：未登录访问业务页跳登录页；普通用户访问 `/admin` 被弹回聊天页（后端同样校验，返回 403）
- 可重点演示：30 分钟闲置超时、封禁后立即无法登录、敏感词拦截、每日限额超限提示、删除会话后管理端仍可见（90 天内数据保留）、AI 服务不可用时提问不丢失

## 密钥泄露处理

如果真实凭据曾经提交或上传到远程仓库，应立即在对应服务中撤销并重新生成；仅从当前文件删除并不能清除旧 Git 历史中的凭据。
