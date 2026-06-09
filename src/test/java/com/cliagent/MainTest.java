package com.cliagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    void recognizesExitCommands() {
        assertTrue(Main.isExitCommand("exit"));
        assertTrue(Main.isExitCommand("quit"));
        assertTrue(Main.isExitCommand("EXIT"));
        assertTrue(Main.isExitCommand("  quit  "));
        assertFalse(Main.isExitCommand("exit now"));
        assertFalse(Main.isExitCommand("clear"));
    }

    @Test
    void recognizesClearCommands() {
        assertTrue(Main.isClearCommand("clear"));
        assertTrue(Main.isClearCommand("/clear"));
        assertTrue(Main.isClearCommand("CLEAR"));
        assertTrue(Main.isClearCommand("  /CLEAR  "));
        assertFalse(Main.isClearCommand("clear history"));
        assertFalse(Main.isClearCommand("exit"));
    }
}
