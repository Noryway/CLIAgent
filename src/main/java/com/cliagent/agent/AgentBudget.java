package com.cliagent.agent;

import com.cliagent.llm.LlmClient.ToolCall;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * ReAct 循环的退出预算：硬轮数兜底 + 停滞检测（连续相同工具调用）。
 */
public class AgentBudget {

    public enum ExitReason {
        WITHIN_BUDGET,//在预算内
        STAGNATION_DETECTED,//检测到停滞
        HARD_ITERATION_LIMIT//超过最大迭代次数
    }

    private static final int DEFAULT_STAGNATION_WINDOW = 3; //默认停滞窗口
    private static final int DEFAULT_HARD_MAX_ITERATIONS = 10; //默认最大迭代次数

    private final int stagnationWindow; //停滞窗口
    private final int hardMaxIterations; //硬轮数兜底

    private final Deque<String> recentToolSignatures = new ArrayDeque<>(); //最近工具调用签名队列
    private int iteration; //迭代次数   
    private boolean stagnant; //是否停滞    

    /**
     * 构造函数
     * @param stagnationWindow 停滞窗口
     * @param hardMaxIterations 硬轮数兜底
     */
    public AgentBudget(int stagnationWindow, int hardMaxIterations) {
        //如果停滞窗口小于2，则抛出异常
        if (stagnationWindow < 2) {
            throw new IllegalArgumentException("stagnationWindow must be >= 2");
        }
        //如果硬轮数兜底小于1，则抛出异常
        if (hardMaxIterations <= 0) {
            throw new IllegalArgumentException("hardMaxIterations must be positive");
        }
        //设置停滞窗口和硬轮数兜底
        this.stagnationWindow = stagnationWindow;
        this.hardMaxIterations = hardMaxIterations;
    }

    /**
     * 默认构造函数
     * @return 默认的AgentBudget实例
     */
    public static AgentBudget defaults() {
        return new AgentBudget(DEFAULT_STAGNATION_WINDOW, DEFAULT_HARD_MAX_ITERATIONS);
    }

    /** 进入新一轮迭代，返回当前轮次（从 1 开始）。 */
    public int beginIteration() {
        return ++iteration;
    }

    /**
     * 记录本轮工具调用签名。最近 {@link #stagnationWindow} 轮签名完全相同则判定停滞。
     * 无工具调用时清空签名队列（模型改走纯文本路线视为脱离重复）。
     */
    public void recordToolCalls(List<ToolCall> toolCalls) {
        //如果工具调用列表为空，则清空最近工具调用签名队列和停滞状态
        if (toolCalls == null || toolCalls.isEmpty()) {
            recentToolSignatures.clear();
            stagnant = false;
            return;
        }
        //计算工具调用签名
        String signature = signatureOf(toolCalls);
        //将签名添加到最近工具调用签名队列
        recentToolSignatures.addLast(signature);
        //如果最近工具调用签名队列大于停滞窗口，则移除第一个签名
        while (recentToolSignatures.size() > stagnationWindow) {
            recentToolSignatures.removeFirst();
        }
        //如果最近工具调用签名队列等于停滞窗口，则检查是否停滞
        if (recentToolSignatures.size() == stagnationWindow) {
            //获取第一个签名
            String first = recentToolSignatures.peekFirst();
            //如果所有签名都等于第一个签名，则设置停滞状态
            stagnant = recentToolSignatures.stream().allMatch(sig -> sig.equals(first));
        }
    }

    /**
     * 检查是否需要退出
     * @return 退出原因
     */
    public ExitReason check() {
        //如果停滞状态为true，则返回停滞检测退出原因
        if (stagnant) {
            return ExitReason.STAGNATION_DETECTED;
        }
        //如果迭代次数大于等于硬轮数兜底，则返回硬轮数兜底退出原因
        if (iteration >= hardMaxIterations) {
            return ExitReason.HARD_ITERATION_LIMIT;
        }
        //如果迭代次数小于硬轮数兜底，则返回在预算内退出原因
        return ExitReason.WITHIN_BUDGET;
    }

    public int iteration() {
        return iteration;
    }

    public int hardMaxIterations() {
        return hardMaxIterations;
    }

    public int stagnationWindow() {
        return stagnationWindow;
    }

    /**
     * 描述退出原因
     * @param reason 退出原因
     * @return 描述
     */
    public String describeExit(ExitReason reason) {
        return switch (reason) {
            case WITHIN_BUDGET -> "未触发兜底条件";
            case STAGNATION_DETECTED ->
                    "检测到连续 " + stagnationWindow + " 轮重复的工具调用，疑似死循环，已停止。";
            case HARD_ITERATION_LIMIT ->
                    "超过最大迭代次数 (" + hardMaxIterations + ")，已停止。";
        };
    }

    /**
     * 计算工具调用签名
     * @param toolCalls 工具调用列表
     * @return 工具调用签名
     */
    private static String signatureOf(List<ToolCall> toolCalls) {
        StringBuilder sb = new StringBuilder();
        for (ToolCall tc : toolCalls) {
            sb.append(tc.function().name())
                    .append('|')
                    .append(tc.function().arguments())
                    .append(';');
        }
        return sb.toString();
    }
}
