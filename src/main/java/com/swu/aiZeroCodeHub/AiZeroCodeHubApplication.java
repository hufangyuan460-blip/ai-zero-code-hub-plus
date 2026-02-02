package com.swu.aiZeroCodeHub;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@EnableAspectJAutoProxy(exposeProxy=true)
@ConfigurationPropertiesScan
@MapperScan("com.swu.aiZeroCodeHub.mapper")
@ComponentScan(
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.swu\\.aiZeroCodeHub\\.generatorResult\\..*"
        )
)
public class AiZeroCodeHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiZeroCodeHubApplication.class, args);
    }

}
