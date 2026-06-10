package com.cliagent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 长期记忆 JSON 持久化：默认 {@code ~/.cliagent/memory.json}。 */
public class MemoryStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<MemoryEntry>> ENTRY_LIST_TYPE = new TypeReference<>() {
    };

    private final Path storageFile;//磁盘路径
    private final List<MemoryEntry> entries = new ArrayList<>();//内存里的记忆列表

    public MemoryStore(Path storageFile) {
        this.storageFile = Objects.requireNonNull(storageFile, "storageFile");
        load();
    }

    public static Path defaultStoragePath() {
        return Path.of(System.getProperty("user.home"), ".cliagent", "memory.json");
    }

    //添加记忆条目
    public synchronized void add(MemoryEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("记忆条目不能为空");
        }
        entries.add(entry);
        save();
    }

    //列出项目记忆 
    public synchronized List<MemoryEntry> listByProject(String projectPath) {
        String normalized = normalizeProjectPath(projectPath);
        return entries.stream()
                .filter(entry -> Objects.equals(entry.projectPath(), normalized))
                .sorted(Comparator.comparing(MemoryEntry::createdAt).reversed())
                .toList();
    }

    //搜索记忆
    public synchronized List<MemoryEntry> search(String projectPath, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String normalizedProject = normalizeProjectPath(projectPath);
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return entries.stream()
                .filter(entry -> Objects.equals(entry.projectPath(), normalizedProject))
                .filter(entry -> entry.content() != null
                        && entry.content().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(MemoryEntry::createdAt).reversed())
                .toList();
    }

    //清除项目记忆 对应/memory clear 
    public synchronized void clearProject(String projectPath) {
        String normalized = normalizeProjectPath(projectPath);
        entries.removeIf(entry -> Objects.equals(entry.projectPath(), normalized));
        save();
    }

    public synchronized int size() {
        return entries.size();
    }

    public Path getStorageFile() {
        return storageFile;
    }

    //加载记忆
    private void load() {
        if (!Files.exists(storageFile)) {
            return;
        }
        try {
            String raw = Files.readString(storageFile);
            if (raw.isBlank()) {
                return;
            }
            List<MemoryEntry> loaded = MAPPER.readValue(raw, ENTRY_LIST_TYPE);
            entries.clear();
            entries.addAll(loaded);
        } catch (IOException e) {
            throw new IllegalStateException("加载长期记忆失败: " + storageFile, e);
        }
    }

    private void save() {
        try {
            Path parent = storageFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(storageFile.toFile(), entries);
        } catch (IOException e) {
            throw new IllegalStateException("保存长期记忆失败: " + storageFile, e);
        }
    }

    //标准化项目路径
    static String normalizeProjectPath(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            throw new IllegalArgumentException("项目路径不能为空");
        }
        return Path.of(projectPath).toAbsolutePath().normalize().toString();
    }
}
