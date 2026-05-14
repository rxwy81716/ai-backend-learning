package com.jianbo.localaiknowledge.service.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 意图路由器：根据用户问题判断应由哪个专职 Agent 处理。
 *
 * <p>两级决策：
 * <ol>
 *   <li><b>快速匹配</b>（关键词 / 正则，<1ms）：覆盖 ~80% 明确意图
 *   <li><b>LLM 分类</b>（~200ms）：仅当关键词无法判断时调用，输出单标签
 * </ol>
 *
 * <p>默认 fallback 为 {@link AgentType#KNOWLEDGE}：宁可多查一次知识库也不遗漏文档命中。
 */
@Component
@Slf4j
public class IntentRouter {

  private final ChatModel chatModel;

  /** LLM 分类超时（ms）：超时即 fallback 到 KNOWLEDGE */
  private static final long CLASSIFY_TIMEOUT_MS = 2000;

  private static final String ROUTER_PROMPT = """
      你是一个意图分类器。根据用户问题，返回且仅返回下列标签之一，不要解释：
      - DOCUMENT_OVERVIEW：用户要求总结/概括/整理整个知识库，或询问"有哪些文档""知识库里有什么""列出所有文档"等概览性问题
      - DOCUMENT_SEARCH：用户要求在文档中搜索/查找/检索某个关键词或短语，如"搜索包含XX的文档""查找提到XX的文档""检索XX关键词"
      - HOT_SEARCH：涉及平台热搜、热榜、排行榜、推荐内容、trending，或提到具体平台（B站/微博/知乎/抖音/小红书/GitHub等）并询问推荐、热门、流行、排行等
      - CHAT：闲聊寒暄（你好/再见/在吗）、关于助手自身的元问题（你是谁/你能做什么/怎么使用）、纯粹的代码/翻译/数学计算请求
      - KNOWLEDGE：询问具体内容、人物、事件、概念、剧情、条款、数据等任何可能存在于私有文档中的问题。当你不确定时，**必须**返回 KNOWLEDGE（宁可多查一次知识库）
      重要规则：
      1. 只要问题涉及具体内容（人物/事件/剧情/概念/条款等），即使看起来像"创作"，也返回 KNOWLEDGE
      2. 只有纯粹的闲聊、打招呼、关于AI助手本身的元问题，才返回 CHAT
      3. 宁可将问题归类为 KNOWLEDGE，也不要错误地归类为 CHAT
      """;

  /** 热榜关键词集合 */
  private static final Set<String> HOT_KEYWORDS = Set.of(
      "热搜", "热榜", "榜单", "排行榜", "trending", "最热", "正在火",
      "最近流行", "热门话题", "今日热点", "热门排行"
  );

  /** 平台名称集合 */
  private static final Set<String> PLATFORM_NAMES = Set.of(
      "b站", "bilibili", "哔哩哔哩", "微博", "weibo",
      "知乎", "zhihu", "抖音", "douyin", "tiktok",
      "小红书", "github", "百度", "头条", "今日头条"
  );

  /** 推荐/热门相关词 */
  private static final Set<String> RECOMMEND_KEYWORDS = Set.of(
      "推荐", "有什么", "有啥", "热门", "流行", "火的",
      "最近火", "最火", "好看的", "top", "排名"
  );

  /** 元问题关键词：关于助手自身 */
  private static final Set<String> META_KEYWORDS = Set.of(
      "你是谁", "你是什么", "你能做什么", "有哪些功能", "怎么使用",
      "什么模式", "什么模型", "介绍一下你"
  );

  /** 通用任务关键词：写代码、翻译、写作等不需要知识库的通用 AI 能力 */
  private static final Set<String> CHAT_TASK_KEYWORDS = Set.of(
      "写代码", "写一段代码", "写个代码", "编程", "代码示例",
      "写一个程序", "写个程序", "写脚本", "写一段脚本",
      "帮我翻译", "翻译一下", "翻译成",
      "写一篇", "写一段", "帮我写", "写个故事", "写首诗",
      "算一下", "计算一下", "数学题"
  );

  /** 文档内搜索关键词：在文档中搜索/查找指定关键词 */
  private static final Set<String> DOC_SEARCH_KEYWORDS = Set.of(
      "搜索包含", "查找包含", "检索包含", "搜索含有", "查找含有",
      "搜索提到", "查找提到", "搜索关键词", "查找关键词",
      "全文搜索", "全文检索", "搜索文档", "查找文档"
  );

  /** 规划类关键词：多步任务 / 显式要求自主规划（触发 ReAct PlannerAgent） */
  private static final Set<String> PLAN_KEYWORDS = Set.of(
      "规划一下", "帮我规划", "请规划", "多步", "step by step", "拆解一下",
      "一步步", "分步", "逐步分析", "先查再答", "先检索"
  );

  /** 文档概览关键词：总结整个知识库 / 列出所有文档 */
  private static final Set<String> OVERVIEW_KEYWORDS = Set.of(
      "总结知识库", "总结一下知识库", "知识库核心内容", "知识库里有什么",
      "知识库都有什么", "有哪些文档", "所有文档", "文档列表",
      "帮我总结知识库", "概括知识库", "整理知识库", "知识库概览",
      "知识库全部内容", "帮我总结文档"
  );

  public IntentRouter(ChatModel chatModel) {
    this.chatModel = chatModel;
  }

  /**
   * 路由用户问题到对应 Agent 类型。
   *
   * @param question 用户原始问题
   * @return 应处理该问题的 Agent 类型
   */
  public AgentType route(String question) {
    // 1. 快速关键词匹配
    AgentType fast = fastMatch(question);
    if (fast != null) {
      log.info("🔀 IntentRouter 快速匹配 | intent={}, question={}", fast, truncate(question));
      return fast;
    }

    // 2. LLM 分类
    AgentType llmResult = llmClassify(question);
    log.info("🔀 IntentRouter LLM分类 | intent={}, question={}", llmResult, truncate(question));
    return llmResult;
  }

  /**
   * 关键词快速匹配：零成本，覆盖明确意图。
   *
   * @return 匹配到的类型；null = 需要 LLM 进一步判断
   */
  private AgentType fastMatch(String question) {
    String lower = question.toLowerCase();

    // 热榜关键词（精确命中）
    for (String kw : HOT_KEYWORDS) {
      if (lower.contains(kw)) {
        return AgentType.HOT_SEARCH;
      }
    }

    // 平台名 + 推荐词组合 → 热榜
    boolean hasPlatform = PLATFORM_NAMES.stream().anyMatch(lower::contains);
    if (hasPlatform) {
      boolean hasRecommend = RECOMMEND_KEYWORDS.stream().anyMatch(lower::contains);
      if (hasRecommend) {
        return AgentType.HOT_SEARCH;
      }
    }

    // 规划类关键词（显式要求多步 ReAct 流程）
    for (String kw : PLAN_KEYWORDS) {
      if (lower.contains(kw)) {
        return AgentType.PLANNER;
      }
    }

    // 文档内搜索关键词（搜索包含XX的文档）
    for (String kw : DOC_SEARCH_KEYWORDS) {
      if (lower.contains(kw)) {
        return AgentType.DOCUMENT_SEARCH;
      }
    }

    // 文档概览关键词（总结整个知识库）
    for (String kw : OVERVIEW_KEYWORDS) {
      if (lower.contains(kw)) {
        return AgentType.DOCUMENT_OVERVIEW;
      }
    }

    // 元问题 → 通用对话（不需要工具）
    for (String kw : META_KEYWORDS) {
      if (lower.contains(kw)) {
        return AgentType.CHAT;
      }
    }

    // 通用任务（写代码/翻译/写作等）→ 通用对话
    for (String kw : CHAT_TASK_KEYWORDS) {
      if (lower.contains(kw)) {
        return AgentType.CHAT;
      }
    }

    // 纯打招呼
    if (lower.matches("^(你好|hi|hello|hey|嗨|在吗|在不在)[!！。.？?\\s]*$")) {
      return AgentType.CHAT;
    }

    return null;
  }

  /**
   * LLM 轻量分类：用一次短调用（~50 token 输出）判断意图。
   * 超时或异常时 fallback 到 KNOWLEDGE（宁可多查）。
   */
  private AgentType llmClassify(String question) {
    try {
      Prompt prompt = new Prompt(List.of(
          new SystemMessage(ROUTER_PROMPT),
          new UserMessage(question)
      ));

      String result = CompletableFuture
          .supplyAsync(() -> chatModel.call(prompt).getResult().getOutput().getText())
          .get(CLASSIFY_TIMEOUT_MS, TimeUnit.MILLISECONDS);

      if (result == null || result.isBlank()) {
        return AgentType.KNOWLEDGE;
      }

      String tag = result.trim().toUpperCase()
          .replaceAll("[^A-Z_]", ""); // 剥离 LLM 可能输出的标点

      return switch (tag) {
        case "DOCUMENT_OVERVIEW", "DOCUMENTOVERVIEW" -> AgentType.DOCUMENT_OVERVIEW;
        case "DOCUMENT_SEARCH", "DOCUMENTSEARCH" -> AgentType.DOCUMENT_SEARCH;
        case "HOT_SEARCH", "HOTSEARCH" -> AgentType.HOT_SEARCH;
        case "CHAT" -> AgentType.CHAT;
        default -> AgentType.KNOWLEDGE;
      };
    } catch (Exception e) {
      log.warn("IntentRouter LLM分类失败，fallback KNOWLEDGE | err={}", e.getMessage());
      return AgentType.KNOWLEDGE;
    }
  }

  private static String truncate(String s) {
    return s.length() > 50 ? s.substring(0, 50) + "…" : s;
  }
}
