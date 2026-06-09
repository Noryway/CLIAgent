package com.cliagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * 注册表内部的工具定义：元数据 + 执行逻辑。
 * 发给 LLM 时通过 {@link ToolRegistry#getToolDefinitions()} 转为 {@link com.cliagent.llm.LlmClient.Tool}。
 */
public record Tool(
        String name,//工具名称
        String description,//工具描述
        JsonNode parameters, //发给LLM的参数
        ToolExecutor executor //工具执行器
) {
}
