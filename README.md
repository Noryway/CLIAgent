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
| Day 5 | ✅ | `AgentBudget` 停滞检测 + 硬轮数兜底 |
| Day 6 | ✅ | `PathGuard` + `CommandGuard` 策略围栏 |
| Day 7 | ✅ | `ReplCommandParser` 斜杠命令解析 |
| Day 8 | ✅ | Token 累计 + `/context` 可观测 |
| Day 9 | ✅ | `projectPath` 显式沙箱 + `--cwd` 启动参数 |
| Day 10 | ✅ | SSE 流式输出 + `--stream` 启动参数 |
| 阶段 3 | ✅ | Memory 长期记忆（`/save` + `~/.cliagent/memory.json`） |
| 阶段 4 | ✅ | Plan-and-Execute（`/plan` + 依赖拓扑顺序执行） |

## 功能概览

- **DeepSeek API**：OkHttp 封装，OpenAI 兼容协议（非流式 + SSE 流式）
- **四种消息角色**：`system` / `user` / `assistant` / `tool`
- **Tool Call**：请求体序列化 `tools` + `tool_calls`，响应解析 `tool_calls`
- **配置加载**：`-D` 系统属性 > 环境变量 > `./.env`
- **ToolRegistry**：注册、执行、导出 5 个内置工具定义
- **ReAct Agent**：`Agent.run()` 自动 chat → 执行工具 → 再 chat，直到 LLM 返回纯文本
- **AgentBudget**：硬轮数上限（10）+ 停滞检测（连续 3 次相同工具调用自动停止）
- **策略围栏**：`PathGuard` 限制文件路径在项目根内；`CommandGuard` 拦截危险 shell
- **项目沙箱**：启动时显式 `setProjectPath`；文件工具与 `execute_command` 共用同一项目根；支持 `--cwd` 指定目录
- **交互式 REPL**：无参数启动进入多轮对话；`ReplCommandParser` 解析 `exit` / `clear` / `help` / `context`；未知 `/` 命令不发给 LLM
- **Token 可观测**：`Agent` 累计 `usage` token；`/context` 查看 history 条数与用量（不调 API）
- **流式 SSE**：`--stream` 启用；`StreamListener` 逐 chunk 打印 `assistant>` 回答（ReAct + tool_calls 仍可用）
- **长期记忆 Memory**：`/save` 持久化到 `~/.cliagent/memory.json`；`/memory list|search|clear`；`/clear` 不清长期记忆；Agent 每轮注入 system
- **Plan-and-Execute**：`/plan <复杂任务>` → LLM 拆步骤 → 按依赖拓扑顺序逐步委托 Agent 执行
- **单元测试**：LLM + 工具 + Agent + Memory + Plan + REPL 命令 Mock 测试（不依赖 API Key）

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

# 4. REPL 交互模式（推荐，多轮对话；沙箱=当前目录）
java -jar target/CLIAgent-1.0-SNAPSHOT.jar

# 4b. 指定 Agent 工作目录（对其它项目做 Agent 操作）
java -jar target/CLIAgent-1.0-SNAPSHOT.jar --cwd /path/to/your-project

# 5. 单次对话（带参数，兼容脚本调用）
java -jar target/CLIAgent-1.0-SNAPSHOT.jar "用一句话介绍 ReAct"

# 6. ReAct + 工具（自动 list_dir / read_file 等）
java -jar target/CLIAgent-1.0-SNAPSHOT.jar "列出当前目录有哪些文件"
java -jar target/CLIAgent-1.0-SNAPSHOT.jar "读取 README.md 并用一句话总结"
java -jar target/CLIAgent-1.0-SNAPSHOT.jar --cwd /path/to/project "执行 pwd 并告诉我当前目录"

# 7. 流式输出（assistant> 逐字出现）
java -jar target/CLIAgent-1.0-SNAPSHOT.jar --stream
java -jar target/CLIAgent-1.0-SNAPSHOT.jar --stream "用三句话介绍 ReAct"
```

### REPL 命令

| 命令 | 作用 |
|------|------|
| `exit` / `quit` / `/exit` / `/quit` | 退出程序 |
| `clear` / `/clear` | 清空**短期**对话 history（保留 system；**不清**长期记忆） |
| `help` / `/help` | 显示可用命令 |
| `context` / `/context` | 查看 history 条数、累计 token、模型名 |
| `/save <事实>` | 保存当前项目的长期记忆（写入 `~/.cliagent/memory.json`） |
| `/memory` | 查看长期记忆摘要（条数 + 项目路径） |
| `/memory list` | 列出当前项目全部长期记忆 |
| `/memory search <词>` | 关键词搜索长期记忆 |
| `/memory clear` | 清空**当前项目**长期记忆（不影响 `/clear`） |
| `/plan <复杂任务>` | Plan-and-Execute：拆解计划并逐步执行（普通输入仍走 ReAct） |
| `/xxx`（未知） | 本地提示，不调用 API |
| 空行 | 跳过，不调用 API |
| 其他输入 | 发送给 Agent，进入 ReAct 循环 |

### 项目沙箱（projectPath）

Agent 的所有文件工具（`list_dir` / `read_file` / `write_file` / `create_project`）和 shell 命令（`execute_command`）都在同一个 **projectPath** 下运行：

| 机制 | 作用 |
|------|------|
| `Main.resolveProjectPath()` | 启动时解析沙箱根（默认当前目录绝对路径） |
| `ToolRegistry.setProjectPath()` | 设置并规范化项目根 |
| `PathGuard` | 阻止 `../etc/passwd` 等路径逃逸 |
| `ProcessBuilder.directory()` | `execute_command("pwd")` 的 cwd = projectPath |

**默认行为**：在目录 A 下启动 jar → 沙箱 = A 的绝对路径。REPL 启动时会打印 `项目目录: ...`。

**指定目录**：用 `--cwd` 覆盖（目录必须存在）：

```bash
# 在 /tmp/my-app 沙箱内进入 REPL
java -jar target/CLIAgent-1.0-SNAPSHOT.jar --cwd /tmp/my-app

# 单次对话，对指定项目提问
java -jar target/CLIAgent-1.0-SNAPSHOT.jar --cwd /path/to/B "列出根目录文件"
```

> `.env` 仍从**启动时的当前工作目录**读取（`EnvConfig` 行为不变）；`--cwd` 只影响 Agent 工具沙箱，不改变配置文件查找位置。建议在含 `.env` 的目录启动，或用环境变量 / `-D` 传 API Key。

### 流式输出（`--stream`）

默认非流式：等 LLM 返回完整 JSON 后一次性打印 `assistant> ...`。

加 `--stream` 后，最终文本回答通过 SSE 逐 chunk 打印（体验类似 ChatGPT 打字效果）：

| 组件 | 作用 |
|------|------|
| `DeepSeekClient.chat(..., StreamListener)` | `stream: true`，解析 `data: {...}` / `[DONE]` |
| `StreamListener.onContentDelta` | 每收到一小段文本触发回调 |
| `Agent.run(input, streaming=true)` | 首个 delta 前打 `assistant> `，逐字 `print`，结束换行 |
| tool 轮次 | 仍显示 `[tool] ...`；无 content delta 时不误打 `assistant> ` |

```bash
# REPL 流式模式
java -jar target/CLIAgent-1.0-SNAPSHOT.jar --stream

# 单次流式对话
java -jar target/CLIAgent-1.0-SNAPSHOT.jar --stream "介绍一下 Java Agent CLI 架构"

# 可与 --cwd 组合
java -jar target/CLIAgent-1.0-SNAPSHOT.jar --cwd /path/to/project --stream
```

### 长期记忆（Memory）

短期对话（`Agent.history`）与长期记忆（磁盘 JSON）**职责分离**：

| 存储 | 生命周期 | 命令 |
|------|----------|------|
| 短期 history | 当前进程、`/clear` 可清 | `clear` / 多轮对话 |
| 长期 memory | 跨会话、`~/.cliagent/memory.json` | `/save`、`/memory *` |

| 组件 | 作用 |
|------|------|
| `MemoryStore` | 读写 `~/.cliagent/memory.json` |
| `MemoryManager` | 按 `projectPath` 作用域 save / list / search |
| `Agent.refreshSystemPrompt()` | 每轮 run 把长期记忆注入 system prompt |
| `ReplCommandParser` | 解析 `/save`、`/memory list|search|clear` |

```bash
you> /save 用户叫小明，项目使用 Java 17
💾 已保存到长期记忆: ...

you> /memory list
长期记忆 (1 条):
  1. 用户叫小明，项目使用 Java 17

you> clear                    # 只清短期 history
🗑️ 对话历史已清空。

you> 我叫什么？              # 仍可从长期记忆回答（注入 system）
assistant> ...

you> /memory clear            # 只清当前项目的长期记忆
🗑️ 当前项目的长期记忆已清空。

# 重启进程后 /memory list 仍可见（验收跨会话）
```

> 长期记忆按 **projectPath** 隔离：在目录 A 启动 `/save` 的内容，在 `--cwd B` 下不可见。

### Plan-and-Execute（`/plan`）

复杂多步任务走 **先规划、后执行**；普通单行输入仍走 ReAct。

| 组件 | 作用 |
|------|------|
| `PlanParser` | LLM 生成 JSON 计划；简单任务 fallback 为单步 |
| `ExecutionPlan` | 任务 DAG + 拓扑排序 + `formatSummary()` |
| `Task` | 步骤节点：`id`、`description`、`dependencies`、状态 |
| `PlanExecutor` | 展示计划 → 按依赖顺序逐步 `agent.run(stepPrompt)` |

```bash
you> /plan 在当前目录创建 plan-demo 文件夹，然后列出 plan-demo 目录内容

📋 正在规划: ...
📋 执行计划: ...
   1. [ ] 创建 plan-demo 文件夹 (依赖: 无)
   2. [ ] 列出 plan-demo 目录内容 (依赖: task_1)

🚀 开始执行计划...

▶️ [task_1] 步骤 1/2: 创建 plan-demo 文件夹
assistant> ...
✅ [task_1] 完成

▶️ [task_2] 步骤 2/2: 列出 plan-demo 目录内容
assistant> ...
✅ [task_2] 完成

✅ 计划执行完成！
```

> 与 paicli 裁剪版对比：v1 **顺序执行**（不做并行批次）；不做 Enter 审批 / 失败 replan。

### 预期输出

**REPL 多轮对话：**

```text
CLIAgent 已启动，项目目录: /home/you/CLIAgent
输入 help 查看命令，exit 退出。
you> 我叫小明
assistant> 你好小明！...
you> /context
history: 3 条 | token: in=120 out=45 total=165 | 模型: deepseek-chat
you> clear
🗑️ 对话历史已清空。
you> /context
history: 1 条 | token: in=0 out=0 total=0 | 模型: deepseek-chat
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

**流式输出（`--stream`）：**

```text
CLIAgent 已启动，项目目录: /home/you/CLIAgent
流式输出已启用（--stream）。
输入 help 查看命令，exit 退出。
you> 用三句话介绍 ReAct
assistant> ReAct 是一种...（此处逐字出现，非一次性弹出）
```

**长期记忆：**

```text
you> /save 默认使用 Maven 构建
💾 已保存到长期记忆: 默认使用 Maven 构建
you> /memory
长期记忆: 1 条 | 项目: /home/you/CLIAgent
you> clear
🗑️ 对话历史已清空。
you> 这个项目怎么构建？
assistant> （参考 system 中的长期记忆回答）
```

**Plan-and-Execute：**

```text
you> /plan 列出当前目录的文件
📋 正在规划: 列出当前目录的文件
📋 执行计划: 直接执行简单任务：...
🚀 开始执行计划...
▶️ [task_1] 步骤 1/1: 列出当前目录的文件
  [tool] list_dir → ...
assistant> 当前目录包含 ...
✅ [task_1] 完成
✅ 计划执行完成！
```

## 项目结构

```text
CLIAgent/
├── pom.xml
├── .env.example
├── README.md
├── docs/
│   ├── DEVELOPMENT-PLAN.md   # 完整开发计划（Day 5–10 + 阶段 3/4）
│   └── SESSION-HANDOFF.md
└── src/
    ├── main/java/com/cliagent/
    │   ├── Main.java
    │   ├── cli/
    │   │   └── ReplCommandParser.java
    │   ├── agent/
    │   │   ├── Agent.java
    │   │   └── AgentBudget.java
    │   ├── memory/
    │   │   ├── MemoryEntry.java
    │   │   ├── MemoryStore.java
    │   │   └── MemoryManager.java
    │   ├── plan/
    │   │   ├── Task.java
    │   │   ├── ExecutionPlan.java
    │   │   ├── PlanParser.java
    │   │   └── PlanExecutor.java
    │   ├── config/
    │   │   └── EnvConfig.java
    │   ├── policy/
    │   │   ├── PathGuard.java
    │   │   └── CommandGuard.java
    │   ├── llm/
    │   │   ├── LlmClient.java
    │   │   ├── StreamListener.java
    │   │   └── DeepSeekClient.java
    │   └── tool/
    │       ├── Tool.java
    │       ├── ToolExecutor.java
    │       └── ToolRegistry.java
    └── test/java/com/cliagent/
        ├── MainTest.java
        ├── cli/
        │   └── ReplCommandParserTest.java
        ├── agent/
        │   ├── AgentTest.java
        │   └── AgentBudgetTest.java
        ├── memory/
        │   ├── MemoryStoreTest.java
        │   └── MemoryManagerTest.java
        ├── plan/
        │   ├── ExecutionPlanTest.java
        │   ├── PlanParserTest.java
        │   └── PlanExecutorTest.java
        ├── llm/
        │   ├── MessageTest.java
        │   └── DeepSeekClientTest.java
        ├── policy/
        │   ├── PathGuardTest.java
        │   └── CommandGuardTest.java
        └── tool/
            └── ToolRegistryTest.java
```

## 架构

```text
Main
  ├── parseCliArgs()：--cwd / --stream
  ├── MemoryManager + ToolRegistry.setProjectPath()
  ├── ReplCommandParser：exit/clear/help/context/save/memory/plan
  ├── PlanParser + PlanExecutor（/plan）
  └── Agent(llm, registry, memoryManager)
        ├── run() 前 refreshSystemPrompt()：注入长期记忆
        ├── ReAct + AgentBudget + 可选 SSE 流式
        ├── getContextStatus()：/context
        └── clearHistory()：只清短期 history

MemoryStore → ~/.cliagent/memory.json（跨会话）
PlanExecutor → 每步 enriched prompt 委托 Agent.run()
```

## 配置说明

### API Key 加载优先级

`EnvConfig.get()` / `EnvConfig.require()` 按以下顺序查找，先命中为准：

1. JVM 系统属性：`java -DDEEPSEEK_API_KEY=sk-xxx -jar ...`
2. 环境变量：`export DEEPSEEK_API_KEY=sk-xxx`
3. 项目根目录 `./.env` 文件

> 必须在项目根目录启动（或该目录下有 `.env`），否则读不到配置文件。Agent 工具沙箱默认为启动时的当前目录；可用 `--cwd` 指定其它项目目录。

### 启动参数

| 参数 | 说明 |
|------|------|
| `--cwd <dir>` | 指定 Agent 工具沙箱根目录（必须已存在）；可单独使用（REPL）或与 prompt 组合（单次模式） |
| `--stream` | 启用 SSE 流式输出；最终文本回答逐 chunk 打印（默认非流式） |

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

# 只跑 AgentBudget 测试
mvn test -Dtest=AgentBudgetTest

# 只跑 REPL / CLI 参数解析测试
mvn test -Dtest=MainTest

# 只跑 REPL 命令解析测试
mvn test -Dtest=ReplCommandParserTest

# 跳过测试打包
mvn clean package -DskipTests

# 只跑 Memory 测试
mvn test -Dtest=MemoryStoreTest,MemoryManagerTest

# 只跑 Plan 测试
mvn test -Dtest=ExecutionPlanTest,PlanParserTest,PlanExecutorTest

# 开发态 REPL
mvn -q exec:java -Dexec.mainClass="com.cliagent.Main"

# 只跑 LLM / SSE 解析测试
mvn test -Dtest=DeepSeekClientTest

# 开发态单次对话
mvn -q exec:java -Dexec.mainClass="com.cliagent.Main" -Dexec.args="你好"

# 开发态流式 REPL
mvn -q exec:java -Dexec.mainClass="com.cliagent.Main" -Dexec.args="--stream"
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
| ReAct 循环 | ✅ | `Agent.run()` while 循环 |
| AgentBudget 硬轮数 | ✅ | 默认最多 10 轮 LLM 调用 |
| 停滞检测 | ✅ | 连续 3 次相同 tool+args 自动停止 |
| 多轮对话 history | ✅ | `Agent.history` 成员变量，跨 `run()` 共享 |
| REPL 交互入口 | ✅ | `Main.runRepl()` + `BufferedReader` |
| `clearHistory()` | ✅ | 清空历史，只保留 system |
| PathGuard / CommandGuard | ✅ | 路径沙箱 + 命令黑名单 |
| projectPath 显式沙箱 | ✅ | `setProjectPath` + `--cwd` + `pwd` 验收 |
| ReplCommandParser | ✅ | `/help`、未知 `/` 命令本地处理 |
| Token 统计 + `/context` | ✅ | `recordTokens()` + `getContextStatus()` |
| 流式 SSE + `--stream` | ✅ | `StreamListener` + SSE 解析 + 逐字打印 |
| 长期记忆 Memory | ✅ | `/save` + JSON 持久化 + system 注入 |
| `/clear` vs `/memory clear` | ✅ | 短期 history vs 长期 memory 分离 |
| Plan-and-Execute | ✅ | `/plan` + 拓扑排序 + 顺序逐步执行 |
| ReAct vs Plan 分工 | ✅ | 普通输入 ReAct；复杂多步 `/plan` |

## 开发计划

> 完整版见 [docs/DEVELOPMENT-PLAN.md](docs/DEVELOPMENT-PLAN.md)

### 阶段 1（Day 0–4）✅ 已完成

脚手架 → LLM 协议 → 五工具 → ReAct → REPL 多轮对话。

### 阶段 2（Day 5–10）✅ 已完成

主题：**让 Agent 从「能跑」变成「能放心跑、好调试」**

| Day | 状态 | 模块 | 关键产出 |
|-----|------|------|----------|
| Day 5 | ✅ | AgentBudget | 停滞检测 + 硬轮数兜底 |
| Day 6 | ✅ | 策略围栏 | `PathGuard` + `CommandGuard` |
| Day 7 | ✅ | 命令解析 | `ReplCommandParser` + `/help` |
| Day 8 | ✅ | 可观测 | Token 累计 + `/context` |
| Day 9 | ✅ | 项目沙箱 | `projectPath` + `--cwd` |
| Day 10 | ✅ | 流式输出 | SSE + `--stream` 逐字打印 |

### 阶段 3（Day 11+）Memory ✅ 已完成

主题：**跨会话长期记忆，面试可讲 10 分钟**

| Step | 状态 | 模块 | 关键产出 |
|------|------|------|----------|
| Step 1 | ✅ | Memory 核心 | `MemoryEntry` / `MemoryStore` / `MemoryManager` |
| Step 2 | ✅ | REPL 命令 | `/save`、`/memory list|search|clear` |
| Step 3 | ✅ | Agent 注入 | `buildContextBlock()` → system prompt |
| Step 4 | ✅ | 文档 | README + DEVELOPMENT-PLAN |

### 阶段 4（Plan-and-Execute）✅ 已完成

主题：**复杂任务拆解 + 步骤状态机，面试可讲 10 分钟**

| Step | 状态 | 模块 | 关键产出 |
|------|------|------|----------|
| Step 1 | ✅ | Plan 核心 | `Task` / `ExecutionPlan` / `PlanParser` |
| Step 2 | ✅ | REPL 命令 | `/plan <任务>` |
| Step 3 | ✅ | 执行器 | `PlanExecutor` 顺序委托 `Agent.run()` |
| Step 4 | ✅ | 文档 | README + DEVELOPMENT-PLAN |

**未选路线（后续可选）**：RAG、Plan 并行批次、失败 replan

### 开发规范

```text
读 DEVELOPMENT-PLAN.md → 实现 → mvn test → demo → git commit → 下一步
```

## 推送到 GitHub

远程仓库：`git@github.com:Noryway/CLIAgent.git`（SSH）

```bash
cd /home/ubuntu/simple-agent-cli

# 1. 查看改动
git status
git diff

# 2. 暂存 Plan 阶段（不要 add .env）
git add README.md docs/DEVELOPMENT-PLAN.md \
  src/main/java/com/cliagent/Main.java \
  src/main/java/com/cliagent/cli/ReplCommandParser.java \
  src/main/java/com/cliagent/plan/ \
  src/test/java/com/cliagent/plan/ \
  src/test/java/com/cliagent/cli/ReplCommandParserTest.java

# 3. 提交
git commit -m "$(cat <<'EOF'
feat: Plan — Plan-and-Execute with /plan command

EOF
)"

# 4. 推送
git push origin main
```

验证：`git status` 显示 `Your branch is up to date with 'origin/main'`。

## License

MIT
