package com.cliagent;

import com.cliagent.agent.Agent;
import com.cliagent.config.EnvConfig;
import com.cliagent.llm.DeepSeekClient;
import com.cliagent.tool.ToolRegistry;

import java.io.IOException;

/**
 * Day 3：通过 {@link Agent} 运行 ReAct 循环，自动执行 ToolRegistry 中的内置工具。
 *
 * <p>用法：
 * <pre>
 *   java -jar CLIAgent.jar "你好"
 *   java -jar CLIAgent.jar "列出当前目录有哪些文件"
 *   java -jar CLIAgent.jar "读取 README.md 并用一句话总结"
 * </pre>
 */
public class Main {

    private static final String DEFAULT_MODEL = "deepseek-chat";

    public static void main(String[] args) {
        String apiKey = EnvConfig.require("DEEPSEEK_API_KEY");
        if (apiKey == null) {
            printKeyHelp();
            System.exit(1);
        }

        String model = EnvConfig.get("DEEPSEEK_MODEL", DEFAULT_MODEL);
        DeepSeekClient client = new DeepSeekClient(apiKey, model);
        ToolRegistry registry = new ToolRegistry();
        Agent agent = new Agent(client, registry);

        String userInput = resolveUserInput(args);
        System.out.println("you> " + userInput);

        try {
            String answer = agent.run(userInput);
            System.out.println("assistant> " + answer);
        } catch (IOException e) {
            System.err.println("❌ 调用失败: " + e.getMessage());
            System.exit(2);
        }
    }

    private static String resolveUserInput(String[] args) {
        if (args.length == 0) {
            return "你好，请用一句话介绍自己";
        }
        return String.join(" ", args);
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
