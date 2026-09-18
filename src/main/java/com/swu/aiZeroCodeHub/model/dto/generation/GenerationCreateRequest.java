package com.swu.aiZeroCodeHub.model.dto.generation;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建一次代码生成运行的 HTTP 请求。runId 不属于客户端请求字段。
 */
@Data
public class GenerationCreateRequest implements Serializable {

    private Long appId;
    private String userMessage;
    private String codeGenType;
    private String executionMode;
}
