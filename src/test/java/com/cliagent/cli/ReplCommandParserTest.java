package com.cliagent.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplCommandParserTest {

    @Test
    void parsesExitCommands() {
        assertEquals(ReplCommandParser.CommandType.EXIT, ReplCommandParser.parse("exit").type());
        assertEquals(ReplCommandParser.CommandType.EXIT, ReplCommandParser.parse("quit").type());
        assertEquals(ReplCommandParser.CommandType.EXIT, ReplCommandParser.parse("/exit").type());
        assertEquals(ReplCommandParser.CommandType.EXIT, ReplCommandParser.parse("  /QUIT  ").type());
        assertEquals(ReplCommandParser.CommandType.EXIT, ReplCommandParser.parse(null).type());
    }

    @Test
    void parsesClearCommands() {
        assertEquals(ReplCommandParser.CommandType.CLEAR, ReplCommandParser.parse("clear").type());
        assertEquals(ReplCommandParser.CommandType.CLEAR, ReplCommandParser.parse("/clear").type());
        assertEquals(ReplCommandParser.CommandType.CLEAR, ReplCommandParser.parse("  CLEAR  ").type());
    }

    @Test
    void parsesHelpCommands() {
        assertEquals(ReplCommandParser.CommandType.HELP, ReplCommandParser.parse("help").type());
        assertEquals(ReplCommandParser.CommandType.HELP, ReplCommandParser.parse("/help").type());
        assertEquals(ReplCommandParser.CommandType.HELP, ReplCommandParser.parse("  /HELP  ").type());
    }

    @Test
    void parsesContextCommands() {
        assertEquals(ReplCommandParser.CommandType.CONTEXT, ReplCommandParser.parse("context").type());
        assertEquals(ReplCommandParser.CommandType.CONTEXT, ReplCommandParser.parse("/context").type());
        assertEquals(ReplCommandParser.CommandType.CONTEXT, ReplCommandParser.parse("  /CONTEXT  ").type());
        assertNull(ReplCommandParser.parse("/context").payload());
    }

    @Test
    void parsesUnknownSlashCommands() {
        ReplCommandParser.ParsedCommand command = ReplCommandParser.parse("/foo");

        assertEquals(ReplCommandParser.CommandType.UNKNOWN, command.type());
        assertEquals("/foo", command.payload());
    }

    @Test
    void parsesChatInput() {
        ReplCommandParser.ParsedCommand command = ReplCommandParser.parse("  列出当前目录  ");

        assertEquals(ReplCommandParser.CommandType.CHAT, command.type());
        assertEquals("列出当前目录", command.payload());
    }

    @Test
    void doesNotTreatExitPhraseAsCommand() {
        assertEquals(ReplCommandParser.CommandType.CHAT, ReplCommandParser.parse("exit now").type());
        assertEquals(ReplCommandParser.CommandType.CHAT, ReplCommandParser.parse("clear history").type());
    }

    @Test
    void blankInputIsChatWithEmptyPayload() {
        ReplCommandParser.ParsedCommand command = ReplCommandParser.parse("   ");

        assertEquals(ReplCommandParser.CommandType.CHAT, command.type());
        assertEquals("", command.payload());
    }

    @Test
    void parsesMemorySaveCommands() {
        assertEquals(ReplCommandParser.CommandType.MEMORY_SAVE,
                ReplCommandParser.parse("/save 项目使用 Java 17").type());
        assertEquals("项目使用 Java 17",
                ReplCommandParser.parse("/save 项目使用 Java 17").payload());
        assertEquals(ReplCommandParser.CommandType.MEMORY_SAVE,
                ReplCommandParser.parse("/save").type());
        assertEquals("", ReplCommandParser.parse("/save").payload());
    }

    @Test
    void parsesMemoryCommands() {
        assertEquals(ReplCommandParser.CommandType.MEMORY_STATUS,
                ReplCommandParser.parse("/memory").type());
        assertEquals(ReplCommandParser.CommandType.MEMORY_LIST,
                ReplCommandParser.parse("/memory list").type());
        assertEquals(ReplCommandParser.CommandType.MEMORY_CLEAR,
                ReplCommandParser.parse("/memory clear").type());
        assertEquals(ReplCommandParser.CommandType.MEMORY_SEARCH,
                ReplCommandParser.parse("/memory search Java").type());
        assertEquals("Java", ReplCommandParser.parse("/memory search Java").payload());
    }

    @Test
    void unknownMemorySubcommandStaysUnknown() {
        assertEquals(ReplCommandParser.CommandType.UNKNOWN,
                ReplCommandParser.parse("/memory foo").type());
    }

    @Test
    void parsesPlanCommands() {
        assertEquals(ReplCommandParser.CommandType.PLAN,
                ReplCommandParser.parse("/plan").type());
        assertEquals("", ReplCommandParser.parse("/plan").payload());
        assertEquals(ReplCommandParser.CommandType.PLAN,
                ReplCommandParser.parse("/plan 创建目录并写 README").type());
        assertEquals("创建目录并写 README",
                ReplCommandParser.parse("/plan 创建目录并写 README").payload());
        assertEquals("列出文件",
                ReplCommandParser.parse("  /PLAN   列出文件  ").payload());
    }

    @Test
    void formatHelpListsCoreCommands() {
        String help = ReplCommandParser.formatHelp();

        assertTrue(help.contains("exit"));
        assertTrue(help.contains("/clear"));
        assertTrue(help.contains("/help"));
        assertTrue(help.contains("/context"));
        assertTrue(help.contains("/save"));
        assertTrue(help.contains("/memory list"));
        assertTrue(help.contains("/plan"));
        assertNull(ReplCommandParser.parse("/help").payload());
    }
}
