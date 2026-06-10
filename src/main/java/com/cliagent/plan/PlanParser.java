package com.cliagent.plan;

import com.cliagent.llm.LlmClient;
import com.cliagent.llm.LlmClient.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用 LLM 将复杂目标拆解为 {@link ExecutionPlan}；
 * 简单单步目标走本地 fallback，不调用 API。
 */
public class PlanParser {

    private static final String PLANNER_SYSTEM_PROMPT = """
            你是任务规划器。将用户目标拆解为可执行的步骤计划。
            只输出 JSON，不要 markdown 代码块，不要额外解释。
            JSON 格式：
            {
              "summary": "计划一句话摘要",
              "tasks": [
                {
                  "id": "1",
                  "description": "步骤描述，要具体可执行",
                  "dependencies": []
                },
                {
                  "id": "2",
                  "description": "下一步描述",
                  "dependencies": ["1"]
                }
              ]
            }
            规则：
            - tasks 至少 1 条，复杂任务拆成 2-6 步
            - dependencies 引用同 plan 内其他 task 的 id
            - 不要产生循环依赖
            - description 用中文，适合交给带工具的编程助手执行
            """;

    private final LlmClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlanParser(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public ExecutionPlan createPlan(String goal) throws IOException {
        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("计划目标不能为空");
        }

        String normalizedGoal = goal.trim();
        if (isSimpleGoal(normalizedGoal)) {
            return createMinimalPlan(normalizedGoal);
        }

        List<Message> messages = Arrays.asList(
                Message.system(PLANNER_SYSTEM_PROMPT),
                Message.user("请为以下任务制定执行计划：\n" + normalizedGoal)
        );
        LlmClient.ChatResponse response = llmClient.chat(messages, null);
        return parsePlan(normalizedGoal, response.content());
    }

    ExecutionPlan parsePlan(String goal, String planJson) throws IOException {
        String cleaned = planJson == null ? "" : planJson
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();
        if (cleaned.isBlank()) {
            throw new IOException("LLM 未返回有效计划 JSON");
        }

        JsonNode root = mapper.readTree(cleaned);
        String summary = root.path("summary").asText("");
        JsonNode tasksNode = root.path("tasks");
        if (!tasksNode.isArray() || tasksNode.isEmpty()) {
            throw new IOException("计划 JSON 缺少 tasks 数组");
        }

        ExecutionPlan plan = new ExecutionPlan(generatePlanId(), goal);
        plan.setSummary(summary.isBlank() ? "执行计划" : summary);

        Map<String, String> idMapping = new HashMap<>();
        int taskIndex = 1;
        for (JsonNode taskNode : tasksNode) {
            String originalId = taskNode.path("id").asText("task_" + taskIndex);
            String newId = "task_" + taskIndex++;
            idMapping.put(originalId, newId);
            String description = taskNode.path("description").asText();
            if (description.isBlank()) {
                throw new IOException("任务 description 不能为空: " + originalId);
            }
            plan.addTask(new Task(newId, description));
        }

        taskIndex = 1;
        for (JsonNode taskNode : tasksNode) {
            String newId = "task_" + taskIndex++;
            Task task = plan.getTask(newId);
            JsonNode depsNode = taskNode.path("dependencies");
            if (depsNode.isArray()) {
                for (JsonNode depNode : depsNode) {
                    String originalDepId = depNode.asText();
                    String mappedDepId = idMapping.getOrDefault(originalDepId, originalDepId);
                    Task dep = plan.getTask(mappedDepId);
                    if (dep != null) {
                        task.addDependency(mappedDepId);
                        dep.addDependent(task.getId());
                    }
                }
            }
        }

        if (!plan.computeExecutionOrder()) {
            throw new IOException("计划中存在循环依赖");
        }
        return plan;
    }

    private ExecutionPlan createMinimalPlan(String goal) {
        ExecutionPlan plan = new ExecutionPlan(generatePlanId(), goal);
        plan.setSummary("直接执行简单任务：" + goal);
        plan.addTask(new Task("task_1", goal));
        if (!plan.computeExecutionOrder()) {
            throw new IllegalStateException("简单计划不应出现循环依赖");
        }
        return plan;
    }

    private boolean isSimpleGoal(String goal) {
        boolean hasMultiStepCue = goal.contains("然后")
                || goal.contains("并且")
                || goal.contains("并")
                || goal.contains("再")
                || goal.contains("最后")
                || goal.contains("同时")
                || goal.contains("先")
                || goal.contains("之后")
                || goal.contains("接着")
                || goal.contains("以及");
        if (hasMultiStepCue) {
            return false;
        }
        if (goal.length() > 30) {
            return false;
        }
        return goal.contains("列出")
                || goal.contains("查看")
                || goal.contains("读取")
                || goal.contains("显示")
                || goal.contains("执行")
                || goal.contains("运行")
                || goal.contains("搜索")
                || goal.contains("当前目录")
                || goal.contains("文件");
    }

    private static String generatePlanId() {
        return "plan_" + System.currentTimeMillis();
    }
}
