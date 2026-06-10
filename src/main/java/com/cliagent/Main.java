package com.cliagent;

import com.cliagent.agent.Agent;
import com.cliagent.cli.ReplCommandParser;
import com.cliagent.cli.ReplCommandParser.CommandType;
import com.cliagent.cli.ReplCommandParser.ParsedCommand;
import com.cliagent.config.EnvConfig;
import com.cliagent.llm.DeepSeekClient;
import com.cliagent.tool.ToolRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Day 8：REPL 多轮对话 + {@link ReplCommandParser}（含 /context）；有命令行参数时保持单次模式。
 *
 * <p>用法：
 * <pre>
 *   java -jar CLIAgent.jar                    # REPL 交互模式
 *   java -jar CLIAgent.jar "你好"              # 单次对话
 *   java -jar CLIAgent.jar "列出当前目录有哪些文件"
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

        //如果args为空，则进入REPL模式，否则执行单次对话
        if (args.length == 0) {
            runRepl(agent);
        } else {
            runOnce(agent, String.join(" ", args));
        }
    }
    //REPL模式
    private static void runRepl(Agent agent) {
        System.out.println("CLIAgent 已启动，输入 help 查看命令，exit 退出。");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("you> ");
            String input;
            try {
                input = reader.readLine();
            } catch (IOException e) {
                System.err.println("❌ 读取输入失败: " + e.getMessage());
                break;
            }

            ParsedCommand command = ReplCommandParser.parse(input);
            switch (command.type()) {
                case EXIT -> {
                    System.out.println("再见！");
                    return;
                }
                case CLEAR -> {
                    agent.clearHistory();
                    System.out.println("🗑️ 对话历史已清空。");
                }
                case HELP -> printReplHelp();
                case CONTEXT -> System.out.println(agent.getContextStatus());
                case UNKNOWN -> {
                    System.out.println("❌ 未知命令: " + command.payload());
                    printReplHelp();
                }
                case CHAT -> {
                    if (command.payload().isBlank()) {
                        continue;
                    }
                    try {
                        String answer = agent.run(command.payload());
                        System.out.println("assistant> " + answer);
                    } catch (IOException e) {
                        System.err.println("❌ 调用失败: " + e.getMessage());
                    }
                }
            }
        }
    }

    //单次对话
    private static void runOnce(Agent agent, String userInput) {
        System.out.println("you> " + userInput);

        try {
            String answer = agent.run(userInput);
            System.out.println("assistant> " + answer);
        } catch (IOException e) {
            System.err.println("❌ 调用失败: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void printReplHelp() {
        System.out.println(ReplCommandParser.formatHelp());
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
