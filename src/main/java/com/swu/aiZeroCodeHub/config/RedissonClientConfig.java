package com.swu.aiZeroCodeHub.config;

import cn.hutool.core.util.StrUtil;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonClientConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        SingleServerConfig single = config.useSingleServer();
        String address = "redis://" + redisProperties.getHost() + ":" + redisProperties.getPort();
        single.setAddress(address);
        if (StrUtil.isNotBlank(redisProperties.getPassword())) {
            single.setPassword(redisProperties.getPassword());
        }
        single.setDatabase(redisProperties.getDatabase());
        return Redisson.create(config);
    }
}
