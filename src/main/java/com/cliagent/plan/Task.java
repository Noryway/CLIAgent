package com.cliagent.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 计划中的单个可执行步骤。 */
public class Task {

    public enum Status {
        PENDING,//待执行
        RUNNING,//执行中
        COMPLETED,//完成
        FAILED,//失败
        SKIPPED//跳过
    }

    private final String id;
    private final String description;//任务描述
    private final List<String> dependencies;//依赖任务
    private final List<String> dependents;//被依赖任务
    private volatile Status status;
    private volatile String result;
    private volatile String error;//错误信息

    public Task(String id, String description) {
        this(id, description, List.of());
    }

    public Task(String id, String description, List<String> dependencies) {
        this.id = id;
        this.description = description;
        this.dependencies = new ArrayList<>(dependencies);
        this.dependents = new ArrayList<>();
        this.status = Status.PENDING;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Status getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public String getError() {
        return error;
    }

    public List<String> getDependencies() {
        return List.copyOf(dependencies);
    }

    public List<String> getDependents() {
        return List.copyOf(dependents);
    }

    public void addDependency(String taskId) {
        if (!dependencies.contains(taskId)) {
            dependencies.add(taskId);
        }
    }

    public void addDependent(String taskId) {
        if (!dependents.contains(taskId)) {
            dependents.add(taskId);
        }
    }

    public void markRunning() {
        status = Status.RUNNING;
    }

    public void markCompleted(String result) {
        status = Status.COMPLETED;
        this.result = result;
    }

    public void markFailed(String error) {
        status = Status.FAILED;
        this.error = error;
    }

    public void markSkipped() {
        status = Status.SKIPPED;
    }

    /** 所有依赖任务均已完成时，当前任务才可执行。 */
    public boolean isExecutable(Map<String, Task> allTasks) {
        if (status != Status.PENDING) {
            return false;
        }
        for (String depId : dependencies) {
            Task dep = allTasks.get(depId);
            if (dep == null || dep.getStatus() != Status.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "Task[" + id + ": " + description + "] (" + status + ")";
    }
}
