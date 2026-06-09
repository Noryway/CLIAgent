# CLIAgent 会话交接文档

> 本文档由 Cursor 对话整理，供**新会话**快速恢复上下文。  
> 项目路径：`/home/ubuntu/simple-agent-cli`  
> GitHub：<https://github.com/Noryway/CLIAgent>（用户 `Noryway`）

---

## 一、项目背景与目标

### 1.1 是什么

**CLIAgent** 是从零手搓的 Java Agent CLI 学习项目，对标 Claude Code / Aider / Codex CLI 的**最小可学习版本**。

- **技术栈**：Java 17、Maven、OkHttp 4.12、Jackson 2.16、JUnit 5、DeepSeek API（OpenAI 兼容协议）
- **groupId**：`com.cliagent`
- **artifactId / jar**：`CLIAgent-1.0-SNAPSHOT.jar`
- **主类**：`com.cliagent.Main`

### 1.2 简历对应能力（用户正在准备面试）

1. 基于 Cursor 从零搭建 Agent CLI，实现 ReAct（Day 3 待做）
2. OkHttp 封装 DeepSeek API，Tool Call 消息序列化与解析，四种 message role
3. ToolRegistry 五工具 + 交互式 CLI + `.env` 加载（Day 2–4 待做）

### 1.3 参考项目

同 workspace 下有完整版 **PaiCLI**（`/home/ubuntu/paicli`），功能很多（Memory、RAG、MCP、TUI）。  
CLIAgent 是**刻意裁剪的学习版**，卡壳时可对照阅读，但不要直接抄。

| 卡在哪 | 看 paicli |
|--------|-----------|
| Message / LlmClient | `com/paicli/llm/LlmClient.java` |
| HTTP + 序列化 | `com/paicli/llm/AbstractOpenAiCompatibleClient.java` |
| ReAct 循环 | `com/paicli/agent/Agent.java` 119–250 行 |
| ToolRegistry | `com/paicli/tool/ToolRegistry.java` |
| .env 加载 | `com/paicli/cli/Main.java` loadConfigValue |

---

## 二、当前进度（截至 Day 1 完成）

| 阶段 | 状态 | 产出 |
|------|------|------|
| Day 0 | ✅ | Maven 脚手架、`Main` 单次 HTTP 调 DeepSeek、`.env` 加载 |
| Day 1 | ✅ | `EnvConfig`、`LlmClient`、`DeepSeekClient`、单元测试、README |
| Day 2 | ⏳ | `ToolRegistry` + 5 工具（read_file / write_file / list_dir / execute_command / create_project） |
| Day 3 | ⏳ | `Agent.run()` ReAct 循环 |
| Day 4 | ⏳ | REPL、clear / exit、多轮对话 |

### Git 提交历史（预期）

```text
feat: Day 1 — LlmClient, DeepSeekClient, and Tool Call protocol
chore: rename artifact and project to CLIAgent
chore: Day 0 scaffold for Simple Agent CLI
```

推送方式：用户已配置 SSH（`git@github.com:Noryway/CLIAgent.git`），HTTPS 曾因 GnuTLS 不稳定。

---

## 三、项目结构

```text
simple-agent-cli/
├── pom.xml
├── .env.example          # 模板（.env 在 .gitignore，不入库）
├── README.md
├── docs/
│   └── SESSION-HANDOFF.md   # 本文档
└── src/
    ├── main/java/com/cliagent/
    │   ├── Main.java              # 入口：组装 messages/tools，调用 chat，打印结果
    │   ├── config/
    │   │   └── EnvConfig.java     # -D > env > ./.env
    │   └── llm/
    │       ├── LlmClient.java       # 接口 + Message/Tool/ToolCall/ChatResponse
    │       └── DeepSeekClient.java  # OkHttp + buildRequestBody + parseResponse
    └── test/java/com/cliagent/llm/
        ├── MessageTest.java
        └── DeepSeekClientTest.java
```

---

## 四、架构与数据流（用户已理解的核心）

### 4.1 分层

```text
Main（入口 / 组装）
  → EnvConfig（读 key/model）
  → DeepSeekClient.chat(messages, tools)
       → buildRequestBody()   Java → JSON
       → OkHttp POST          → api.deepseek.com
       → parseResponse()      JSON → ChatResponse
  → Main.printResponse()
```

### 4.2 `chat()` 方法（用户已完全掌握）

```text
输入：List<Message> + List<Tool>（Java 对象）
  ① buildRequestBody → JSON 字符串
  ② Request.Builder → URL + Authorization: Bearer + POST body
  ③ HTTP.newCall(request).execute() → 同步发给 DeepSeek，收回 Response
  ④ response.body().string() → raw JSON 字符串
  ⑤ 非 2xx → 抛 IOException；成功 → parseResponse(raw) → ChatResponse
```

要点：

- `OkHttpClient`、`ObjectMapper` 为 **static final 单例**；`apiKey`、`model` 为实例字段
- `try-with-resources` 必须 close Response，防连接泄漏
- `ResponseBody.string()` 只能读一次
- Day 1 **只调一次** chat，不执行工具，不 ReAct 循环

### 4.3 四种 Message role

| role | 用途 | 关键字段 |
|------|------|----------|
| system | 系统提示 | content |
| user | 用户输入 | content |
| assistant | LLM 回复 | content + 可选 toolCalls |
| tool | 工具结果 | content + **toolCallId**（必须配对） |

### 4.4 Tool vs ToolCall

| | Tool | ToolCall |
|---|------|----------|
| 方向 | 你告诉 LLM 有什么工具（请求 `tools[]`） | LLM 告诉你要调什么（响应 `tool_calls[]`） |
| arguments | JSON Schema 在 parameters | **字符串** JSON，不是嵌套对象 |

### 4.5 EnvConfig 优先级

```text
System.getProperty(-D) > System.getenv > ./.env（相对 cwd，非 jar 目录）
```

---

## 五、关键文件说明

### 5.1 `EnvConfig.java`

- 工具类（private 构造器，全 static）
- `get(key)` / `get(key, default)` / `require(key)`
- `.env` 简化解析：支持 `#` 注释、引号；不支持 `export KEY=`

### 5.2 `LlmClient.java`

- 接口：`chat(messages, tools)`、`getModelName()`
- record：`Message`、`ToolCall`、`Tool`、`ChatResponse`
- `ChatResponse.hasToolCalls()` → Day 3 ReAct 分支条件

### 5.3 `DeepSeekClient.java`

- `API_URL` = `https://api.deepseek.com/chat/completions`
- `buildRequestBody`：序列化 4 种 role + tools 数组，`stream: false`
- `parseResponse`：取 `choices[0].message`，用 `path()` 防 NPE
- `parseToolCalls`：arguments 兼容 string 或 object

### 5.4 `Main.java`

- 普通：`java -jar CLIAgent.jar "问题"`
- 演示 Tool Call：`java -jar CLIAgent.jar --demo-tool "现在几点？"`
- `--demo-tool` 注册 `get_current_time`，只**解析打印** tool_calls，不执行

---

## 六、运行方式

```bash
cd /home/ubuntu/simple-agent-cli
cp .env.example .env   # 填入 DEEPSEEK_API_KEY
mvn clean package
java -jar target/CLIAgent-1.0-SNAPSHOT.jar "用一句话介绍 ReAct"
java -jar target/CLIAgent-1.0-SNAPSHOT.jar --demo-tool "现在几点？"
mvn test
```

环境变量：

- `DEEPSEEK_API_KEY`（必填）
- `DEEPSEEK_MODEL`（可选，默认 `deepseek-chat`）

---

## 七、用户学习记录与易错点

### 7.1 已掌握

- `chat()` 完整流程（序列化 → POST → 收响应 → 解析）
- OkHttp 单例 vs 实例字段分工
- `trim()` vs `isBlank()` 区别
- Day 0 ≠ ReAct；Day 1 只调一次 LLM

### 7.2 测验中曾混淆（需巩固）

| 误区 | 正解 |
|------|------|
| `.env` 值带空格会「找不到 key」 | 会找到 key，但 `trim()` 防的是 401；找不到 key 是 cwd 不对或 key 未配置 |
| `export KEY=val` 写在 .env 里 | 不支持，必须 `KEY=val` |
| Day 1 已实现 ReAct | 错，只有协议能力，循环在 Day 3 |
| `arguments` 可以是 JSON 对象 | 协议要求字符串 |
| 在构造函数里 new OkHttpClient | 应 static 单例 |

### 7.3 paicli 相关（用户环境）

- paicli 在 `/home/ubuntu/paicli`，banner 已改为 `CLIAgent λ`
- paicli `.env` 有 DeepSeek key；`PAICLI_LOG_DIR=/Users/yourname/...` 在 Linux 上会报错，应注释或改 `~/.paicli/logs`

---

## 八、Day 2 实施要点（下一步）

### 8.1 新增文件

```text
tool/Tool.java           # record(name, description, parameters, executor)
tool/ToolRegistry.java   # 注册 + executeTool + getToolDefinitions
```

### 8.2 五个工具

1. `list_dir` — 最简单，先做
2. `read_file` — offset/limit
3. `write_file` — 建父目录
4. `execute_command` — ProcessBuilder + 60s 超时
5. `create_project` — 目录 + pom 骨架

### 8.3 设计约束

- `executeTool(name, argsJson)` 内部 try-catch，异常转字符串返回 LLM
- JSON Schema 用 `createParameters(Param...)` 统一生成
- 第一版可不加 PathGuard，Day 2+ 再加

### 8.4 Day 3 预览（ReAct 伪代码）

```java
history.add(Message.user(userInput));
while (iter < MAX) {
    ChatResponse resp = llm.chat(history, tools);
    if (resp.hasToolCalls()) {
        history.add(Message.assistant(resp.content(), resp.toolCalls()));
        for (ToolCall tc : resp.toolCalls()) {
            String result = registry.executeTool(tc.function().name(), tc.function().arguments());
            history.add(Message.tool(tc.id(), result));
        }
        continue;
    }
    return resp.content();
}
```

---

## 九、相关文档（项目内）

- `README.md` — 快速开始与架构
- `MINI-AGENT-从0到1清单.md` — 从 0 到 1 清单
- `阶段1-拆解小项目.md` — 阶段拆解
- paicli：`/home/ubuntu/paicli/面试准备-*.md` — 面试题

---

## 十、给新会话的提示词（复制下面整段）

见同目录 `NEW-SESSION-PROMPT.md`，或复制以下块：

```markdown
【角色】你是 CLIAgent 项目的结对编程导师，继续带我完成 Java Agent CLI（ReAct + Tool Call）。

【项目】
- 路径：/home/ubuntu/simple-agent-cli
- GitHub：Noryway/CLIAgent
- 栈：Java 17, Maven, OkHttp, Jackson, DeepSeek API
- 进度：Day 0–1 已完成；Day 2 ToolRegistry 待做

【Day 1 已完成】
- EnvConfig：-D > env > ./.env
- LlmClient：Message/Tool/ToolCall/ChatResponse，四种 role
- DeepSeekClient：buildRequestBody + chat(OkHttp POST) + parseResponse
- Main：system+user 消息，--demo-tool 演示 tool_calls 解析（不执行）
- 测试：MessageTest + DeepSeekClientTest

【我已掌握】
- chat() 流程：Java 序列化 → Request POST → DeepSeek 响应 → parseResponse
- OkHttpClient static 单例；Response try-with-resources
- Tool vs ToolCall；arguments 必须是字符串

【请遵守】
1. 先读 docs/SESSION-HANDOFF.md 和 README.md
2. 改动最小化，匹配现有代码风格
3. 卡壳时可参考 /home/ubuntu/paicli 但勿大段抄袭
4. 每阶段：实现 → mvn test → 可运行 demo
5. 不要提交 .env；不要改 git config

【下一步默认任务】
实现 Day 2：ToolRegistry + read_file / write_file / list_dir / execute_command / create_project

请先确认当前代码状态，再列出 Day 2 文件清单和实现顺序，等我确认后开始写代码。
```

---

*文档版本：2026-06-08 · Day 1 完成态*
