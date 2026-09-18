package com.swu.aiZeroCodeHub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationAppLockServiceTest {

    private StringRedisTemplate redisTemplate;
    private GenerationAppLockService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        service = new GenerationAppLockService();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
    }

    @Test
    void usesExactPerAppKeyAndReleasesOnlyTheSameRun() {
        String runId = UUID.randomUUID().toString();
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        assertTrue(service.acquire(42L, runId));
        assertTrue(service.release(42L, runId));
        assertEquals("agent:app:active:42", service.key(42L));
        verify(redisTemplate, org.mockito.Mockito.atLeastOnce()).execute(any(RedisScript.class),
                org.mockito.ArgumentMatchers.eq(java.util.List.of("agent:app:active:42")),
                any(Object[].class));
        service.shutdown();
    }
}
