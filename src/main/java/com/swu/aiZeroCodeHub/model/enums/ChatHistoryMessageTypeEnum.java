package com.swu.aiZeroCodeHub.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息类型枚举
 */
@Getter
public enum ChatHistoryMessageTypeEnum {

    USER("用户消息", "userMessage"),
    AI("AI消息", "aiMessage");

    private final String text;
    private final String value;


    ChatHistoryMessageTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 获取值列表
     */
    public static List<String> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     *
     */
    public static ChatHistoryMessageTypeEnum getEnumByValue(String value) {
        if (value == null) {
            return null;
        }
        for (ChatHistoryMessageTypeEnum anEnum : values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null; // 明确返回null，表示未找到
    }

    /**
     * 安全校验方法
     * 检查给定的value是否是一个有效的枚举值
     */
    public static boolean isValidValue(String value) {
        return getEnumByValue(value) != null;
    }
}