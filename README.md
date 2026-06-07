# Simple Agent CLI

从零手搓的 Java Agent CLI，基于 DeepSeek API 实现 ReAct 模式 + Tool Call。
对标 Claude Code / Aider 的最小可学习版本，**核心 ~600 行 Java**。

## 5 天开发计划

| 阶段 | 目标 | 关键产出 |
|------|------|----------|
| **Day 0** | 项目脚手架 + DeepSeek API 调通 | `Main.java` 一次性 HTTP 调用 |
| Day 1 | LlmClient + Message 协议（4 种角色） | `LlmClient` / `DeepSeekClient` |
| Day 2 | ToolRegistry + 5 个内置工具 | `read_file` / `write_file` / `list_dir` / `execute_command` / `create_project` |
| Day 3 | Agent ReAct 循环 | `Agent.run()` 50 行核心 |
| Day 4 | 交互式 CLI + clear/exit | REPL + `.env` 加载 |

当前状态：✅ Day 0 完成

## 快速开始

```bash
# 1. 复制环境变量模板，填入你的 DeepSeek API Key
cp .env.example .env
vim .env   # 把 DEEPSEEK_API_KEY 改成真实的

# 2. 打包（首次会下载依赖，几十秒）
mvn clean package -DskipTests

# 3. 跑起来
java -jar target/simple-agent-cli-1.0-SNAPSHOT.jar "你好，请用一句话介绍自己"
```

预期输出：

```
you> 你好，请用一句话介绍自己
assistant> 我是 DeepSeek 智能助手，可以帮你回答问题、写代码、做分析。
```

## 技术栈

- **Java 17**：`record` / `sealed` / 模式匹配
- **Maven**：构建 + fat jar 打包
- **OkHttp 4.12**：HTTP 客户端（连接池 / 4 段超时 / SSE 支持）
- **Jackson 2.16**：JSON 序列化（树模型 `ObjectNode` / `JsonNode`）
- **JUnit 5**：单元测试
- **DeepSeek API**：LLM（OpenAI Function Calling 兼容协议）

## 项目结构（当前 Day 0）

```
simple-agent-cli/
├── pom.xml
├── .env.example
├── .gitignore
├── README.md
└── src/main/java/com/cliagent/
    └── Main.java          # Day 0：单次 HTTP 调用 demo
```

后续会扩展为：

```
src/main/java/com/cliagent/
├── Main.java              # REPL 入口
├── config/
│   └── EnvConfig.java     # .env / env / -D 三级加载
├── llm/
│   ├── LlmClient.java     # 接口 + Message/Tool/ChatResponse record
│   └── DeepSeekClient.java
├── tool/
│   ├── Tool.java
│   └── ToolRegistry.java
└── agent/
    └── Agent.java         # ReAct 循环
```

## API Key 加载优先级

`Main.loadApiKey()` 按下面顺序逐个尝试，先命中为准：

1. JVM 系统属性：`java -DDEEPSEEK_API_KEY=sk-xxx -jar ...`
2. 环境变量：`export DEEPSEEK_API_KEY=sk-xxx`
3. 项目根目录的 `./.env` 文件

## 设计要点速记（面试自检）

- ✅ `OkHttpClient` **全局单例**（避免连接池 / 线程泄漏）
- ✅ `ObjectMapper` 也是全局单例（线程安全但构造贵）
- ✅ 4 种消息角色：`system` / `user` / `assistant` / `tool`
- ⏳ `tool_calls.function.arguments` 必须是 **JSON 字符串**而非对象（Day 1 实现）
- ⏳ `tool` 消息必须带 `tool_call_id` 与 assistant 的 toolCalls 配对（Day 1）
- ⏳ ReAct 循环退出靠 LLM 自然不再 `tool_calls`，加 MAX_ITERATIONS 兜底（Day 3）

## License

MIT
