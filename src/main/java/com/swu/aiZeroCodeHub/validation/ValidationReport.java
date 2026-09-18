package com.swu.aiZeroCodeHub.validation;

import java.util.List;

/** Deterministic validation result; it deliberately does not contain source contents. */
public record ValidationReport(
        boolean passed,
        List<ValidationIssue> issues,
        String summary,
        String fingerprint,
        boolean repairable,
        List<String> affectedFiles,
        String artifactHash,
        String commandOutputSummary
) {
    public ValidationReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
        affectedFiles = affectedFiles == null ? List.of() : List.copyOf(affectedFiles);
    }

    public String repairPrompt() {
        StringBuilder prompt = new StringBuilder();
        issues.stream().limit(8).forEach(issue -> prompt.append("- ")
                .append(issue.message()).append(issue.filePath() == null ? "" : "（" + issue.filePath() + "）")
                .append('\n'));
        return prompt.toString().length() > 4_000
                ? prompt.substring(0, 4_000)
                : prompt.toString();
    }
}
