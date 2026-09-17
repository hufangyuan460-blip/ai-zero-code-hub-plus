package com.swu.aiZeroCodeHub.langgraph4j.node;

import com.swu.aiZeroCodeHub.langgraph4j.ai.AiCodeGenTypeRoutingService;
import com.swu.aiZeroCodeHub.langgraph4j.state.WorkflowContext;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import com.swu.aiZeroCodeHub.utils.SpringContextUtil;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 智能路由工作节点
 */
@Slf4j
public class RouterNode {

    /** 兼容旧的示例工作流；正式入口使用带依赖参数的工厂方法。 */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return create(SpringContextUtil.getBean(AiCodeGenTypeRoutingService.class));
    }

    public static AsyncNodeAction<MessagesState<String>> create(AiCodeGenTypeRoutingService routingService) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 智能路由");

            CodeGenTypeEnum generationType;
            if (context.getGenerationType() != null) {
                // 用户请求的类型优先于智能路由，保证本次请求参数可预测。
                generationType = context.getGenerationType();
                log.info("使用请求指定的代码生成类型: {}", generationType.getValue());
            } else {
                try {
                // 根据原始提示词进行智能路由
                generationType = routingService.routeCodeGenType(context.getOriginalPrompt());
                log.info("AI智能路由完成，选择类型: {} ({})", generationType.getValue(), generationType.getText());
                } catch (Exception e) {
                    log.error("AI智能路由失败，使用默认HTML类型: {}", e.getMessage());
                    generationType = CodeGenTypeEnum.HTML;
                }
            }

            // 更新状态
            context.setCurrentStep("智能路由");
            context.setGenerationType(generationType);
            return WorkflowContext.saveContext(context);
        });
    }
}
