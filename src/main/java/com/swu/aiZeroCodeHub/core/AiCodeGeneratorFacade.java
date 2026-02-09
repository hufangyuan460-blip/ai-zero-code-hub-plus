package com.swu.aiZeroCodeHub.core;

import cn.hutool.json.JSONUtil;
import com.swu.aiZeroCodeHub.aiService.AiCodeGeneratorService;
import com.swu.aiZeroCodeHub.core.executor.CodeFileSaverExecutor;
import com.swu.aiZeroCodeHub.core.executor.CodeParserExecutor;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.message.AiResponseMessage;
import com.swu.aiZeroCodeHub.model.message.ToolExecutedMessage;
import com.swu.aiZeroCodeHub.model.message.ToolRequestMessage;
import com.swu.aiZeroCodeHub.model.vo.ai.CodeResult;
import com.swu.aiZeroCodeHub.model.vo.ai.HtmlCodeResult;
import com.swu.aiZeroCodeHub.model.vo.ai.MultiFileCodeResult;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成统一入口（Facade）。
 *
 * <p>按 {@link CodeGenTypeEnum} 选择生成方式；流式场景会在结束时解析 raw 文本并落盘。
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {
    @Resource
    private com.swu.aiZeroCodeHub.config.AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;
    @Resource
    private CodeParserExecutor codeParserExecutor;
    @Resource
    private CodeFileSaverExecutor codeFileSaverExecutor;


    /**
     * 流式：生成时返回 chunk，流结束时解析并落盘。
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        long realAppId = appId == null ? 0L : appId;
        // 根据生成类型选择对应的 AI 服务实例（不同类型可使用不同模型 / 工具配置）
        AiCodeGeneratorService aiCodeGeneratorService =
                aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(realAppId, codeGenTypeEnum);

        // Use if-else instead of switch expression to avoid anonymous inner class issues in some environments
        if (codeGenTypeEnum == CodeGenTypeEnum.HTML) {
            return generateAndSaveHtmlCodeStream(aiCodeGeneratorService, userMessage, appId);
        } else if (codeGenTypeEnum == CodeGenTypeEnum.MULTI_FILE) {
            return generateAndSaveMultiFileCodeStream(aiCodeGeneratorService, userMessage, appId);
        } else if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            return generateAndSaveVueProjectCodeStream(aiCodeGeneratorService, userMessage, appId);
        } else {
            String errorMessage = "不支持的生成类型" + codeGenTypeEnum.getValue();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
        }
    }




    /**
     * 生成HTML模式的代码并且保存(流式)
     * @param userMessage
     * @param appId
     * @return
     */
    private Flux<String> generateAndSaveHtmlCodeStream(AiCodeGeneratorService aiCodeGeneratorService, String userMessage,Long appId){
        Flux<String> fluxResult = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
        StringBuilder codeBuilder = new StringBuilder();
        return fluxResult
                .doOnNext(chunk->{
                    codeBuilder.append(chunk);
                })
                .doOnComplete(()->{
                    try{
                        String completeHtmlCode = codeBuilder.toString();
                        CodeResult codeResult = codeParserExecutor.parse(completeHtmlCode, CodeGenTypeEnum.HTML);
                        File savedDir = codeFileSaverExecutor.save(codeResult, CodeGenTypeEnum.HTML,appId);
                        log.info("保存成功，路径为：{}",savedDir.getAbsolutePath());
                    }catch (Exception e){
                        log.error("保存失败：{}",e.getMessage());
                    }
                });
    }

    /**
     * 生成多文件代码并且保存(流式)
     * @param userMessage
     * @param appId
     * @return
     */
    private Flux<String> generateAndSaveMultiFileCodeStream(AiCodeGeneratorService aiCodeGeneratorService, String userMessage,Long appId){
        Flux<String> fluxResult = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
        StringBuilder codeBuilder = new StringBuilder();
        return fluxResult
                .doOnNext(chunk->{
                    codeBuilder.append(chunk);
                })
                .doOnComplete(()->{
                    try{
                        String completeMultiFileCode = codeBuilder.toString();
                        CodeResult codeResult = codeParserExecutor.parse(completeMultiFileCode, CodeGenTypeEnum.MULTI_FILE);
                        File savedDir = codeFileSaverExecutor.save(codeResult, CodeGenTypeEnum.MULTI_FILE,appId);
                        log.info("保存成功，路径为：{}",savedDir.getAbsolutePath());

                    }catch (Exception e){
                        log.error("保存失败：{}",e.getMessage());
                    }
                });
    }

    /**
     * 生成vue项目代码比保存
     * @param aiCodeGeneratorService
     * @param userMessage
     * @param appId
     * @return
     */
    private Flux<String> generateAndSaveVueProjectCodeStream(AiCodeGeneratorService aiCodeGeneratorService,
                                                             String userMessage,
                                                             Long appId) {
        // Vue 工程模式下，代码由 FileWriteTool 直接写入到项目目录，
        // 这里主要负责把推理模型的思考过程 / 计划 / 工具调用说明按流式返回给前端展示。
        TokenStream tokenStream = aiCodeGeneratorService.generateProjectCodeStream(appId, userMessage);
        Flux<String> fluxResult = processTokenStream(tokenStream);
        StringBuilder contentBuilder = new StringBuilder();
        return fluxResult
                .doOnNext(chunk -> {
                    contentBuilder.append(chunk);
                })
                .doOnComplete(() -> {
                    // 仅做日志记录，不再二次解析或落盘（文件已由工具完成写入）
                    log.info("Vue 项目生成完成，appId: {}, 总输出长度: {}", appId, contentBuilder.length());
                })
                .doOnError(e -> {
                    log.error("Vue 项目生成失败，appId: {}, error: {}", appId, e.getMessage(), e);
                });
    }



    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     * @param tokenStream TokenStream 对象
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }








}
