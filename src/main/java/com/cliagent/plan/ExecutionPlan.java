package com.cliagent.plan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 由多个 {@link Task} 构成的执行计划，支持依赖拓扑排序。 */
public class ExecutionPlan {

    public enum PlanStatus {
        CREATED,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private final String id;
    private final String goal;
    private final Map<String, Task> tasks = new LinkedHashMap<>();
    private final List<String> executionOrder = new ArrayList<>();
    private PlanStatus status = PlanStatus.CREATED;
    private String summary;

    public ExecutionPlan(String id, String goal) {
        this.id = id;
        this.goal = goal;
    }

    public String getId() {
        return id;
    }

    public String getGoal() {
        return goal;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setStatus(PlanStatus status) {
        this.status = status;
    }

    public void addTask(Task task) {
        tasks.put(task.getId(), task);
        for (String depId : task.getDependencies()) {
            Task dep = tasks.get(depId);
            if (dep != null) {
                dep.addDependent(task.getId());
            }
        }
    }

    public Task getTask(String id) {
        return tasks.get(id);
    }

    public Collection<Task> getAllTasks() {
        return tasks.values();
    }

    public List<Task> getExecutableTasks() {
        return tasks.values().stream()
                .filter(task -> task.isExecutable(tasks))
                .toList();
    }

    public boolean computeExecutionOrder() {
        executionOrder.clear();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (Task task : tasks.values()) {
            if (!visited.contains(task.getId()) && !topologicalSort(task, visited, visiting)) {
                return false;
            }
        }
        return true;
    }

    private boolean topologicalSort(Task task, Set<String> visited, Set<String> visiting) {
        String id = task.getId();
        if (visiting.contains(id)) {
            return false;
        }
        if (visited.contains(id)) {
            return true;
        }

        visiting.add(id);
        for (String depId : task.getDependencies()) {
            Task dep = tasks.get(depId);
            if (dep != null && !topologicalSort(dep, visited, visiting)) {
                return false;
            }
        }
        visiting.remove(id);
        visited.add(id);
        executionOrder.add(id);
        return true;
    }

    public List<String> getExecutionOrder() {
        if (executionOrder.isEmpty()) {
            computeExecutionOrder();
        }
        return List.copyOf(executionOrder);
    }

    public double getProgress() {
        if (tasks.isEmpty()) {
            return 1.0;
        }
        long completed = tasks.values().stream()
                .filter(task -> task.getStatus() == Task.Status.COMPLETED)
                .count();
        return (double) completed / tasks.size();
    }

    public boolean isAllCompleted() {
        return tasks.values().stream()
                .allMatch(task -> task.getStatus() == Task.Status.COMPLETED);
    }

    public boolean hasFailed() {
        return tasks.values().stream()
                .anyMatch(task -> task.getStatus() == Task.Status.FAILED);
    }

    public void markStarted() {
        status = PlanStatus.RUNNING;
    }

    public void markCompleted() {
        status = PlanStatus.COMPLETED;
    }

    public void markFailed() {
        status = PlanStatus.FAILED;
    }

    /** 终端展示用：步骤编号、描述、依赖与状态。 */
    public String formatSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 执行计划");
        if (summary != null && !summary.isBlank()) {
            sb.append(": ").append(summary.trim());
        }
        sb.append('\n');
        sb.append("   目标: ").append(compactGoal(goal, 60)).append('\n');
        sb.append("   任务数: ").append(tasks.size()).append('\n');

        List<String> order = getExecutionOrder();
        for (int i = 0; i < order.size(); i++) {
            Task task = tasks.get(order.get(i));
            String deps = task.getDependencies().isEmpty()
                    ? "无"
                    : String.join(", ", task.getDependencies());
            sb.append(String.format("   %d. [%s] %s (依赖: %s)%n",
                    i + 1,
                    statusIcon(task.getStatus()),
                    task.getDescription(),
                    deps));
        }
        return sb.toString().trim();
    }

    private static String compactGoal(String rawGoal, int maxLength) {
        String singleLine = rawGoal == null ? "" : rawGoal
                .replace("\r\n", " ")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim()
                .replaceAll(" {2,}", " ");
        if (singleLine.length() <= maxLength) {
            return singleLine;
        }
        return singleLine.substring(0, maxLength - 3) + "...";
    }

    private static String statusIcon(Task.Status status) {
        return switch (status) {
            case PENDING -> " ";
            case RUNNING -> ">";
            case COMPLETED -> "✓";
            case FAILED -> "✗";
            case SKIPPED -> "-";
        };
    }

    @Override
    public String toString() {
        return "ExecutionPlan[" + id + ": " + goal + "] (" + tasks.size() + " tasks, " + status + ")";
    }
}
