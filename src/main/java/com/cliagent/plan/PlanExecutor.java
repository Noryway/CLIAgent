package com.cliagent.plan;

import com.cliagent.agent.Agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Plan-and-Execute：生成计划后按依赖顺序逐步委托 {@link Agent} 执行。
 */
public class PlanExecutor {

    private final PlanParser planParser;

    public PlanExecutor(PlanParser planParser) {
        this.planParser = planParser;
    }

    /**
     * 规划并执行完整流程：展示计划摘要 → 顺序执行各步骤 → 返回最终汇总。
     */
    public String execute(String goal, Agent agent, boolean streaming) throws IOException {
        ExecutionPlan plan = planParser.createPlan(goal);
        System.out.println(plan.formatSummary());
        System.out.println();
        return executePlan(plan, agent, streaming);
    }

    /**
     * 执行已有计划（供测试或跳过规划阶段时使用）。
     */
    public String executePlan(ExecutionPlan plan, Agent agent, boolean streaming) throws IOException {
        if (plan.getAllTasks().isEmpty()) {
            return "⚠️ 计划中没有可执行任务。";
        }

        plan.markStarted();
        System.out.println("🚀 开始执行计划...\n");

        List<String> completedSummaries = new ArrayList<>();
        int totalSteps = plan.getAllTasks().size();

        while (true) {
            List<Task> readyTasks = getExecutableTasksInOrder(plan);
            if (readyTasks.isEmpty()) {
                break;
            }

            Task task = readyTasks.get(0);
            int stepIndex = plan.getExecutionOrder().indexOf(task.getId()) + 1;
            task.markRunning();
            System.out.printf("▶️ [%s] 步骤 %d/%d: %s%n",
                    task.getId(), stepIndex, totalSteps, task.getDescription());

            try {
                String prompt = buildTaskPrompt(plan.getGoal(), plan, task, completedSummaries);
                String stepResult = agent.run(prompt, streaming);
                if (!streaming) {
                    System.out.println("assistant> " + stepResult);
                }
                task.markCompleted(stepResult);
                completedSummaries.add(formatCompletedStep(task, stepResult));
                System.out.printf("✅ [%s] 完成%n%n", task.getId());
            } catch (IOException e) {
                task.markFailed(e.getMessage());
                plan.markFailed();
                return "❌ 计划执行失败 [" + task.getId() + "]: " + e.getMessage();
            }
        }

        if (!plan.isAllCompleted()) {
            plan.markFailed();
            return "⚠️ 计划未能继续推进，存在未满足依赖或未执行的任务。";
        }

        plan.markCompleted();
        return "✅ 计划执行完成！\n" + String.join("\n", completedSummaries);
    }

    static String buildTaskPrompt(String goal, ExecutionPlan plan, Task task,
                                  List<String> completedSummaries) {
        int stepIndex = plan.getExecutionOrder().indexOf(task.getId()) + 1;
        int totalSteps = plan.getAllTasks().size();

        StringBuilder sb = new StringBuilder();
        sb.append("【计划执行 - 步骤 ").append(task.getId())
                .append(" (").append(stepIndex).append('/').append(totalSteps).append(")】\n");
        sb.append("总目标: ").append(goal.trim()).append('\n');
        sb.append("当前步骤: ").append(task.getDescription()).append('\n');

        if (completedSummaries.isEmpty()) {
            sb.append("已完成步骤: 无\n");
        } else {
            sb.append("已完成步骤:\n");
            for (String summary : completedSummaries) {
                sb.append("- ").append(summary).append('\n');
            }
        }

        sb.append("请只完成当前步骤，完成后给出简要结果。");
        return sb.toString().trim();
    }

    private static List<Task> getExecutableTasksInOrder(ExecutionPlan plan) {
        Set<String> executableIds = plan.getExecutableTasks().stream()
                .map(Task::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return plan.getExecutionOrder().stream()
                .filter(executableIds::contains)
                .map(plan::getTask)
                .toList();
    }

    private static String formatCompletedStep(Task task, String result) {
        String brief = result == null ? "" : result.trim().replace('\n', ' ');
        if (brief.length() > 120) {
            brief = brief.substring(0, 120) + "...";
        }
        if (brief.isBlank()) {
            return task.getId() + ": " + task.getDescription();
        }
        return task.getId() + ": " + brief;
    }
}
