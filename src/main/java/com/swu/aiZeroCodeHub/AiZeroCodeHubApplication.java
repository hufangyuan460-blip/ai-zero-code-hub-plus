package com.swu.aiZeroCodeHub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy=true)
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
