package com.jianbo.localaiknowledge.adapter;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

/**
 * 流式桥接器：把 LangChain4j 1.x 的回调式 {@link StreamingChatResponseHandler} 转为 Reactor Flux。
 *
 * <h2>为什么需要这个适配器？</h2>
 * <p>Spring AI 原版直接返回 {@code Flux<String>}（Reactor 原生），前端通过 SSE 消费。
 * LangChain4j 的流式接口是回调式的 {@code StreamingChatResponseHandler}，
 * 两者不兼容。本类把回调转换为 Flux，让所有 Agent 和 Controller 无需感知差异。</p>
 *
 * <h2>对比</h2>
 * <pre>
 * Spring AI:
 *   chatClient.prompt(messages).stream().content()  →  Flux&lt;String&gt;
 *
 * LangChain4j 1.x（原始写法）:
 *   streamingModel.chat(chatRequest, new StreamingChatResponseHandler() {
 *       void onPartialResponse(String token) { ... }
 *       void onCompleteResponse(ChatResponse response) { ... }
 *       void onError(Throwable error) { ... }
 *   });
 *
 * 本适配器:
 *   TokenStreamFluxAdapter.toFlux(handler -> model.chat(chatRequest, handler))  →  Flux&lt;String&gt;
 * </pre>
 */
@Slf4j
public class TokenStreamFluxAdapter {

    /**
     * 创建一个 Flux，当订阅时触发流式生成。
     *
     * @param streamTrigger 接收一个 handler 回调，由调用方在内部调 model.chat(request, handler)
     * @return 逐 token 发射的 Flux
     */
    public static Flux<String> toFlux(Consumer<StreamingChatResponseHandler> streamTrigger) {
        return Flux.create(sink -> {
            StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (partialResponse != null && !partialResponse.isEmpty()) {
                        sink.next(partialResponse);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    if (log.isDebugEnabled()) {
                        TokenUsage usage = completeResponse.tokenUsage();
                        if (usage != null) {
                            log.debug("Stream complete | input={} output={} total={}",
                                    usage.inputTokenCount(), usage.outputTokenCount(), usage.totalTokenCount());
                        }
                    }
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    log.error("Stream error: {}", error.getMessage());
                    sink.error(error);
                }
            };

            try {
                streamTrigger.accept(handler);
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    /**
     * 同步调用 + 包装为 Flux（用于不支持流式的场景，如 PlannerAgent 的中间步骤）。
     */
    public static Flux<String> fromBlocking(String text) {
        return Flux.just(text);
    }
}
