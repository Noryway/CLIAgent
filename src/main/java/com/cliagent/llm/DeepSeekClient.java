package com.cliagent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * DeepSeek API 客户端（OpenAI 兼容协议，非流式）。
 */
public class DeepSeekClient implements LlmClient {

    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-chat";

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String model;

    public DeepSeekClient(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    public DeepSeekClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model != null && !model.isBlank() ? model : DEFAULT_MODEL;
    }

    @Override
    public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
        ObjectNode body = buildRequestBody(messages, tools);
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody respBody = response.body();//取出响应体
            String raw = respBody == null ? "" : respBody.string();//将响应体转换为字符串
            if (!response.isSuccessful()) {//如果响应不成功，则抛出异常
                throw new IOException("HTTP " + response.code() + ": " + raw);
            }
            return parseResponse(raw); //解析响应
        }
    }

    @Override
    public String getModelName() {
        return model;
    }

    /** 供单元测试验证序列化结果 */
    ObjectNode buildRequestBody(List<Message> messages, List<Tool> tools) {
        ObjectNode requestBody = MAPPER.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("stream", false);

        ArrayNode messagesArray = requestBody.putArray("messages");
        for (Message msg : messages) {
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", msg.role());
            if (msg.content() != null) {
                msgNode.put("content", msg.content());
            }

            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                ArrayNode toolCallsArray = msgNode.putArray("tool_calls");
                for (ToolCall tc : msg.toolCalls()) {
                    ObjectNode tcNode = toolCallsArray.addObject();
                    tcNode.put("id", tc.id());
                    tcNode.put("type", "function");
                    ObjectNode functionNode = tcNode.putObject("function");
                    functionNode.put("name", tc.function().name());
                    // arguments 必须是 JSON 字符串，不是嵌套对象
                    functionNode.put("arguments", tc.function().arguments());
                }
            }

            if (msg.toolCallId() != null) {
                msgNode.put("tool_call_id", msg.toolCallId());
            }
        }

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = requestBody.putArray("tools");
            for (Tool tool : tools) {
                ObjectNode toolNode = toolsArray.addObject();
                toolNode.put("type", "function");
                ObjectNode functionNode = toolNode.putObject("function");
                functionNode.put("name", tool.name());
                functionNode.put("description", tool.description());
                functionNode.set("parameters", tool.parameters());
            }
        }
        return requestBody;
    }

    /** 
     * OpenAI 兼容协议的响应格式
     * {
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
            "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 5
            }
        }
     * 
     * 
    */

    ChatResponse parseResponse(String raw) throws IOException {
        JsonNode root = MAPPER.readTree(raw);
        //path("choices"):获取choices节点
        //path(0):获取choices节点的第一个元素
        //path("message"):获取message节点
        JsonNode message = root.path("choices").path(0).path("message");
        //asText("assistant"):获取role节点的值，如果为空，则返回"assistant"
        String role = message.path("role").asText("assistant");
        String content = message.path("content").isNull() ? null : message.path("content").asText(null);
        //tool_calls
        List<ToolCall> toolCalls = parseToolCalls(message.path("tool_calls"));

        JsonNode usage = root.path("usage");
        int inputTokens = usage.path("prompt_tokens").asInt(0);
        int outputTokens = usage.path("completion_tokens").asInt(0);

        return new ChatResponse(role, content, toolCalls, inputTokens, outputTokens);
    }

    private static List<ToolCall> parseToolCalls(JsonNode toolCallsNode) {
        if (toolCallsNode == null || !toolCallsNode.isArray() || toolCallsNode.isEmpty()) {
            return List.of();
        }
        List<ToolCall> result = new ArrayList<>();
        for (JsonNode tc : toolCallsNode) {
            String id = tc.path("id").asText("");
            JsonNode fn = tc.path("function");
            String name = fn.path("name").asText("");
            // arguments 在协议里是字符串，兼容少数返回 object 的实现
            String arguments;
            JsonNode argsNode = fn.path("arguments");
            if (argsNode.isTextual()) {
                arguments = argsNode.asText("");
            } else if (argsNode.isObject() || argsNode.isArray()) {
                arguments = argsNode.toString();
            } else {
                arguments = "";
            }
            if (!id.isBlank() && !name.isBlank()) {
                result.add(new ToolCall(id, new ToolCall.Function(name, arguments)));
            }
        }
        return List.copyOf(result);
    }
}
