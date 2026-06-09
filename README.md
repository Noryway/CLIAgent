# CLIAgent λ

从零手搓的 Java Agent CLI，基于 DeepSeek API 实现 ReAct + Tool Call。
对标 Claude Code / Aider 的最小可学习版本。

**仓库**：<https://github.com/Noryway/CLIAgent>

## 当前进度

| 阶段 | 状态 | 关键产出 |
|------|------|----------|
| Day 0 | ✅ | 项目脚手架 + DeepSeek API 调通 |
| Day 1 | ✅ | `LlmClient` / `DeepSeekClient` / `EnvConfig`，四种 message role + Tool Call 序列化/解析 |
| Day 2 | ✅ | `ToolRegistry` + 5 个内置工具 + 单元测试 |
| Day 3 | ✅ | `Agent.run()` ReAct 循环 + Main 集成 |
| Day 4 | ✅ | 交互式 REPL（多轮对话 / clear / exit） |

## 功能概览

- **DeepSeek API**：OkHttp 封装，OpenAI 兼容协议（非流式）
- **四种消息角色**：`system` / `user` / `assistant` / `tool`
- **Tool Call**：请求体序列化 `tools` + `tool_calls`，响应解析 `tool_calls`
- **配置加载**：`-D` 系统属性 > 环境变量 > `./.env`
- **ToolRegistry**：注册、执行、导出 5 个内置工具定义
- **ReAct Agent**：`Agent.run()` 自动 chat → 执行工具 → 再 chat，直到 LLM 返回纯文本
- **交互式 REPL**：无参数启动进入多轮对话；支持 `exit` / `quit` 退出、`clear` / `/clear` 清空历史
- **单元测试**：LLM 协议 + 工具 + Agent + REPL 命令 Mock 测试（不依赖 API Key）

### 内置工具（Day 2）

| 工具 | 说明 |
|------|------|
| `list_dir` | 列出目录内容（`[D]` / `[F]`） |
| `read_file` | 读文件，支持 `offset` / `limit`，最多 2000 行 |
| `write_file` | 写文件，自动创建父目录 |
| `execute_command` | 执行 shell（60s 超时，输出最多 8KB） |
| `create_project` | 创建项目骨架（`java` / `python` / `node`） |

## 环境要求

- JDK 17+
- Maven 3.6+
- DeepSeek API Key（<https://platform.deepseek.com/>）

## 快速开始

```bash
# 1. 克隆并进入项目
git clone git@github.com:Noryway/CLIAgent.git
cd CLIAgent

# 2. 配置 API Key
cp .env.example .env
# 编辑 .env，填入 DEEPSEEK_API_KEY=sk-xxx

# 3. 编译、测试、打包
mvn clean package

# 4. REPL 交互模式（推荐，多轮对话）
java -jar target/CLIAgent-1.0-SNAPSHOT.jar

# 5. 单次对话（带参数，兼容脚本调用）
java -jar target/CLIAgent-1.0-SNAPSHOT.jar "用一句话介绍 ReAct"

# 6. ReAct + 工具（自动 list_dir / read_file 等）
java -jar target/CLIAgent-1.0-SNAPSHOT.jar "列出当前目录有哪些文件"
java -jar target/CLIAgent-1.0-SNAPSHOT.jar "读取 README.md 并用一句话总结"
```

### REPL 命令

| 命令 | 作用 |
|------|------|
| `exit` / `quit` | 退出程序 |
| `clear` / `/clear` | 清空对话历史（保留 system 提示词） |
| 空行 | 跳过，不调用 API |
| 其他输入 | 发送给 Agent，进入 ReAct 循环 |

### 预期输出

**REPL 多轮对话：**

```text
CLIAgent 已启动，输入 exit 退出，clear 清空对话。
you> 我叫小明
assistant> 你好小明！...
you> 我叫什么？
assistant> 你叫小明。
you> clear
🗑️ 对话历史已清空。
you> exit
再见！
```

**单次对话：**

```text
you> 用一句话介绍 ReAct
assistant> ReAct 是一种结合推理与行动的智能体框架...
```

**ReAct + 工具：**

```text
you> 列出当前目录有哪些文件
  [tool] list_dir → 目录内容 (.): [D] src [F] pom.xml [F] README.md ...
assistant> 当前目录包含 src、pom.xml、README.md 等文件和目录。
```

## 项目结构

```text
CLIAgent/
├── pom.xml
├── .env.example
├── README.md
├── docs/
│   └── SESSION-HANDOFF.md
└── src/
    ├── main/java/com/cliagent/
    │   ├── Main.java
    │   ├── agent/
    │   │   └── Agent.java
    │   ├── config/
    │   │   └── EnvConfig.java
    │   ├── llm/
    │   │   ├── LlmClient.java
    │   │   └── DeepSeekClient.java
    │   └── tool/
    │       ├── Tool.java
    │       ├── ToolExecutor.java
    │       └── ToolRegistry.java
    └── test/java/com/cliagent/
        ├── MainTest.java
        ├── agent/
        │   └── AgentTest.java
        ├── llm/
        │   ├── MessageTest.java
        │   └── DeepSeekClientTest.java
        └── tool/
            └── ToolRegistryTest.java
```

## 架构

```text
Main
  ├── 无参数 → runRepl()：while 读 you> → exit/clear/对话
  ├── 有参数 → runOnce()：单次 agent.run()
  ├── EnvConfig
  ├── ToolRegistry（5 内置工具）
  └── Agent（history 跨 run 共享）
        └── run(userInput): ReAct while 循环
              → hasToolCalls? executeTool → Message.tool → continue
              → else return 最终答案
        └── clearHistory()：只保留 system 消息
```

## 配置说明

### API Key 加载优先级

`EnvConfig.get()` / `EnvConfig.require()` 按以下顺序查找，先命中为准：

1. JVM 系统属性：`java -DDEEPSEEK_API_KEY=sk-xxx -jar ...`
2. 环境变量：`export DEEPSEEK_API_KEY=sk-xxx`
3. 项目根目录 `./.env` 文件

> 必须在项目根目录启动（或该目录下有 `.env`），否则读不到配置文件。

### 环境变量

| 变量 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `DEEPSEEK_API_KEY` | 是 | — | DeepSeek API Key |
| `DEEPSEEK_MODEL` | 否 | `deepseek-chat` | 模型名称 |

## 开发

```bash
# 跑全部测试（LLM 协议 + 工具）
mvn test

# 只跑 Agent 测试
mvn test -Dtest=AgentTest

# 只跑 REPL 命令解析测试
mvn test -Dtest=MainTest

# 跳过测试打包
mvn clean package -DskipTests

# 开发态 REPL
mvn -q exec:java -Dexec.mainClass="com.cliagent.Main"

# 开发态单次对话
mvn -q exec:java -Dexec.mainClass="com.cliagent.Main" -Dexec.args="你好"
```

## 设计要点（面试自检）

| 要点 | 状态 | 说明 |
|------|------|------|
| `OkHttpClient` 全局单例 | ✅ | 复用连接池，避免线程/端口泄漏 |
| `ObjectMapper` 全局单例 | ✅ | 线程安全，构造成本高 |
| 四种 message role | ✅ | `LlmClient.Message` 工厂方法 |
| `arguments` 必须是 JSON 字符串 | ✅ | OpenAI 协议要求 |
| `tool` 消息带 `tool_call_id` | ✅ | 与 assistant 的 `tool_calls[i].id` 配对 |
| ToolRegistry 注册表模式 | ✅ | 加工具只改 register + 实现方法 |
| 工具执行异常转字符串 | ✅ | 不抛给 LLM，便于 ReAct 继续 |
| ReAct 循环 | ✅ | `Agent.run()` + `MAX_ITERATIONS` 防死循环 |
| 多轮对话 history | ✅ | `Agent.history` 成员变量，跨 `run()` 共享 |
| REPL 交互入口 | ✅ | `Main.runRepl()` + `BufferedReader` |
| `clearHistory()` | ✅ | 清空历史，只保留 system |
| 流式 SSE | ⏳ | 进阶项 |

## License

MIT
