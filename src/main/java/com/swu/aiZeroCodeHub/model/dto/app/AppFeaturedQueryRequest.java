package com.swu.aiZeroCodeHub.model.dto.app;

import com.swu.aiZeroCodeHub.common.dto.PageRequest;
import lombok.Data;

import java.io.Serializable;

@Data
public class AppFeaturedQueryRequest extends PageRequest implements Serializable {

    private String appName;

    private static final long serialVersionUID = 1L;
}
