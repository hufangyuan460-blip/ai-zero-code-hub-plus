package com.swu.aiZeroCodeHub.validation;

import com.swu.aiZeroCodeHub.core.build.BuildExecutionResult;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;

import java.nio.file.Path;

public interface ValidationService {
    ValidationReport validate(Path artifactDirectory, CodeGenTypeEnum generationType);

    default ValidationReport validateBuiltArtifact(Path artifactDirectory,
                                                   CodeGenTypeEnum generationType,
                                                   BuildExecutionResult buildResult) {
        return validate(artifactDirectory, generationType);
    }
}
