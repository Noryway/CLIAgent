package com.cliagent.memory;

import java.time.Instant;
import java.util.UUID;

/** 长期记忆条目：跨会话持久化的事实。 */
public record MemoryEntry(String id, String content, String projectPath, String createdAt) {

    public static MemoryEntry create(String content, String projectPath) {
        return new MemoryEntry(
                UUID.randomUUID().toString(),
                content,
                projectPath,
                Instant.now().toString()
        );
    }
}
