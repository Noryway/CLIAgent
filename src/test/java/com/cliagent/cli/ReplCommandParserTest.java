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
    void formatHelpListsCoreCommands() {
        String help = ReplCommandParser.formatHelp();

        assertTrue(help.contains("exit"));
        assertTrue(help.contains("/clear"));
        assertTrue(help.contains("/help"));
        assertNull(ReplCommandParser.parse("/help").payload());
    }
}
