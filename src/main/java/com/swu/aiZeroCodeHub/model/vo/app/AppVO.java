package com.swu.aiZeroCodeHub.model.vo.app;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppVO {

    private Long id;

    private String appName;

    private String cover;

    private String initPrompt;

    private String codeGenType;

    private String deployKey;

    private LocalDateTime deployedTime;

    private Integer priority;

    private Long userId;

    private LocalDateTime editTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
