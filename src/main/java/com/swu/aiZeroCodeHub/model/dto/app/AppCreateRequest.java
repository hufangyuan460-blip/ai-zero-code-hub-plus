package com.swu.aiZeroCodeHub.model.dto.app;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppCreateRequest implements Serializable {

    private String appName;

    private String initPrompt;

    private String codeGenType;

    private String cover;

    private static final long serialVersionUID = 1L;
}
