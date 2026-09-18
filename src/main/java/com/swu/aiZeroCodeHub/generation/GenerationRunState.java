package com.swu.aiZeroCodeHub.generation;

import com.swu.aiZeroCodeHub.model.enums.ExecutionModeEnum;

import java.util.List;
import java.util.Set;

/**
 * Redis 中保存的单次生成运行状态。
 */
public record GenerationRunState(
        String runId,
        Long appId,
        Long userId,
        ExecutionModeEnum executionMode,
        GenerationRunStatus status,
        String currentStep,
        String startedAt,
        String updatedAt,
        String finishedAt,
        String errorMessage,
        int retryCount,
        int maxRetryCount,
        boolean cancelRequested,
        String validationFingerprint,
        Set<String> seenFingerprints,
        String artifactHash,
        List<String> changedFiles,
        int validationIssueCount,
        int llmCallCount,
        int toolCallCount,
        int buildCallCount,
        int repairAttempt,
        int maxRepairAttempts
) {
    public GenerationRunState(String runId,
                              Long appId,
                              Long userId,
                              ExecutionModeEnum executionMode,
                              GenerationRunStatus status,
                              String currentStep,
                              String startedAt,
                              String updatedAt,
                              String finishedAt,
                              String errorMessage,
                              int retryCount,
                              int maxRetryCount,
                              boolean cancelRequested) {
        this(runId, appId, userId, executionMode, status, currentStep, startedAt, updatedAt,
                finishedAt, errorMessage, retryCount, maxRetryCount, cancelRequested,
                null, Set.of(), null, List.of(), -1, 0, 0, 0, 0, 1);
    }

    /** Compatibility constructor for callers created before validation/cost counters were added. */
    public GenerationRunState(String runId, Long appId, Long userId, ExecutionModeEnum executionMode,
                              GenerationRunStatus status, String currentStep, String startedAt,
                              String updatedAt, String finishedAt, String errorMessage, int retryCount,
                              int maxRetryCount, boolean cancelRequested, String validationFingerprint,
                              Set<String> seenFingerprints, String artifactHash, List<String> changedFiles,
                              int llmCallCount, int toolCallCount, int repairAttempt, int maxRepairAttempts) {
        this(runId, appId, userId, executionMode, status, currentStep, startedAt, updatedAt, finishedAt,
                errorMessage, retryCount, maxRetryCount, cancelRequested, validationFingerprint,
                seenFingerprints, artifactHash, changedFiles, -1, llmCallCount, toolCallCount, 0,
                repairAttempt, maxRepairAttempts);
    }

    public boolean isActive() {
        return status == GenerationRunStatus.PENDING || status == GenerationRunStatus.RUNNING;
    }
}
