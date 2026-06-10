package com.cliagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final DeepSeekClient client = new DeepSeekClient("test-key", "deepseek-chat");

    @Test
    void serializesFourMessageRoles() {
        LlmClient.ToolCall tc = new LlmClient.ToolCall(
                "call_abc",
                new LlmClient.ToolCall.Function("read_file", "{\"path\":\"README.md\"}")
        );
        List<LlmClient.Message> messages = List.of(
                LlmClient.Message.system("你是助手"),
                LlmClient.Message.user("读 README"),
                LlmClient.Message.assistant("好的", List.of(tc)),
                LlmClient.Message.tool("call_abc", "# CLIAgent")
        );

        ObjectNode body = client.buildRequestBody(messages, List.of());
        assertEquals("deepseek-chat", body.path("model").asText());
        assertFalse(body.path("stream").asBoolean(true));

        var msgs = body.path("messages");
        assertEquals(4, msgs.size());
        assertEquals("system", msgs.get(0).path("role").asText());
        assertEquals("user", msgs.get(1).path("role").asText());
        assertEquals("assistant", msgs.get(2).path("role").asText());
        assertTrue(msgs.get(2).path("tool_calls").isArray());
        assertEquals("call_abc", msgs.get(2).path("tool_calls").get(0).path("id").asText());
        // arguments 必须是字符串
        assertTrue(msgs.get(2).path("tool_calls").get(0).path("function").path("arguments").isTextual());
        assertEquals("tool", msgs.get(3).path("role").asText());
        assertEquals("call_abc", msgs.get(3).path("tool_call_id").asText());
    }

    @Test
    void serializesToolDefinitions() {
        ObjectNode parameters = MAPPER.createObjectNode();
        parameters.put("type", "object");
        parameters.putObject("properties").putObject("path")
                .put("type", "string")
                .put("description", "文件路径");
        parameters.putArray("required").add("path");

        LlmClient.Tool tool = new LlmClient.Tool("read_file", "读取文件", parameters);
        ObjectNode body = client.buildRequestBody(
                List.of(LlmClient.Message.user("读 a.txt")),
                List.of(tool)
        );

        var tools = body.path("tools");
        assertEquals(1, tools.size());
        assertEquals("function", tools.get(0).path("type").asText());
        assertEquals("read_file", tools.get(0).path("function").path("name").asText());
    }

    @Test
    void parsesToolCallsFromResponse() throws Exception {
        String responseJson = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [{
                        "id": "call_xyz",
                        "type": "function",
                        "function": {
                          "name": "get_current_time",
                          "arguments": "{}"
                        }
                      }]
                    },
                    "finish_reason": "tool_calls"
                  }],
                  "usage": { "prompt_tokens": 10, "completion_tokens": 5 }
                }
                """;

        LlmClient.ChatResponse resp = client.parseResponse(responseJson);

        assertTrue(resp.hasToolCalls());
        assertEquals("get_current_time", resp.toolCalls().get(0).function().name());
        assertEquals("{}", resp.toolCalls().get(0).function().arguments());
        assertEquals(10, resp.inputTokens());
        assertEquals(5, resp.outputTokens());
    }

    @Test
    void buildRequestBodySupportsStreamFlag() {
        ObjectNode body = client.buildRequestBody(
                List.of(LlmClient.Message.user("hi")),
                List.of(),
                true
        );
        assertTrue(body.path("stream").asBoolean(false));
    }

    @Test
    void parsesStreamResponseContentDeltas() throws Exception {
        String sse = """
                data: {"choices":[{"delta":{"role":"assistant","content":"Hello"}}]}

                data: {"choices":[{"delta":{"content":" world"}}]}

                data: {"usage":{"prompt_tokens":3,"completion_tokens":2}}

                data: [DONE]

                """;

        LlmClient.ChatResponse resp = client.parseStreamResponse(sse, StreamListener.NO_OP);

        assertEquals("assistant", resp.role());
        assertEquals("Hello world", resp.content());
        assertFalse(resp.hasToolCalls());
        assertEquals(3, resp.inputTokens());
        assertEquals(2, resp.outputTokens());
    }

    @Test
    void parseStreamResponseInvokesListener() throws Exception {
        String sse = """
                data: {"choices":[{"delta":{"content":"Re"}}]}

                data: {"choices":[{"delta":{"content":"Act"}}]}

                data: [DONE]

                """;

        List<String> deltas = new ArrayList<>();
        client.parseStreamResponse(sse, deltas::add);

        assertEquals(List.of("Re", "Act"), deltas);
    }
}
