package com.swu.aiZeroCodeHub.model.dto.app;

import com.swu.aiZeroCodeHub.common.dto.PageRequest;
import lombok.Data;

import java.io.Serializable;

@Data
public class AppAdminQueryRequest extends PageRequest implements Serializable {

    private Long id;

    private String appName;

    private String cover;

    private String initPrompt;

    private String codeGenType;

    private String deployKey;

    private Integer priority;

    private Long userId;

    private static final long serialVersionUID = 1L;
}
