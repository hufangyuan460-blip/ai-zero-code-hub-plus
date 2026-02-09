package com.swu.aiZeroCodeHub.manager;

import com.swu.aiZeroCodeHub.aiTool.AiTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具管理类，负责自动注入并管理所有 AiTool 实例。
 */
@Slf4j
@Component
public class ToolManager {

    private final List<AiTool> toolList;
    private final Map<String, AiTool> toolMap = new HashMap<>();

    @Autowired
    public ToolManager(List<AiTool> toolList) {
        this.toolList = toolList;
        for (AiTool tool : toolList) {
            String toolName = tool.getClass().getSimpleName();
            // 首字母小写，匹配 Bean 名称的常见规则，也方便后续查找
            // toolMap.put(Character.toLowerCase(toolName.charAt(0)) + toolName.substring(1), tool);
            // 或者直接用简单类名
            toolMap.put(toolName, tool);
            log.info("Registered AI Tool: {}", toolName);
        }
    }

    /**
     * 获取所有工具实例列表（数组形式，供 LangChain4j 使用）。
     *
     * @return 工具对象数组
     */
    public Object[] getAllTools() {
        return toolList.toArray();
    }

    /**
     * 根据工具名称获取工具实例。
     *
     * @param toolName 工具类名（如 FileWriteTool）
     * @return 工具实例，如果未找到则返回 null
     */
    public AiTool getToolByName(String toolName) {
        // 尝试直接匹配
        AiTool tool = toolMap.get(toolName);
        if (tool != null) {
            return tool;
        }
        // 尝试匹配首字母大写的类名（防止传入的是小写开头的 bean name）
        if (toolName.length() > 0) {
             String capitalized = Character.toUpperCase(toolName.charAt(0)) + toolName.substring(1);
             return toolMap.get(capitalized);
        }
        return null;
    }
}
