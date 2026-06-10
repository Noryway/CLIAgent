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
        MEMORY_SAVE,//保存长期记忆
        MEMORY_STATUS,//长期记忆摘要
        MEMORY_LIST,//列出长期记忆
        MEMORY_SEARCH,//搜索长期记忆
        MEMORY_CLEAR,//清空当前项目长期记忆
        PLAN,//Plan-and-Execute 复杂任务
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
        if (isMemorySave(trimmed)) {
            return parseMemorySave(trimmed);
        }
        if (isMemoryCommand(trimmed)) {
            return parseMemoryCommand(trimmed);
        }
        if (isPlan(trimmed)) {
            return parsePlan(trimmed);
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
                  clear, /clear              清空对话历史（保留 system，不清长期记忆）
                  help, /help                显示此帮助
                  context, /context          查看 history 与 token 统计
                  /save <事实>               保存当前项目的长期记忆
                  /memory                    查看长期记忆摘要
                  /memory list               列出当前项目长期记忆
                  /memory search <关键词>    搜索长期记忆
                  /memory clear              清空当前项目长期记忆（不影响 /clear）
                  /plan <复杂任务>           Plan-and-Execute：拆解并逐步执行
                  其他输入                   发送给 Agent（进入 ReAct 循环）
                """;
    }

    private static ParsedCommand parsePlan(String trimmed) {
        if (equalsIgnoreCase(trimmed, "/plan")) {
            return new ParsedCommand(CommandType.PLAN, "");
        }
        return new ParsedCommand(CommandType.PLAN, trimmed.substring(6).trim());
    }

    private static ParsedCommand parseMemorySave(String trimmed) {
        if (equalsIgnoreCase(trimmed, "/save")) {
            return new ParsedCommand(CommandType.MEMORY_SAVE, "");
        }
        return new ParsedCommand(CommandType.MEMORY_SAVE, trimmed.substring(6).trim());
    }

    private static ParsedCommand parseMemoryCommand(String trimmed) {
        if (equalsIgnoreCase(trimmed, "/memory")) {
            return new ParsedCommand(CommandType.MEMORY_STATUS, null);
        }
        if (equalsIgnoreCase(trimmed, "/memory list")) {
            return new ParsedCommand(CommandType.MEMORY_LIST, null);
        }
        if (equalsIgnoreCase(trimmed, "/memory clear")) {
            return new ParsedCommand(CommandType.MEMORY_CLEAR, null);
        }
        if (startsWithIgnoreCase(trimmed, "/memory search ")) {
            return new ParsedCommand(CommandType.MEMORY_SEARCH, trimmed.substring(15).trim());
        }
        if (equalsIgnoreCase(trimmed, "/memory search")) {
            return new ParsedCommand(CommandType.MEMORY_SEARCH, "");
        }
        return new ParsedCommand(CommandType.UNKNOWN, trimmed);
    }

    private static boolean isMemorySave(String trimmed) {
        return equalsIgnoreCase(trimmed, "/save")
                || startsWithIgnoreCase(trimmed, "/save ");
    }

    private static boolean isMemoryCommand(String trimmed) {
        return equalsIgnoreCase(trimmed, "/memory")
                || startsWithIgnoreCase(trimmed, "/memory ");
    }

    private static boolean isPlan(String trimmed) {
        return equalsIgnoreCase(trimmed, "/plan")
                || startsWithIgnoreCase(trimmed, "/plan ");
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

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
