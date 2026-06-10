package com.cliagent.llm;

/**
 * SSE 流式回调：每收到一段 assistant content delta 时触发。
 */
@FunctionalInterface
public interface StreamListener {

    StreamListener NO_OP = delta -> {};

    void onContentDelta(String delta);
}
