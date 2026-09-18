package com.swu.aiZeroCodeHub.core.build;

public record BuildExecutionResult(boolean success, String outputSummary, int exitCode,
                                   boolean timedOut, String failureReason) {
}
