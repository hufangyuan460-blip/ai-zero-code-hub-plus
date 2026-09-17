package com.swu.aiZeroCodeHub.langgraph4j.node;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.swu.aiZeroCodeHub.langgraph4j.ai.CodeQualityCheckService;
import com.swu.aiZeroCodeHub.langgraph4j.WorkflowEventSupport;
import com.swu.aiZeroCodeHub.langgraph4j.model.QualityResult;
import com.swu.aiZeroCodeHub.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import com.swu.aiZeroCodeHub.utils.SpringContextUtil;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 代码质量检查节点
 */
@Slf4j
public class CodeQualityCheckNode {

    private static final int MAX_QUALITY_INPUT_LENGTH = 12_000;

    /** 兼容旧的示例工作流；正式入口使用带依赖参数的工厂方法。 */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return create(SpringContextUtil.getBean(CodeQualityCheckService.class));
    }

    public static AsyncNodeAction<MessagesState<String>> create(CodeQualityCheckService qualityCheckService) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码质量检查");
            WorkflowEventSupport.stepStarted(context, "代码质量检查");
            String generatedCodeDir = context.getGeneratedCodeDir();
            QualityResult qualityResult;
            try {
                // 1. 读取并拼接代码文件内容
                String codeContent = readAndConcatenateCodeFiles(generatedCodeDir);
                if (StrUtil.isBlank(codeContent)) {
                    log.warn("未找到可检查的代码文件");
                    qualityResult = QualityResult.builder()
                            .isValid(false)
                            .errors(List.of("未找到可检查的代码文件"))
                            .suggestions(List.of("请确保代码生成成功"))
                            .build();
                } else {
                    // 2. 调用 AI 进行代码质量检查
                    qualityResult = qualityCheckService.checkCodeQuality(codeContent);
                    log.info("代码质量检查完成 - 是否通过: {}", qualityResult.getIsValid());
                }
            } catch (Exception e) {
                log.error("代码质量检查异常: {}", e.getMessage(), e);
                qualityResult = QualityResult.builder()
                        .isValid(false)
                        .errors(List.of("代码质量检查失败，请稍后重试"))
                        .suggestions(List.of("确认项目文件完整后重新生成"))
                        .build();
            }
            // 3. 更新状态
            context.setCurrentStep("代码质量检查");
            context.setQualityResult(qualityResult);
            String status = Boolean.TRUE.equals(qualityResult.getIsValid()) ? "质检通过" : "质检未通过";
            WorkflowEventSupport.stepCompleted(context, "代码质量检查", status);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 需要检查的文件扩展名
     */
    private static final List<String> CODE_EXTENSIONS = Arrays.asList(
            ".html", ".htm", ".css", ".js", ".json", ".vue", ".ts", ".jsx", ".tsx"
    );

    /**
     * 读取并拼接代码目录下的所有代码文件
     */
    private static String readAndConcatenateCodeFiles(String codeDir) {
        if (StrUtil.isBlank(codeDir)) {
            return "";
        }
        File directory = new File(codeDir);
        if (!directory.exists() || !directory.isDirectory()) {
            log.error("代码目录不存在或不是目录: {}", codeDir);
            return "";
        }
        StringBuilder codeContent = new StringBuilder();
        codeContent.append("# 项目文件结构和代码内容\n\n");
        boolean[] truncated = {false};
        // 使用 Hutool 的 walkFiles 方法遍历所有文件
        FileUtil.walkFiles(directory, file -> {
            // 过滤条件：跳过隐藏文件、特定目录下的文件、非代码文件
            if (shouldSkipFile(file, directory)) {
                return;
            }
            if (isCodeFile(file) && codeContent.length() < MAX_QUALITY_INPUT_LENGTH) {
                String relativePath = FileUtil.subPath(directory.getAbsolutePath(), file.getAbsolutePath());
                String fileHeader = "## 文件: " + relativePath + "\n\n";
                String fileContent = FileUtil.readUtf8String(file);
                int remaining = MAX_QUALITY_INPUT_LENGTH - codeContent.length();
                String fileBlock = fileHeader + fileContent + "\n\n";
                if (fileBlock.length() > remaining) {
                    if (remaining > 0) {
                        codeContent.append(fileBlock, 0, remaining);
                    }
                    truncated[0] = true;
                } else {
                    codeContent.append(fileBlock);
                }
            }
        });
        if (truncated[0] && codeContent.length() < MAX_QUALITY_INPUT_LENGTH) {
            codeContent.append("\n[其余文件内容已省略]");
        }
        return codeContent.toString();
    }

    /**
     * 判断是否应该跳过此文件
     */
    private static boolean shouldSkipFile(File file, File rootDir) {
        String relativePath = FileUtil.subPath(rootDir.getAbsolutePath(), file.getAbsolutePath());
        // 跳过隐藏文件
        if (file.getName().startsWith(".")) {
            return true;
        }
        // 跳过特定目录下的文件
        return relativePath.contains("node_modules" + File.separator) ||
                relativePath.contains("dist" + File.separator) ||
                relativePath.contains("target" + File.separator) ||
                relativePath.contains(".git" + File.separator);
    }

    /**
     * 判断是否是需要检查的代码文件
     */
    private static boolean isCodeFile(File file) {
        String fileName = file.getName().toLowerCase();
        return CODE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
}
