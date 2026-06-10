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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Day 9：显式 projectPath 沙箱 + {@link ReplCommandParser} REPL；有命令行参数时保持单次模式。
 *
 * <p>用法：
 * <pre>
 *   java -jar CLIAgent.jar                              # REPL，沙箱=当前目录
 *   java -jar CLIAgent.jar --cwd /path/to/project       # REPL，指定项目目录
 *   java -jar CLIAgent.jar "你好"                        # 单次对话
 *   java -jar CLIAgent.jar --stream "用三句话介绍 ReAct"
 * </pre>
 */
public class Main {

    private static final String DEFAULT_MODEL = "deepseek-chat";

    /** 剥离 {@code --cwd} / {@code --stream} 后的 CLI 参数、项目根与流式开关。 */
    record ParsedCliArgs(String projectPath, String[] promptArgs, boolean streaming) {
    }

    public static void main(String[] args) {
        String apiKey = EnvConfig.require("DEEPSEEK_API_KEY");
        if (apiKey == null) {
            printKeyHelp();
            System.exit(1);
        }

        ParsedCliArgs cli;
        try {
            cli = parseCliArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("❌ " + e.getMessage());
            System.exit(1);
            return;
        }

        String model = EnvConfig.get("DEEPSEEK_MODEL", DEFAULT_MODEL);
        DeepSeekClient client = new DeepSeekClient(apiKey, model);
        ToolRegistry registry = new ToolRegistry();
        registry.setProjectPath(cli.projectPath());
        Agent agent = new Agent(client, registry);

        if (cli.promptArgs().length == 0) {
            runRepl(agent, cli.projectPath(), cli.streaming());
        } else {
            runOnce(agent, String.join(" ", cli.promptArgs()), cli.streaming());
        }
    }

    /**
     * 解析命令行：提取 {@code --cwd} / {@code --stream}，其余参数作为单次模式的 user prompt。
     * 未指定 {@code --cwd} 时，沙箱根为 {@code user.dir} 的绝对规范化路径。
     */
    static ParsedCliArgs parseCliArgs(String[] args) {
        String cwdOverride = null;
        boolean streaming = false;
        List<String> promptArgs = new ArrayList<>();
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if ("--cwd".equals(args[i])) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--cwd 需要指定目录路径");
                    }
                    cwdOverride = args[++i];
                } else if ("--stream".equals(args[i])) {
                    streaming = true;
                } else {
                    promptArgs.add(args[i]);
                }
            }
        }
        String projectPath = resolveProjectPath(cwdOverride);
        return new ParsedCliArgs(projectPath, promptArgs.toArray(String[]::new), streaming);
    }

    static String resolveProjectPath(String cwdOverride) {
        Path root = cwdOverride == null || cwdOverride.isBlank()
                ? Path.of(".").toAbsolutePath().normalize()
                : Path.of(cwdOverride).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("项目目录不存在或不是目录: " + root);
        }
        return root.toString();
    }

    //REPL模式
    private static void runRepl(Agent agent, String projectPath, boolean streaming) {
        System.out.println("CLIAgent 已启动，项目目录: " + projectPath);
        if (streaming) {
            System.out.println("流式输出已启用（--stream）。");
        }
        System.out.println("输入 help 查看命令，exit 退出。");
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
                        if (streaming) {
                            agent.run(command.payload(), true);
                        } else {
                            String answer = agent.run(command.payload());
                            System.out.println("assistant> " + answer);
                        }
                    } catch (IOException e) {
                        System.err.println("❌ 调用失败: " + e.getMessage());
                    }
                }
            }
        }
    }

    //单次对话
    private static void runOnce(Agent agent, String userInput, boolean streaming) {
        System.out.println("you> " + userInput);

        try {
            if (streaming) {
                agent.run(userInput, true);
            } else {
                String answer = agent.run(userInput);
                System.out.println("assistant> " + answer);
            }
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
