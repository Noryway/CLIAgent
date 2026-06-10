package com.cliagent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    void parseCliArgsDefaultProjectPath() {
        String expected = Path.of(".").toAbsolutePath().normalize().toString();
        Main.ParsedCliArgs cli = Main.parseCliArgs(new String[]{});

        assertEquals(expected, cli.projectPath());
        assertEquals(0, cli.promptArgs().length);
    }

    @Test
    void parseCliArgsWithCwdOnly(@TempDir Path tempDir) {
        Main.ParsedCliArgs cli = Main.parseCliArgs(new String[]{"--cwd", tempDir.toString()});

        assertEquals(tempDir.toAbsolutePath().normalize().toString(), cli.projectPath());
        assertEquals(0, cli.promptArgs().length);
    }

    @Test
    void parseCliArgsStripsCwdFromPrompt(@TempDir Path tempDir) {
        Main.ParsedCliArgs cli = Main.parseCliArgs(new String[]{"--cwd", tempDir.toString(), "hello", "world"});

        assertEquals(tempDir.toAbsolutePath().normalize().toString(), cli.projectPath());
        assertEquals(2, cli.promptArgs().length);
        assertEquals("hello", cli.promptArgs()[0]);
        assertEquals("world", cli.promptArgs()[1]);
    }

    @Test
    void parseCliArgsRejectsMissingCwdValue() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> Main.parseCliArgs(new String[]{"--cwd"}));
        assertTrue(error.getMessage().contains("--cwd"));
    }

    @Test
    void resolveProjectPathRejectsNonDirectory() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> Main.resolveProjectPath("/no/such/cliagent-dir-xyz"));
        assertTrue(error.getMessage().contains("不是目录"));
    }
}
