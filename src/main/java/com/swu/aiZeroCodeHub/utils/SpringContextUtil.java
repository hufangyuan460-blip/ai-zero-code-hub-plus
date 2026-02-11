package com.swu.aiZeroCodeHub.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextUtil.applicationContext = applicationContext;
    }

    public static <T> T getBean(Class<T> clazz) {
        ApplicationContext ctx = applicationContext;
        if (ctx == null) {
            throw new IllegalStateException("ApplicationContext is not initialized");
        }
        return ctx.getBean(clazz);
    }

    public static Object getBean(String name) {
        ApplicationContext ctx = applicationContext;
        if (ctx == null) {
            throw new IllegalStateException("ApplicationContext is not initialized");
        }
        return ctx.getBean(name);
    }
}
