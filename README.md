# 乙甲 AI 聊天

本地运行的 AI 聊天示例，前端使用 Vue 3 + Vite，后端使用 Spring Boot + MyBatis，数据保存在 MySQL。

## 目录

- `front/`：Vue 前端
- `end/end/`：Spring Boot 后端

## 运行前配置

不要将数据库密码或模型 API Key 写入源码、前端环境变量或 Git。启动后端前，在 IDEA 的 Spring Boot Run Configuration 中设置环境变量：

- `DB_URL`：MySQL JDBC 地址；可省略，默认连接 `127.0.0.1:3306/ai_chat_local`
- `DB_USERNAME`：数据库用户名；可省略，默认 `root`
- `DB_PASSWORD`：数据库密码
- `DEEPSEEK_API_KEY`：DeepSeek API Key

先在 MySQL 中创建 `ai_chat_local` 数据库，并执行 `end/end/src/main/resources/db/schema.sql`。API Key 仅在后端配置，不能配置为 `VITE_` 开头的变量。

## 启动后端

用 IDEA 打开 `end/end/pom.xml`，配置上述环境变量后运行 `com.lrj.end.EndApplication`。后端默认监听 `http://127.0.0.1:8080`。

也可以在 `end/end/` 目录运行：

```powershell
./mvnw.cmd spring-boot:run
```

## 启动前端

在 `front/` 目录执行：

```powershell
npm install
npm run dev
```

浏览器打开 Vite 输出的地址（默认 `http://127.0.0.1:5173`）。Vite 会将 `/api` 请求代理到本地后端 `8080` 端口。

## 密钥泄露处理

如果真实凭据曾经提交或上传到远程仓库，应立即在对应服务中撤销并重新生成；仅从当前文件删除并不能清除旧 Git 历史中的凭据。
