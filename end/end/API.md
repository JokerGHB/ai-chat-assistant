# 本地聊天后端 API

## 本机运行配置

后端默认连接 `127.0.0.1:3306/ai_chat_local`，用户名默认为 `root`，密码默认为空。若 IDEA 中创建的数据库名称或 MySQL 登录信息不同，请在 IDEA 的 Spring Boot Run Configuration 中设置环境变量：

- `DB_URL`：完整 JDBC 地址，例如 `jdbc:mysql://127.0.0.1:3306/你的数据库名?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Taipei&useSSL=false&allowPublicKeyRetrieval=true`
- `DB_USERNAME`：MySQL 用户名
- `DB_PASSWORD`：MySQL 密码
- `SERVER_PORT`：可选，默认 `8080`
- `DEEPSEEK_API_KEY`：DeepSeek API 密钥，在 IDEA 的 Spring Boot Run Configuration → Environment variables 中填写，不要放入前端或提交到代码库

`src/main/resources/db/schema.sql` 中的表名和字段需与使用的数据库一致。后端不会自动建库或执行初始化 SQL。

在 IDEA 中运行 `EndApplication`，或在 `end/end` 目录执行 `mvnw.cmd spring-boot:run`。

## 已实现接口

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `GET` | `/api/sessions` | 按更新时间倒序查询会话 |
| `POST` | `/api/sessions` | 创建会话；请求体可省略 |
| `GET` | `/api/sessions/{sessionId}` | 查询会话 |
| `PATCH` | `/api/sessions/{sessionId}` | 修改会话标题 |
| `DELETE` | `/api/sessions/{sessionId}` | 删除会话及其消息 |
| `GET` | `/api/sessions/{sessionId}/messages` | 按时间顺序查询消息 |
| `POST` | `/api/sessions/{sessionId}/messages` | 保存用户消息、调用 DeepSeek 并保存 AI 回复 |
| `GET` | `/api/sessions/{sessionId}/messages/{messageId}` | 查询单条消息 |
| `GET` | `/api/models` | 查询已启用的模型配置 |

创建会话示例：

```json
{
  "title": "新对话",
  "modelConfigId": 1
}
```

发送消息并获取 AI 回复示例：

```json
{
  "content": "你好，介绍一下自己。"
}
```

成功时返回用户消息和 AI 消息：

```json
{
  "userMessage": { "id": 10, "sessionId": 1, "role": "user", "content": "你好，介绍一下自己。", "status": "complete" },
  "assistantMessage": { "id": 11, "sessionId": 1, "role": "assistant", "content": "你好！我是……", "status": "complete", "promptTokens": 28, "completionTokens": 42 }
}
```

## DeepSeek 配置

后端固定通过 DeepSeek Chat Completions 接口调用 `deepseek-flash`，地址为 `https://api.deepseek.com/chat/completions`。模型和地址在 `src/main/resources/application.properties` 中；密钥只从环境变量 `DEEPSEEK_API_KEY` 读取。没有配置密钥时，聊天接口返回 `503` 并提示设置位置。上游鉴权、额度或网络错误会返回经过处理的错误信息，用户消息仍会保存在本地数据库。

此接口会把当前会话的用户和助手历史消息发送到 DeepSeek 生成回复。当前采用普通同步回复，不包含流式输出。

请求校验或资源不存在时，接口会返回包含 `status`、`message`、`path` 和 `timestamp` 的 JSON 错误对象。

