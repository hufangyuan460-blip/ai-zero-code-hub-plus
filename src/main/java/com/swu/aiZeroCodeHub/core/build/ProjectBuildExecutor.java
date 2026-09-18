package com.swu.aiZeroCodeHub.core.build;

public interface ProjectBuildExecutor {
    BuildExecutionResult build(BuildRequest request);

    /** Whether this executor is actually available in the current deployment. */
    default boolean isAvailable() {
        return true;
    }
}
