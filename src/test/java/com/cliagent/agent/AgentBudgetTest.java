package com.cliagent.agent;

import com.cliagent.llm.LlmClient.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentBudgetTest {

    private static final ToolCall LIST_DIR_CALL = new ToolCall(
            "call_1",
            new ToolCall.Function("list_dir", "{\"path\":\".\"}")
    );

    @Test
    void defaultsUseExpectedLimits() {
        AgentBudget budget = AgentBudget.defaults();

        assertEquals(3, budget.stagnationWindow());
        assertEquals(10, budget.hardMaxIterations());
    }

    @Test
    void staysWithinBudgetInitially() {
        AgentBudget budget = AgentBudget.defaults();
        assertEquals(AgentBudget.ExitReason.WITHIN_BUDGET, budget.check());
    }

    @Test
    void hardIterationLimitTriggersAfterMaxRounds() {
        AgentBudget budget = new AgentBudget(3, 2);

        budget.beginIteration();
        assertEquals(AgentBudget.ExitReason.WITHIN_BUDGET, budget.check());

        budget.beginIteration();
        assertEquals(AgentBudget.ExitReason.HARD_ITERATION_LIMIT, budget.check());
        assertEquals("超过最大迭代次数 (2)，已停止。",
                budget.describeExit(AgentBudget.ExitReason.HARD_ITERATION_LIMIT));
    }

    @Test
    void stagnationDetectedAfterRepeatedToolCalls() {
        AgentBudget budget = new AgentBudget(3, 10);

        budget.recordToolCalls(List.of(LIST_DIR_CALL));
        assertEquals(AgentBudget.ExitReason.WITHIN_BUDGET, budget.check());

        budget.recordToolCalls(List.of(LIST_DIR_CALL));
        assertEquals(AgentBudget.ExitReason.WITHIN_BUDGET, budget.check());

        budget.recordToolCalls(List.of(LIST_DIR_CALL));
        assertEquals(AgentBudget.ExitReason.STAGNATION_DETECTED, budget.check());
        assertEquals("检测到连续 3 轮重复的工具调用，疑似死循环，已停止。",
                budget.describeExit(AgentBudget.ExitReason.STAGNATION_DETECTED));
    }

    @Test
    void differentToolCallsDoNotTriggerStagnation() {
        AgentBudget budget = new AgentBudget(3, 10);

        budget.recordToolCalls(List.of(LIST_DIR_CALL));
        budget.recordToolCalls(List.of(new ToolCall(
                "call_2",
                new ToolCall.Function("read_file", "{\"path\":\"README.md\"}")
        )));
        budget.recordToolCalls(List.of(LIST_DIR_CALL));

        assertEquals(AgentBudget.ExitReason.WITHIN_BUDGET, budget.check());
    }

    @Test
    void emptyToolCallsClearStagnationTracking() {
        AgentBudget budget = new AgentBudget(3, 10);

        budget.recordToolCalls(List.of(LIST_DIR_CALL));
        budget.recordToolCalls(List.of(LIST_DIR_CALL));
        budget.recordToolCalls(List.of());

        assertEquals(AgentBudget.ExitReason.WITHIN_BUDGET, budget.check());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new AgentBudget(1, 10));
        assertThrows(IllegalArgumentException.class, () -> new AgentBudget(3, 0));
    }
}
