package com.swu.aiZeroCodeHub.service;

import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.generation.GenerationRequest;
import com.swu.aiZeroCodeHub.generation.GenerationRunState;
import com.swu.aiZeroCodeHub.generation.GenerationRunStatus;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.enums.ExecutionModeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class GenerationRunStateServiceTest {

    private StringRedisTemplate redisTemplate;
    private HashOperations hashOperations;
    private GenerationRunStateService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        service = new GenerationRunStateService();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
    }

    @Test
    void usesExactRunKeyAndCreatesPendingStateWithTwentyFourHourTtl() {
        String runId = UUID.randomUUID().toString();
        GenerationRequest request = new GenerationRequest(10L, 20L, "hello", CodeGenTypeEnum.HTML,
                ExecutionModeEnum.DIRECT, null, runId);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        when(hashOperations.entries("agent:run:" + runId)).thenReturn(Map.ofEntries(
                Map.entry("runId", runId),
                Map.entry("appId", "10"),
                Map.entry("userId", "20"),
                Map.entry("executionMode", "DIRECT"),
                Map.entry("status", "PENDING"),
                Map.entry("currentStep", "排队中"),
                Map.entry("startedAt", "2026-09-18T00:00:00Z"),
                Map.entry("updatedAt", "2026-09-18T00:00:00Z"),
                Map.entry("finishedAt", ""),
                Map.entry("errorMessage", ""),
                Map.entry("retryCount", "0"),
                Map.entry("maxRetryCount", "2"),
                Map.entry("cancelRequested", "false")));

        GenerationRunState state = service.create(request, 2);

        assertEquals("agent:run:" + runId, service.key(runId));
        assertEquals(GenerationRunStatus.PENDING, state.status());
        assertEquals(24 * 60 * 60, GenerationRunStateService.RUN_STATE_TTL.toSeconds());
        verify(redisTemplate).execute(any(RedisScript.class), org.mockito.ArgumentMatchers.eq(
                java.util.List.of("agent:run:" + runId)), any(Object[].class));
    }

    @Test
    void rejectsNonUuidRunKey() {
        assertThrows(BusinessException.class, () -> service.key("not-a-run-id"));
    }

    @Test
    void mapsAtomicCancelResultsToPermissionAndTerminalOutcomes() {
        String runId = UUID.randomUUID().toString();
        User owner = new User();
        owner.setId(20L);
        owner.setUserRole("user");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L)
                .thenReturn(-2L)
                .thenReturn(-3L);

        assertEquals(GenerationRunStateService.CancelResult.REQUESTED,
                service.requestCancel(runId, owner));
        assertEquals(GenerationRunStateService.CancelResult.FORBIDDEN,
                service.requestCancel(runId, owner));
        assertEquals(GenerationRunStateService.CancelResult.NOT_ACTIVE,
                service.requestCancel(runId, owner));
        assertTrue(GenerationRunStateService.RUN_KEY_PREFIX.equals("agent:run:"));
    }

    @Test
    void repairScriptReceivesIssueCountAndCurrentStepInSeparatePositions() {
        String runId = UUID.randomUUID().toString();
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        assertEquals(1, service.registerRepair(runId, "fingerprint", "artifact-hash",
                java.util.List.of("src/App.vue"), 2, "正在进行第 1 次修复"));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), args.capture());
        Object[] values = args.getValue();
        assertEquals("fingerprint", values[0]);
        assertEquals("artifact-hash", values[1]);
        assertEquals("src/App.vue", values[2]);
        assertEquals("2", values[3]);
        assertEquals("正在进行第 1 次修复", values[4]);
        assertEquals("currentStep', ARGV[5]", "currentStep', ARGV[5]");
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) ReflectionTestUtils.getField(
                GenerationRunStateService.class, "REPAIR_SCRIPT");
        assertTrue(script.getScriptAsString().contains("'currentStep', ARGV[5]"));
        assertTrue(script.getScriptAsString().contains("'validationIssueCount', ARGV[4]"));
    }

    @Test
    void readsPersistedValidationIssueCountAndCostCounters() {
        String runId = UUID.randomUUID().toString();
        when(hashOperations.entries("agent:run:" + runId)).thenReturn(Map.ofEntries(
                Map.entry("runId", runId), Map.entry("appId", "10"), Map.entry("userId", "20"),
                Map.entry("executionMode", "DIRECT"), Map.entry("status", "RUNNING"),
                Map.entry("currentStep", "确定性验证"), Map.entry("startedAt", "now"),
                Map.entry("updatedAt", "now"), Map.entry("finishedAt", ""), Map.entry("errorMessage", ""),
                Map.entry("retryCount", "1"), Map.entry("maxRetryCount", "2"),
                Map.entry("cancelRequested", "false"), Map.entry("validationFingerprint", "fp"),
                Map.entry("artifactHash", "hash"), Map.entry("changedFiles", "src/App.vue"),
                Map.entry("validationIssueCount", "3"), Map.entry("llmCallCount", "1"),
                Map.entry("toolCallCount", "2"), Map.entry("buildCallCount", "1"),
                Map.entry("repairAttempt", "1"), Map.entry("maxRepairAttempts", "1")));

        GenerationRunState state = service.getRequired(runId);

        assertEquals(3, state.validationIssueCount());
        assertEquals(1, state.buildCallCount());
        assertEquals(java.util.List.of("src/App.vue"), state.changedFiles());
    }
}
