package com.swu.aiZeroCodeHub.core.build;

import org.junit.jupiter.api.Test;
import com.swu.aiZeroCodeHub.core.builder.VueProjectBuilder;
import java.nio.file.Files;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildExecutorBoundaryTest {

    @Test
    void productionDefaultRejectsWithoutRunningLocalCommands() {
        SandboxBuildExecutor executor = new SandboxBuildExecutor();

        assertFalse(executor.isAvailable());
        assertFalse(executor.build(request()).success());
    }

    @Test
    void configuredProductionGatewayIsTheOnlyBuildBoundary() {
        RemoteBuildGateway gateway = new RemoteBuildGateway() {
            @Override
            public BuildExecutionResult build(BuildRequest request) {
                return new BuildExecutionResult(true, "isolated build complete", 0, false, null);
            }
        };
        RemoteSandboxBuildExecutor executor = new RemoteSandboxBuildExecutor(gateway);

        assertTrue(executor.isAvailable());
        assertTrue(executor.build(request()).success());
    }

    @Test
    void developmentExecutorAvailabilityDoesNotImplyProductionAvailability() {
        assertTrue(new LocalDevelopmentBuildExecutor().isAvailable());
    }

    @Test
    void facadeInvokesTheConfiguredExecutorOnce() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        ProjectBuildExecutor executor = request -> {
            calls.incrementAndGet();
            return new BuildExecutionResult(true, "ok", 0, false, null);
        };
        Path project = Files.createTempDirectory("vue-build-test");
        Files.writeString(project.resolve("package.json"), "{\"scripts\":{\"build\":\"vite build\"}}");
        VueProjectBuilder builder = new VueProjectBuilder(executor);

        builder.buildProjectResult(project.toString());

        assertTrue(calls.get() == 1);
    }

    private BuildRequest request() {
        return new BuildRequest(Path.of("."), Duration.ofSeconds(1), 100, 128, 50);
    }
}
