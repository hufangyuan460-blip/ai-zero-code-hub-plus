package com.swu.aiZeroCodeHub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 代码输出目录配置（code.output.root-dir），默认 ${user.dir}/tmp/code_output。
 */
@Data
@Component
@ConfigurationProperties(prefix = "code.output")
public class CodeOutputProperties {

    private String rootDir = System.getProperty("user.dir") + "/tmp/code_output";
}
