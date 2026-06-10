package com.cliagent.tool;

import com.cliagent.policy.CommandGuard;
import com.cliagent.policy.PathGuard;
import com.cliagent.llm.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 工具注册表：注册内置工具、执行 tool_call、导出 LLM 可用的工具定义。
 */
public class ToolRegistry {

    //最大读取文件行数
    private static final int MAX_READ_FILE_LINES = 2000;
    //命令超时时间
    private static final int COMMAND_TIMEOUT_SECONDS = 60;
    //命令输出最大字符数
    private static final int MAX_COMMAND_OUTPUT_CHARS = 8_000;
    //对象映射器，用于将JSON字符串转换为Java对象
    private static final ObjectMapper MAPPER = new ObjectMapper();

    //工具注册表，用于注册工具
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private String projectPath = System.getProperty("user.dir");
    private PathGuard pathGuard = new PathGuard(projectPath);

    public ToolRegistry() {
        registerBuiltinTools();
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = normalizeProjectPath(projectPath);
        this.pathGuard = new PathGuard(this.projectPath);
    }

    public String getProjectPath() {
        return projectPath;
    }

    private static String normalizeProjectPath(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            throw new IllegalArgumentException("项目根路径不能为空");
        }
        return Path.of(projectPath).toAbsolutePath().normalize().toString();
    }

    public void register(Tool tool) {
        //将工具注册到工具注册表中
        tools.put(tool.name(), tool);
    }

    /**
     * 按名称执行工具。argumentsJson 来自 LLM tool_call 的 function.arguments 字符串。
     * 异常统一转为字符串返回，不向上抛。
     */
    public String executeTool(String name, String argumentsJson) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return "未知工具: " + name;
        }
        try {
            Map<String, String> args = parseArguments(argumentsJson);
            String result = tool.executor().execute(args);
            return result == null ? "" : result;
        } catch (Exception e) {
            return "工具执行失败: " + e.getMessage();
        }
    }

    /** 转为 LlmClient.Tool 列表，供 DeepSeekClient.buildRequestBody 使用 */
    public List<LlmClient.Tool> getToolDefinitions() {
        return tools.values().stream()
                .map(t -> new LlmClient.Tool(t.name(), t.description(), t.parameters()))
                .toList();
    }

    /** Day 2 Step 2 起在此注册 list_dir / read_file 等内置工具 */
    private void registerBuiltinTools() {
        register(new Tool(
                "list_dir",
                "列出目录内容",
                createParameters(new Param("path", "string", "目录路径", true)),
                args -> this.listDir(args.get("path"))
        ));
        register(new Tool(
                "read_file",
                "读取文件内容；可用 offset/limit 按行读取，避免把大文件整段塞进上下文",
                createParameters(
                        new Param("path", "string", "文件路径", true),
                        new Param("offset", "integer", "起始行号，1 表示第一行", false),
                        new Param("limit", "integer", "最多读取多少行，最大 2000 行", false)
                ),
                args -> this.readFile(args.get("path"), args.get("offset"), args.get("limit"))
        ));
        register(new Tool(
                "write_file",
                "写入文件内容；若父目录不存在会自动创建",
                createParameters(
                        new Param("path", "string", "文件路径", true),
                        new Param("content", "string", "要写入的文件内容", true)
                ),
                args -> this.writeFile(args.get("path"), args.get("content"))
        ));
        register(new Tool(
                "execute_command",
                "在当前项目目录中执行 Shell 命令（60 秒超时，输出最多 8KB）",
                createParameters(new Param("command", "string", "要执行的命令", true)),
                args -> this.executeCommand(args.get("command"))
        ));
        register(new Tool(
                "create_project",
                "创建新项目结构",
                createParameters(
                        new Param("name", "string", "项目名称或目录路径", true),
                        new Param("type", "string", "项目类型 (java/python/node)", true)
                ),
                args -> this.createProject(args.get("name"), args.get("type"))
        ));
    }

    private String listDir(String path) throws IOException {
        if (path == null || path.isBlank()) {
            return "目录路径不能为空";
        }
        Path dir = pathGuard.resolveSafe(path);
        if (!Files.exists(dir)) {
            return "目录不存在: " + path;
        }
        if (!Files.isDirectory(dir)) {
            return "不是目录: " + path;
        }
        try (var stream = Files.list(dir)) {
            List<String> entries = stream
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(p -> (Files.isDirectory(p) ? "[D] " : "[F] ") + p.getFileName())
                    .collect(Collectors.toList());
            if (entries.isEmpty()) {
                return "目录为空: " + path;
            }
            return "目录内容 (" + path + "):\n" + String.join("\n", entries);
        }
    }

    private String readFile(String path, String offsetStr, String limitStr) throws IOException {
        if (path == null || path.isBlank()) {
            return "文件路径不能为空";
        }
        Path file = pathGuard.resolveSafe(path);
        if (!Files.exists(file)) {
            return "文件不存在: " + path;
        }
        if (!Files.isRegularFile(file)) {
            return "不是普通文件: " + path;
        }

        //读取文件全部内容，并按行分割
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int total = lines.size();
        if (total == 0) {
            return "文件为空: " + path;
        }

        int offset = Math.max(1, parseOptionalInt(offsetStr, 1));
        int defaultLimit = Math.min(total - offset + 1, MAX_READ_FILE_LINES);
        int limit = Math.max(1, Math.min(parseOptionalInt(limitStr, defaultLimit), MAX_READ_FILE_LINES));

        if (offset > total) {
            return "文件共 " + total + " 行，offset 超出范围";
        }

        int from = offset - 1;
        int to = Math.min(from + limit, total);
        StringBuilder sb = new StringBuilder();
        sb.append("文件内容 (").append(path).append(", lines ")
                .append(offset).append("-").append(to)
                .append(" of ").append(total).append("):\n");
        for (int i = from; i < to; i++) {
            sb.append(String.format("%5d | %s%n", i + 1, lines.get(i)));
        }
        if (to < total) {
            sb.append("...(已截断，可用 offset=").append(to + 1).append(" 继续读取)");
        }
        return sb.toString().trim();
    }

    private String writeFile(String path, String content) throws IOException {
        if (path == null || path.isBlank()) {
            return "文件路径不能为空";
        }
        Path file = pathGuard.resolveSafe(path);
        if (Files.exists(file) && Files.isDirectory(file)) {
            return "目标是目录，不能写入: " + path;
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
        return "文件已写入: " + path;
    }

    private String executeCommand(String command) throws Exception {
        String normalized = command == null ? "" : command.trim();
        if (normalized.isEmpty()) {
            return "命令不能为空";
        }

        String denyReason = CommandGuard.check(normalized);
        if (denyReason != null) {
            return "策略拒绝: " + denyReason;
        }

        //创建一个单线程的线程池，用于读取命令输出
        ExecutorService outputReader = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "cliagent-command-output");
            thread.setDaemon(true);
            return thread;
        });

        Process process = null;
        try {
            /** 
             * ProcessBuilder 用于创建一个进程，并执行命令
             * 参数：
             * - bash: 使用 bash 命令
             * - -c: 执行命令
             * - normalized: 要执行的命令
             * - directory: 当前项目目录
             * - redirectErrorStream: 将错误输出重定向到标准输出
             * - start: 启动进程
            */
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", normalized);
            pb.directory(Path.of(projectPath).toFile());
            //将stderr合并到stout
            pb.redirectErrorStream(true);
            //启动进程
            process = pb.start();

            Process runningProcess = process;
            //提交一个任务到线程池，用于读取命令输出
            Future<String> outputFuture = outputReader.submit(() -> readProcessOutput(runningProcess));
            //等待命令执行完成，或超时
            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();//强制终止进程
                process.waitFor(2, TimeUnit.SECONDS);//等待2秒，确保进程完全终止
                outputFuture.cancel(true);//取消读取命令输出的任务
                return "命令执行超时（" + COMMAND_TIMEOUT_SECONDS + " 秒），已强制终止";
            }

            String output = getCommandOutput(outputFuture);//获取命令输出
            int exitCode = process.exitValue();//获取命令退出码
            return String.format("命令执行完成 (exit code: %d)\n%s", exitCode, output).trim();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            outputReader.shutdownNow();
        }
    }
    //读取进程输出
    private static String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();//用于存储命令输出
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() >= MAX_COMMAND_OUTPUT_CHARS) {
                    break;
                }
                int remaining = MAX_COMMAND_OUTPUT_CHARS - output.length();
                if (line.length() > remaining) {
                    output.append(line, 0, remaining);
                } else {
                    output.append(line);
                }
                output.append('\n');
            }
        }
        if (output.length() >= MAX_COMMAND_OUTPUT_CHARS) {
            return output.substring(0, MAX_COMMAND_OUTPUT_CHARS) + "\n...(输出已截断)";
        }
        return output.toString();
    }

    //获取命令输出
    private static String getCommandOutput(Future<String> outputFuture) throws Exception {
        //等待任务完成，或超时
        try {
            return outputFuture.get(2, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            outputFuture.cancel(true);
            return "(命令已结束，但输出读取超时)";
        }
    }

    /**
     * 创建新项目结构
     * @param name 项目名称或目录路径
     * @param type 项目类型 (java/python/node)
     * @return 项目创建结果
     * @throws IOException 如果创建项目失败
     */
    private String createProject(String name, String type) throws IOException {
        if (name == null || name.isBlank()) {
            return "项目名称不能为空";
        }
        if (type == null || type.isBlank()) {
            return "项目类型不能为空";
        }

        String normalizedType = type.toLowerCase();
        if (!normalizedType.equals("java") && !normalizedType.equals("python") && !normalizedType.equals("node")) {
            return "不支持的项目类型: " + type + "（支持 java/python/node）";
        }

        Path projectRoot = pathGuard.resolveSafe(name);
        Files.createDirectories(projectRoot);//创建项目根目录

        //每个分支只做：建子目录、写模版文件
        switch (normalizedType) {
            case "java" -> {
                Files.createDirectories(projectRoot.resolve("src/main/java"));
                Files.createDirectories(projectRoot.resolve("src/main/resources"));
                Files.writeString(projectRoot.resolve("pom.xml"), """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <project xmlns="http://maven.apache.org/POM/4.0.0"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 \
                        http://maven.apache.org/xsd/maven-4.0.0.xsd">
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>%s</artifactId>
                            <version>1.0-SNAPSHOT</version>
                        </project>
                        """.formatted(safeArtifactId(name)));
            }
            case "python" -> {
                Files.writeString(projectRoot.resolve("main.py"), "# 主程序入口\n");
                Files.writeString(projectRoot.resolve("requirements.txt"), "# 依赖列表\n");
            }
            case "node" -> {
                Files.writeString(projectRoot.resolve("package.json"),
                        """
                        {
                          "name": "%s",
                          "version": "1.0.0"
                        }
                        """.formatted(safePackageName(name)));
            }
        }
        return "项目已创建: " + name + " (类型: " + normalizedType + ")";
    }

    /** 从路径取最后一段作为 Maven artifactId，去掉非法字符 */
    private static String safeArtifactId(String name) {
        String base = Path.of(name).getFileName().toString();
        return base.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    private static String safePackageName(String name) {
        return safeArtifactId(name).toLowerCase();
    }

    private static int parseOptionalInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Map<String, String> parseArguments(String argumentsJson) throws Exception {
        Map<String, String> argMap = new HashMap<>();
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return argMap;
        }
        JsonNode args = MAPPER.readTree(argumentsJson);
        if (!args.isObject()) {
            return argMap;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = args.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            if (value.isNull()) {
                argMap.put(entry.getKey(), null);
            } else if (value.isValueNode()) {
                argMap.put(entry.getKey(), value.asText());
            } else {
                argMap.put(entry.getKey(), value.toString());
            }
        }
        return argMap;
    }

    record Param(String name, String type, String description, boolean required) {
    }

    static ObjectNode createParameters(Param... params) {
        ObjectNode parameters = MAPPER.createObjectNode();
        parameters.put("type", "object");// "type":"object"
        ObjectNode properties = parameters.putObject("properties");//加入一个properties的节点
        ArrayNode required = parameters.putArray("required");//加入一个required的数组

        for (Param param : params) {
            ObjectNode prop = properties.putObject(param.name());
            prop.put("type", param.type());
            prop.put("description", param.description());
            if (param.required()) {
                required.add(param.name());
            }
        }
        return parameters;
    }
}
