package com.swu.aiZeroCodeHub.generation;

import reactor.core.publisher.Flux;

/**
 * 单次代码生成执行策略。
 */
public interface GenerationStrategy {

    Flux<GenerationEvent> generate(GenerationRequest request);
}
