package com.swu.aiZeroCodeHub.core.build;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Production adapter for a platform-provided remote/isolated build service.
 * No local process or shell command is started here.
 */
@Component
@Profile("!development")
@ConditionalOnProperty(name = "agent.generation.remote-build-enabled", havingValue = "true")
public class RemoteSandboxBuildExecutor implements ProjectBuildExecutor {

    private final RemoteBuildGateway gateway;

    public RemoteSandboxBuildExecutor(RemoteBuildGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public BuildExecutionResult build(BuildRequest request) {
        if (!isAvailable()) {
            return new BuildExecutionResult(false, "远程隔离构建网关未接入", -1, false,
                    "REMOTE_GATEWAY_NOT_CONFIGURED");
        }
        return gateway.build(request);
    }

    @Override
    public boolean isAvailable() {
        return gateway != null && gateway.isAvailable();
    }
}
