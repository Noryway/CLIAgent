package com.cliagent.plan;

import com.cliagent.llm.LlmClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanParserTest {

    @Test
    void createsMinimalPlanForSimpleGoalWithoutCallingLlm() throws Exception {
        PlanParser parser = new PlanParser(new FailingLlmClient());

        ExecutionPlan plan = parser.createPlan("列出当前目录的文件");

        assertEquals("直接执行简单任务：列出当前目录的文件", plan.getSummary());
        assertEquals(List.of("task_1"), plan.getExecutionOrder());
        assertEquals("列出当前目录的文件", plan.getTask("task_1").getDescription());
    }

    @Test
    void delegatesComplexGoalToLlmPlannerPath() throws Exception {
        PlanParser parser = new PlanParser(new StubLlmClient("""
                {
                  "summary": "复杂任务",
                  "tasks": [
                    {
                      "id": "task_a",
                      "description": "先读取 pom.xml",
                      "dependencies": []
                    },
                    {
                      "id": "task_b",
                      "description": "再验证项目结构",
                      "dependencies": ["task_a"]
                    }
                  ]
                }
                """));

        ExecutionPlan plan = parser.createPlan("先读取 pom.xml 然后验证项目结构");

        assertEquals("复杂任务", plan.getSummary());
        assertEquals(2, plan.getAllTasks().size());
        assertTrue(plan.getTask("task_2").getDependencies().contains("task_1"));
        assertEquals(List.of("task_1", "task_2"), plan.getExecutionOrder());
    }

    @Test
    void stripsMarkdownCodeFenceFromLlmOutput() throws Exception {
        PlanParser parser = new PlanParser(new StubLlmClient("""
                ```json
                {
                  "summary": "带围栏",
                  "tasks": [
                    { "id": "1", "description": "第一步", "dependencies": [] }
                  ]
                }
                ```
                """));

        ExecutionPlan plan = parser.createPlan("先创建目录然后写文件");

        assertEquals("带围栏", plan.getSummary());
        assertEquals(1, plan.getAllTasks().size());
    }

    @Test
    void parsePlanRejectsEmptyTasks() {
        PlanParser parser = new PlanParser(new StubLlmClient("""
                { "summary": "空", "tasks": [] }
                """));

        assertThrows(IOException.class,
                () -> parser.parsePlan("demo", "{ \"summary\": \"空\", \"tasks\": [] }"));
    }

    @Test
    void createPlanRejectsBlankGoal() {
        PlanParser parser = new PlanParser(new StubLlmClient("{}"));

        assertThrows(IllegalArgumentException.class, () -> parser.createPlan("   "));
    }

    private static final class FailingLlmClient implements LlmClient {
        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
            throw new IOException("simple goal should not call llm");
        }

        @Override
        public String getModelName() {
            return "failing-stub";
        }
    }

    private static final class StubLlmClient implements LlmClient {
        private final String content;

        private StubLlmClient(String content) {
            this.content = content;
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) {
            return new ChatResponse("assistant", content, null, 100, 20);
        }

        @Override
        public String getModelName() {
            return "stub-planner";
        }
    }
}
