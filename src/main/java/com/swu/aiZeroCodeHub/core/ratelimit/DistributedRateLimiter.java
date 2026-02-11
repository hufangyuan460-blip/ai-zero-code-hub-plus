package com.swu.aiZeroCodeHub.core.ratelimit;

import jakarta.annotation.Resource;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

@Component
public class DistributedRateLimiter {

    @Resource
    private RedissonClient redissonClient;

    public boolean tryAcquire(String key, long rate, long intervalSeconds) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.trySetRate(RateType.OVERALL, rate, intervalSeconds, RateIntervalUnit.SECONDS);
        return rateLimiter.tryAcquire();
    }
}
