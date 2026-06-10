package com.cliagent.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;

/**
 * LLM 客户端抽象：封装 OpenAI 兼容协议的 chat + Tool Call。
 */
public interface LlmClient {

    ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException;

    /** 流式 chat；默认实现忽略 listener，走非流式 {@link #chat(List, List)}。 */
    default ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener)
            throws IOException {
        return chat(messages, tools);
    }

    String getModelName();

    /**
     * 对话消息，支持 system / user / assistant / tool 四种 role。
     * system: 系统消息，用于设置LLM的上下文
     * user: 用户消息，用于发送用户输入
     * assistant: 助手消息，用于接收LLM的响应
     * tool: 工具消息，用于调用工具
     */ 
    record Message(String role, String content, List<ToolCall> toolCalls, String toolCallId) {

        public static Message system(String content) {
            return new Message("system", content, null, null);
        }

        public static Message user(String content) {
            return new Message("user", content, null, null);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content, null, null);
        }

        public static Message assistant(String content, List<ToolCall> toolCalls) {
            return new Message("assistant", content, toolCalls, null);
        }

        public static Message tool(String toolCallId, String content) {
            return new Message("tool", content, null, toolCallId);
        }
    }

    record ToolCall(String id, Function function) {
        public record Function(String name, String arguments) {
        }
    }

    /** 传给 LLM 的工具定义（JSON Schema 在 parameters 里） */
    record Tool(String name, String description, JsonNode parameters) {
    }

    record ChatResponse(String role, String content, List<ToolCall> toolCalls,
                        int inputTokens, int outputTokens) {

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
