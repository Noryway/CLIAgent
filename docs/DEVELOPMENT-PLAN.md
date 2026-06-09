# CLIAgent 开发计划

> 项目路径：`/home/ubuntu/simple-agent-cli`  
> 仓库：<https://github.com/Noryway/CLIAgent>  
> 参考（只读）：`/home/ubuntu/paicli`  
> 原则：**一个小模块做完 → `mvn test` → 能 demo → git commit → 再开下一个**

---

## 一、项目定位

**CLIAgent** 是从零手搓的 Java Agent CLI 学习项目，对标 Claude Code / Aider / paicli 的**最小可学习版本**。

| 维度 | CLIAgent | paicli |
|------|----------|--------|
| 目标 | 学透 ReAct + Tool Call + CLI 架构 | 完整产品（Memory/RAG/MCP/TUI） |
| 技术栈 | Java 17, Maven, OkHttp, Jackson, DeepSeek | 同上 + JLine + 更多子系统 |
| 策略 | 刻意裁剪，每个模块能讲清「为什么」 | 生产级，功能齐全 |

### 简历能力映射

1. 基于 Cursor 从零搭建 Agent CLI，实现 ReAct 循环
2. OkHttp 封装 DeepSeek API，Tool Call 四种 message role 序列化/解析
3. ToolRegistry 五工具 + 交互式 REPL + 安全阀 + 策略围栏

---

## 二、已完成进度（阶段 1 ✅）

| Day | 状态 | 关键产出 | 参考 commit |
|-----|------|----------|-------------|
| Day 0 | ✅ | Maven 脚手架、DeepSeek 调通、`.env` 加载 | `chore: Day 0 scaffold` |
| Day 1 | ✅ | `EnvConfig`、`LlmClient`、`DeepSeekClient`、单元测试 | `feat: Day 1 — LlmClient...` |
| Day 2 | ✅ | `ToolRegistry` + 5 内置工具 | `feat: Day 2 — ToolRegistry...` |
| Day 3 | ✅ | `Agent.run()` ReAct 循环 + Main 集成 | `feat: Day 3 — Agent ReAct loop` |
| Day 4 | ✅ | REPL 多轮对话、`clear`/`exit`、`clearHistory()` | `feat: Day 4 — REPL...` |
| Day 5 | ✅ | `AgentBudget` 停滞检测 + 硬轮数兜底 | `feat: Day 5 — AgentBudget...` |

### 当前架构（Day 5）

```text
Main
  ├─ 无参数 → runRepl()：you> 循环
  ├─ 有参数 → runOnce()：单次对话
  ├─ EnvConfig（-D > env > .env）
  ├─ ToolRegistry（5 内置工具）
  └─ Agent
        ├─ history（跨 run 共享）
        ├─ ReAct while + AgentBudget（硬轮数 10 + 停滞窗口 3）
        └─ clearHistory()（只保留 system）
```

### 已知缺口（对比 paicli，阶段 2 要补）

- [x] 停滞检测（连续相同 tool+args 死循环）
- [ ] PathGuard / CommandGuard（工具安全围栏）
- [ ] 命令解析器（`/` 未知命令不发给 LLM）
- [ ] Token 统计与 `/context` 可观测
- [ ] 流式 SSE 输出
- [ ] 长期记忆 / Plan / RAG（阶段 3 选一个）

---

## 三、阶段 2 总览（Day 5–10，约 6 天）

> 主题：**让 Agent 从「能跑」变成「能放心跑、好调试」**  
> 参考 paicli：`AgentBudget`、`PathGuard`、`CliCommandParser`

| Day | 模块 | 新增文件 | 做完能干嘛 |
|-----|------|----------|------------|
| Day 5 | ✅ AgentBudget 停滞检测 | `agent/AgentBudget.java` | 连续 3 次相同工具调用自动停止 |
| Day 6 | 策略围栏 | `policy/PathGuard.java`、`policy/CommandGuard.java` | 读写/命令限制在项目沙箱内 |
| Day 7 | ReplCommandParser | `cli/ReplCommandParser.java` | `/help`、`/exit`、未知 `/` 命令本地处理 |
| Day 8 | Token 统计 + `/context` | `Agent` 增强 | 看 history 条数、token 用量 |
| Day 9 | projectPath 显式化 | `ToolRegistry` 增强 | 指定工作目录，配合 PathGuard |
| Day 10 | 流式 SSE（可选） | `DeepSeekClient` 增强 | `assistant>` 逐字输出 |

---

## 四、Day 5：AgentBudget 停滞检测

### 目标

防止模型反复调用相同工具（如 `list_dir(".")` × 10），比单纯 `MAX_ITERATIONS` 更精准。

### 参考 paicli

- `com/paicli/agent/AgentBudget.java` — 停滞窗口、硬轮数、token 预算

### 要新建/修改的文件

```text
src/main/java/com/cliagent/agent/
├── AgentBudget.java    # 新建
└── Agent.java          # 改造：for-loop 改用 budget.check()

src/test/java/com/cliagent/agent/
├── AgentBudgetTest.java  # 新建
└── AgentTest.java        # 补充停滞场景
```

### 核心设计（最小版）

```java
public class AgentBudget {
    private final int hardMaxIterations;      // 默认 10（保留现有值）
    private final int stagnationWindow;       // 默认 3
    private final Deque<String> recentToolSigs = new ArrayDeque<>();

    public ExitReason check() { /* 硬轮数 + 停滞 */ }
    public void recordToolCall(String name, String argsJson) { /* 签名入队 */ }
}
```

**阶段 2 先不做 token 硬预算**（paicli 默认也是 `Integer.MAX_VALUE`），停滞检测 + 硬轮数即可。

### 任务清单

- [x] 创建 `AgentBudget.java`
- [x] `Agent.run()` 循环开头 `budget.check()`，工具执行后 `recordToolCalls()`
- [x] 测试：Mock LLM 连续返回相同 `list_dir` → 第 3 次后停止
- [x] 测试：正常单次工具调用不受影响
- [x] `mvn test` 全绿

### 验收标准

- 正常问答、单次工具调用行为不变
- 故意触发重复工具调用时，返回明确提示（含原因：停滞检测）
- 能解释：硬轮数 vs 停滞检测的区别

### commit 建议

```text
feat: Day 5 — AgentBudget stagnation detection
```

### 面试知识点

- 为什么要停滞检测？（防模型兜圈烧 token，比纯轮数更聪明）
- 工具签名怎么算？（`name + argumentsJson` 拼接）

---

## 五、Day 6：策略围栏 PathGuard + CommandGuard

### 目标

`write_file` / `read_file` / `list_dir` 限制在项目目录内；`execute_command` 拦截危险 shell。

### 参考 paicli

- `com/paicli/policy/PathGuard.java`
- `com/paicli/policy/CommandGuard.java`
- `ToolRegistry.executeTool` 中的策略拦截

### 要新建/修改的文件

```text
src/main/java/com/cliagent/
├── policy/
│   ├── PathGuard.java       # 新建
│   └── CommandGuard.java    # 新建
└── tool/
    └── ToolRegistry.java    # 改造：危险工具前过 guard

src/test/java/com/cliagent/
└── policy/
    ├── PathGuardTest.java   # 新建
    └── CommandGuardTest.java # 新建
```

### 核心设计

**PathGuard**

```java
public class PathGuard {
    private final Path projectRoot;

    public Path resolve(String userPath) throws PolicyException {
        // 规范化路径，必须在 projectRoot 下（防 ../ 逃逸）
    }
}
```

**CommandGuard**

```java
public class CommandGuard {
    public static String check(String command) {
        // 黑名单：rm -rf /、mkfs、curl|bash、> /dev/sd* 等
        // 命中返回拒绝原因，否则返回 null
    }
}
```

### 任务清单

- [ ] 创建 `PathGuard`、`CommandGuard`
- [ ] `ToolRegistry` 增加 `setProjectPath(String)` / `getProjectPath()`
- [ ] `list_dir` / `read_file` / `write_file` 路径经 PathGuard
- [ ] `execute_command` 经 CommandGuard
- [ ] 策略拒绝返回字符串（不抛给上层），LLM 可继续 ReAct
- [ ] 单元测试覆盖：正常路径、`../etc/passwd`、危险命令
- [ ] `mvn test` 全绿

### 验收标准

- 项目内正常读写、列目录不受影响
- `read_file("../../etc/passwd")` 被拒绝
- `execute_command("rm -rf /")` 被拒绝
- 能演示并讲解「Agent 安全三板斧」：围栏 + 审计（后续）+ HITL（进阶）

### commit 建议

```text
feat: Day 6 — PathGuard and CommandGuard policy layer
```

---

## 六、Day 7：ReplCommandParser

### 目标

命令与对话分离；以 `/` 开头但不认识的输入不发给 LLM；统一扩展入口。

### 参考 paicli

- `com/paicli/cli/CliCommandParser.java`
- `CliCommandParserTest.java`

### 要新建/修改的文件

```text
src/main/java/com/cliagent/
├── cli/
│   └── ReplCommandParser.java   # 新建
└── Main.java                    # 改造：switch(parser.parse())

src/test/java/com/cliagent/
├── MainTest.java                # 迁移/扩充
└── cli/
    └── ReplCommandParserTest.java  # 新建
```

### 核心设计

```java
public final class ReplCommandParser {
    enum CommandType { CHAT, EXIT, CLEAR, HELP, UNKNOWN }
    record ParsedCommand(CommandType type, String payload) {}

    static ParsedCommand parse(String input) {
        // exit / quit / /exit / /quit → EXIT
        // clear / /clear → CLEAR
        // help / /help → HELP
        // 以 / 开头但不认识 → UNKNOWN
        // 其他 → CHAT
    }
}
```

### 任务清单

- [ ] 创建 `ReplCommandParser`
- [ ] `Main.runRepl()` 改用 `switch (command.type())`
- [ ] 删除 `Main.isExitCommand` / `isClearCommand`（逻辑迁入 Parser）
- [ ] `HELP` 打印可用命令列表
- [ ] `UNKNOWN` 打印「未知命令」+ 帮助，不调 API
- [ ] 补充 `/exit`、`/help` 测试
- [ ] `mvn test` 全绿

### 验收标准

- 输入 `/help` 显示命令列表，不消耗 API
- 输入 `/foo` 提示未知命令，不消耗 API
- `exit` / `/exit` / `clear` / `/clear` 行为与 Day 4 一致

### commit 建议

```text
feat: Day 7 — ReplCommandParser for slash commands
```

---

## 七、Day 8：Token 统计 + `/context`

### 目标

累计 input/output token；REPL 输入 `/context` 查看会话状态。

### 参考 paicli

- `AgentBudget.recordTokens()`
- `/context` 命令 → `getContextStatus()`

### 要修改的文件

```text
src/main/java/com/cliagent/
├── agent/
│   ├── Agent.java          # 累计 token、getContextStatus()
│   └── AgentBudget.java    # 可选：迁入 token 累计
├── cli/
│   └── ReplCommandParser.java  # 新增 CONTEXT 类型
└── Main.java               # 处理 /context

src/test/java/com/cliagent/agent/
└── AgentTest.java          # token 累计测试
```

### 核心设计

```java
// Agent 内
private int totalInputTokens;
private int totalOutputTokens;

public String getContextStatus() {
    return String.format(
        "history: %d 条 | token: in=%d out=%d | 模型: %s",
        history.size(), totalInputTokens, totalOutputTokens, llm.getModelName()
    );
}
```

### 任务清单

- [ ] 每轮 `chat()` 后从 `ChatResponse` 累加 token
- [ ] `ReplCommandParser` 增加 `CONTEXT`
- [ ] `/context` 打印 `agent.getContextStatus()`
- [ ] `clearHistory()` 时 token 是否重置——建议**重置**（新会话统计从零开始）
- [ ] `mvn test` 全绿

### 验收标准

- 聊几轮后 `/context` 显示非零 token
- `clear` 后 token 统计归零（或 history 条数回到 1）
- 不调用 API 即可查看状态

### commit 建议

```text
feat: Day 8 — token tracking and /context command
```

---

## 八、Day 9：projectPath 显式化

### 目标

工具在明确的项目目录下执行，与 PathGuard 形成「沙箱」叙事；支持启动参数指定 cwd。

### 要修改的文件

```text
src/main/java/com/cliagent/
├── tool/ToolRegistry.java   # setProjectPath，execute_command 用 pb.directory()
└── Main.java                # 启动时 setProjectPath，可选 --cwd 参数
```

### 任务清单

- [ ] `ToolRegistry` 构造时默认 `System.getProperty("user.dir")`
- [ ] `execute_command` 的 `ProcessBuilder` 使用 `projectPath` 作为 working directory
- [ ] （可选）`java -jar ... --cwd /path` 覆盖项目路径
- [ ] README 补充「必须在项目根启动」说明
- [ ] `mvn test` 全绿

### 验收标准

- 在项目 A 启动，列目录只看到 A 的文件
- `execute_command("pwd")` 返回 projectPath

### commit 建议

```text
feat: Day 9 — explicit projectPath for tool sandbox
```

---

## 九、Day 10：流式 SSE 输出（可选进阶）

### 目标

`assistant>` 回复逐字出现，提升 REPL 体验；接口层预留流式能力。

### 参考 paicli

- `DeepSeekClient` 流式解析
- `StreamRenderer`（我们第一版可只用 `System.out::print`）

### 要修改的文件

```text
src/main/java/com/cliagent/
├── llm/
│   ├── LlmClient.java         # 增加 chatStreaming 或 Consumer 回调
│   └── DeepSeekClient.java    # SSE 解析 data: {...}
├── agent/Agent.java           # 流式时逐 chunk 打印
└── Main.java                  # 可选 --stream 开关
```

### 任务清单

- [ ] `buildRequestBody` 支持 `stream: true`
- [ ] 解析 SSE `data: [DONE]` 结束
- [ ] REPL 默认非流式，加 `--stream` 或 `/stream on` 启用
- [ ] Mock 测试流式解析（不依赖 API）
- [ ] 流式模式下 tool_calls 仍能正确 ReAct（可分两期：先纯文本流）

### 验收标准

- `--stream` 模式下长回答逐字打印
- 非流式模式行为不变
- `mvn test` 全绿

### commit 建议

```text
feat: Day 10 — streaming SSE chat output
```

---

## 十、阶段 3 总览（Day 11+，选一个主线）

> 主题：**有一个能深挖的亮点模块，够面试讲 10 分钟**  
> 三选一，不要三个同时开。

| 路线 | 模块 | 周期 | 面试亮点 |
|------|------|------|----------|
| **A（推荐）** | Memory 长期记忆 | 5–7 天 | `/save` 跨会话、clear 不清长期记忆 |
| B | Plan-and-Execute | 7–10 天 | 复杂任务拆解、步骤状态机 |
| C | RAG 代码检索 | 7–10 天 | `/index` `/search`、embedding 检索 |

### 路线 A：Memory（推荐）

```text
memory/
├── MemoryEntry.java
├── MemoryManager.java     # 短期裁剪 + 长期持久化
└── MemoryStore.java       # ~/.cliagent/memory.json

命令：/save <事实>、/memory list、/memory search
```

参考 paicli：`MemoryManager`、`/save`、`/memory clear` vs `/clear` 职责分离。

**验收**：重启进程后 `/memory list` 仍能看到上次 `/save` 的内容；`/clear` 不清长期记忆。

### 路线 B：Plan-and-Execute

```text
plan/
├── Task.java
├── PlanParser.java
└── PlanExecutor.java

命令：/plan <复杂任务>
```

参考 paicli：`com/paicli/plan/Task.java`、Planner SubAgent。

**验收**：输入「创建一个 Spring Boot 项目并写 HelloController」，输出分步计划并逐步执行。

### 路线 C：RAG

```text
rag/
├── CodeChunker.java
├── CodeRetriever.java
└── EmbeddingClient.java（或本地简化版）

命令：/index [path]、/search <query>
```

参考 paicli：`CodeRetriever`、`/index`、`/search`。

**验收**：索引后 `/search 登录逻辑` 能返回相关代码片段。

---

## 十一、明确不做的模块（阶段 1–2）

| 模块 | 原因 |
|------|------|
| JLine 全屏 TUI | 依赖重，与 Agent 核心弱相关 |
| MCP 协议 | 适合独立专项，工作量大 |
| Multi-Agent Team | 需 SubAgent 池 + 编排器 |
| LSP / Browser / Snapshot | IDE 级产品能力 |
| HITL 人工审批 | 可在 Guard 成熟后作为 Day 12+ 项 |

---

## 十二、开发规范（每 Day 遵守）

### 工作流

```text
1. 读 docs/DEVELOPMENT-PLAN.md + 参考 paicli 对应文件
2. 列出本 Day 文件清单，确认后写代码
3. 最小 diff，匹配现有风格
4. mvn test 全绿
5. 手动 demo（REPL 或管道测试）
6. git commit（用户明确要求时）
7. 更新 README 当前进度（可选）
```

### Git

- 远程：`git@github.com:Noryway/CLIAgent.git`（SSH）
- 不提交 `.env`
- 不修改 `git config`
- commit 消息：`feat: Day N — 简短描述`

### 测试策略

| 层 | 方式 |
|----|------|
| LLM 协议 | Mock JSON 字符串解析（`DeepSeekClientTest`） |
| 工具 | 真实文件系统 + 临时目录（`ToolRegistryTest`） |
| Agent | Mock `LlmClient`（`AgentTest`） |
| 命令 | `ReplCommandParserTest` |
| 策略 | 纯单元测试（`PathGuardTest`） |
| 集成 | 管道 REPL + 真 API（手动，不入 CI） |

### 参考 paicli 对照表

| 卡在哪 | 看 paicli |
|--------|-----------|
| ReAct 循环 | `agent/Agent.java` 119–250 行 |
| AgentBudget | `agent/AgentBudget.java` |
| 策略围栏 | `policy/PathGuard.java`、`policy/CommandGuard.java` |
| 命令解析 | `cli/CliCommandParser.java` |
| Memory | `memory/MemoryManager.java` |
| Plan | `plan/Task.java` |

---

## 十三、里程碑与完成定义

### 阶段 2 完成（Day 10 后）

- [ ] Agent 有停滞检测，不会傻循环
- [ ] 工具有 PathGuard + CommandGuard
- [ ] REPL 有统一命令解析器 + `/help` + `/context`
- [ ] README 与本文档进度同步
- [ ] 能完成 5 分钟面试演示：REPL 多轮 → 工具调用 → clear → 安全拒绝 → `/context`

### 阶段 3 完成（选一个路线后）

- [ ] 有一个可深挖模块（Memory / Plan / RAG）
- [ ] 模块有独立单元测试
- [ ] 简历上可写「实现 xxx，对标 paicli 裁剪版」

---

## 十四、新会话恢复提示词（复制用）

```markdown
【角色】CLIAgent 结对编程导师，继续 Day N 开发。

【项目】/home/ubuntu/simple-agent-cli，GitHub Noryway/CLIAgent
【文档】docs/DEVELOPMENT-PLAN.md、README.md
【参考】/home/ubuntu/paicli（只读对照）
【进度】Day 0–4 已完成，下一步见 DEVELOPMENT-PLAN.md Day N

【请遵守】
1. 先读计划文档和现有源码
2. 最小 diff，每步 mvn test
3. 不提交 .env，commit 仅在我明确要求时

请先确认当前代码状态，列出本 Day 文件清单，等我确认后开始写代码。
```

---

*文档版本：2026-06-09 · Day 5 完成态 · 阶段 2 进行中（下一步 Day 6）*
