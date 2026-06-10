package com.cliagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryStoreTest {

    @Test
    void persistsEntriesAcrossReload(@TempDir Path tempDir) {
        Path storageFile = tempDir.resolve("memory.json");
        String projectPath = tempDir.resolve("project-a").toAbsolutePath().normalize().toString();

        MemoryStore store = new MemoryStore(storageFile);
        store.add(MemoryEntry.create("项目使用 Java 17", projectPath));

        MemoryStore reloaded = new MemoryStore(storageFile);
        assertEquals(1, reloaded.size());
        assertEquals("项目使用 Java 17", reloaded.listByProject(projectPath).get(0).content());
    }

    @Test
    void listByProjectFiltersOtherProjects(@TempDir Path tempDir) {
        Path storageFile = tempDir.resolve("memory.json");
        String projectA = tempDir.resolve("a").toAbsolutePath().normalize().toString();
        String projectB = tempDir.resolve("b").toAbsolutePath().normalize().toString();

        MemoryStore store = new MemoryStore(storageFile);
        store.add(MemoryEntry.create("A 项目事实", projectA));
        store.add(MemoryEntry.create("B 项目事实", projectB));

        assertEquals(1, store.listByProject(projectA).size());
        assertEquals("A 项目事实", store.listByProject(projectA).get(0).content());
    }

    @Test
    void searchMatchesSubstringCaseInsensitive(@TempDir Path tempDir) {
        Path storageFile = tempDir.resolve("memory.json");
        String projectPath = tempDir.toAbsolutePath().normalize().toString();

        MemoryStore store = new MemoryStore(storageFile);
        store.add(MemoryEntry.create("默认使用中文回答", projectPath));
        store.add(MemoryEntry.create("构建工具是 Maven", projectPath));

        assertEquals(1, store.search(projectPath, "中文").size());
        assertTrue(store.search(projectPath, "maven").get(0).content().contains("Maven"));
    }

    @Test
    void clearProjectRemovesOnlyMatchingProject(@TempDir Path tempDir) {
        Path storageFile = tempDir.resolve("memory.json");
        String projectA = tempDir.resolve("a").toAbsolutePath().normalize().toString();
        String projectB = tempDir.resolve("b").toAbsolutePath().normalize().toString();

        MemoryStore store = new MemoryStore(storageFile);
        store.add(MemoryEntry.create("A", projectA));
        store.add(MemoryEntry.create("B", projectB));

        store.clearProject(projectA);

        assertEquals(0, store.listByProject(projectA).size());
        assertEquals(1, store.listByProject(projectB).size());
    }
}
