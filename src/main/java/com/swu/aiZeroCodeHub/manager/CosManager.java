package com.swu.aiZeroCodeHub.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.CannedAccessControlList;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.swu.aiZeroCodeHub.config.CosClientConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * COS对象存储器
 * @author yupi
 */
@Component
@Slf4j
public class CosManager {
    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     * @param key 唯一键
     * @param file 文件
     * @return 上传结果
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        // 设置对象为公有读，便于前端直接显示
        putObjectRequest.setCannedAcl(CannedAccessControlList.PublicRead);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传文件到COS并返回访问URL
     * @param key COS对象键（完整路径）
     * @param file 要上传的文件
     * @return 文件的访问URL，失败返回null
     */
    public String uploadFile(String key, File file) {
        if (StringUtils.hasText(key) && key.startsWith("/")) {
            key = key.substring(1);
        }
        // 上传文件
        PutObjectResult result = putObject(key, file);
        if (result != null) {
            // 构建访问URL
            String host = cosClientConfig.getHost();
            String url;

            if (StringUtils.hasText(host)) {
                // 如果配置了自定义域名，使用自定义域名
                // 确保host以/结尾，key不以/开头
                if (host.endsWith("/")) {
                    host = host.substring(0, host.length() - 1);
                }
                url = host + "/" + key;
            } else {
                // 否则使用默认的COS域名
                // 格式：https://{bucket}.cos.{region}.myqcloud.com/{key}
                String region = cosClientConfig.getRegion();
                String bucket = cosClientConfig.getBucket();
                url = String.format("https://%s.cos.%s.myqcloud.com/%s", bucket, region, key);
            }

            log.info("文件上传COS成功: {} -> {}", file.getName(), url);
            // 验证URL是否可读
            if (isUrlReachable(url)) {
                return url;
            } else {
                log.warn("COS对象上传后不可访问（可能是权限或URL问题），将回退到本地: {}", url);
                return null;
            }
        } else {
            log.error("文件上传COS失败，返回结果为空");
            return null;
        }
    }

    private boolean isUrlReachable(String urlStr) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int code = connection.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
