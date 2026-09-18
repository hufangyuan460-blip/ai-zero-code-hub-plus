package com.swu.aiZeroCodeHub.core.build;

import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Fallback bean keeps startup deterministic while clearly reporting missing platform wiring. */
@Component
@Profile("!development")
@ConditionalOnMissingBean(RemoteBuildGateway.class)
public class UnavailableRemoteBuildGateway implements RemoteBuildGateway {
    @Override
    public BuildExecutionResult build(BuildRequest request) {
        return new BuildExecutionResult(false, "远程隔离构建网关未接入", -1, false,
                "REMOTE_GATEWAY_NOT_CONFIGURED");
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
