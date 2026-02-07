package com.swu.aiZeroCodeHub.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 腾讯云COS配置类
 * @author yupi
 */
@Configuration
@ConfigurationProperties(prefix = "cos.client")
@Data
public class CosClientConfig {
    /**
     * 域名
     */
    private String host;

    /**
     * secretId
     */
    private String secretId;

    /**
     * 密钥（注意不要泄露）
     */
    private String secretKey;

    /**
     * 区域
     */
    private String region;

    /**
     * 桶名
     */
    private String bucket;

    @Bean
    public COSClient cosClient() {
        // 初始化用户身份信息(secretId, secretKey)
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);

        // 设置bucket的区域，COS地域的简称请参照 https://www.qcloud.com/document/product/436/6224
        ClientConfig clientConfig = new ClientConfig(new Region(region));

        // 在腾讯云 COS SDK 5.6.227 中，设置自定义域名需要使用不同的方式
        // 通常自定义域名是在上传/下载时通过构造完整的URL来使用，而不是在ClientConfig中设置
        // 这里我们暂时注释掉这部分代码，后续如果需要可以单独处理自定义域名逻辑
        /*
        if (StringUtils.hasText(host)) {
            // 注意：在腾讯云 COS SDK 5.6.227 中，ClientConfig 没有 setEndpoint 方法
            // 如果需要使用自定义域名，可以考虑以下方式之一：
            // 1. 在上传/下载时手动构造URL
            // 2. 通过设置 EndpointBuilder（但需要查看具体版本是否有此方法）
            String endpoint = host.replaceAll("^https?://", "");
            // clientConfig.setEndpoint(endpoint); // 这个方法在5.6.227版本中不存在
        }
        */

        // 生成cos客户端
        return new COSClient(cred, clientConfig);
    }
}