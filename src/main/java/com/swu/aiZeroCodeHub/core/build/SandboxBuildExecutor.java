package com.swu.aiZeroCodeHub.core.build;

import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Production default: local npm execution is disabled until a real sandbox is configured. */
@Component
@Profile("!development")
@ConditionalOnProperty(name = "agent.generation.remote-build-enabled", havingValue = "false", matchIfMissing = true)
public class SandboxBuildExecutor implements ProjectBuildExecutor {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public BuildExecutionResult build(BuildRequest request) {
        return new BuildExecutionResult(false, "生产环境未配置沙箱构建执行器", -1, false,
                "SANDBOX_NOT_CONFIGURED");
    }
}
