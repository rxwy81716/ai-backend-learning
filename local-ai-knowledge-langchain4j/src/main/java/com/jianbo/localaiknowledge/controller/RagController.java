package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.service.agent.MultiAgentOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * RAG 智能问答 Controller（对标 Spring AI 版）。
 *
 * <h2>差异</h2>
 * <p>接口签名完全一致，前端 SSE 消费代码无需任何改动。
 * 内部从 Spring AI ChatClient 切换到 LangChain4j StreamingChatLanguageModel + 适配器。</p>
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final MultiAgentOrchestrator orchestrator;

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody Map<String, Object> body) {
        String question = (String) body.getOrDefault("question", "");
        String sessionId = (String) body.getOrDefault("sessionId", "");
        String chatMode = (String) body.getOrDefault("chatMode", "KNOWLEDGE");
        String promptName = (String) body.getOrDefault("promptName", "");
        String modelKey = (String) body.getOrDefault("modelKey", "default");
        boolean thinking = Boolean.TRUE.equals(body.get("thinking"));

        // TODO: 从 SecurityContext 获取 userId（与 Spring AI 版一致）
        String userId = "demo-user";

        return orchestrator.chatStream(sessionId, question, userId, chatMode, promptName, modelKey, thinking);
    }
}
