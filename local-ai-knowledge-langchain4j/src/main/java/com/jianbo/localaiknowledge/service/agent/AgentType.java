package com.jianbo.localaiknowledge.service.agent;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Agent 类型枚举（与 Spring AI 版完全一致，无 AI 框架依赖） */
@Getter
@RequiredArgsConstructor
public enum AgentType {
    KNOWLEDGE("knowledge_base"),
    HOT_SEARCH("hot_search"),
    DOCUMENT_OVERVIEW("document_overview"),
    DOCUMENT_SEARCH("document_search"),
    CHAT("llm_direct"),
    PLANNER("planner");

    private final String sourceTag;
}
