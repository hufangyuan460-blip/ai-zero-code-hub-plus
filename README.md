# AI Zero Code Hub Plus

AI Zero Code Hub Plus 是一个基于自然语言生成网页应用的全栈项目。用户可以描述想要实现的页面或应用，系统通过 AI 生成代码、保存项目文件并提供在线预览、部署和源码下载能力。

## 项目能力

- 支持原生 HTML、原生多文件和 Vue 工程三种代码生成模式。
- 通过流式对话展示 AI 生成过程，支持持续修改和增量生成。
- Vue 工程模式提供文件读写、修改、删除和目录查看等工具能力。
- 生成项目可以在线预览，部署成功后可下载源码压缩包。
- 保存应用、对话历史和生成文件，支持应用管理及管理员操作。
- 使用 Redis 提供短期对话记忆，并将历史记录持久化到 MySQL。

## 技术架构

项目由 Spring Boot 后端和 Vue 3 前端组成：

- 后端：Spring Boot 3、MyBatis-Flex、MySQL、Redis、Caffeine。
- AI 能力：LangChain4j、LangGraph4j，结合流式响应和工具调用完成代码生成。
- 前端：Vue 3、TypeScript、Vite、Ant Design Vue。
- 文件与部署：后端负责项目文件保存、构建、部署、预览和 ZIP 下载。

核心流程如下：

1. 用户创建应用并选择代码生成模式。
2. 前端通过对话接口发送需求，后端调用对应的 AI 生成服务。
3. AI 以流式消息返回结果；Vue 模式通过工具调用生成或修改项目文件。
4. 生成结果保存到应用目录，并可执行部署和在线预览。
5. 部署完成后，应用所有者可以下载项目源码。

## 目录说明

```text
src/                         Spring Boot 后端源码
ai-zero-code-hub-frontend/   Vue 3 前端源码
sql/                         数据库初始化脚本
```

## 本地运行

### 后端

项目使用 Java 21 和 Maven：

```bash
./mvnw spring-boot:run
```

Windows 环境可使用：

```powershell
.\mvnw.cmd spring-boot:run
```

启动前请准备 MySQL、Redis，并通过本地配置或环境变量注入数据库、Redis 及 AI 服务所需的配置。

### 前端

```bash
cd ai-zero-code-hub-frontend
npm install
npm run dev
```

生产构建：

```bash
npm run build
```

## 测试

后端测试：

```bash
./mvnw test
```

前端类型检查、构建和压缩：

```bash
cd ai-zero-code-hub-frontend
npm run build
```

## 安全与数据边界

- 聊天历史读取和写入受应用所有者或管理员权限控制。
- 对话记忆窗口限制为最近 20 条消息，避免完整源码和工具参数无限进入上下文。
- 用户消息和 AI 历史内容有明确大小上限，超长 AI 结果使用说明性占位文本保存。
- 应用删除时同步清理数据库历史、Redis 对话记忆和后端内存缓存。
- 敏感配置应通过环境变量或部署平台密钥注入，不在仓库中保存明文凭据。
