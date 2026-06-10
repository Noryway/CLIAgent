package com.cliagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryManagerTest {

    @Test
    void saveAndReloadFromDisk(@TempDir Path tempDir) {
        Path storageFile = tempDir.resolve("memory.json");
        String projectPath = tempDir.resolve("demo").toAbsolutePath().normalize().toString();

        MemoryManager manager = new MemoryManager(storageFile);
        manager.setProjectPath(projectPath);
        manager.save("用户叫小明");

        MemoryManager reloaded = new MemoryManager(storageFile);
        reloaded.setProjectPath(projectPath);

        assertEquals(1, reloaded.list().size());
        assertEquals("用户叫小明", reloaded.list().get(0).content());
    }

    @Test
    void searchReturnsMatchingFacts(@TempDir Path tempDir) {
        Path storageFile = tempDir.resolve("memory.json");
        String projectPath = tempDir.toAbsolutePath().normalize().toString();

        MemoryManager manager = new MemoryManager(storageFile);
        manager.setProjectPath(projectPath);
        manager.save("项目使用 Java 17");
        manager.save("默认 Maven 构建");

        assertEquals(1, manager.search("Java").size());
        assertTrue(manager.formatStatus().contains("长期记忆: 2 条"));
    }

    @Test
    void clearProjectMemoriesKeepsOtherProjects(@TempDir Path tempDir) {
        Path storageFile = tempDir.resolve("memory.json");
        String projectA = tempDir.resolve("a").toAbsolutePath().normalize().toString();
        String projectB = tempDir.resolve("b").toAbsolutePath().normalize().toString();

        MemoryManager managerA = new MemoryManager(storageFile);
        managerA.setProjectPath(projectA);
        managerA.save("A 的事实");

        MemoryManager managerB = new MemoryManager(storageFile);
        managerB.setProjectPath(projectB);
        managerB.save("B 的事实");

        managerA.clearProjectMemories();

        assertEquals(0, managerA.list().size());
        assertEquals(1, managerB.list().size());
    }

    @Test
    void saveRequiresProjectPath(@TempDir Path tempDir) {
        MemoryManager manager = new MemoryManager(tempDir.resolve("memory.json"));
        assertThrows(IllegalStateException.class, () -> manager.save("未设置项目路径"));
    }

    @Test
    void saveRejectsBlankContent(@TempDir Path tempDir) {
        MemoryManager manager = new MemoryManager(tempDir.resolve("memory.json"));
        manager.setProjectPath(tempDir.toString());
        assertThrows(IllegalArgumentException.class, () -> manager.save("   "));
    }

    @Test
    void buildContextBlockReturnsEmptyWhenNoMemories(@TempDir Path tempDir) {
        MemoryManager manager = new MemoryManager(tempDir.resolve("memory.json"));
        manager.setProjectPath(tempDir.toString());

        assertEquals("", manager.buildContextBlock("你好"));
    }

    @Test
    void buildContextBlockIncludesProjectMemories(@TempDir Path tempDir) {
        MemoryManager manager = new MemoryManager(tempDir.resolve("memory.json"));
        manager.setProjectPath(tempDir.toString());
        manager.save("项目使用 Java 17");

        String block = manager.buildContextBlock("介绍一下项目");

        assertTrue(block.contains("已知长期记忆"));
        assertTrue(block.contains("项目使用 Java 17"));
    }
}
