package com.swu.aiZeroCodeHub.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swu.aiZeroCodeHub.core.build.BuildExecutionResult;
import com.swu.aiZeroCodeHub.core.build.ProjectBuildExecutor;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeterministicValidationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void validatesBasicHtmlWithoutReadingSourceIntoTheReport() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), "<html><body>ok</body></html>");
        ProjectBuildExecutor executor = mock(ProjectBuildExecutor.class);
        DeterministicValidationService service = new DeterministicValidationService(new ObjectMapper(), executor);

        ValidationReport report = service.validate(tempDir, CodeGenTypeEnum.HTML);

        assertTrue(report.passed());
        assertTrue(report.summary().contains("确定性验证"));
        assertFalse(report.summary().contains("<html>"));
        assertTrue(report.affectedFiles().contains("index.html"));
    }

    @Test
    void rejectsIncompleteMultiFileProjectWithRepairableFinding() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), "<html></html>");
        Files.writeString(tempDir.resolve("bad.json"), "{");
        ProjectBuildExecutor executor = mock(ProjectBuildExecutor.class);
        DeterministicValidationService service = new DeterministicValidationService(new ObjectMapper(), executor);

        ValidationReport report = service.validate(tempDir, CodeGenTypeEnum.MULTI_FILE);

        assertFalse(report.passed());
        assertTrue(report.repairable());
        assertTrue(report.issues().stream().anyMatch(issue -> "FILE_MISSING".equals(issue.code())));
        assertTrue(report.issues().stream().anyMatch(issue -> "JSON_INVALID".equals(issue.code())));
    }

    @Test
    void vueStructuralValidationDoesNotBuildBeforeProjectBuilder() throws Exception {
        Files.writeString(tempDir.resolve("package.json"), "{\"scripts\":{\"build\":\"vite build\"}}");
        Files.createDirectories(tempDir.resolve("dist"));
        ProjectBuildExecutor executor = mock(ProjectBuildExecutor.class);
        when(executor.build(any())).thenReturn(new BuildExecutionResult(true, "ok", 0, false, null));
        DeterministicValidationService service = new DeterministicValidationService(new ObjectMapper(), executor);

        ValidationReport report = service.validate(tempDir, CodeGenTypeEnum.VUE_PROJECT);

        assertTrue(report.passed());
        verify(executor, never()).build(any());
    }

    @Test
    void vueBuildResultIsIncludedInFinalValidationWithoutExposingOutput() throws Exception {
        Files.writeString(tempDir.resolve("package.json"), "{\"scripts\":{\"build\":\"vite build\"}}");
        ProjectBuildExecutor executor = mock(ProjectBuildExecutor.class);
        DeterministicValidationService service = new DeterministicValidationService(new ObjectMapper(), executor);

        ValidationReport report = service.validateBuiltArtifact(tempDir, CodeGenTypeEnum.VUE_PROJECT,
                new BuildExecutionResult(false, "npm token=secret /home/user/project", 1, false,
                        "NON_ZERO_EXIT"));

        assertFalse(report.passed());
        assertTrue(report.issues().stream().anyMatch(issue -> "BUILD_FAILED".equals(issue.code())));
        assertFalse(report.summary().contains("secret"));
        verify(executor, never()).build(any());
    }
}
