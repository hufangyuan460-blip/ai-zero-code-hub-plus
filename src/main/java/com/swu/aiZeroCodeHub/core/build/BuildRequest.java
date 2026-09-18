package com.swu.aiZeroCodeHub.core.build;

import java.nio.file.Path;
import java.time.Duration;

public record BuildRequest(Path workingDirectory, Duration timeout, int maxOutputChars,
                           long maxMemoryMb, int maxCpuPercent) {
}
