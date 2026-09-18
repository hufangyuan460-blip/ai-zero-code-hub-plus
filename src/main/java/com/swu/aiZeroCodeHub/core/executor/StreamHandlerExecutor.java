package com.swu.aiZeroCodeHub.core.executor;

import com.swu.aiZeroCodeHub.core.streamHandler.JsonMessageStreamHandler;
import com.swu.aiZeroCodeHub.core.streamHandler.SimpleTextStreamHandler;
import com.swu.aiZeroCodeHub.model.entity.User;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.function.BooleanSupplier;

import static com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum.MULTI_FILE;

/**
 * 流处理器执行器
 * 根据代码生成类型创建合适的流处理器：
 * 1. 传统的Flux<String>流（HTML、MULTI_FILE） -> SimpleTextStreamHandler
 * 2. TokenStream格式的复杂流（VUE_PROJECT） -> JsonMessageStreamHandler
 */
@Slf4j
@Component
public class StreamHandlerExecutor {
    @Resource
    private JsonMessageStreamHandler jsonMessageStreamHandler;

    /**
     * 创建流处理器并处理聊天历史记录
     *
     * @param originFlux 原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId 应用ID
     * @param loginUser 登录用户
     * @param codeGenType 代码生成类型
     * @return 处理后的流
     */
    public Flux<String> doExecute(Flux<String> originFlux,
                                  ChatHistoryService chatHistoryService,
                                  long appId,
                                  User loginUser,
                                  CodeGenTypeEnum codeGenType) {
        return doExecute(originFlux, chatHistoryService, appId, loginUser, codeGenType, () -> false);
    }

    public Flux<String> doExecute(Flux<String> originFlux,
                                  ChatHistoryService chatHistoryService,
                                  long appId,
                                  User loginUser,
                                  CodeGenTypeEnum codeGenType,
                                  BooleanSupplier cancellationChecker) {
        return doExecute(originFlux, chatHistoryService, appId, loginUser, codeGenType,
                cancellationChecker, () -> true);
    }

    public Flux<String> doExecute(Flux<String> originFlux,
                                  ChatHistoryService chatHistoryService,
                                  long appId,
                                  User loginUser,
                                  CodeGenTypeEnum codeGenType,
                                  BooleanSupplier cancellationChecker,
                                  BooleanSupplier toolBudgetChecker) {
        return switch (codeGenType) {
            case VUE_PROJECT ->
                // 使用注入的组件实例
                    jsonMessageStreamHandler.handle(originFlux, chatHistoryService, appId, loginUser,
                            cancellationChecker, toolBudgetChecker);
            case HTML, MULTI_FILE ->
                // 简单文本处理器不需要依赖注入
                    new SimpleTextStreamHandler().handle(originFlux, chatHistoryService, appId, loginUser,
                            cancellationChecker, toolBudgetChecker);
        };
    }
}
