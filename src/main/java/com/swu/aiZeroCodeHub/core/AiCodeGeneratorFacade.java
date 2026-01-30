package com.swu.aiZeroCodeHub.core;

import com.swu.aiZeroCodeHub.aiService.AiCodeGeneratorService;
import com.swu.aiZeroCodeHub.core.executor.CodeFileSaverExecutor;
import com.swu.aiZeroCodeHub.core.executor.CodeParserExecutor;
import com.swu.aiZeroCodeHub.exception.BusinessException;
import com.swu.aiZeroCodeHub.exception.ErrorCode;
import com.swu.aiZeroCodeHub.model.enums.CodeGenTypeEnum;
import com.swu.aiZeroCodeHub.model.vo.ai.CodeResult;
import com.swu.aiZeroCodeHub.model.vo.ai.HtmlCodeResult;
import com.swu.aiZeroCodeHub.model.vo.ai.MultiFileCodeResult;
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
    private AiCodeGeneratorService aiCodeGeneratorService;
    @Resource
    private CodeParserExecutor codeParserExecutor;
    @Resource
    private CodeFileSaverExecutor codeFileSaverExecutor;

    /**
     * 非流式：生成并落盘，返回保存目录。
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum){
        if (codeGenTypeEnum==null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"生成类型为空");
        }
        return switch (codeGenTypeEnum){
            case HTML -> generateAndSaveHtmlCode(userMessage);
            case MULTI_FILE -> generateAndSaveMultiFileCode(userMessage);
            default -> {
                String errorMessage="不支持的生成类型" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,errorMessage);
            }

        };
    }

    /**
     * 流式：生成时返回 chunk，流结束时解析并落盘。
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> generateAndSaveHtmlCodeStream(userMessage);
            case MULTI_FILE -> generateAndSaveMultiFileCodeStream(userMessage);
            default -> {
                String errorMessage = "不支持的生成类型" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 生成HTML模式的代码并且保存(流式)
     * @param userMessage
     * @return
     */
    public Flux<String> generateAndSaveHtmlCodeStream(String userMessage){
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
                        File savedDir = codeFileSaverExecutor.save(codeResult, CodeGenTypeEnum.HTML);
                        log.info("保存成功，路径为：{}",savedDir.getAbsolutePath());
                    }catch (Exception e){
                        log.error("保存失败：{}",e.getMessage());
                    }
                });
    }

    private Flux<String> generateAndSaveMultiFileCodeStream(String userMessage){
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
                        File savedDir = codeFileSaverExecutor.save(codeResult, CodeGenTypeEnum.MULTI_FILE);
                        log.info("保存成功，路径为：{}",savedDir.getAbsolutePath());

                    }catch (Exception e){
                        log.error("保存失败：{}",e.getMessage());
                    }
                });
    }

    /**
     * 调用业务生成单HTML代码并保存到本地
     * @param userMessage
     * @return
     */
    private File generateAndSaveHtmlCode(String userMessage){
        HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
        return codeFileSaverExecutor.save(htmlCodeResult, CodeGenTypeEnum.HTML);
    }

    /**
     * 调用业务生成多代码文件并保存到本地
     * @param userMessage
     * @return
     */
    private File generateAndSaveMultiFileCode(String userMessage){
        MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
        return codeFileSaverExecutor.save(multiFileCodeResult, CodeGenTypeEnum.MULTI_FILE);
    }
}
