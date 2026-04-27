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
 * RAG 智能路由 Agent（多智能体入口）
 *
 * 决策链路：
 *   1. 用户隔离检索（私有文档 + 公共文档）
 *   2. 知识库有结果 → 走 RAG 回答（最可靠）
 *   3. 知识库无结果 + 网络搜索已启用 → 降级到网络搜索
 *   4. 都无结果 → LLM 直答（换宽松 Prompt，用模型自身知识回答，可能有幻觉）
 *
 * 架构：
 *   ┌────────────────────────────────────┐
 *   │         RagAgentService              │  ← 路由层
 *   └───┬───────────┬───────────┬─────────┘
 *       │           │           │
 *   ┌───▼───┐ ┌───▼─────┐ ┌──▼──────┐
 *   │ Agent1 │ │ Agent2    │ │ Agent3     │
 *   │ 知识库 │ │ 网络搜索  │ │ LLM 直答  │
 *   │(ES RAG)│ │(Tavily)  │ │(自身知识) │
 *   └────────┘ └─────────┘ └──────────┘
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagAgentService {

    private final EsVectorSearchService searchService;
    private final WebSearchService webSearchService;
    private final ChatHistoryCacheService chatHistoryCache;
    private final SystemPromptService systemPromptService;
    private final ChatContextUtil chatContextUtil;
    private final ObjectMapper objectMapper;

    @Qualifier("MiniMaxChatClient")
    private final ChatClient chatClient;

    private static final int RAG_TOP_K = 5;
    private static final double SIMILARITY_THRESHOLD = 0.3;
    private static final int MAX_HISTORY_MESSAGES = 20;

    /** LLM 直答 Prompt（知识库未命中时使用，允许模型用自身知识回答） */
    private static final String LLM_DIRECT_PROMPT =
            "你是一个智能问答助手。用户的问题在知识库中未找到相关内容，" +
            "请基于你自身的知识尽可能准确地回答。\n\n" +
            "注意：\n" +
            "- 如果你不确定，请明确告知用户\"以下回答基于通用知识，仅供参考\"\n" +
            "- 不要编造具体数据、链接或不存在的来源\n" +
            "- 鼓励用户上传相关文档以获得更准确的回答";

    /**
     * 智能问答（同步）—— 自动路由知识库 or 网络搜索
     *
     * @param sessionId  会话 ID（null = 单轮）
     * @param question   用户问题
     * @param userId     用户 ID（null = 只看公共文档）
     * @param promptName SystemPrompt 名称（null = 默认）
     */
    public Map<String, Object> chat(String sessionId, String question, String userId, String promptName) {
        // ===== Agent 1: 知识库检索 =====
        List<Document> docs = (userId != null && !userId.isBlank())
                ? searchService.searchWithOwnership(question, userId, RAG_TOP_K, SIMILARITY_THRESHOLD)
                : searchService.search(question, RAG_TOP_K, SIMILARITY_THRESHOLD);

        String source;
        String filledPrompt;

        if (!docs.isEmpty()) {
            // ===== 知识库命中：最可靠 =====
            source = "knowledge_base";
            String ctx = buildDocContext(docs);
            String sysPrompt = loadSystemPrompt(promptName);
            filledPrompt = sysPrompt.replace("{context}", ctx);
            log.info("Agent 路由 → 知识库 | userId={}, hitDocs={}", userId, docs.size());
        } else if (webSearchService.isEnabled()) {
            // ===== Agent 2: 网络搜索降级 =====
            log.info("知识库无结果，尝试网络搜索 | question={}", question);
            var webResults = webSearchService.search(question);
            if (!webResults.isEmpty()) {
                source = "web_search";
                String ctx = webSearchService.formatAsContext(webResults);
                String sysPrompt = loadSystemPrompt(promptName);
                filledPrompt = sysPrompt.replace("{context}", ctx);
                log.info("Agent 路由 → 网络搜索 | 结果数={}", webResults.size());
            } else {
                // 网络搜索也无结果 → LLM 直答
                source = "llm_direct";
                filledPrompt = LLM_DIRECT_PROMPT;
                log.info("Agent 路由 → LLM 直答（知识库+网搜均无结果）");
            }
        } else {
            // ===== Agent 3: LLM 直答（网络搜索未启用） =====
            source = "llm_direct";
            filledPrompt = LLM_DIRECT_PROMPT;
            log.info("Agent 路由 → LLM 直答（知识库无结果）");
        }

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(filledPrompt));

        // 加载历史对话
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

        // 调用 LLM
        String answer = chatClient.prompt()
                .messages(messages)
                .call()
                .content();

        // 持久化
        if (sessionId != null) {
            chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "user", question));
            String meta = toJson(Map.of("source", source, "hitCount",
                    docs.isEmpty() ? 0 : docs.size()));
            chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "assistant", answer, meta));
        }

        // 构建响应
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);
        result.put("source", source);
        result.put("hitCount", docs.size());
        if (sessionId != null) {
            result.put("sessionId", sessionId);
        }
        if ("llm_direct".equals(source)) {
            result.put("disclaimer", "此回答基于 AI 通用知识，未经知识库验证，仅供参考");
        }
        if (!docs.isEmpty()) {
            result.put("references", buildReferences(docs));
        }
        return result;
    }

    /**
     * 智能问答（SSE 流式）—— 自动路由
     */
    public Flux<String> chatStream(String sessionId, String question, String userId, String promptName) {
        // Agent 1: 知识库检索
        List<Document> docs = (userId != null && !userId.isBlank())
                ? searchService.searchWithOwnership(question, userId, RAG_TOP_K, SIMILARITY_THRESHOLD)
                : searchService.search(question, RAG_TOP_K, SIMILARITY_THRESHOLD);

        String source;
        String filledPrompt;

        if (!docs.isEmpty()) {
            source = "knowledge_base";
            String ctx = buildDocContext(docs);
            String sysPrompt = loadSystemPrompt(promptName);
            filledPrompt = sysPrompt.replace("{context}", ctx);
        } else if (webSearchService.isEnabled()) {
            var webResults = webSearchService.search(question);
            if (!webResults.isEmpty()) {
                source = "web_search";
                String ctx = webSearchService.formatAsContext(webResults);
                String sysPrompt = loadSystemPrompt(promptName);
                filledPrompt = sysPrompt.replace("{context}", ctx);
            } else {
                source = "llm_direct";
                filledPrompt = LLM_DIRECT_PROMPT;
            }
        } else {
            source = "llm_direct";
            filledPrompt = LLM_DIRECT_PROMPT;
        }

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(filledPrompt));

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

        if (sessionId != null) {
            chatHistoryCache.saveMessage(ChatMessage.of(sessionId, "user", question));
        }

        final String finalSource = source;
        StringBuilder fullAnswer = new StringBuilder();

        return chatClient.prompt()
                .messages(messages)
                .stream()
                .content()
                .doOnNext(fullAnswer::append)
                .doOnComplete(() -> {
                    if (sessionId != null) {
                        String meta = toJson(Map.of("source", finalSource));
                        chatHistoryCache.saveMessage(
                                ChatMessage.of(sessionId, "assistant", fullAnswer.toString(), meta));
                    }
                })
                .doOnError(e -> log.error("Agent 流式异常: {}", e.getMessage()));
    }

    // ==================== 辅助方法 ====================

    private String buildDocContext(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String docSource = doc.getMetadata().getOrDefault("source", "未知").toString();
            sb.append("【").append(i + 1).append("】[来源: ").append(docSource).append("]\n");
            sb.append(doc.getText()).append("\n\n");
        }
        return sb.toString();
    }

    private List<Map<String, Object>> buildReferences(List<Document> docs) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (Document doc : docs) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("source", doc.getMetadata().getOrDefault("source", "未知"));
            ref.put("content", doc.getText().length() > 200
                    ? doc.getText().substring(0, 200) + "..." : doc.getText());
            refs.add(ref);
        }
        return refs;
    }

    private String loadSystemPrompt(String promptName) {
        SystemPrompt prompt;
        if (promptName != null && !promptName.isBlank()) {
            prompt = systemPromptService.getByName(promptName);
        } else {
            prompt = systemPromptService.getDefault();
        }
        if (prompt == null) {
            return """
                你是一个知识库问答助手。请根据以下参考资料回答问题。
                如果资料中没有相关内容，请回答"暂未找到相关信息"。
                
                【参考资料】
                {context}""";
        }
        return prompt.getContent();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
