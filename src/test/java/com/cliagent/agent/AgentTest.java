package com.cliagent.agent;

import com.cliagent.llm.LlmClient;
import com.cliagent.llm.LlmClient.ChatResponse;
import com.cliagent.llm.LlmClient.Message;
import com.cliagent.llm.LlmClient.Tool;
import com.cliagent.llm.LlmClient.ToolCall;
import com.cliagent.llm.StreamListener;
import com.cliagent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
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
    void accumulatesTokensAfterRun() throws IOException {
        Agent agent = new Agent(new SingleReplyLlm("你好"), new ToolRegistry());
        agent.run("你好");

        assertEquals(
                "history: 3 条 | token: in=10 out=5 total=15 | 模型: stub-single",
                agent.getContextStatus()
        );
    }

    @Test
    void accumulatesTokensAcrossMultipleRuns() throws IOException {
        Agent agent = new Agent(new SingleReplyLlm("回复"), new ToolRegistry());
        agent.run("第一轮");
        agent.run("第二轮");

        assertEquals(
                "history: 5 条 | token: in=20 out=10 total=30 | 模型: stub-single",
                agent.getContextStatus()
        );
    }

    @Test
    void clearHistoryResetsTokenCounters() throws IOException {
        Agent agent = new Agent(new SingleReplyLlm("你好"), new ToolRegistry());
        agent.run("你好");
        agent.clearHistory();

        assertEquals(1, agent.getHistory().size());
        assertEquals(
                "history: 1 条 | token: in=0 out=0 total=0 | 模型: stub-single",
                agent.getContextStatus()
        );
    }

    @Test
    void reactLoopAccumulatesTokensFromMultipleChatCalls() throws IOException {
        Agent agent = new Agent(new TwoRoundLlm(), new ToolRegistry());
        agent.run("列出当前目录");

        assertEquals(
                "history: 5 条 | token: in=30 out=13 total=43 | 模型: stub-react",
                agent.getContextStatus()
        );
    }

    @Test
    void stagnationStopsRepeatedIdenticalToolCalls() throws IOException {
        Agent agent = new Agent(new RepeatedToolLlm(), new ToolRegistry());
        String answer = agent.run("一直列出当前目录");

        assertEquals("检测到连续 3 轮重复的工具调用，疑似死循环，已停止。", answer);
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

    @Test
    void runStreamingUsesStreamingChat() throws IOException {
        StreamingLlm llm = new StreamingLlm();
        Agent agent = new Agent(llm, new ToolRegistry());

        String answer = agent.run("你好", true);

        assertEquals("Hello", answer);
        assertTrue(llm.streamingChatUsed);
        assertEquals(List.of("He", "llo"), llm.contentDeltas);
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

    /** 流式：三参数 chat 逐 delta 回调 */
    private static final class StreamingLlm implements LlmClient {
        private boolean streamingChatUsed;
        private final List<String> contentDeltas = new ArrayList<>();

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) {
            return new ChatResponse("assistant", "Hello", List.of(), 1, 1);
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener)
                throws IOException {
            streamingChatUsed = true;
            listener.onContentDelta("He");
            contentDeltas.add("He");
            listener.onContentDelta("llo");
            contentDeltas.add("llo");
            return new ChatResponse("assistant", "Hello", List.of(), 1, 1);
        }

        @Override
        public String getModelName() {
            return "stub-stream";
        }
    }

    /** 每轮都返回相同的 list_dir tool_call，用于触发停滞检测 */
    private static final class RepeatedToolLlm implements LlmClient {
        private int calls = 0;

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) {
            calls++;
            ToolCall tc = new ToolCall(
                    "call_repeat_" + calls,
                    new ToolCall.Function("list_dir", "{\"path\":\".\"}")
            );
            return new ChatResponse("assistant", null, List.of(tc), 10, 5);
        }

        @Override
        public String getModelName() {
            return "stub-stagnation";
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
