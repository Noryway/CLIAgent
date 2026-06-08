package com.cliagent.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * 配置加载：JVM 系统属性 &gt; 环境变量 &gt; 项目根目录 {@code ./.env}。
 */
public final class EnvConfig {

    private EnvConfig() {
    }

    public static String get(String key) {
        return get(key, null);
    }

    public static String get(String key, String defaultValue) {
        String fromProp = System.getProperty(key);
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp.trim();
        }

        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }

        File envFile = new File(".env");
        if (envFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
                String line;
                String prefix = key + "=";
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    if (line.startsWith(prefix)) {
                        String value = stripQuotes(line.substring(prefix.length()).trim());
                        return value.isBlank() ? defaultValue : value;
                    }
                }
            } catch (IOException ignored) {
                // 读取失败回退到 defaultValue
            }
        }
        return defaultValue;
    }

    public static String require(String key) {
        String value = get(key);
        return value == null || value.isBlank() ? null : value;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
