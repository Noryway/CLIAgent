package com.cliagent.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTest {

    @Test
    void systemMessageHasCorrectRole() {
        LlmClient.Message msg = LlmClient.Message.system("你是助手");
        assertEquals("system", msg.role());
        assertEquals("你是助手", msg.content());
        assertNull(msg.toolCalls());
        assertNull(msg.toolCallId());
    }

    @Test
    void userMessageHasCorrectRole() {
        LlmClient.Message msg = LlmClient.Message.user("你好");
        assertEquals("user", msg.role());
        assertEquals("你好", msg.content());
    }

    @Test
    void assistantWithToolCalls() {
        LlmClient.ToolCall tc = new LlmClient.ToolCall(
                "call_1",
                new LlmClient.ToolCall.Function("read_file", "{\"path\":\"a.txt\"}")
        );
        LlmClient.Message msg = LlmClient.Message.assistant("我来读文件", List.of(tc));
        assertEquals("assistant", msg.role());
        assertEquals(1, msg.toolCalls().size());
        assertEquals("call_1", msg.toolCalls().get(0).id());
    }

    @Test
    void toolMessageRequiresToolCallId() {
        LlmClient.Message msg = LlmClient.Message.tool("call_1", "文件内容...");
        assertEquals("tool", msg.role());
        assertEquals("call_1", msg.toolCallId());
        assertEquals("文件内容...", msg.content());
    }
}
