package com.cliagent.plan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionPlanTest {

    @Test
    void computeExecutionOrderRespectsDependencies() {
        ExecutionPlan plan = new ExecutionPlan("plan_1", "demo");
        Task task1 = new Task("task_1", "create project");
        Task task2 = new Task("task_2", "read pom", List.of("task_1"));
        Task task3 = new Task("task_3", "verify structure", List.of("task_2"));

        plan.addTask(task1);
        plan.addTask(task2);
        plan.addTask(task3);

        assertEquals(List.of("task_1", "task_2", "task_3"), plan.getExecutionOrder());
    }

    @Test
    void executableTasksWaitUntilDependenciesComplete() {
        ExecutionPlan plan = new ExecutionPlan("plan_2", "demo");
        Task task1 = new Task("task_1", "create project");
        Task task2 = new Task("task_2", "read pom", List.of("task_1"));

        plan.addTask(task1);
        plan.addTask(task2);

        assertEquals(List.of(task1), plan.getExecutableTasks());

        task1.markCompleted("done");

        assertEquals(List.of(task2), plan.getExecutableTasks());
    }

    @Test
    void addTaskBuildsDependentRelationship() {
        ExecutionPlan plan = new ExecutionPlan("plan_3", "demo");
        Task task1 = new Task("task_1", "create project");
        Task task2 = new Task("task_2", "read pom", List.of("task_1"));

        plan.addTask(task1);
        plan.addTask(task2);

        assertTrue(plan.getTask("task_1").getDependents().contains("task_2"));
    }

    @Test
    void detectsCycleInDependencies() {
        ExecutionPlan plan = new ExecutionPlan("plan_cycle", "demo");
        Task task1 = new Task("task_1", "step 1");
        Task task2 = new Task("task_2", "step 2", List.of("task_1"));
        plan.addTask(task1);
        plan.addTask(task2);

        task1.addDependency("task_2");
        task2.addDependent("task_1");

        assertFalse(plan.computeExecutionOrder());
    }

    @Test
    void formatSummaryListsTasksInOrder() {
        ExecutionPlan plan = new ExecutionPlan("plan_4", "创建并验证 demo");
        plan.setSummary("三步验证");
        plan.addTask(new Task("task_1", "创建 demo 目录"));
        plan.addTask(new Task("task_2", "列出 demo 目录", List.of("task_1")));

        String summary = plan.formatSummary();

        assertTrue(summary.contains("📋 执行计划: 三步验证"));
        assertTrue(summary.contains("1. [ ] 创建 demo 目录"));
        assertTrue(summary.contains("2. [ ] 列出 demo 目录 (依赖: task_1)"));
    }

    @Test
    void progressTracksCompletedTasks() {
        ExecutionPlan plan = new ExecutionPlan("plan_5", "demo");
        Task task1 = new Task("task_1", "step 1");
        Task task2 = new Task("task_2", "step 2");
        plan.addTask(task1);
        plan.addTask(task2);

        assertEquals(0.0, plan.getProgress(), 0.001);

        task1.markCompleted("ok");
        assertEquals(0.5, plan.getProgress(), 0.001);

        task2.markCompleted("ok");
        assertTrue(plan.isAllCompleted());
        assertEquals(1.0, plan.getProgress(), 0.001);
    }
}
