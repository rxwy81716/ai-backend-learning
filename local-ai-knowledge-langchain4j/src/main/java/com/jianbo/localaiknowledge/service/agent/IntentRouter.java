package com.jianbo.localaiknowledge.service.agent;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 意图路由器（对标 Spring AI 版 IntentRouter）。
 *
 * <h2>差异</h2>
 * <p>LLM 调用方式不同：Spring AI 用 ChatClient.prompt().call()，
 * LangChain4j 用 ChatLanguageModel.chat(text)。
 * 其余关键词匹配逻辑完全一致。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IntentRouter {

    private final ChatModel chatModel;

    private static final Map<AgentType, List<String>> KEYWORD_MAP = Map.of(
            AgentType.DOCUMENT_OVERVIEW, List.of("文档概览", "知识库概览", "有哪些文档", "文档列表", "所有文档"),
            AgentType.DOCUMENT_SEARCH, List.of("搜索文档", "查找文档", "文档中搜索", "全文搜索"),
            AgentType.HOT_SEARCH, List.of("热搜", "热榜", "trending", "热门话题", "微博热搜", "知乎热榜"),
            AgentType.CHAT, List.of("你是谁", "你好", "你叫什么", "闲聊")
    );

    private static final String ROUTER_PROMPT = """
            请判断用户问题的意图分类，只输出标签（一个词）：
            - KNOWLEDGE: 需要查询私有知识库
            - CHAT: 闲聊/问候/元问题
            - HOT_SEARCH: 查热搜热榜
            - DOCUMENT_OVERVIEW: 查看文档列表/概览
            - DOCUMENT_SEARCH: 在文档中搜索关键词
            - PLANNER: 复杂问题需要多步骤规划
            
            用户问题：%s
            意图标签：""";

    public AgentType route(String question) {
        // 1. 关键词快匹配
        String lower = question.toLowerCase();
        for (var entry : KEYWORD_MAP.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw)) {
                    log.info("[Router] 关键词命中: {} → {}", kw, entry.getKey());
                    return entry.getKey();
                }
            }
        }

        // 2. LLM 分类
        try {
            String prompt = String.format(ROUTER_PROMPT, question);
            String label = chatModel.chat(prompt).trim().toUpperCase();
            log.info("[Router] LLM 分类: {} → {}", question, label);

            return switch (label) {
                case "CHAT" -> AgentType.CHAT;
                case "HOT_SEARCH" -> AgentType.HOT_SEARCH;
                case "DOCUMENT_OVERVIEW" -> AgentType.DOCUMENT_OVERVIEW;
                case "DOCUMENT_SEARCH" -> AgentType.DOCUMENT_SEARCH;
                case "PLANNER" -> AgentType.PLANNER;
                default -> AgentType.KNOWLEDGE;
            };
        } catch (Exception e) {
            log.warn("[Router] LLM 分类失败，默认 KNOWLEDGE: {}", e.getMessage());
            return AgentType.KNOWLEDGE;
        }
    }
}
