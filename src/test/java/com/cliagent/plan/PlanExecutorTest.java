package com.cliagent.plan;

import com.cliagent.agent.Agent;
import com.cliagent.llm.LlmClient;
import com.cliagent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanExecutorTest {

    @Test
    void executesTasksInDependencyOrder() throws Exception {
        ExecutionPlan plan = new ExecutionPlan("plan_test", "demo goal");
        plan.setSummary("两步验证");
        plan.addTask(new Task("task_1", "创建 demo 目录"));
        plan.addTask(new Task("task_2", "列出 demo 目录", List.of("task_1")));
        plan.computeExecutionOrder();

        RecordingLlm llm = new RecordingLlm();
        Agent agent = new Agent(llm, new ToolRegistry());
        PlanExecutor executor = new PlanExecutor(new PlanParser(new FailingLlmClient()));

        String result = executor.executePlan(plan, agent, false);

        assertEquals(2, llm.userPrompts.size());
        assertTrue(llm.userPrompts.get(0).contains("创建 demo 目录"));
        assertTrue(llm.userPrompts.get(1).contains("列出 demo 目录"));
        assertTrue(llm.userPrompts.get(1).contains("task_1"));
        assertTrue(result.contains("计划执行完成"));
        assertTrue(plan.isAllCompleted());
    }

    @Test
    void buildTaskPromptIncludesPriorResults() {
        ExecutionPlan plan = new ExecutionPlan("plan_prompt", "总目标");
        plan.addTask(new Task("task_1", "第一步"));
        plan.addTask(new Task("task_2", "第二步", List.of("task_1")));
        plan.computeExecutionOrder();

        String prompt = PlanExecutor.buildTaskPrompt(
                "总目标",
                plan,
                plan.getTask("task_2"),
                List.of("task_1: 目录已创建")
        );

        assertTrue(prompt.contains("总目标: 总目标"));
        assertTrue(prompt.contains("当前步骤: 第二步"));
        assertTrue(prompt.contains("task_1: 目录已创建"));
        assertTrue(prompt.contains("步骤 task_2 (2/2)"));
    }

    @Test
    void stopsWhenStepFails() throws Exception {
        ExecutionPlan plan = new ExecutionPlan("plan_fail", "demo");
        plan.addTask(new Task("task_1", "会成功的步骤"));
        plan.addTask(new Task("task_2", "会失败的步骤", List.of("task_1")));
        plan.computeExecutionOrder();

        FailingOnSecondCallLlm llm = new FailingOnSecondCallLlm();
        Agent agent = new Agent(llm, new ToolRegistry());
        PlanExecutor executor = new PlanExecutor(new PlanParser(new FailingLlmClient()));

        String result = executor.executePlan(plan, agent, false);

        assertTrue(result.contains("计划执行失败"));
        assertTrue(result.contains("task_2"));
        assertEquals(Task.Status.COMPLETED, plan.getTask("task_1").getStatus());
        assertEquals(Task.Status.FAILED, plan.getTask("task_2").getStatus());
        assertTrue(plan.hasFailed());
    }

    private static final class RecordingLlm implements LlmClient {
        private final List<String> userPrompts = new ArrayList<>();

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) {
            String userPrompt = messages.stream()
                    .filter(message -> "user".equals(message.role()))
                    .reduce((first, second) -> second)
                    .map(Message::content)
                    .orElse("");
            userPrompts.add(userPrompt);
            return new ChatResponse("assistant", "完成: " + userPrompts.size(), null, 5, 3);
        }

        @Override
        public String getModelName() {
            return "recording-stub";
        }
    }

    private static final class FailingOnSecondCallLlm implements LlmClient {
        private int calls;

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
            calls++;
            if (calls == 2) {
                throw new IOException("mock step failure");
            }
            return new ChatResponse("assistant", "ok", null, 1, 1);
        }

        @Override
        public String getModelName() {
            return "failing-on-second-stub";
        }
    }

    private static final class FailingLlmClient implements LlmClient {
        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) throws IOException {
            throw new IOException("planner should not be called");
        }

        @Override
        public String getModelName() {
            return "failing-stub";
        }
    }
}
