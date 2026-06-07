package com.cliagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Day 0：最小可运行 demo —— 用 OkHttp 调一次 DeepSeek API，验证脚手架可用。
 *
 * <p>这里故意写得"扁平"，把所有逻辑塞在一个类里。后面 Day 1+ 会把它拆成：
 * <ul>
 *   <li>{@code config/EnvConfig} —— API Key 三级加载</li>
 *   <li>{@code llm/LlmClient + DeepSeekClient} —— HTTP 封装 + 消息序列化</li>
 *   <li>{@code tool/ToolRegistry} —— 工具注册表 + JSON Schema</li>
 *   <li>{@code agent/Agent} —— ReAct 循环</li>
 * </ul>
 */
public class Main {

    /** DeepSeek OpenAI 兼容端点 */
    private static final String API_URL = "https://api.deepseek.com/chat/completions";

    /** 默认模型；可通过 .env 的 DEEPSEEK_MODEL 覆盖 */
    private static final String DEFAULT_MODEL = "deepseek-chat";

    /**
     * OkHttpClient 必须全局单例：内置连接池 + 线程池，频繁 new 会耗尽端口和线程。
     * 4 段超时：
     *   - connectTimeout: TCP 握手最大时长
     *   - readTimeout:    两次 read 之间最大间隔（流式场景需要放大）
     *   - writeTimeout:   写请求体最大时长
     *   - callTimeout:    整次调用兜底
     */
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build();

    /** Jackson ObjectMapper 也建议全局单例（线程安全，但构造很贵） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        String apiKey = loadApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("❌ 未找到 DEEPSEEK_API_KEY");
            System.err.println();
            System.err.println("请按下面任一方式提供：");
            System.err.println("  1. 在项目根创建 .env：cp .env.example .env，再编辑填入真实 key");
            System.err.println("  2. 设置环境变量：    export DEEPSEEK_API_KEY=sk-xxx");
            System.err.println("  3. JVM 系统属性：    java -DDEEPSEEK_API_KEY=sk-xxx -jar ...");
            System.exit(1);
        }

        String model = loadEnvValue("DEEPSEEK_MODEL", DEFAULT_MODEL);
        String userInput = args.length > 0
                ? String.join(" ", args)
                : "你好，请用一句话介绍自己";

        System.out.println("you> " + userInput);
        try {
            String answer = chat(apiKey, model, userInput);
            System.out.println("assistant> " + answer);
        } catch (IOException e) {
            System.err.println("❌ 调用失败: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * 发一次非流式 chat 请求。Day 1 会把这块拆出来变成 {@code LlmClient.chat(...)}。
     */
    private static String chat(String apiKey, String model, String userInput) throws IOException {
        // 1. 用 Jackson 树模型构造请求体（Day 1 会换成 buildRequestBody(messages, tools)）
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userInput);

        // 2. 构造 HTTP 请求
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();

        // 3. 执行并解析响应（try-with-resources 保证 Response 一定关闭）
        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody respBody = response.body();
            String raw = respBody == null ? "" : respBody.string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + raw);
            }
            JsonNode root = MAPPER.readTree(raw);
            // choices[0].message.content 是 OpenAI 兼容协议的标准位置
            return root.path("choices").path(0).path("message").path("content").asText("");
        }
    }

    /**
     * 加载 DEEPSEEK_API_KEY，优先级：
     *   1. JVM 系统属性 -D
     *   2. 环境变量
     *   3. 项目根目录 ./.env
     */
    private static String loadApiKey() {
        return loadEnvValue("DEEPSEEK_API_KEY", null);
    }

    /**
     * 通用配置加载（同上三级优先级），找不到时返回 defaultValue。
     */
    private static String loadEnvValue(String key, String defaultValue) {
        String fromProp = System.getProperty(key);
        if (fromProp != null && !fromProp.isBlank()) return fromProp.trim();

        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();

        File envFile = new File(".env");
        if (envFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
                String line;
                String prefix = key + "=";
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (line.startsWith(prefix)) {
                        String value = line.substring(prefix.length()).trim();
                        // 去掉两侧可选的引号
                        if (value.length() >= 2
                                && ((value.startsWith("\"") && value.endsWith("\""))
                                ||  (value.startsWith("'")  && value.endsWith("'")))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        return value.isBlank() ? defaultValue : value;
                    }
                }
            } catch (IOException ignored) {
                // 读取失败回退到 defaultValue
            }
        }
        return defaultValue;
    }
}
