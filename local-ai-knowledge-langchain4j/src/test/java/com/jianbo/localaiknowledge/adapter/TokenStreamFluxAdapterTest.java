package com.jianbo.localaiknowledge.adapter;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * TokenStreamFluxAdapter 单元测试（LangChain4j 1.x API）。
 *
 * <p>验证 {@code StreamingChatResponseHandler} → Reactor Flux 桥接的正确性。
 * <ul>
 *   <li>onPartialResponse → Flux.next</li>
 *   <li>onCompleteResponse → Flux.complete</li>
 *   <li>onError → Flux.error</li>
 *   <li>空 token 被过滤</li>
 * </ul>
 */
class TokenStreamFluxAdapterTest {

    @Test
    @DisplayName("正常流式：多个 token + 完成信号")
    void shouldEmitTokensAndComplete() {
        var flux = TokenStreamFluxAdapter.toFlux(handler -> {
            handler.onPartialResponse("Hello");
            handler.onPartialResponse(" ");
            handler.onPartialResponse("World");
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("Hello World"))
                    .tokenUsage(new TokenUsage(5, 2))
                    .build());
        });

        StepVerifier.create(flux)
                .expectNext("Hello")
                .expectNext(" ")
                .expectNext("World")
                .verifyComplete();
    }

    @Test
    @DisplayName("空 token 应被过滤")
    void shouldFilterEmptyTokens() {
        var flux = TokenStreamFluxAdapter.toFlux(handler -> {
            handler.onPartialResponse("A");
            handler.onPartialResponse("");
            handler.onPartialResponse(null);
            handler.onPartialResponse("B");
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("AB"))
                    .build());
        });

        StepVerifier.create(flux)
                .expectNext("A")
                .expectNext("B")
                .verifyComplete();
    }

    @Test
    @DisplayName("onError → Flux.error 传播")
    void shouldPropagateError() {
        RuntimeException expected = new RuntimeException("模型炸了");
        var flux = TokenStreamFluxAdapter.toFlux(handler -> {
            handler.onPartialResponse("partial");
            handler.onError(expected);
        });

        StepVerifier.create(flux)
                .expectNext("partial")
                .expectErrorMatches(e -> e.getMessage().equals("模型炸了"))
                .verify();
    }

    @Test
    @DisplayName("streamTrigger 自身抛异常 → Flux.error")
    void shouldHandleTriggerException() {
        var flux = TokenStreamFluxAdapter.toFlux(handler -> {
            throw new RuntimeException("初始化失败");
        });

        StepVerifier.create(flux)
                .expectErrorMessage("初始化失败")
                .verify();
    }

    @Test
    @DisplayName("fromBlocking 同步包装")
    void fromBlockingShouldEmitSingleValue() {
        var flux = TokenStreamFluxAdapter.fromBlocking("同步回答");
        StepVerifier.create(flux)
                .expectNext("同步回答")
                .verifyComplete();
    }
}
