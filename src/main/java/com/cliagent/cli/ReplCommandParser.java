package com.cliagent.cli;

/**
 * REPL 命令解析：区分对话输入与本地斜杠命令。
 */
public final class ReplCommandParser {

    public enum CommandType {
        CHAT,//对话
        EXIT,//退出
        CLEAR,//清空对话历史
        HELP,//显示帮助
        CONTEXT,//查看上下文状态
        UNKNOWN//未知命令
    }

    /**
     * 解析命令
     * @param type 命令类型
     * @param payload 命令参数
     * @return 解析后的命令
     */
    public record ParsedCommand(CommandType type, String payload) {
    }

    private ReplCommandParser() {
    }

    /**
     * 解析命令
     * @param input 输入命令
     * @return 解析后的命令
     */
    public static ParsedCommand parse(String input) {
        //如果输入为空，则返回退出命令
        if (input == null) {
            return new ParsedCommand(CommandType.EXIT, null);
        }

        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return new ParsedCommand(CommandType.CHAT, "");
        }

        //如果输入为退出命令，则返回退出命令
        if (isExit(trimmed)) {
            return new ParsedCommand(CommandType.EXIT, null);
        }
        if (isClear(trimmed)) {
            return new ParsedCommand(CommandType.CLEAR, null);
        }
        if (isHelp(trimmed)) {
            return new ParsedCommand(CommandType.HELP, null);
        }
        if (isContext(trimmed)) {
            return new ParsedCommand(CommandType.CONTEXT, null);
        }
        if (trimmed.startsWith("/")) {
            return new ParsedCommand(CommandType.UNKNOWN, trimmed);
        }

        return new ParsedCommand(CommandType.CHAT, trimmed);
    }

    public static String formatHelp() {
        return """
                可用命令：
                  exit, quit, /exit, /quit   退出程序
                  clear, /clear              清空对话历史（保留 system）
                  help, /help                显示此帮助
                  context, /context          查看 history 与 token 统计
                  其他输入                   发送给 Agent（进入 ReAct 循环）
                """;
    }

    private static boolean isExit(String trimmed) {
        return equalsIgnoreCase(trimmed, "exit")
                || equalsIgnoreCase(trimmed, "quit")
                || equalsIgnoreCase(trimmed, "/exit")
                || equalsIgnoreCase(trimmed, "/quit");
    }

    private static boolean isClear(String trimmed) {
        return equalsIgnoreCase(trimmed, "clear")
                || equalsIgnoreCase(trimmed, "/clear");
    }

    private static boolean isHelp(String trimmed) {
        return equalsIgnoreCase(trimmed, "help")
                || equalsIgnoreCase(trimmed, "/help");
    }

    private static boolean isContext(String trimmed) {
        return equalsIgnoreCase(trimmed, "context")
                || equalsIgnoreCase(trimmed, "/context");
    }

    private static boolean equalsIgnoreCase(String value, String expected) {
        return expected.equalsIgnoreCase(value);
    }
}
