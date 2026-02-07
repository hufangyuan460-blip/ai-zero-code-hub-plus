package com.swu.aiZeroCodeHub.config;

import com.swu.aiZeroCodeHub.constant.AppConstant;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                //允许cookie
                .allowCredentials(true)
                //放行域名
                .allowedOriginPatterns("*")
                .allowedHeaders("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .exposedHeaders("*");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射生成代码的预览路径：/static/** -> 本地文件系统 CODE_OUTPUT_ROOT_DIR
        registry.addResourceHandler("/static/**")
                .addResourceLocations("file:" + AppConstant.CODE_OUTPUT_ROOT_DIR + "/");

        // 映射部署代码的访问路径：/app/** -> 本地文件系统 CODE_DEPLOY_ROOT_DIR
        registry.addResourceHandler("/app/**")
                .addResourceLocations("file:" + AppConstant.CODE_DEPLOY_ROOT_DIR + "/");
    }
}
