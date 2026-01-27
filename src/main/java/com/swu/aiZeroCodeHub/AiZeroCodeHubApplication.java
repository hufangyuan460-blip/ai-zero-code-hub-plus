package com.swu.aiZeroCodeHub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy=true)
public class AiZeroCodeHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiZeroCodeHubApplication.class, args);
    }

}
