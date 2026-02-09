package com.swu.aiZeroCodeHub.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface ProjectDownloadService {
    String createDownloadToken(Long appId, Long userId);

    boolean validateDownloadToken(String token, Long appId, Long userId);

    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletRequest request, HttpServletResponse response);
}
