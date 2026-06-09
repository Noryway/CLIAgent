package com.cliagent.tool;

import java.util.Map;

/**
 * 工具执行函数：把解析后的参数 Map 转成给 LLM 看的字符串结果。
 */
@FunctionalInterface
public interface ToolExecutor {

    String execute(Map<String, String> args) throws Exception;
}
