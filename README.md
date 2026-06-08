# CLIAgent λ

从零手搓的 Java Agent CLI，基于 DeepSeek API 实现 ReAct + Tool Call。
对标 Claude Code / Aider 的最小可学习版本。

**仓库**：<https://github.com/Noryway/CLIAgent>

## 当前进度

| 阶段 | 状态 | 关键产出 |
|------|------|----------|
| Day 0 | ✅ | 项目脚手架 + DeepSeek API 调通 |
| Day 1 | ✅ | `LlmClient` / `DeepSeekClient` / `EnvConfig`，四种 message role + Tool Call 序列化/解析 |
| Day 2 | ⏳ | `ToolRegistry` + 5 个内置工具 |
| Day 3 | ⏳ | `Agent.run()` ReAct 循环 |
| Day 4 | ⏳ | 交互式 CLI（clear / exit / REPL） |

## 功能概览

- **DeepSeek API**：OkHttp 封装，OpenAI 兼容协议（非流式）
- **四种消息角色**：`system` / `user` / `assistant` / `tool`
- **Tool Call**：请求体序列化 `tools` + `tool_calls`，响应解析 `tool_calls`
- **配置加载**：`-D` 系统属性 > 环境变量 > `./.env`
- **单元测试**：Message 工厂方法 + 序列化/反序列化测试

> Day 1 尚未实现：ReAct 循环、工具真正执行、交互式多轮对话。

## 环境要求

- JDK 17+
- Maven 3.6+
- DeepSeek API Key（<https://platform.deepseek.com/>）

## 快速开始

```bash
# 1. 克隆并进入项目
git clone https://github.com/Noryway/CLIAgent.git
cd CLIAgent

# 2. 配置 API Key
cp .env.example .env
# 编辑 .env，填入 DEEPSEEK_API_KEY=sk-xxx

# 3. 编译、测试、打包
mvn clean package

# 4. 普通对话（system + user → assistant 文本）
java -jar target/CLIAgent-1.0-SNAPSHOT.jar "用一句话介绍 ReAct"

# 5. 演示 Tool Call（注册 get_current_time，观察 LLM 是否返回 tool_calls）
java -jar target/CLIAgent-1.0-SNAPSHOT.jar --demo-tool "现在几点？"
```

### 预期输出

**普通对话：**

```text
you> 用一句话介绍 ReAct
assistant> ReAct 是一种结合推理与行动的智能体框架...
tokens: input=22 output=47
```

**Tool Call 演示（LLM 决定调工具时）：**

```text
you> 现在几点？
(demo: 已注册 get_current_time 工具，观察 LLM 是否返回 tool_calls)
assistant> (请求调用工具，Day 2 将在 ToolRegistry 中真正执行)
  tool_call id=call_xxx name=get_current_time args={}
tokens: input=... output=...
```

## 项目结构

```text
CLIAgent/
├── pom.xml
├── .env.example
├── README.md
└── src/
    ├── main/java/com/cliagent/
    │   ├── Main.java                 # 入口：组装 messages/tools，调用 LLM
    │   ├── config/
    │   │   └── EnvConfig.java        # 配置三级加载
    │   └── llm/
    │       ├── LlmClient.java        # 接口 + Message/Tool/ToolCall/ChatResponse
    │       └── DeepSeekClient.java   # OkHttp + JSON 序列化/解析
    └── test/java/com/cliagent/llm/
        ├── MessageTest.java
        └── DeepSeekClientTest.java
```

**后续规划：**

```text
src/main/java/com/cliagent/
├── tool/
│   ├── Tool.java
│   └── ToolRegistry.java     # read_file / write_file / list_dir / ...
└── agent/
    └── Agent.java            # ReAct 主循环
```

## 架构

```text
Main
  ├── EnvConfig          读取 DEEPSEEK_API_KEY / DEEPSEEK_MODEL
  ├── List<Message>      system + user（Day 3 起维护完整 history）
  ├── List<Tool>         可选工具定义（--demo-tool）
  └── DeepSeekClient.chat(messages, tools)
        ├── buildRequestBody()   Java → JSON（四种 role + tools）
        ├── OkHttp POST          → api.deepseek.com/chat/completions
        └── parseResponse()      JSON → ChatResponse（content / tool_calls）
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
# 只跑测试
mvn test

# 跳过测试打包
mvn clean package -DskipTests

# 开发态直接运行（需先 mvn compile）
mvn -q exec:java -Dexec.mainClass="com.cliagent.Main" -Dexec.args="你好"
```

## 设计要点（面试自检）

| 要点 | 状态 | 说明 |
|------|------|------|
| `OkHttpClient` 全局单例 | ✅ | 复用连接池，避免线程/端口泄漏 |
| `ObjectMapper` 全局单例 | ✅ | 线程安全，构造成本高 |
| 四种 message role | ✅ | `LlmClient.Message` 工厂方法 |
| `arguments` 必须是 JSON 字符串 | ✅ | OpenAI 协议要求，防模型输出非法 JSON 拖垮整包 |
| `tool` 消息带 `tool_call_id` | ✅ | 与 assistant 的 `tool_calls[i].id` 配对 |
| ReAct 循环 + 工具执行 | ⏳ | Day 2–3 |
| 流式 SSE | ⏳ | 进阶项 |

## License

MIT
