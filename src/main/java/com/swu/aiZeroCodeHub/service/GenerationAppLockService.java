package com.swu.aiZeroCodeHub.service;

import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 应用级生成租约。每个 Redis key 只保存一个 runId，释放时必须再次校验值。
 */
@Service
public class GenerationAppLockService {

    public static final String APP_LOCK_KEY_PREFIX = "agent:app:active:";
    public static final Duration APP_LOCK_TTL = Duration.ofMinutes(25);

    private static final RedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 0
            end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
            return 1
            """, Long.class);

    private static final RedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);

    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final ScheduledExecutorService renewalExecutor = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "agent-app-lock-renewal");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, ScheduledFuture<?>> renewals = new ConcurrentHashMap<>();

    public boolean acquire(Long appId, String runId) {
        String key = key(appId);
        validateRunId(runId);
        Long result = stringRedisTemplate.execute(ACQUIRE_SCRIPT, Collections.singletonList(key),
                runId, String.valueOf(APP_LOCK_TTL.toSeconds()));
        boolean acquired = Long.valueOf(1L).equals(result);
        if (acquired) {
            ScheduledFuture<?> future = renewalExecutor.scheduleAtFixedRate(
                    () -> renew(appId, runId), APP_LOCK_TTL.toSeconds() / 3,
                    Math.max(1, APP_LOCK_TTL.toSeconds() / 3), TimeUnit.SECONDS);
            ScheduledFuture<?> previous = renewals.put(runId, future);
            if (previous != null) {
                previous.cancel(false);
            }
        }
        return acquired;
    }

    public boolean renew(Long appId, String runId) {
        Long result = stringRedisTemplate.execute(RENEW_SCRIPT, Collections.singletonList(key(appId)),
                runId, String.valueOf(APP_LOCK_TTL.toSeconds()));
        return Long.valueOf(1L).equals(result);
    }

    public boolean release(Long appId, String runId) {
        ScheduledFuture<?> renewal = renewals.remove(runId);
        if (renewal != null) {
            renewal.cancel(false);
        }
        Long result = stringRedisTemplate.execute(RELEASE_SCRIPT, Collections.singletonList(key(appId)), runId);
        return Long.valueOf(1L).equals(result);
    }

    public String key(Long appId) {
        if (appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "appId 无效");
        }
        return APP_LOCK_KEY_PREFIX + appId;
    }

    @PreDestroy
    public void shutdown() {
        renewals.values().forEach(future -> future.cancel(false));
        renewalExecutor.shutdownNow();
    }

    private void validateRunId(String runId) {
        try {
            UUID.fromString(runId);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "runId 无效");
        }
    }
}
