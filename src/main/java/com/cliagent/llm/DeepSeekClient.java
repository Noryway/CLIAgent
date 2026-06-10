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
import okio.BufferedSource;

import java.io.IOException;
import java.io.StringReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * DeepSeek API 客户端（OpenAI 兼容协议，支持非流式与 SSE 流式）。
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
        ObjectNode body = buildRequestBody(messages, tools, false);
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody respBody = response.body();
            String raw = respBody == null ? "" : respBody.string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + raw);
            }
            return parseResponse(raw);
        }
    }

    @Override
    public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener)
            throws IOException {
        StreamListener streamListener = listener == null ? StreamListener.NO_OP : listener;
        ObjectNode body = buildRequestBody(messages, tools, true);
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody respBody = response.body();
            if (!response.isSuccessful()) {
                String raw = respBody == null ? "" : respBody.string();
                throw new IOException("HTTP " + response.code() + ": " + raw);
            }
            if (respBody == null) {
                throw new IOException("API 返回空响应体");
            }
            return parseStreamSource(respBody.source(), streamListener);
        }
    }

    @Override
    public String getModelName() {
        return model;
    }

    /** 供单元测试验证序列化结果（默认非流式）。 */
    ObjectNode buildRequestBody(List<Message> messages, List<Tool> tools) {
        return buildRequestBody(messages, tools, false);
    }

    //构建请求体
    ObjectNode buildRequestBody(List<Message> messages, List<Tool> tools, boolean stream) {
        ObjectNode requestBody = MAPPER.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("stream", stream);

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

    /** 供单元测试：解析 Mock SSE 文本并触发 listener。 */
    ChatResponse parseStreamResponse(String sseBody, StreamListener listener) throws IOException {
        StreamAccumulator acc = new StreamAccumulator(listener == null ? StreamListener.NO_OP : listener);
        try (BufferedReader reader = new BufferedReader(new StringReader(sseBody))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (acc.consumeLine(line)) {
                    break;
                }
            }
        }
        return acc.toChatResponse();
    }

    private ChatResponse parseStreamSource(BufferedSource source, StreamListener listener) throws IOException {
        StreamAccumulator acc = new StreamAccumulator(listener);
        while (!source.exhausted()) {
            String line = source.readUtf8Line();
            if (line == null) {
                break;
            }
            if (acc.consumeLine(line)) {
                break;
            }
        }
        return acc.toChatResponse();
    }

    private static final class StreamAccumulator {
        private final StreamListener listener;
        private String role = "assistant";
        private final StringBuilder content = new StringBuilder();
        private final List<ToolCallAccumulator> toolAccumulators = new ArrayList<>();
        private int inputTokens;
        private int outputTokens;

        StreamAccumulator(StreamListener listener) {
            this.listener = listener;
        }

        boolean consumeLine(String line) throws IOException {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.startsWith("data:")) {
                return false;
            }

            String payload = trimmed.substring("data:".length()).trim();
            if (payload.isEmpty()) {
                return false;
            }
            if ("[DONE]".equals(payload)) {
                return true;
            }

            JsonNode root = MAPPER.readTree(payload);

            JsonNode usage = root.path("usage");
            if (!usage.isMissingNode()) {
                inputTokens = usage.path("prompt_tokens").asInt(inputTokens);
                outputTokens = usage.path("completion_tokens").asInt(outputTokens);
            }

            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return false;
            }

            JsonNode delta = choices.get(0).path("delta");
            if (delta.isMissingNode() || delta.isNull()) {
                delta = choices.get(0).path("message");
            }
            if (delta.isMissingNode() || delta.isNull()) {
                return false;
            }

            String deltaRole = delta.path("role").asText("");
            if (!deltaRole.isEmpty()) {
                role = deltaRole;
            }

            String contentDelta = delta.path("content").asText("");
            if (!contentDelta.isEmpty()) {
                content.append(contentDelta);
                listener.onContentDelta(contentDelta);
            }

            mergeToolCallDeltas(toolAccumulators, delta.path("tool_calls"));
            return false;
        }

        ChatResponse toChatResponse() {
            List<ToolCall> toolCalls = buildToolCalls(toolAccumulators);
            String text = content.isEmpty() ? null : content.toString();
            return new ChatResponse(role, text, toolCalls, inputTokens, outputTokens);
        }
    }

    private static void mergeToolCallDeltas(List<ToolCallAccumulator> accumulators, JsonNode toolCallsNode) {
        if (toolCallsNode == null || !toolCallsNode.isArray()) {
            return;
        }
        for (JsonNode tc : toolCallsNode) {
            int index = tc.path("index").asInt(accumulators.size());
            while (accumulators.size() <= index) {
                accumulators.add(new ToolCallAccumulator());
            }
            ToolCallAccumulator acc = accumulators.get(index);
            String id = tc.path("id").asText("");
            if (!id.isEmpty()) {
                acc.id = id;
            }
            JsonNode function = tc.path("function");
            String name = function.path("name").asText("");
            if (!name.isEmpty()) {
                acc.name.append(name);
            }
            String arguments = function.path("arguments").asText("");
            if (!arguments.isEmpty()) {
                acc.arguments.append(arguments);
            }
        }
    }

    private static List<ToolCall> buildToolCalls(List<ToolCallAccumulator> accumulators) {
        if (accumulators.isEmpty()) {
            return List.of();
        }
        List<ToolCall> toolCalls = new ArrayList<>();
        for (ToolCallAccumulator acc : accumulators) {
            if (acc.id == null || acc.id.isBlank()) {
                continue;
            }
            String name = acc.name.toString();
            if (name.isBlank()) {
                continue;
            }
            toolCalls.add(new ToolCall(acc.id, new ToolCall.Function(name, acc.arguments.toString())));
        }
        return List.copyOf(toolCalls);
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

    private static final class ToolCallAccumulator {
        private String id;
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();
    }
}
