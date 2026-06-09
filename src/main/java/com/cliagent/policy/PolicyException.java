package com.cliagent.policy;

/** 策略围栏拒绝（路径越界、危险命令等）。 */
public class PolicyException extends RuntimeException {

    public PolicyException(String message) {
        super(message);
    }
}
