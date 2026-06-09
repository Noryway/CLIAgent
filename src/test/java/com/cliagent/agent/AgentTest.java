package com.cliagent.agent;

import com.cliagent.llm.LlmClient;
import com.cliagent.llm.LlmClient.ChatResponse;
import com.cliagent.llm.LlmClient.Message;
import com.cliagent.llm.LlmClient.Tool;
import com.cliagent.llm.LlmClient.ToolCall;
import com.cliagent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTest {

    @Test
    void agentStartsWithSystemMessage() {
        Agent agent = new Agent(new SingleReplyLlm("hello"), new ToolRegistry());
        assertEquals("system", agent.getHistory().get(0).role());
    }

    @Test
    void runReturnsDirectAnswerWithoutTools() throws IOException {
        Agent agent = new Agent(new SingleReplyLlm("你好，我是 CLIAgent"), new ToolRegistry());
        String answer = agent.run("你好");

        assertEquals("你好，我是 CLIAgent", answer);
        assertEquals(3, agent.getHistory().size());
        assertEquals("assistant", agent.getHistory().get(2).role());
    }

    @Test
    void multipleRunsAccumulateHistory() throws IOException {
        Agent agent = new Agent(new SingleReplyLlm("第一轮"), new ToolRegistry());
        agent.run("我叫小明");
        agent.run("1+1等于几");

        List<Message> history = agent.getHistory();
        assertEquals(5, history.size());
        assertEquals("system", history.get(0).role());
        assertEquals("user", history.get(1).role());
        assertEquals("assistant", history.get(2).role());
        assertEquals("user", history.get(3).role());
        assertEquals("assistant", history.get(4).role());
    }

    @Test
    void clearHistoryForgotsPriorConversation() throws IOException {
        Agent agent = new Agent(new MemoryCheckLlm(), new ToolRegistry());
        agent.run("我叫小明");
        assertEquals("你叫小明。", agent.run("我叫什么？"));

        agent.clearHistory();
        assertEquals("我不知道你的名字。", agent.run("我叫什么？"));
    }

    @Test
    void clearHistoryKeepsOnlySystemMessage() throws IOException {
        Agent agent = new Agent(new SingleReplyLlm("你好"), new ToolRegistry());
        agent.run("我叫小明");

        assertTrue(agent.getHistory().size() > 1);

        agent.clearHistory();

        List<Message> history = agent.getHistory();
        assertEquals(1, history.size());
        assertEquals("system", history.get(0).role());
    }

    @Test
    void reactLoopExecutesToolThenReturnsFinalAnswer() throws IOException {
        Agent agent = new Agent(new TwoRoundLlm(), new ToolRegistry());
        String answer = agent.run("列出当前目录");

        assertEquals("已根据工具结果完成回答。", answer);
        List<Message> history = agent.getHistory();
        assertEquals("system", history.get(0).role());
        assertEquals("user", history.get(1).role());
        assertEquals("assistant", history.get(2).role());
        assertTrue(history.get(2).toolCalls() != null && !history.get(2).toolCalls().isEmpty());
        assertEquals("tool", history.get(3).role());
        assertEquals("call_test_1", history.get(3).toolCallId());
        assertTrue(history.get(3).content().startsWith("目录内容"));
        assertEquals("assistant", history.get(4).role());
    }

    /** 根据 history 里是否出现过「小明」决定能否回答名字 */
    private static final class MemoryCheckLlm implements LlmClient {
        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) {
            boolean knowsName = messages.stream()
                    .anyMatch(m -> "user".equals(m.role())
                            && m.content() != null
                            && m.content().contains("小明"));
            String reply = knowsName ? "你叫小明。" : "我不知道你的名字。";
            return new ChatResponse("assistant", reply, List.of(), 10, 5);
        }

        @Override
        public String getModelName() {
            return "stub-memory";
        }
    }

    /** 单轮：直接返回文本，无 tool_calls */
    private static final class SingleReplyLlm implements LlmClient {
        private final String reply;

        SingleReplyLlm(String reply) {
            this.reply = reply;
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) {
            return new ChatResponse("assistant", reply, List.of(), 10, 5);
        }

        @Override
        public String getModelName() {
            return "stub-single";
        }
    }

    /** 两轮：第 1 轮 tool_calls，第 2 轮纯文本 */
    private static final class TwoRoundLlm implements LlmClient {
        private int calls = 0;

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) {
            calls++;
            if (calls == 1) {
                assertEquals("user", messages.get(messages.size() - 1).role());
                ToolCall tc = new ToolCall(
                        "call_test_1",
                        new ToolCall.Function("list_dir", "{\"path\":\".\"}")
                );
                return new ChatResponse("assistant", null, List.of(tc), 10, 5);
            }
            assertEquals("tool", messages.get(messages.size() - 1).role());
            return new ChatResponse("assistant", "已根据工具结果完成回答。", List.of(), 20, 8);
        }

        @Override
        public String getModelName() {
            return "stub-react";
        }
    }
}
