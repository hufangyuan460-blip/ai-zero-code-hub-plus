package com.swu.aiZeroCodeHub.core.build;

/**
 * Boundary owned by the deployment platform. Implementations may call a remote
 * worker or an isolated build service; the application never runs npm itself.
 */
public interface RemoteBuildGateway {
    BuildExecutionResult build(BuildRequest request);

    default boolean isAvailable() {
        return true;
    }
}
