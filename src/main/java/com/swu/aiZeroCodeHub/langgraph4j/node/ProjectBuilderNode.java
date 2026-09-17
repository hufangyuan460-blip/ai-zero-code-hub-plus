package com.swu.aiZeroCodeHub.langgraph4j.node;

import com.swu.aiZeroCodeHub.core.builder.VueProjectBuilder;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.langgraph4j.WorkflowEventSupport;
import com.swu.aiZeroCodeHub.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import com.swu.aiZeroCodeHub.utils.SpringContextUtil;

import java.io.File;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 项目构建节点。
 */
@Slf4j
public class ProjectBuilderNode {

    /** 兼容旧的示例工作流；正式入口使用带依赖参数的工厂方法。 */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return create(SpringContextUtil.getBean(VueProjectBuilder.class));
    }

    public static AsyncNodeAction<MessagesState<String>> create(VueProjectBuilder vueBuilder) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 项目构建，appId={}", context.getAppId());
            WorkflowEventSupport.stepStarted(context, "项目构建");

            String generatedCodeDir = context.getGeneratedCodeDir();
            boolean buildSuccess = vueBuilder.buildProject(generatedCodeDir);
            if (!buildSuccess) {
                String message = "工作流在项目构建阶段失败：Vue 项目构建失败";
                context.setErrorMessage(message);
                WorkflowEventSupport.stepCompleted(context, "项目构建", "失败");
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, message);
            }

            String buildResultDir = generatedCodeDir + File.separator + "dist";
            context.setCurrentStep("项目构建");
            context.setBuildResultDir(buildResultDir);
            WorkflowEventSupport.stepCompleted(context, "项目构建", "完成");
            log.info("项目构建节点完成，最终目录: {}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }
}
