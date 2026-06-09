package com.cliagent.policy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 路径围栏：文件类工具的路径必须在项目根目录内。
 * 防止绝对路径逃逸、{@code ..} 穿越和符号链接指向外部。
 */
public class PathGuard {

    private final Path rootPath;

    public PathGuard(String root) {
        if (root == null || root.isBlank()) {
            throw new IllegalArgumentException("项目根路径不能为空");
        }
        
        Path candidate = Paths.get(root).toAbsolutePath().normalize();
        Path real = candidate;
        try {
            if (Files.exists(candidate)) {
                real = candidate.toRealPath();
            }
        } catch (IOException ignored) {
            // 根目录尚不存在时沿用规范化路径
        }
        this.rootPath = real;
    }

    public Path getRootPath() {
        return rootPath;
    }

    /** 校验路径在项目根内，返回安全的绝对路径。 */
    public Path resolveSafe(String input) {
        if (input == null || input.isBlank()) {
            throw new PolicyException("路径不能为空");
        }

        Path raw = Paths.get(input);
        Path resolved = raw.isAbsolute()
                ? raw.normalize()
                : rootPath.resolve(raw).normalize();

        Path realResolved = resolveRealPath(resolved);
        if (!realResolved.startsWith(rootPath)) {
            throw new PolicyException(
                    "路径越界: " + input + " 不在项目根 " + rootPath + " 之内");
        }
        return realResolved;
    }

    /**
     * 解析真实路径
     * @param target 目标路径
     * @return 真实路径
     */
    private Path resolveRealPath(Path target) {
        Path existing = target;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return target.toAbsolutePath().normalize();
        }
        try {
            Path realExisting = existing.toRealPath();
            Path remainder = existing.relativize(target);
            return realExisting.resolve(remainder).normalize();
        } catch (IOException e) {
            return target.toAbsolutePath().normalize();
        }
    }
}
