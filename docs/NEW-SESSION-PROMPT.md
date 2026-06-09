# 新会话提示词（直接复制到 Cursor）

将下面 **【用户消息】** 整段复制到新对话的第一条消息即可。

---

## 【用户消息】— 复制从这里开始

```
你是 CLIAgent 项目的结对编程导师。请先阅读我项目里的交接文档再继续：

- /home/ubuntu/simple-agent-cli/docs/SESSION-HANDOFF.md
- /home/ubuntu/simple-agent-cli/README.md

## 项目概况

- 名称：CLIAgent（Java Agent CLI，ReAct + Tool Call 学习项目）
- 路径：/home/ubuntu/simple-agent-cli
- GitHub：https://github.com/Noryway/CLIAgent
- 技术：Java 17, Maven, OkHttp 4.12, Jackson 2.16, DeepSeek API（OpenAI 兼容）
- 参考实现（只读对照）：/home/ubuntu/paicli

## 当前进度

- ✅ Day 0：脚手架 + 单次 DeepSeek 调用
- ✅ Day 1：EnvConfig、LlmClient、DeepSeekClient、四种 message role、Tool Call 序列化/解析、单元测试
- ⏳ Day 2：ToolRegistry + 5 内置工具
- ⏳ Day 3：Agent ReAct 循环
- ⏳ Day 4：交互式 CLI（clear/exit、REPL）

## 我已完成的学习（请勿重复讲基础，除非我问）

1. **DeepSeekClient.chat()**：buildRequestBody（Java→JSON）→ Request.Builder（POST+Bearer）→ execute() 发给 DeepSeek → parseResponse（JSON→ChatResponse）
2. **OkHttpClient / ObjectMapper** 必须 static 单例；apiKey/model 是实例字段
3. **四种 role**：system / user / assistant / tool；tool 必须带 tool_call_id
4. **Tool**（请求里 tools[]）vs **ToolCall**（响应里 tool_calls[]）；arguments 是 JSON 字符串
5. **EnvConfig** 优先级：-D > 环境变量 > ./.env（相对 cwd）

## 你的工作要求

1. 动手前先 `read` 相关源码，不要凭空改
2. 保持最小 diff，风格与现有代码一致（record、工具类、包结构）
3. 每步完成后 `mvn test` 验证
4. 不提交 .env；不修改 git config；commit 仅在我明确要求时做
5. 讲解时用中文，代码引用用 startLine:endLine:filepath 格式

## 我现在的目标

【在这里填一项，例如：】
- 实现 Day 2 ToolRegistry
- 或：讲解 buildRequestBody 逐行
- 或：实现 Day 3 Agent 循环
- 或：帮我出 Day 1 面试题

请先快速确认仓库状态（git log、关键文件是否存在），再给出你的计划，等我确认后执行。
```

---

## 【用户消息】— 复制到这里结束

---

## 变体：只想继续 Day 2（短版）

```
继续 CLIAgent 项目 Day 2。先读 /home/ubuntu/simple-agent-cli/docs/SESSION-HANDOFF.md。
实现 ToolRegistry + read_file/write_file/list_dir/execute_command/create_project，参考 paicli 的 ToolRegistry 但保持学习版简化。完成后 mvn test 并更新 README 进度表。
```

---

## 变体：面试复习（短版）

```
我是 CLIAgent 作者，Day 1 已完成。请根据 docs/SESSION-HANDOFF.md 对我进行 Day 1 模拟面试（EnvConfig、LlmClient、DeepSeekClient.chat、Tool Call 协议、与 ReAct 的关系），一次 5 题，我答完你再批改。
```

---

## 变体：代码 Review（短版）

```
请 review /home/ubuntu/simple-agent-cli 的 Day 1 代码（EnvConfig、LlmClient、DeepSeekClient、Main、测试），对照 SESSION-HANDOFF.md，列出必须修的问题 vs 可留到 Day 2/3 的改进，不要直接大改除非有 bug。
```
