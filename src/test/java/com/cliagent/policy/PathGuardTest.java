package com.cliagent.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathGuardTest {

    @Test
    void resolvesRelativePathInsideRoot(@TempDir Path tempDir) throws Exception {
        PathGuard guard = new PathGuard(tempDir.toString());
        Path file = tempDir.resolve("docs/readme.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "hello");

        Path resolved = guard.resolveSafe("docs/readme.md");
        assertEquals(file.toRealPath(), resolved);
    }

    @Test
    void rejectsParentTraversal(@TempDir Path tempDir) {
        PathGuard guard = new PathGuard(tempDir.toString());

        PolicyException ex = assertThrows(PolicyException.class,
                () -> guard.resolveSafe("../../etc/passwd"));
        assertTrue(ex.getMessage().contains("路径越界"));
    }

    @Test
    void rejectsAbsolutePathOutsideRoot(@TempDir Path tempDir) {
        PathGuard guard = new PathGuard(tempDir.toString());

        PolicyException ex = assertThrows(PolicyException.class,
                () -> guard.resolveSafe("/etc/passwd"));
        assertTrue(ex.getMessage().contains("路径越界"));
    }

    @Test
    void allowsNewFilePathInsideRoot(@TempDir Path tempDir) {
        PathGuard guard = new PathGuard(tempDir.toString());

        Path resolved = guard.resolveSafe("new/nested/file.txt");
        assertTrue(resolved.startsWith(tempDir.toAbsolutePath().normalize()));
    }

    @Test
    void rejectsBlankPath(@TempDir Path tempDir) {
        PathGuard guard = new PathGuard(tempDir.toString());
        assertThrows(PolicyException.class, () -> guard.resolveSafe("  "));
    }
}
