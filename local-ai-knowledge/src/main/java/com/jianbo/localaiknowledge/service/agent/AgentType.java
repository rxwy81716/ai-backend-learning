package com.jianbo.localaiknowledge.service.agent;

/**
 * 多 Agent 类型枚举。
 *
 * <p>每种类型对应一个专职 Agent，由 {@link IntentRouter} 根据用户问题意图选择。
 */
public enum AgentType {

  /** 知识库专家：检索企业私域文档，精准引用回答 */
  KNOWLEDGE("knowledge_base"),

  /** 热榜专家：查询各平台实时热搜/热榜数据 */
  HOT_SEARCH("hot_search"),

  /** 文档概览：遍历所有文档并综合总结 */
  DOCUMENT_OVERVIEW("knowledge_base"),

  /** 文档内搜索：在知识库文档中全文检索指定关键词，定位具体文档 */
  DOCUMENT_SEARCH("knowledge_base"),

  /** 通用对话：无工具，基于 LLM 自身知识直接回答 */
  CHAT("llm_direct"),

  /** ReAct 规划器：Think → Act → Observe 循环，自主拆解多步任务并调用工具 */
  PLANNER("planner");

  /** 与前端 META.source 对齐的标识，保持向后兼容 */
  private final String sourceTag;

  AgentType(String sourceTag) {
    this.sourceTag = sourceTag;
  }

  public String sourceTag() {
    return sourceTag;
  }
}
