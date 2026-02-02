package com.swu.aiZeroCodeHub.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
@Slf4j
public class RedisChatMemoryStoreConfig {
    private String host;
    private int port;
    private String password;
    private long ttl;


    @Bean
    public RedisChatMemoryStore redisChatMemoryStore() {
        String effectiveHost = StringUtils.hasText(host) ? host : "localhost";
        int effectivePort = port > 0 ? port : 6379;
        long effectiveTtl = ttl > 0 ? ttl : 3600;

        if (!StringUtils.hasText(host)) {
            log.warn("spring.data.redis.host 未配置，使用默认值 {}", effectiveHost);
        }

        RedisChatMemoryStore.Builder builder = RedisChatMemoryStore.builder()
                .host(effectiveHost)
                .port(effectivePort)
                .ttl(effectiveTtl);

        if (StringUtils.hasText(password)) {
            builder.password(password);
        }


        return builder.build();
    }
}
