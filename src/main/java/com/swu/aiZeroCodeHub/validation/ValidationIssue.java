package com.swu.aiZeroCodeHub.validation;

/** A bounded, user-safe deterministic validation finding. */
public record ValidationIssue(String code, String message, String filePath, boolean repairable) {
}
