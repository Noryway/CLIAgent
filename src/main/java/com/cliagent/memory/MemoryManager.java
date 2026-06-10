package com.cliagent.memory;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** 长期记忆门面：按 projectPath 作用域 save / list / search / clear。 */
public class MemoryManager {

    private final MemoryStore store;
    private String projectPath;

    public MemoryManager() {
        this(MemoryStore.defaultStoragePath());
    }

    public MemoryManager(Path storageFile) {
        this.store = new MemoryStore(storageFile);
    }

    MemoryManager(MemoryStore store) {
        this.store = store;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = MemoryStore.normalizeProjectPath(projectPath);
    }

    public String getProjectPath() {
        return projectPath;
    }

    public MemoryEntry save(String content) {
        requireProjectPath();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("记忆内容不能为空");
        }
        MemoryEntry entry = MemoryEntry.create(content.trim(), projectPath);
        store.add(entry);
        return entry;
    }

    public List<MemoryEntry> list() {
        requireProjectPath();
        return store.listByProject(projectPath);
    }

    public List<MemoryEntry> search(String query) {
        requireProjectPath();
        return store.search(projectPath, query);
    }

    public void clearProjectMemories() {
        requireProjectPath();
        store.clearProject(projectPath);
    }

    public int countForProject() {
        requireProjectPath();
        return list().size();
    }

    public String formatStatus() {
        requireProjectPath();
        return String.format(Locale.ROOT, "长期记忆: %d 条 | 项目: %s", countForProject(), projectPath);
    }

    public String formatList() {
        List<MemoryEntry> entries = list();
        if (entries.isEmpty()) {
            return "（当前项目暂无长期记忆，可用 /save <事实> 添加）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("长期记忆 (").append(entries.size()).append(" 条):\n");
        for (int i = 0; i < entries.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(entries.get(i).content()).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    public String formatSearchResults(String query) {
        List<MemoryEntry> entries = search(query);
        if (entries.isEmpty()) {
            return "未找到匹配「" + query.trim() + "」的长期记忆。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("搜索「").append(query.trim()).append("」命中 ").append(entries.size()).append(" 条:\n");
        for (int i = 0; i < entries.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(entries.get(i).content()).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /**
     * 为 Agent system prompt 构建长期记忆块。
     * v1：注入当前项目的全部长期记忆；若 user 输入非空，优先合并搜索命中项。
     */
    public String buildContextBlock(String userInput) {
        requireProjectPath();
        List<MemoryEntry> entries = list();
        if (userInput != null && !userInput.isBlank()) {
            List<MemoryEntry> matched = search(userInput);
            if (!matched.isEmpty()) {
                entries = matched;
            }
        }
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("已知长期记忆（跨会话保留，请优先参考）：");
        for (MemoryEntry entry : entries) {
            sb.append('\n').append("- ").append(entry.content());
        }
        return sb.toString();
    }

    public Path getStorageFile() {
        return store.getStorageFile();
    }

    private void requireProjectPath() {
        if (projectPath == null || projectPath.isBlank()) {
            throw new IllegalStateException("请先设置 projectPath");
        }
    }
}
