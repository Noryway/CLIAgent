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

## 功能概览

- **DeepSeek API**：OkHttp 封装，OpenAI 兼容协议（非流式）
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
- **单元测试**：LLM 协议 + 工具 + Agent + AgentBudget + REPL 命令 Mock 测试（不依赖 API Key）

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
```

### REPL 命令

| 命令 | 作用 |
|------|------|
| `exit` / `quit` / `/exit` / `/quit` | 退出程序 |
| `clear` / `/clear` | 清空对话历史（保留 system 提示词） |
| `help` / `/help` | 显示可用命令 |
| `context` / `/context` | 查看 history 条数、累计 token、模型名 |
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

## 项目结构

```text
CLIAgent/
├── pom.xml
├── .env.example
├── README.md
├── docs/
│   ├── DEVELOPMENT-PLAN.md   # 完整开发计划（Day 5–10 + 阶段 3）
│   └── SESSION-HANDOFF.md
└── src/
    ├── main/java/com/cliagent/
    │   ├── Main.java
    │   ├── cli/
    │   │   └── ReplCommandParser.java
    │   ├── agent/
    │   │   ├── Agent.java
    │   │   └── AgentBudget.java
    │   ├── config/
    │   │   └── EnvConfig.java
    │   ├── policy/
    │   │   ├── PathGuard.java
    │   │   └── CommandGuard.java
    │   ├── llm/
    │   │   ├── LlmClient.java
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
  ├── parseCliArgs()：解析 --cwd，剥离启动参数
  ├── 无参数 → runRepl()：打印 projectPath，ReplCommandParser 循环
  ├── 有参数 → runOnce()：单次 agent.run()
  ├── EnvConfig
  ├── ToolRegistry.setProjectPath()：PathGuard + execute_command cwd
  └── Agent（history + token 跨 run 共享）
        ├── run(userInput): ReAct while 循环
        │     → recordTokens()：累加 usage
        │     → AgentBudget.check()：停滞 / 硬轮数兜底
        │     → hasToolCalls? executeTool → continue
        │     → else return 最终答案
        ├── getContextStatus()：/context 可观测
        └── clearHistory()：只保留 system，token 归零
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
| 流式 SSE | ⏳ | Day 10 |

## 开发计划

> 完整版见 [docs/DEVELOPMENT-PLAN.md](docs/DEVELOPMENT-PLAN.md)

### 阶段 1（Day 0–4）✅ 已完成

脚手架 → LLM 协议 → 五工具 → ReAct → REPL 多轮对话。

### 阶段 2（Day 5–10）进行中

主题：**让 Agent 从「能跑」变成「能放心跑、好调试」**

| Day | 状态 | 模块 | 关键产出 |
|-----|------|------|----------|
| Day 5 | ✅ | AgentBudget | 停滞检测 + 硬轮数兜底 |
| Day 6 | ✅ | 策略围栏 | `PathGuard` + `CommandGuard` |
| Day 7 | ✅ | 命令解析 | `ReplCommandParser` + `/help` |
| Day 8 | ✅ | 可观测 | Token 累计 + `/context` |
| Day 9 | ✅ | 项目沙箱 | `projectPath` + `--cwd` |
| Day 10 | ⏳ | 流式输出 | SSE 逐字打印（可选） |

### 阶段 3（Day 11+）三选一

| 路线 | 模块 | 面试亮点 |
|------|------|----------|
| **A（推荐）** | Memory | `/save` 跨会话、clear 不清长期记忆 |
| B | Plan-and-Execute | 复杂任务拆解、步骤状态机 |
| C | RAG | `/index` `/search` 代码检索 |

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

# 2. 暂存 Day 9（不要 add .env）
git add README.md docs/DEVELOPMENT-PLAN.md \
  src/main/java/com/cliagent/Main.java \
  src/main/java/com/cliagent/tool/ToolRegistry.java \
  src/test/java/com/cliagent/MainTest.java \
  src/test/java/com/cliagent/tool/ToolRegistryTest.java

# 3. 提交
git commit -m "$(cat <<'EOF'
feat: Day 9 — explicit projectPath for tool sandbox

EOF
)"

# 4. 推送
git push origin main
```

验证：`git status` 显示 `Your branch is up to date with 'origin/main'`。

## License

MIT
