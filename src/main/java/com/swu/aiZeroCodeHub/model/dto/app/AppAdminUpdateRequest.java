package com.swu.aiZeroCodeHub.model.dto.app;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppAdminUpdateRequest implements Serializable {

    private Long id;

    private String appName;

    private String cover;

    private Integer priority;

    private static final long serialVersionUID = 1L;
}
