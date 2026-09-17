package com.swu.aiZeroCodeHub.core.streamHandler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;

/**
 * 将模型流转换为可写入聊天历史的安全文本。
 */
public final class HistoryContentSanitizer {

    private HistoryContentSanitizer() {
    }

    /**
     * HTML/多文件模式的消息是普通文本；Vue 模式只提取 AI 文本和工具摘要。
     */
    public static String sanitize(String chunk, CodeGenTypeEnum codeGenType) {
        if (chunk == null || codeGenType != CodeGenTypeEnum.VUE_PROJECT) {
            return chunk == null ? "" : chunk;
        }
        try {
            JSONObject json = JSONUtil.parseObj(chunk);
            String type = json.getStr("type");
            if ("ai_response".equals(type)) {
                return StrUtil.blankToDefault(json.getStr("data"), "");
            }
            if ("tool_executed".equals(type)) {
                String toolName = json.getStr("name");
                JSONObject arguments = StrUtil.isBlank(json.getStr("arguments"))
                        ? JSONUtil.createObj()
                        : JSONUtil.parseObj(json.getStr("arguments"));
                String path = arguments.getStr("relativeFilePath");
                if (StrUtil.isBlank(path)) {
                    path = arguments.getStr("relativeDirPath");
                }
                return buildToolSummary(toolName, path);
            }
        } catch (Exception ignored) {
            // 未知的 Vue JSON 不写入历史，避免把完整工具参数或源码落库。
        }
        return "";
    }

    public static String buildToolSummary(String toolName, String path) {
        String target = StrUtil.isBlank(path) ? "" : "：" + path;
        if ("writeFile".equals(toolName)) {
            return "已写入文件" + target;
        }
        if ("modifyFile".equals(toolName)) {
            return "已修改文件" + target;
        }
        if ("readFile".equals(toolName)) {
            return "已读取文件" + target;
        }
        if ("deleteFile".equals(toolName)) {
            return "已删除文件" + target;
        }
        if ("getProjectFileTree".equals(toolName)) {
            return "已获取目录结构" + target;
        }
        return "工具执行完成：" + StrUtil.blankToDefault(toolName, "未知工具") + target;
    }
}
