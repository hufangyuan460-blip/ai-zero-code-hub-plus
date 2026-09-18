package com.swu.aiZeroCodeHub.generation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 服务端运行治理参数。客户端不能覆盖这些值。
 */
@Data
@ConfigurationProperties(prefix = "agent.generation")
public class GenerationRunProperties {

    private Duration directTimeout = Duration.ofMinutes(10);
    private Duration workflowTimeout = Duration.ofMinutes(20);
    private Duration workflowNodeTimeout = Duration.ofMinutes(10);
    private int maxRetryCount = 2;
    private int maxRepairAttempts = 1;
    private int maxLlmCalls = 2;
    private int maxToolCalls = 20;
    private Duration artifactRetention = Duration.ofHours(24);
    private boolean remoteBuildEnabled = false;
    private String remoteBuildUrl = "";
    private long generationRate = 1;
    private Duration generationRateWindow = Duration.ofSeconds(5);

    public Duration effectiveDirectTimeout() {
        return positiveOrDefault(directTimeout, Duration.ofMinutes(10));
    }

    public Duration effectiveWorkflowTimeout() {
        return positiveOrDefault(workflowTimeout, Duration.ofMinutes(20));
    }

    public Duration effectiveWorkflowNodeTimeout() {
        return positiveOrDefault(workflowNodeTimeout, Duration.ofMinutes(10));
    }

    public int effectiveMaxRetryCount() {
        return maxRetryCount < 0 ? 2 : Math.min(maxRetryCount, 2);
    }

    public int effectiveMaxRepairAttempts() {
        return maxRepairAttempts < 0 ? 1 : Math.min(maxRepairAttempts, 1);
    }

    public int effectiveMaxLlmCalls() {
        return maxLlmCalls < 0 ? 2 : Math.min(maxLlmCalls, 2);
    }

    public int effectiveMaxToolCalls() {
        return maxToolCalls < 0 ? 20 : Math.min(maxToolCalls, 20);
    }

    public Duration effectiveArtifactRetention() {
        return positiveOrDefault(artifactRetention, Duration.ofHours(24));
    }

    public long effectiveGenerationRate() {
        return generationRate <= 0 ? 1 : Math.min(generationRate, 100);
    }

    public long effectiveGenerationRateWindowSeconds() {
        Duration value = positiveOrDefault(generationRateWindow, Duration.ofSeconds(5));
        return Math.max(1, Math.min(value.toSeconds(), 3600));
    }

    private Duration positiveOrDefault(Duration value, Duration defaultValue) {
        return value == null || value.isZero() || value.isNegative() ? defaultValue : value;
    }
}
