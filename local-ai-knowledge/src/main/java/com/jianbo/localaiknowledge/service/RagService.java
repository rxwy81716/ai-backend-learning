package com.jianbo.localaiknowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.localaiknowledge.model.ChatMessage;
import com.jianbo.localaiknowledge.model.SystemPrompt;
import com.jianbo.localaiknowledge.utils.ChatContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * RAG 问答服务
 *
 * 完整流程：
 *   1. 从 ES 向量检索相关文档片段
 *   2. 从 DB 加载 SystemPrompt（可动态切换）
 *   3. 组装上下文：SystemPrompt + 历史对话 + 用户问题
 *   4. 调用 LLM 生成回答（支持同步/流式）
 *   5. 持久化对话到 DB
 *
 * 抗幻觉策略：
 *   - SystemPrompt 严格约束只引用参考资料
 *   - 检索结果为空时直接返回"未找到"，不交给 LLM
 *   - 返回置信度 + 来源引用供前端判断
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagService {

    private final EsVectorSearchService searchService;
    private final ChatHistoryCacheService chatHistoryCache;
    private final SystemPromptService systemPromptService;
    private final ChatContextUtil chatContextUtil;
    private final ObjectMapper objectMapper;

    @Qualifier("MiniMaxChatClient")
    private final ChatClient chatClient;

    /** 检索 topK */
    private static final int RAG_TOP_K = 5;
    /** 相似度阈值（低于此值视为无相关内容） */
    private static final double SIMILARITY_THRESHOLD = 0.3;
    /** 多轮对话最多加载最近 N 条历史 */
    private static final int MAX_HISTORY_MESSAGES = 20;

    // ==================== 单轮问答（同步） ====================

    /**
     * RAG 单轮问答
     *
     * @param question   用户问题
     * @param promptName 指定 SystemPrompt 名称（null = 使用默认）
     * @return 回答结果（含 answer + references + confidence）
     */
    public Map<String, Object> chat(String question, String promptName) {
        // 1. 向量检索
        List<Document> docs = searchService.search(question, RAG_TOP_K, SIMILARITY_THRESHOLD);

        // 2. 抗幻觉：无相关文档直接返回
        if (docs.isEmpty()) {
            log.info("RAG 检索无结果, question={}", question);
            return Map.of(
                    "answer", "根据现有知识库，暂未找到与您问题相关的信息。请尝试换个问法或上传相关文档。",
                    "references", List.of(),
                    "confidence", 0.0,
                    "hitCount", 0
            );
        }

        // 3. 构建上下文
        String context = buildContext(docs);
        String systemPromptContent = loadSystemPrompt(promptName);
        String filledPrompt = systemPromptContent.replace("{context}", context);

        // 4. 调用 LLM
        log.info("RAG 问答 | question={}, hitDocs={}", question, docs.size());
        String answer = chatClient.prompt()
                .system(filledPrompt)
                .user(question)
                .call()
                .content();

        // 5. 构建引用信息
        List<Map<String, Object>> references = buildReferences(docs);
        double avgScore = docs.stream()
                .mapToDouble(d -> d.getMetadata().containsKey("score")
                        ? ((Number) d.getMetadata().get("score")).doubleValue() : 0.5)
                .average().orElse(0.5);

        return Map.of(
                "answer", answer,
                "references", references,
                "confidence", Math.round(avgScore * 100.0) / 100.0,
                "hitCount", docs.size()
        );
    }

    // ==================== 多轮对话（同步） ====================

    /**
     * RAG 多轮对话
     *
     * @param sessionId  会话 ID（前端生成或后端分配）
     * @param question   用户问题
     * @param promptName 指定 SystemPrompt 名称（null = 使用默认）
     * @return 回答结果
     */
    public Map<String, Object> multiChat(String sessionId, String question, String promptName) {
        // 1. 向量检索
        List<Document> docs = searchService.search(question, RAG_TOP_K, SIMILARITY_THRESHOLD);

        if (docs.isEmpty()) {
            // 持久化用户消息（DB + Redis 双写）
            chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "user", question));
            String noResultAnswer = "根据现有知识库，暂未找到与您问题相关的信息。请尝试换个问法或上传相关文档。";
            chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "assistant", noResultAnswer));

            return Map.of(
                    "sessionId", sessionId,
                    "answer", noResultAnswer,
                    "references", List.of(),
                    "confidence", 0.0,
                    "hitCount", 0
            );
        }

        // 2. 构建消息列表
        String context = buildContext(docs);
        String systemPromptContent = loadSystemPrompt(promptName);
        String filledPrompt = systemPromptContent.replace("{context}", context);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(filledPrompt));

        // 3. 加载历史对话（优先走 Redis 缓存）
        List<ChatMessage> history = chatHistoryCache.loadRecentHistory(sessionId, MAX_HISTORY_MESSAGES);
        for (ChatMessage msg : history) {
            switch (msg.getRole()) {
                case "user" -> messages.add(new UserMessage(msg.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        // 4. 添加当前问题
        messages.add(new UserMessage(question));

        // 5. Token 裁剪（保留 System，从最早的历史对话开始删）
        chatContextUtil.trimByToken(messages);

        // 6. 调用 LLM
        log.info("RAG 多轮 | session={}, question={}, historySize={}, hitDocs={}",
                sessionId, question, history.size(), docs.size());

        String answer = chatClient.prompt()
                .messages(messages)
                .call()
                .content();

        // 7. 持久化当前轮对话（DB + Redis 双写）
        chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "user", question));

        List<Map<String, Object>> references = buildReferences(docs);
        String metadataJson = toJson(Map.of("references", references, "hitCount", docs.size()));
        chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "assistant", answer, metadataJson));

        double avgScore = docs.stream()
                .mapToDouble(d -> d.getMetadata().containsKey("score")
                        ? ((Number) d.getMetadata().get("score")).doubleValue() : 0.5)
                .average().orElse(0.5);

        return Map.of(
                "sessionId", sessionId,
                "answer", answer,
                "references", references,
                "confidence", Math.round(avgScore * 100.0) / 100.0,
                "hitCount", docs.size()
        );
    }

    // ==================== 流式问答（SSE） ====================

    /**
     * RAG 流式问答（SSE）
     * 适合前端逐字展示，高并发友好（非阻塞）
     */
    public Flux<String> chatStream(String sessionId, String question, String promptName) {
        // 1. 向量检索（同步，通常 <100ms）
        List<Document> docs = searchService.search(question, RAG_TOP_K, SIMILARITY_THRESHOLD);

        if (docs.isEmpty()) {
            if (sessionId != null) {
                chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "user", question));
                String noResult = "根据现有知识库，暂未找到与您问题相关的信息。";
                chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "assistant", noResult));
            }
            return Flux.just("根据现有知识库，暂未找到与您问题相关的信息。");
        }

        // 2. 构建消息
        String context = buildContext(docs);
        String systemPromptContent = loadSystemPrompt(promptName);
        String filledPrompt = systemPromptContent.replace("{context}", context);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(filledPrompt));

        // 加载历史（有 sessionId 时，优先走 Redis）
        if (sessionId != null) {
            List<ChatMessage> history = chatHistoryCache.loadRecentHistory(sessionId, MAX_HISTORY_MESSAGES);
            for (ChatMessage msg : history) {
                switch (msg.getRole()) {
                    case "user" -> messages.add(new UserMessage(msg.getContent()));
                    case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }
        messages.add(new UserMessage(question));
        chatContextUtil.trimByToken(messages);

        // 3. 持久化用户消息（DB + Redis 双写）
        if (sessionId != null) {
            chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "user", question));
        }

        // 4. 流式调用 LLM
        log.info("RAG 流式 | session={}, question={}, hitDocs={}", sessionId, question, docs.size());

        StringBuilder fullAnswer = new StringBuilder();

        return chatClient.prompt()
                .messages(messages)
                .stream()
                .content()
                .doOnNext(fullAnswer::append)
                .doOnComplete(() -> {
                    // 流完成后持久化 assistant 回答
                    if (sessionId != null) {
                        List<Map<String, Object>> refs = buildReferences(docs);
                        String meta = toJson(Map.of("references", refs, "hitCount", docs.size()));
                        chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "assistant", fullAnswer.toString(), meta));
                    }
                    log.info("RAG 流式完成 | session={}, answerLen={}", sessionId, fullAnswer.length());
                })
                .doOnError(e -> log.error("RAG 流式异常 | session={}, error={}", sessionId, e.getMessage()));
    }

    // ==================== 辅助方法 ====================

    /** 从检索文档构建上下文文本 */
    private String buildContext(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String source = doc.getMetadata().getOrDefault("source", "未知").toString();
            sb.append("【").append(i + 1).append("】[来源: ").append(source).append("]\n");
            sb.append(doc.getText()).append("\n\n");
        }
        return sb.toString();
    }

    /** 构建引用信息列表 */
    private List<Map<String, Object>> buildReferences(List<Document> docs) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (Document doc : docs) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("source", doc.getMetadata().getOrDefault("source", "未知"));
            ref.put("content", doc.getText().length() > 200
                    ? doc.getText().substring(0, 200) + "..." : doc.getText());
            if (doc.getMetadata().containsKey("score")) {
                ref.put("score", doc.getMetadata().get("score"));
            }
            refs.add(ref);
        }
        return refs;
    }

    /** 加载 SystemPrompt（按名称或默认，走 Caffeine 缓存） */
    private String loadSystemPrompt(String promptName) {
        SystemPrompt prompt;
        if (promptName != null && !promptName.isBlank()) {
            prompt = systemPromptService.getByName(promptName);
        } else {
            prompt = systemPromptService.getDefault();
        }

        if (prompt == null) {
            // 兜底 Prompt（DB 没配时使用）
            return """
                你是一个知识库问答助手。请根据以下参考资料回答问题。
                如果资料中没有相关内容，请回答"暂未找到相关信息"。
                
                【参考资料】
                {context}""";
        }
        return prompt.getContent();
    }

    /** JSON 序列化（静默失败） */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
