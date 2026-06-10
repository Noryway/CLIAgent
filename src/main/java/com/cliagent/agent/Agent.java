package com.cliagent.agent;

import com.cliagent.llm.LlmClient;
import com.cliagent.llm.LlmClient.ChatResponse;
import com.cliagent.llm.LlmClient.Message;
import com.cliagent.llm.LlmClient.ToolCall;
import com.cliagent.tool.ToolRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ReAct Agent：维护对话 history，循环调用 LLM；若返回 tool_calls 则执行工具并继续。
 */
public class Agent {

    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是 CLIAgent，一个简洁的 Java 编程助手。可以使用工具读写文件、列目录、执行命令。";

    private final LlmClient llm;
    private final ToolRegistry registry;
    private final List<Message> history = new ArrayList<>();
    private int totalInputTokens;
    private int totalOutputTokens;

    public Agent(LlmClient llm, ToolRegistry registry) {
        this.llm = llm;
        this.registry = registry;
        history.add(Message.system(DEFAULT_SYSTEM_PROMPT));
    }

    /**
     * 处理一轮用户输入：ReAct 循环直到 LLM 返回纯文本或达到迭代上限（非流式）。
     */
    public String run(String userInput) throws IOException {
        return run(userInput, false);
    }

    /**
     * 处理一轮用户输入。{@code streaming=true} 时最终文本回答逐 chunk 打印到 stdout。
     */
    public String run(String userInput, boolean streaming) throws IOException {
        history.add(Message.user(userInput));
        AgentBudget budget = AgentBudget.defaults();

        while (true) {
            AgentBudget.ExitReason exitReason = budget.check();
            if (exitReason != AgentBudget.ExitReason.WITHIN_BUDGET) {
                return budget.describeExit(exitReason);
            }

            budget.beginIteration();
            boolean[] prefixPrinted = {false};
            ChatResponse response = streaming
                    ? llm.chat(history, registry.getToolDefinitions(), contentDelta -> {
                        if (!prefixPrinted[0]) {
                            System.out.print("assistant> ");
                            prefixPrinted[0] = true;
                        }
                        System.out.print(contentDelta);
                    })
                    : llm.chat(history, registry.getToolDefinitions());
            recordTokens(response);

            //如果响应有工具调用，则执行工具
            if (response.hasToolCalls()) {
                //将响应添加到历史记录中
                history.add(Message.assistant(response.content(), response.toolCalls()));
                for (ToolCall tc : response.toolCalls()) {
                    String result = registry.executeTool(
                            tc.function().name(),
                            tc.function().arguments()
                    );
                    history.add(Message.tool(tc.id(), result));
                    printToolExecution(tc.function().name(), result);
                }
                budget.recordToolCalls(response.toolCalls());
                continue;
            }

            String answer = response.content() != null ? response.content() : "";
            history.add(Message.assistant(answer));
            if (streaming && prefixPrinted[0]) {
                System.out.println();
            }
            return answer;
        }
    }

    private static void printToolExecution(String toolName, String result) {
        String preview = result == null ? "" : result.replace('\n', ' ').trim();
        if (preview.length() > 120) {
            preview = preview.substring(0, 120) + "...";
        }
        System.out.printf("  [tool] %s → %s%n", toolName, preview);
    }

    /** 返回当前会话上下文状态（history 条数、累计 token、模型名）。 */
    public String getContextStatus() {
        return String.format(
                "history: %d 条 | token: in=%d out=%d total=%d | 模型: %s",
                history.size(),
                totalInputTokens,
                totalOutputTokens,
                totalInputTokens + totalOutputTokens,
                llm.getModelName()
        );
    }

    /** 清空对话历史，保留 system 消息；token 统计一并重置。 */
    public void clearHistory() {
        Message systemMsg = history.get(0);
        history.clear();
        history.add(systemMsg);
        totalInputTokens = 0;
        totalOutputTokens = 0;
    }

    private void recordTokens(ChatResponse response) {
        totalInputTokens += Math.max(0, response.inputTokens());
        totalOutputTokens += Math.max(0, response.outputTokens());
    }

    /** 供测试或调试查看当前 history */
    List<Message> getHistory() {
        return List.copyOf(history);
    }
}
