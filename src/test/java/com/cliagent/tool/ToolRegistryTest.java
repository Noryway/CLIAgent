package com.cliagent.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {

    @Test
    void registryIncludesBuiltinToolDefinitions() {
        ToolRegistry registry = new ToolRegistry();
        assertEquals(5, registry.getToolDefinitions().size());
        assertEquals("list_dir", registry.getToolDefinitions().get(0).name());
        assertEquals("read_file", registry.getToolDefinitions().get(1).name());
        assertEquals("write_file", registry.getToolDefinitions().get(2).name());
        assertEquals("execute_command", registry.getToolDefinitions().get(3).name());
        assertEquals("create_project", registry.getToolDefinitions().get(4).name());
    }

    @Test
    void executeUnknownToolReturnsMessage() {
        ToolRegistry registry = new ToolRegistry();
        assertEquals("未知工具: missing", registry.executeTool("missing", "{}"));
    }

    @Test
    void listDirListsCurrentDirectory() {
        ToolRegistry registry = new ToolRegistry();
        String result = registry.executeTool("list_dir", "{\"path\":\".\"}");
        assertTrue(result.startsWith("目录内容 (.):"));
        assertTrue(result.contains("[F]") || result.contains("[D]"));
    }

    @Test
    void listDirMissingPathReturnsMessage() {
        ToolRegistry registry = new ToolRegistry();
        assertEquals("目录路径不能为空", registry.executeTool("list_dir", "{}"));
    }

    @Test
    void listDirNonexistentPathReturnsMessage() {
        ToolRegistry registry = new ToolRegistry();
        String result = registry.executeTool("list_dir", "{\"path\":\"no-such-dir-xyz\"}");
        assertEquals("目录不存在: no-such-dir-xyz", result);
    }

    @Test
    void readFileReadsProjectReadme() {
        ToolRegistry registry = new ToolRegistry();
        String result = registry.executeTool("read_file", "{\"path\":\"README.md\"}");
        assertTrue(result.contains("文件内容 (README.md"));
        assertTrue(result.contains("CLIAgent"));
    }

    @Test
    void readFileSupportsOffsetAndLimit(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "line1\nline2\nline3\nline4\n");

        ToolRegistry registry = new ToolRegistry();
        String args = "{\"path\":\"" + file.toString().replace("\\", "\\\\") + "\",\"offset\":\"2\",\"limit\":\"2\"}";
        String result = registry.executeTool("read_file", args);

        assertTrue(result.contains("lines 2-3 of 4"));
        assertTrue(result.contains("line2"));
        assertTrue(result.contains("line3"));
        assertTrue(!result.contains("line1"));
    }

    @Test
    void readFileMissingPathReturnsMessage() {
        ToolRegistry registry = new ToolRegistry();
        assertEquals("文件路径不能为空", registry.executeTool("read_file", "{}"));
    }

    @Test
    void readFileNonexistentPathReturnsMessage() {
        ToolRegistry registry = new ToolRegistry();
        assertEquals("文件不存在: no-such-file.txt", registry.executeTool("read_file", "{\"path\":\"no-such-file.txt\"}"));
    }

    @Test
    void writeFileCreatesNestedDirectoriesAndContent(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("nested/dir/hello.txt");
        String pathJson = file.toString().replace("\\", "\\\\");
        String args = "{\"path\":\"" + pathJson + "\",\"content\":\"hello\\nworld\"}";

        ToolRegistry registry = new ToolRegistry();
        assertEquals("文件已写入: " + file, registry.executeTool("write_file", args));
        assertEquals("hello\nworld", Files.readString(file));
    }

    @Test
    void writeFileOverwritesExistingFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("note.txt");
        Files.writeString(file, "old");
        String pathJson = file.toString().replace("\\", "\\\\");

        ToolRegistry registry = new ToolRegistry();
        registry.executeTool("write_file", "{\"path\":\"" + pathJson + "\",\"content\":\"new\"}");
        assertEquals("new", Files.readString(file));
    }

    @Test
    void writeFileThenReadFileRoundTrip(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("roundtrip.txt");
        String pathJson = file.toString().replace("\\", "\\\\");

        ToolRegistry registry = new ToolRegistry();
        registry.executeTool("write_file", "{\"path\":\"" + pathJson + "\",\"content\":\"alpha\"}");
        String readResult = registry.executeTool("read_file", "{\"path\":\"" + pathJson + "\"}");

        assertTrue(readResult.contains("alpha"));
    }

    @Test
    void writeFileMissingPathReturnsMessage() {
        ToolRegistry registry = new ToolRegistry();
        assertEquals("文件路径不能为空", registry.executeTool("write_file", "{\"content\":\"x\"}"));
    }

    @Test
    void writeFileToExistingDirectoryReturnsMessage(@TempDir Path tempDir) {
        String pathJson = tempDir.toString().replace("\\", "\\\\");
        ToolRegistry registry = new ToolRegistry();
        assertEquals("目标是目录，不能写入: " + tempDir, registry.executeTool("write_file",
                "{\"path\":\"" + pathJson + "\",\"content\":\"x\"}"));
    }

    @Test
    void executeCommandRunsEcho() {
        ToolRegistry registry = new ToolRegistry();
        String result = registry.executeTool("execute_command", "{\"command\":\"echo hello-cliagent\"}");
        assertTrue(result.contains("exit code: 0"));
        assertTrue(result.contains("hello-cliagent"));
    }

    @Test
    void executeCommandMissingCommandReturnsMessage() {
        ToolRegistry registry = new ToolRegistry();
        assertEquals("命令不能为空", registry.executeTool("execute_command", "{}"));
    }

    @Test
    void executeCommandTruncatesLargeOutput() {
        ToolRegistry registry = new ToolRegistry();
        String result = registry.executeTool("execute_command",
                "{\"command\":\"printf 'x%.0s' {1..9000}\"}");
        assertTrue(result.contains("输出已截断"));
        assertTrue(result.length() < 9000);
    }

    @Test
    void createProjectJavaStructure(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("demo-app");
        String nameJson = projectRoot.toString().replace("\\", "\\\\");
        ToolRegistry registry = new ToolRegistry();

        String result = registry.executeTool("create_project",
                "{\"name\":\"" + nameJson + "\",\"type\":\"java\"}");

        assertEquals("项目已创建: " + projectRoot + " (类型: java)", result);
        assertTrue(Files.isDirectory(projectRoot.resolve("src/main/java")));
        assertTrue(Files.isDirectory(projectRoot.resolve("src/main/resources")));
        assertTrue(Files.readString(projectRoot.resolve("pom.xml")).contains("<artifactId>demo-app</artifactId>"));
    }

    @Test
    void createProjectPythonStructure(@TempDir Path tempDir) throws Exception {
        Path projectRoot = tempDir.resolve("py-demo");
        String nameJson = projectRoot.toString().replace("\\", "\\\\");
        ToolRegistry registry = new ToolRegistry();

        registry.executeTool("create_project", "{\"name\":\"" + nameJson + "\",\"type\":\"python\"}");

        assertTrue(Files.exists(projectRoot.resolve("main.py")));
        assertTrue(Files.exists(projectRoot.resolve("requirements.txt")));
    }

    @Test
    void createProjectUnsupportedType(@TempDir Path tempDir) {
        Path projectRoot = tempDir.resolve("bad-type");
        String nameJson = projectRoot.toString().replace("\\", "\\\\");
        ToolRegistry registry = new ToolRegistry();

        assertEquals("不支持的项目类型: rust（支持 java/python/node）",
                registry.executeTool("create_project", "{\"name\":\"" + nameJson + "\",\"type\":\"rust\"}"));
    }

    @Test
    void createProjectMissingNameReturnsMessage() {
        ToolRegistry registry = new ToolRegistry();
        assertEquals("项目名称不能为空", registry.executeTool("create_project", "{\"type\":\"java\"}"));
    }
}
