package com.cliagent;

import com.cliagent.config.EnvConfig;
import com.cliagent.llm.DeepSeekClient;
import com.cliagent.llm.LlmClient;
import com.cliagent.llm.LlmClient.ChatResponse;
import com.cliagent.llm.LlmClient.Message;
import com.cliagent.llm.LlmClient.Tool;
import com.cliagent.llm.LlmClient.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Day 1：通过 {@link DeepSeekClient} 调用 LLM，支持四种 message role 与 Tool Call 解析。
 *
 * <p>用法：
 * <pre>
 *   java -jar CLIAgent.jar "你好"
 *   java -jar CLIAgent.jar --demo-tool "现在几点？"
 * </pre>
 */
public class Main {

    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        String apiKey = EnvConfig.require("DEEPSEEK_API_KEY");
        if (apiKey == null) {
            printKeyHelp();
            System.exit(1);
        }

        String model = EnvConfig.get("DEEPSEEK_MODEL", DEFAULT_MODEL);
        DeepSeekClient client = new DeepSeekClient(apiKey, model);

        boolean demoTool = args.length > 0 && "--demo-tool".equals(args[0]);
        //解析用户输入
        String userInput = resolveUserInput(args, demoTool);

        //创建消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system("你是 CLIAgent，一个简洁的 Java 编程助手。"));
        messages.add(Message.user(userInput));

        List<Tool> tools = demoTool ? List.of(demoGetTimeTool()) : List.of();

        System.out.println("you> " + userInput);
        if (demoTool) {
            System.out.println("(demo: 已注册 get_current_time 工具，观察 LLM 是否返回 tool_calls)");
        }

        try {
            ChatResponse response = client.chat(messages, tools);
            printResponse(response);
        } catch (IOException e) {
            System.err.println("❌ 调用失败: " + e.getMessage());
            System.exit(2);
        }
    }

    private static String resolveUserInput(String[] args, boolean demoTool) {
        if (args.length == 0) {
            return "你好，请用一句话介绍自己";
        }
        if (demoTool) {
            if (args.length >= 2) {
                return String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            }
            return "现在几点？请调用工具获取时间。";
        }
        return String.join(" ", args);
    }

    private static Tool demoGetTimeTool() {
        ObjectNode parameters = MAPPER.createObjectNode();
        parameters.put("type", "object");
        parameters.putObject("properties");
        parameters.putArray("required");
        return new Tool(
                "get_current_time",
                "获取当前日期和时间（ISO-8601 格式）",
                parameters
        );
    }

    private static void printResponse(ChatResponse response) {
        if (response.hasToolCalls()) {
            System.out.println("assistant> (请求调用工具，Day 2 将在 ToolRegistry 中真正执行)");
            for (ToolCall tc : response.toolCalls()) {
                System.out.printf("  tool_call id=%s name=%s args=%s%n",
                        tc.id(), tc.function().name(), tc.function().arguments());
            }
            if (response.content() != null && !response.content().isBlank()) {
                System.out.println("  附带文本: " + response.content());
            }
        } else {
            System.out.println("assistant> " + response.content());
        }
        System.out.printf("tokens: input=%d output=%d%n",
                response.inputTokens(), response.outputTokens());
    }

    private static void printKeyHelp() {
        System.err.println("❌ 未找到 DEEPSEEK_API_KEY");
        System.err.println();
        System.err.println("请按下面任一方式提供：");
        System.err.println("  1. 在项目根创建 .env：cp .env.example .env，再编辑填入真实 key");
        System.err.println("  2. 设置环境变量：    export DEEPSEEK_API_KEY=sk-xxx");
        System.err.println("  3. JVM 系统属性：    java -DDEEPSEEK_API_KEY=sk-xxx -jar ...");
    }
}
