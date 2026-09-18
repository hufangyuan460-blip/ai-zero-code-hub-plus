package com.swu.aiZeroCodeHub.service;

import com.swu.aiZeroCodeHub.generation.GenerationRunProperties;
import com.swu.aiZeroCodeHub.generation.GenerationRunState;
import com.swu.aiZeroCodeHub.generation.GenerationRunStatus;
import com.swu.aiZeroCodeHub.model.enums.ExecutionModeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtifactBackupServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void removesOnlyExpiredLegalRunDirectories() throws Exception {
        ArtifactBackupService service = service(Duration.ofHours(1), null);
        Path runs = tempDir.resolve(".agent/runs");
        Files.createDirectories(runs);
        Path expired = Files.createDirectories(runs.resolve(UUID.randomUUID().toString()));
        Path fresh = Files.createDirectories(runs.resolve(UUID.randomUUID().toString()));
        Path invalid = Files.createDirectories(runs.resolve("not-a-uuid"));
        Files.writeString(expired.resolve("manifest"), "html");
        Files.writeString(fresh.resolve("manifest"), "html");
        Files.writeString(invalid.resolve("manifest"), "html");
        Files.setLastModifiedTime(expired, FileTime.from(Instant.now().minus(Duration.ofHours(2))));

        service.cleanupExpiredBackups();

        assertFalse(Files.exists(expired));
        assertTrue(Files.exists(fresh));
        assertTrue(Files.exists(invalid));
    }

    @Test
    void keepsExpiredDirectoryForActiveRunAndNeverThrowsOnCleanupFailure() throws Exception {
        String runId = UUID.randomUUID().toString();
        GenerationRunState active = new GenerationRunState(runId, 1L, 2L, ExecutionModeEnum.DIRECT,
                GenerationRunStatus.RUNNING, "代码生成", "now", "now", null, null, 0, 2, false);
        GenerationRunStateService stateService = mock(GenerationRunStateService.class);
        when(stateService.find(runId)).thenReturn(Optional.of(active));
        ArtifactBackupService service = service(Duration.ofSeconds(1), stateService);
        Path runs = tempDir.resolve(".agent/runs");
        Files.createDirectories(runs);
        Path activeDir = Files.createDirectories(runs.resolve(runId));
        Files.setLastModifiedTime(activeDir, FileTime.from(Instant.now().minus(Duration.ofHours(2))));

        assertDoesNotThrow(service::cleanupExpiredBackups);
        assertTrue(Files.exists(activeDir));
    }

    @Test
    void deletionFailureIsOnlyLoggedAndDoesNotAbortCleanup() throws Exception {
        ArtifactBackupService service = new ArtifactBackupService(tempDir) {
            @Override
            void deleteTree(Path path) throws java.io.IOException {
                throw new java.io.IOException("simulated cleanup failure");
            }
        };
        GenerationRunProperties properties = new GenerationRunProperties();
        properties.setArtifactRetention(Duration.ofSeconds(1));
        ReflectionTestUtils.setField(service, "runProperties", properties);
        Path runs = tempDir.resolve(".agent/runs");
        Files.createDirectories(runs);
        Path expired = Files.createDirectories(runs.resolve(UUID.randomUUID().toString()));
        Files.setLastModifiedTime(expired, FileTime.from(Instant.now().minus(Duration.ofHours(2))));

        assertDoesNotThrow(service::cleanupExpiredBackups);
        assertTrue(Files.exists(expired));
    }

    private ArtifactBackupService service(Duration retention, GenerationRunStateService stateService) {
        ArtifactBackupService service = new ArtifactBackupService(tempDir);
        GenerationRunProperties properties = new GenerationRunProperties();
        properties.setArtifactRetention(retention);
        ReflectionTestUtils.setField(service, "runProperties", properties);
        ReflectionTestUtils.setField(service, "runStateService", stateService);
        return service;
    }
}
