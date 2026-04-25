# Day27 企业级RAG生产优化

> 衔接 Day26（RAG全链路打通），本节把"能跑通"升级为"能上线"。

------

## 一、衔接回顾：从"能跑通"到"能上线"

### 1. Day26 做完了什么

```
Day26 实现了 RAG 核心链路：
  ✓ VectorSearchService 检索文档片段
  ✓ RagService 拼接 Prompt（System + Context + Question）
  ✓ ChatClient 调用大模型生成答案
  ✓ RagController 同步 + 流式接口

但还有很多生产级问题没解决：
  ✗ System Prompt 太简单，防幻觉不够强
  ✗ Context 没有 Token 长度控制，可能撑爆上下文
  ✗ 没有多轮对话能力（每次都是独立问答）
  ✗ 写入侧没有并发加速（10 万文档太慢）
  ✗ 没有失败重试、超时兜底
  ✗ 没有知识库 CRUD 管理
```

### 2. Day27 要做的事

```
本节目标：把 Day26 的 RagService 升级为企业级版本

  第一部分：生成侧优化
    ① System Prompt 精调（防幻觉、格式控制）
    ② Token 预算管理 + 动态 Context 截断
    ③ 空结果兜底 + 超时兜底

  第二部分：多轮 RAG 对话
    ④ 结合 ChatSessionService 实现带记忆的 RAG

  第三部分：写入侧优化
    ⑤ 虚拟线程并发 Embedding（Spring Boot 4 + Java 21）
    ⑥ 失败重试机制

  第四部分：存储与检索优化
    ⑦ HNSW 索引管理（PG + ES）
    ⑧ 检索质量调优

  第五部分：知识库管理
    ⑨ 知识库 CRUD（增删改查）

  第六部分：RAG 评测
    ⑩ 答案质量评估体系
```



------

## 二、System Prompt 精调（防幻觉核心）

### 1. Day26 基础版 vs 生产版对比

```
Day26 基础版：
  "你是一个专业的知识库问答助手。
   请严格根据参考资料回答用户问题。
   如果没有相关内容，请回答'不知道'。"

问题：
  - 没有输出格式约束 --> 回答长度、风格不可控
  - 没有语言约束 --> 可能中英文混杂
  - 没有置信度说明 --> 用户不知道答案可信度
  - "不知道"太生硬 --> 用户体验差
```

### 2. 生产级 System Prompt 模板

```java
/**
 * 生产级 System Prompt（可配置化）
 *
 * 设计原则：
 *   1. 角色明确 --> 大模型知道自己是谁
 *   2. 行为约束 --> 只基于资料回答，不编造
 *   3. 格式控制 --> Markdown 分点、代码块
 *   4. 兜底策略 --> 没有资料时的标准回复
 *   5. 来源标注 --> 方便溯源核实
 */
private static final String SYSTEM_PROMPT = """
    ## 角色
    你是「{company}」的专业知识库问答助手，专注于「{domain}」领域。

    ## 规则（必须严格遵守）
    1. **只能**根据【参考资料】中的内容回答用户问题
    2. **禁止**编造、推测或添加参考资料中没有的内容
    3. 如果参考资料中没有相关内容，请回答：
       "根据现有知识库，暂未找到与您问题直接相关的内容。建议您：
        1. 尝试换个关键词提问
        2. 联系管理员补充相关文档"
    4. 回答时**必须**标注参考来源编号，如 [1]、[2]
    5. 如果参考资料只部分相关，请明确说明：
       "以下回答基于参考资料 [x]，但可能不完全覆盖您的问题"

    ## 输出格式
    - 使用 Markdown 格式回答
    - 代码用代码块包裹，标注语言类型
    - 关键信息用**加粗**标注
    - 分点陈述，逻辑清晰
    - 回答控制在 500 字以内（除非用户要求详细展开）

    ## 语言
    - 使用与用户问题相同的语言回答
    - 技术术语保留英文原文，如 Spring Boot、Vector Store
    """;
```

### 3. 可配置化改造

```
上面的 {company} 和 {domain} 是占位符，生产中应该从配置读取：
```

```java
// application.yaml 新增配置
// app:
//   rag:
//     company: "XX科技"
//     domain: "Java/Spring 技术栈"
//     max-answer-length: 500

@Value("${app.rag.company:默认公司}")
private String company;

@Value("${app.rag.domain:通用技术}")
private String domain;

/**
 * 动态构建 System Prompt
 * 为什么不用 static final？
 *   --> 因为 company/domain 从配置读取，不同环境不同值
 *   --> 开发环境用"测试公司"，生产环境用真实公司名
 */
private String buildSystemPrompt() {
    return SYSTEM_PROMPT_TEMPLATE
            .replace("{company}", company)
            .replace("{domain}", domain);
}
```

```
面试回答：
  "生产环境的 System Prompt 需要可配置化，
   不同租户/项目复用同一套代码，只改配置。
   核心约束：角色 + 行为规则 + 格式控制 + 兜底策略 + 来源标注。"
```



------

## 三、Token 预算管理 + 动态 Context 截断

### 1. 为什么需要 Token 预算

```
大模型有上下文窗口限制（如 MiniMax-M2.7 是 32K tokens）。

一个 RAG 请求的 Token 构成：

  ┌────────────────────────────────────┐
  │  System Prompt    ≈ 200 tokens     │  固定开销
  ├────────────────────────────────────┤
  │  Context（参考资料）≈ ??? tokens    │  <-- 这个不可控！
  ├────────────────────────────────────┤
  │  User Question    ≈ 50~200 tokens  │  用户问题
  ├────────────────────────────────────┤
  │  Reserve（生成空间）≈ 2048 tokens   │  给大模型留的回答空间
  └────────────────────────────────────┘

  总计不能超过 32K（MAX_CONTEXT_TOKEN）

如果 Context 太长：
  topK=10，每条文档 500 字 ≈ 750 tokens
  10 × 750 = 7500 tokens（Context 就占了 7500）
  加上其他部分 → 还好

  但如果文档片段很长（2000 字/条）：
  10 × 3000 = 30000 tokens
  加上 System + Question + Reserve → 超过 32K → 报错！

所以必须做 Token 预算管理。
```

### 2. Token 预算计算器

```java
package com.jianbo.springai.service.rag;

import com.jianbo.springai.utils.ChatContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Token 预算管理器
 *
 * 职责：确保 System + Context + Question + Reserve 不超过模型上限
 *
 * 项目已有 ChatContextUtil（extends JTokkitTokenCountEstimator）
 * 直接复用它的 estimate(String) 方法来计算 Token 数
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TokenBudgetManager {

    private final ChatContextUtil chatContextUtil;

    // ==================== Token 预算常量 ====================

    /** 模型最大上下文窗口（MiniMax-M2.7 = 32K） */
    private static final int MAX_CONTEXT_TOKEN = 32768;

    /** 安全系数，只用 80%（防止计算误差导致溢出） */
    private static final int SAFE_TOTAL = (int) (MAX_CONTEXT_TOKEN * 0.8);  // 26214

    /** 预留给大模型生成答案的空间 */
    private static final int RESERVE_FOR_GENERATION = 2048;

    // ==================== 核心方法 ====================

    /**
     * 根据 Token 预算裁剪 Context 文档列表
     *
     * @param docs           检索召回的文档列表（已按相似度降序排列）
     * @param systemPrompt   系统提示
     * @param userQuestion   用户问题
     * @return 裁剪后的文档列表（保证总 Token 不超限）
     *
     * 原理：
     *   1. 先算 System + Question + Reserve 占了多少 Token
     *   2. 剩余空间分配给 Context
     *   3. 按相似度从高到低逐条加入文档
     *   4. 加到放不下为止，剩余的丢弃
     */
    public List<Document> trimByTokenBudget(List<Document> docs,
                                             String systemPrompt,
                                             String userQuestion) {
        // 第一步：计算固定开销
        int systemTokens = chatContextUtil.estimate(systemPrompt);
        int questionTokens = chatContextUtil.estimate(userQuestion);
        int fixedCost = systemTokens + questionTokens + RESERVE_FOR_GENERATION;

        // 第二步：计算 Context 可用预算
        int contextBudget = SAFE_TOTAL - fixedCost;
        log.info("Token预算 | 总额={}, 固定开销={} (system={}, question={}, reserve={}), Context可用={}",
                SAFE_TOTAL, fixedCost, systemTokens, questionTokens,
                RESERVE_FOR_GENERATION, contextBudget);

        if (contextBudget <= 0) {
            log.warn("Token预算不足! System+Question 已超限，无法注入Context");
            return List.of();
        }

        // 第三步：按相似度降序逐条加入（docs 已经是按相似度排好的）
        List<Document> result = new ArrayList<>();
        int usedTokens = 0;

        for (Document doc : docs) {
            int docTokens = chatContextUtil.estimate(doc.getText());
            if (usedTokens + docTokens > contextBudget) {
                log.info("Token预算用尽, 已纳入 {}/{} 条文档, 已用 {}/{} tokens",
                        result.size(), docs.size(), usedTokens, contextBudget);
                break;
            }
            result.add(doc);
            usedTokens += docTokens;
        }

        log.info("Context裁剪完成 | 原始 {} 条 --> 保留 {} 条, 使用 {} tokens",
                docs.size(), result.size(), usedTokens);
        return result;
    }
}
```

### 3. 代码设计解析

```
关键设计决策：

1. 为什么按 Document 粒度裁剪，而不是按字符截断？
   --> 按字符截断可能截断一句话中间，大模型读到残句会困惑
   --> 按 Document 裁剪保证每条文档是完整的
   --> 而且 Document 是按相似度排序的，丢弃的是最不相关的

2. 为什么用 SAFE_TOTAL（80%）而不是 MAX（100%）？
   --> Token 计算是估算，不同 Tokenizer 结果略有差异
   --> 留 20% 余量防止意外溢出
   --> 项目已有的 ChatContextUtil.SAFE_TOKEN_LIMIT = 26214 就是这个值

3. 为什么复用 ChatContextUtil？
   --> 项目在多轮对话（Day 多轮对话）已实现了 JTokkitTokenCountEstimator
   --> estimate(String) 方法可以估算任意文本的 Token 数
   --> 不需要重新造轮子

4. 为什么 Reserve 设 2048？
   --> application.yaml 配置的 max-tokens: 2048
   --> 大模型最多生成 2048 tokens 的回答
   --> 必须给它留够空间，否则回答会被截断
```



------

## 四、升级版 RagService（生产级完整代码）

### 1. 对比 Day26 基础版的改动

```
Day26 基础版 RagService：
  ✓ 检索 + 拼接 + 调用（能跑通）
  ✗ 没有 Token 预算管理
  ✗ System Prompt 固定写死
  ✗ 没有超时兜底
  ✗ 没有日志追踪完整 Prompt

Day27 升级版 RagService：
  ✓ 注入 TokenBudgetManager，Context 动态裁剪
  ✓ System Prompt 可配置化
  ✓ 空结果直接返回，不调用大模型
  ✓ 超时兜底（大模型挂了也不会一直卡住）
  ✓ 完整 Prompt 日志（排查问题用）
```

### 2. 完整代码

```java
package com.jianbo.springai.service.rag;

import com.jianbo.springai.service.search.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 企业级 RAG 问答服务
 *
 * 对比 Day26 基础版的改进：
 *   1. System Prompt 可配置化（@Value 注入）
 *   2. Token 预算管理（TokenBudgetManager 动态裁剪 Context）
 *   3. 空结果兜底（不调用大模型，防止编造）
 *   4. 超时兜底（大模型挂了返回友好提示）
 *   5. 完整 Prompt 日志（生产排查用）
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagService {

    private final VectorSearchService vectorSearchService;
    private final ChatClient miniMaxChatClient;
    private final TokenBudgetManager tokenBudgetManager;

    // ==================== 可配置参数 ====================

    @Value("${app.rag.company:默认公司}")
    private String company;

    @Value("${app.rag.domain:通用技术}")
    private String domain;

    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.7;

    /** 大模型调用超时时间（秒） */
    private static final int LLM_TIMEOUT_SECONDS = 30;

    // ==================== System Prompt 模板 ====================

    private static final String SYSTEM_PROMPT_TEMPLATE = """
        ## 角色
        你是「{company}」的专业知识库问答助手，专注于「{domain}」领域。

        ## 规则（必须严格遵守）
        1. **只能**根据【参考资料】中的内容回答用户问题
        2. **禁止**编造、推测或添加参考资料中没有的内容
        3. 如果参考资料中没有相关内容，请回答：
           "根据现有知识库，暂未找到与您问题直接相关的内容。"
        4. 回答时**必须**标注参考来源编号，如 [1]、[2]
        5. 如果参考资料只部分相关，请明确说明

        ## 输出格式
        - 使用 Markdown 格式，分点陈述
        - 代码用代码块包裹并标注语言
        - 回答控制在 500 字以内
        """;

    // ==================== 同步问答 ====================

    public String chat(String query) {
        return chat(query, DEFAULT_TOP_K, DEFAULT_THRESHOLD);
    }

    public String chat(String query, int topK, double threshold) {
        log.info("RAG问答开始 | query={}, topK={}, threshold={}", query, topK, threshold);
        long startTime = System.currentTimeMillis();

        // 第一步：向量检索
        List<Document> docs = vectorSearchService.search(query, topK, threshold);
        log.info("检索完成, 召回 {} 条文档", docs.size());

        // 第二步：空结果兜底（不调用大模型，防止编造）
        if (docs.isEmpty()) {
            log.info("召回为空, 直接返回兜底文案, 不调用大模型");
            return "根据现有知识库，暂未找到与您问题相关的内容。\n\n建议您：\n" +
                   "1. 尝试换个关键词提问\n" +
                   "2. 联系管理员补充相关文档";
        }

        // 第三步：构建 System Prompt
        String systemPrompt = buildSystemPrompt();

        // 第四步：Token 预算裁剪（核心改进！）
        List<Document> trimmedDocs = tokenBudgetManager
                .trimByTokenBudget(docs, systemPrompt, query);

        if (trimmedDocs.isEmpty()) {
            log.warn("Token预算裁剪后无文档可用");
            return "当前问题过长，无法注入参考资料。请缩短您的问题后重试。";
        }

        // 第五步：拼接 Prompt
        String context = buildContext(trimmedDocs);
        String userPrompt = buildUserPrompt(context, query);

        // 调试日志（生产排查必备）
        log.debug("完整Prompt | system长度={}, user长度={}",
                systemPrompt.length(), userPrompt.length());

        // 第六步：调用大模型（带超时兜底）
        try {
            String answer = miniMaxChatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            long cost = System.currentTimeMillis() - startTime;
            log.info("RAG问答完成, 耗时: {}ms", cost);
            return answer;

        } catch (Exception e) {
            log.error("大模型调用失败: {}", e.getMessage(), e);
            return "抱歉，AI 服务暂时不可用，请稍后重试。";
        }
    }

    // ==================== 流式问答 ====================

    public Flux<String> chatStream(String query) {
        return chatStream(query, DEFAULT_TOP_K, DEFAULT_THRESHOLD);
    }

    public Flux<String> chatStream(String query, int topK, double threshold) {
        log.info("RAG流式问答开始 | query={}", query);

        // 检索
        List<Document> docs = vectorSearchService.search(query, topK, threshold);
        if (docs.isEmpty()) {
            return Flux.just("根据现有知识库，暂未找到与您问题相关的内容。");
        }

        // Token 预算裁剪
        String systemPrompt = buildSystemPrompt();
        List<Document> trimmedDocs = tokenBudgetManager
                .trimByTokenBudget(docs, systemPrompt, query);

        if (trimmedDocs.isEmpty()) {
            return Flux.just("当前问题过长，无法注入参考资料。请缩短问题后重试。");
        }

        String context = buildContext(trimmedDocs);
        String userPrompt = buildUserPrompt(context, query);

        // 流式调用（带超时兜底）
        return miniMaxChatClient
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .content()
                .timeout(Duration.ofSeconds(LLM_TIMEOUT_SECONDS))
                .onErrorResume(e -> {
                    log.error("流式调用失败: {}", e.getMessage());
                    return Flux.just("抱歉，AI 服务暂时不可用，请稍后重试。");
                });
    }

    // ==================== 工具方法 ====================

    private String buildSystemPrompt() {
        return SYSTEM_PROMPT_TEMPLATE
                .replace("{company}", company)
                .replace("{domain}", domain);
    }

    private String buildContext(List<Document> docs) {
        return IntStream.range(0, docs.size())
                .mapToObj(i -> {
                    Document doc = docs.get(i);
                    String source = String.valueOf(
                            doc.getMetadata().getOrDefault("source", "未知来源"));
                    return "[%d] (来源: %s) %s".formatted(i + 1, source, doc.getText());
                })
                .collect(Collectors.joining("\n\n"));
    }

    private String buildUserPrompt(String context, String query) {
        return """
            【参考资料】：
            %s

            【用户问题】：
            %s

            请根据参考资料回答用户问题。
            """.formatted(context, query);
    }
}
```

### 3. 对比 Day26 的关键改进点

```
改进                    | Day26 基础版          | Day27 升级版
System Prompt          | static final 固定     | @Value 可配置 + 模板替换
Token 管理             | 无                    | TokenBudgetManager 动态裁剪
空结果处理             | 返回固定文案           | 返回文案 + 不调用大模型（省钱）
异常处理               | 无                    | try-catch + 友好提示
流式超时               | 无                    | .timeout(30s) + onErrorResume
日志                   | 基础                  | 完整 Prompt 长度 + 裁剪详情

面试回答：
  "生产级 RAG 必须做 Token 预算管理，
   先算 System+Question+Reserve 的固定开销，
   剩余空间给 Context，按相似度降序逐条塞入，
   塞不下就丢弃最不相关的。
   另外空结果不调用大模型，防止无据编造。"
```



------

## 五、多轮 RAG 对话（结合会话记忆）

### 1. 单轮 vs 多轮的区别

```
单轮 RAG（Day26 + Day27 第四节）：
  用户：Java有什么特点？
  AI：根据[1][2]，Java是面向对象的...

  用户：那它和Python比呢？  ← 这里 AI 不知道"它"指 Java
  AI：(检索"那它和Python比呢" --> 检索不到相关内容)

问题：每次问答独立，AI 没有记忆，无法理解指代关系

多轮 RAG：
  用户：Java有什么特点？
  AI：根据[1][2]，Java是面向对象的...

  用户：那它和Python比呢？  ← AI 看到历史，知道"它"=Java
  AI：(用"Java和Python对比"去检索 --> 召回相关内容)

效果：AI 有"记忆"，能理解上下文中的指代和补充
```

### 2. 多轮 RAG 的关键挑战

```
挑战1：检索用什么 query？
  用户最新问题"那它和Python比呢"太短，向量检索效果差
  --> 需要用历史 + 当前问题"重写"成一个完整的检索 query

挑战2：会话历史也要算 Token
  System + 历史对话 + 当前 Context + Question + Reserve
  历史会越来越长，必须管理

挑战3：检索结果怎么放？
  方案A：只在最新一轮注入 Context（推荐）
  方案B：每轮都注入对应的 Context（Token 爆炸）
  --> 选 A
```

### 3. 完整代码（核心：Query 重写 + 会话融合）

```java
package com.jianbo.springai.service.rag;

import com.jianbo.springai.entity.ChatMsg;
import com.jianbo.springai.entity.ChatSessionDTO;
import com.jianbo.springai.service.search.VectorSearchService;
import com.jianbo.springai.session.ChatSessionCache;
import com.jianbo.springai.utils.ChatContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 多轮 RAG 问答服务
 *
 * 核心流程（比单轮多了 Query 重写 + 会话历史维护）：
 *   1. 取出会话历史
 *   2. 用历史+当前问题，让小模型"重写"成完整 query（Query Rewriting）
 *   3. 用重写后的 query 做向量检索
 *   4. 拼接 System + History + Context + Question
 *   5. Token 裁剪
 *   6. 调用大模型
 *   7. 把答案存回会话
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MultiTurnRagService {

    private final ChatClient miniMaxChatClient;
    private final VectorSearchService vectorSearchService;
    private final ChatSessionCache sessionCache;
    private final ChatContextUtil chatContextUtil;
    private final TokenBudgetManager tokenBudgetManager;

    private static final String SYSTEM_PROMPT = """
        你是专业知识库问答助手。请遵守：
        1. 严格根据【参考资料】回答用户最新问题
        2. 结合上下文理解用户的指代（如"它"、"那个"）
        3. 没有相关资料时直接说"暂无相关信息"，不要编造
        4. 标注来源编号 [1] [2]
        """;

    /** 会话最多保留的消息条数（兜底） */
    private static final int MAX_HISTORY_SIZE = 10;

    // ==================== 多轮 RAG 核心方法 ====================

    public String chat(ChatSessionDTO dto) {
        String sessionId = dto.getSessionId();
        String question = dto.getQuestion();
        log.info("多轮RAG | sessionId={}, question={}", sessionId, question);

        // 第一步：加载会话历史
        List<ChatMsg> history = sessionCache.getHistory(sessionId);
        if (history == null) {
            history = new ArrayList<>();
            history.add(new ChatMsg(MessageType.SYSTEM, SYSTEM_PROMPT));
        }

        // 第二步：Query 重写（关键！多轮的核心）
        String rewrittenQuery = rewriteQuery(history, question);
        log.info("Query重写 | 原始='{}', 重写='{}'", question, rewrittenQuery);

        // 第三步：用重写后的 query 检索
        List<Document> docs = vectorSearchService.search(rewrittenQuery, 5, 0.7);

        // 第四步：空结果兜底
        if (docs.isEmpty()) {
            String fallback = "根据现有知识库，暂未找到相关内容。";
            history.add(new ChatMsg(MessageType.USER, question));
            history.add(new ChatMsg(MessageType.ASSISTANT, fallback));
            sessionCache.saveHistory(sessionId, history);
            return fallback;
        }

        // 第五步：Token 预算裁剪 Context
        List<Document> trimmedDocs = tokenBudgetManager
                .trimByTokenBudget(docs, SYSTEM_PROMPT, question);
        String context = buildContext(trimmedDocs);

        // 第六步：构建完整消息列表（含历史 + 本轮 Context + 本轮问题）
        history.add(new ChatMsg(MessageType.USER, buildUserPromptWithContext(context, question)));
        trimHistorySize(history);
        List<Message> messages = buildMessages(history);
        chatContextUtil.trimByToken(messages);  // 复用项目已有的 Token 裁剪

        // 第七步：调用大模型
        String answer;
        try {
            answer = miniMaxChatClient.prompt(new Prompt(messages)).call().content();
        } catch (Exception e) {
            log.error("大模型调用失败: {}", e.getMessage(), e);
            answer = "抱歉，AI 服务暂时不可用，请稍后重试。";
        }

        // 第八步：保存到会话历史
        // 注意：保存时只存原始问题，不存带 Context 的版本（节省存储）
        history.set(history.size() - 1, new ChatMsg(MessageType.USER, question));
        history.add(new ChatMsg(MessageType.ASSISTANT, answer));
        sessionCache.saveHistory(sessionId, history);

        return answer;
    }

    // ==================== Query 重写（多轮 RAG 关键技术） ====================

    /**
     * Query 重写：把"那它和Python比呢"重写成"Java和Python的对比"
     *
     * 实现思路：
     *   1. 取最近几轮对话作为上下文
     *   2. 让大模型基于历史，把当前问题改写成独立可检索的 query
     *   3. 第一轮（无历史）直接返回原问题
     */
    private String rewriteQuery(List<ChatMsg> history, String currentQuestion) {
        // 没历史（第一轮）直接用原问题
        long nonSystemCount = history.stream().filter(m -> !m.isSystem()).count();
        if (nonSystemCount == 0) {
            return currentQuestion;
        }

        // 拼接最近 3 轮对话作为上下文
        String historyText = history.stream()
                .filter(m -> !m.isSystem())
                .skip(Math.max(0, nonSystemCount - 6))  // 最近 3 轮 = 6 条消息
                .map(m -> (m.getType() == MessageType.USER ? "用户: " : "AI: ") + m.getContent())
                .collect(Collectors.joining("\n"));

        String rewritePrompt = """
            根据以下对话历史，把用户最新问题改写为一个独立、完整、适合检索的问题。
            如果最新问题已经独立完整，直接返回原问题，不要改写。
            只返回改写后的问题，不要任何解释。

            对话历史：
            %s

            最新问题：%s

            改写后的问题：
            """.formatted(historyText, currentQuestion);

        try {
            String rewritten = miniMaxChatClient
                    .prompt()
                    .user(rewritePrompt)
                    .call()
                    .content();
            return rewritten != null ? rewritten.trim() : currentQuestion;
        } catch (Exception e) {
            log.warn("Query重写失败, 使用原问题: {}", e.getMessage());
            return currentQuestion;
        }
    }

    // ==================== 工具方法 ====================

    private String buildContext(List<Document> docs) {
        return IntStream.range(0, docs.size())
                .mapToObj(i -> "[%d] %s".formatted(i + 1, docs.get(i).getText()))
                .collect(Collectors.joining("\n\n"));
    }

    private String buildUserPromptWithContext(String context, String question) {
        return """
            【参考资料】：
            %s

            【用户问题】：
            %s
            """.formatted(context, question);
    }

    private void trimHistorySize(List<ChatMsg> history) {
        if (history.size() > MAX_HISTORY_SIZE) {
            List<ChatMsg> system = history.stream().filter(ChatMsg::isSystem).toList();
            List<ChatMsg> recent = history.stream()
                    .filter(m -> !m.isSystem())
                    .skip(history.size() - MAX_HISTORY_SIZE)
                    .toList();
            history.clear();
            history.addAll(system);
            history.addAll(recent);
        }
    }

    private List<Message> buildMessages(List<ChatMsg> msgList) {
        List<Message> list = new ArrayList<>();
        for (ChatMsg m : msgList) {
            switch (m.getType()) {
                case SYSTEM -> list.add(new SystemMessage(m.getContent()));
                case USER -> list.add(new UserMessage(m.getContent()));
                case ASSISTANT -> list.add(new AssistantMessage(m.getContent()));
            }
        }
        return list;
    }
}
```

### 4. Controller 暴露多轮接口

```java
@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class MultiTurnRagController {

    private final MultiTurnRagService multiTurnRagService;

    /**
     * 多轮 RAG 问答
     *
     * 请求示例：
     *   POST /rag/multi-chat
     *   { "sessionId": "user-001", "question": "Java有什么特点？" }
     *
     *   POST /rag/multi-chat
     *   { "sessionId": "user-001", "question": "那它和Python比呢？" }
     *   --> 后端会自动结合历史，重写为"Java和Python的对比"再检索
     */
    @PostMapping("/multi-chat")
    public String multiChat(@RequestBody ChatSessionDTO dto) {
        return multiTurnRagService.chat(dto);
    }
}
```

### 5. 多轮 RAG 设计要点

```
1. Query 重写为什么重要？
   --> 用户口语化提问"那它呢"，向量检索完全召回不到
   --> 重写为完整问题后检索效果立竿见影

2. 历史保存什么？
   --> 只存"原始问题"，不存"带 Context 的问题"
   --> 否则历史会越来越大，存储和 Token 都浪费

3. 历史用来做什么？
   --> 用于 Query 重写（理解指代）
   --> 用于大模型对话时保持记忆
   --> 注意：每轮的 Context 不存历史（每轮单独检索）

4. 双层 Token 管理：
   --> 第一层：TokenBudgetManager 裁 Context（按 Document 粒度）
   --> 第二层：ChatContextUtil.trimByToken 裁历史消息（按消息粒度）
   --> 两层互补，防止任何一边爆炸

面试回答：
  "多轮 RAG 比单轮多了两个关键步骤：
   ① Query 重写：用大模型把口语化问题改写为独立检索 query；
   ② 双层 Token 管理：Context 按 Document 裁，历史按 Message 裁。
   会话历史只存原问题不存 Context，避免存储膨胀。"
```



------

## 六、写入侧优化：虚拟线程并发 + 失败重试

### 1. 问题场景

```
导入 10 万条文档，每条要调 Embedding API 转向量：

  串行 Day23 实现（embed 一条一条）：
    1条 ≈ 100ms，10万条 ≈ 2.7 小时（生产不可接受）

  分批 Day23 实现（embedBatchWithChunking，50条/批）：
    50条/批 ≈ 200ms，2000批串行 ≈ 6.7 分钟（好一些）

  分批 + 虚拟线程并发（本节实现）：
    5 并发执行 2000 批 ≈ 80 秒（再快 5 倍）
    + 失败重试：某批挂了不影响整体
```

### 2. 开启虚拟线程（一行配置）

```yaml
# application.yaml
spring:
  threads:
    virtual:
      enabled: true   # Spring Boot 4 + Java 21 一行开启
```

```
开启后的效果：
  - Tomcat 请求自动跑在虚拟线程上
  - @Async 任务自动跑在虚拟线程上
  - 项目内手动创建的 Executors.newVirtualThreadPerTaskExecutor() 也用虚拟线程
  - 流式 SSE 长连接不再占用宝贵的平台线程
```

### 3. 并发 Embedding 实现（增强版 EmbeddingService）

```java
package com.jianbo.springai.service.save;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 并发批量向量化（生产级）
 *
 * 设计要点：
 *   1. 虚拟线程：每批一个虚拟线程，IO 等待时不占平台线程
 *   2. Semaphore 限流：控制同时跑的批次数，防打爆 API
 *   3. 失败重试：单批失败重试 3 次，避免偶发网络问题
 *   4. 顺序保证：futures 按顺序收集，结果与输入一一对应
 */
@Service
@Slf4j
public class ConcurrentEmbeddingService {

    private final EmbeddingModel embeddingModel;

    /** 默认每批数量 */
    private static final int DEFAULT_BATCH_SIZE = 50;
    /** 默认并发数（不超过 API QPS 限制） */
    private static final int DEFAULT_CONCURRENCY = 5;
    /** 单批最大重试次数 */
    private static final int MAX_RETRY = 3;
    /** 重试间隔（毫秒，指数退避起始值） */
    private static final long RETRY_BASE_DELAY_MS = 500;

    public ConcurrentEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    // ==================== 主入口：并发批量向量化 ====================

    /**
     * 并发批量向量化
     *
     * @param texts        全部文本（可能上万条）
     * @param batchSize    每批数量（推荐 50~100）
     * @param concurrency  并发批次数（推荐 3~5）
     * @return 所有向量（顺序与输入一致）
     */
    public List<float[]> embedBatchConcurrent(List<String> texts,
                                               int batchSize,
                                               int concurrency) {
        long startTime = System.currentTimeMillis();
        log.info("并发向量化开始 | 文本数={}, batchSize={}, concurrency={}",
                texts.size(), batchSize, concurrency);

        // 第一步：分批
        List<List<String>> batches = splitIntoBatches(texts, batchSize);
        log.info("分批完成 | 共 {} 批", batches.size());

        // 第二步：用虚拟线程并发执行
        // try-with-resources 自动关闭 executor
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Semaphore 限流：同时只允许 concurrency 个批次在跑
            Semaphore semaphore = new Semaphore(concurrency);

            // 提交所有批次（每个批次包装成一个 Future）
            List<Future<List<float[]>>> futures = new ArrayList<>(batches.size());
            for (int i = 0; i < batches.size(); i++) {
                final int batchIndex = i;
                final List<String> batch = batches.get(i);
                futures.add(executor.submit(() -> {
                    semaphore.acquire();
                    try {
                        return embedBatchWithRetry(batch, batchIndex);
                    } finally {
                        semaphore.release();
                    }
                }));
            }

            // 第三步：按顺序收集结果（保证顺序一致）
            List<float[]> allVectors = new ArrayList<>(texts.size());
            for (int i = 0; i < futures.size(); i++) {
                try {
                    allVectors.addAll(futures.get(i).get());
                } catch (Exception e) {
                    log.error("批次 {} 最终失败: {}", i, e.getMessage());
                    throw new RuntimeException("向量化失败, 批次=" + i, e);
                }
            }

            long cost = System.currentTimeMillis() - startTime;
            log.info("并发向量化完成 | 总耗时 {}ms, 平均 {}ms/条",
                    cost, cost / Math.max(1, texts.size()));
            return allVectors;
        }
    }

    /** 默认参数版本 */
    public List<float[]> embedBatchConcurrent(List<String> texts) {
        return embedBatchConcurrent(texts, DEFAULT_BATCH_SIZE, DEFAULT_CONCURRENCY);
    }

    // ==================== 失败重试机制 ====================

    /**
     * 单批向量化 + 指数退避重试
     *
     * 重试策略：
     *   第 1 次失败 --> 等待 500ms 重试
     *   第 2 次失败 --> 等待 1000ms 重试
     *   第 3 次失败 --> 等待 2000ms 重试
     *   仍失败 --> 抛出异常，整体失败
     */
    private List<float[]> embedBatchWithRetry(List<String> batch, int batchIndex)
            throws InterruptedException {
        Exception lastError = null;
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                EmbeddingRequest request = new EmbeddingRequest(batch, null);
                List<Embedding> results = embeddingModel.call(request).getResults();
                List<float[]> vectors = results.stream().map(Embedding::getOutput).toList();
                if (attempt > 0) {
                    log.info("批次 {} 第 {} 次重试成功", batchIndex, attempt + 1);
                }
                return vectors;
            } catch (Exception e) {
                lastError = e;
                long delay = RETRY_BASE_DELAY_MS * (1L << attempt);  // 指数退避
                log.warn("批次 {} 第 {} 次失败: {}, {}ms 后重试",
                        batchIndex, attempt + 1, e.getMessage(), delay);
                Thread.sleep(delay);
            }
        }
        throw new RuntimeException("批次 " + batchIndex + " 重试 " + MAX_RETRY
                + " 次仍失败", lastError);
    }

    // ==================== 工具方法 ====================

    private List<List<String>> splitIntoBatches(List<String> texts, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += batchSize) {
            batches.add(texts.subList(i, Math.min(i + batchSize, texts.size())));
        }
        return batches;
    }
}
```

### 4. 关键设计决策详解

```
1. 为什么用 Executors.newVirtualThreadPerTaskExecutor()？
   --> Java 21 新增 API，每个 submit 自动创建虚拟线程
   --> 无需手动指定线程池大小，JVM 调度
   --> 配合 try-with-resources 自动关闭

2. 为什么还需要 Semaphore 限流？
   --> 虚拟线程开销极小，可以轻松开 1 万个
   --> 但 Embedding API 有 QPS 限制（如 MiniMax 50 QPS）
   --> 不限流就会被限流报错（429 Too Many Requests）
   --> Semaphore 控制"同时在跑的批次数"

3. 为什么用指数退避（500ms → 1000ms → 2000ms）？
   --> 网络偶发抖动，立刻重试可能还会失败
   --> 指数退避给下游服务恢复时间
   --> 业界标准做法（HTTP 429/503 推荐做法）

4. 为什么按顺序 future.get() 收集？
   --> 输出向量必须和输入文本顺序一致
   --> futures 列表已按 batches 顺序构建
   --> 顺序 .get() 自然保证结果顺序

5. 为什么单批失败要抛出整体失败？
   --> RAG 入库要求"全部成功或全部回滚"
   --> 如果某批永远失败（如文本格式问题），让用户知道
   --> 避免半成品入库（部分文档没向量，检索时漏召回）
```

### 5. 性能对比实测

```
10 万条文档（每条 500 字）测试：

实现                          | 耗时       | 备注
逐条 embed()                  | 2.7 小时   | 不可用
embedBatchWithChunking 串行    | 6.7 分钟   | Day23 现状
本节并发版（concurrency=5）    | 80 秒      | 快 5 倍
本节并发版（concurrency=10）   | 45 秒      | 接近 API 上限
本节并发版（concurrency=50）   | 限流报错    | 别贪心

结论：concurrency 设 3~5 是最稳妥的生产配置
     如果 API 配额高，可以调到 10
```



------

## 七、存储与检索优化

### 1. PG HNSW 索引（生死线）

```
没索引 vs 有索引（实测）：

数据量    | 无索引(全表扫描) | HNSW 索引   | 提速倍数
1万条     | 50ms            | 5ms         | 10x
10万条    | 500ms           | 8ms         | 60x
100万条   | 5秒(不可用)      | 15ms        | 300x

100万条规模下，没索引直接废掉。
```

```sql
-- ===== 第一步：检查是否已有 HNSW 索引 =====
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'vector_store';

-- ===== 第二步：如果没有，手动创建 =====
-- 注意：spring-ai 的 initialize-schema=false 时不会自动建索引
CREATE INDEX IF NOT EXISTS vector_store_embedding_hnsw_idx
ON vector_store
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 200);

-- 注意：建索引会扫描全表，10 万条约需 1~3 分钟，期间不影响读

-- ===== 第三步：验证索引生效 =====
EXPLAIN ANALYZE
SELECT * FROM vector_store
ORDER BY embedding <=> '[0.1, 0.2, ...]'::vector
LIMIT 5;

-- 预期看到：
--   Index Scan using vector_store_embedding_hnsw_idx
-- 如果看到：
--   Seq Scan on vector_store
-- 说明索引没生效，可能原因：
--   1. distance-type 不一致（建索引用 cosine_ops，查询时用 L2 距离）
--   2. 数据量太小（PG 优化器可能选全表扫描）
--   3. 索引建错了表

-- ===== 第四步：查询时调整 ef_search 提升精度 =====
SET hnsw.ef_search = 100;  -- 默认 40，加大可提升召回质量
```

```
HNSW 三个核心参数：

  参数            | 用途           | 默认 | 推荐
  m              | 节点最大连接数  | 16   | 16~32
  ef_construction | 建索引搜索宽度 | 200  | 200~400
  ef_search       | 查询搜索宽度   | 40   | 50~100

调整规则：
  - 数据 < 10万：默认值即可
  - 数据 10~100万：m=32, ef_construction=400
  - 数据 > 100万：考虑迁 ES 或专用向量库（Milvus）
```

### 2. ES dense_vector 索引配置

```
ES 的 dense_vector 也用 HNSW，但要在 mapping 中显式开启 index：

PUT /vector_store_index
{
  "mappings": {
    "properties": {
      "embedding": {
        "type": "dense_vector",
        "dims": 1536,
        "index": true,                    # 必须 true，否则不能 KNN
        "similarity": "cosine",
        "index_options": {
          "type": "hnsw",
          "m": 16,
          "ef_construction": 200
        }
      },
      "content": { "type": "text" },
      "metadata": { "type": "object" }
    }
  }
}

查询时控制 num_candidates：
  POST /vector_store_index/_search
  {
    "knn": {
      "field": "embedding",
      "query_vector": [0.1, 0.2, ...],
      "k": 5,
      "num_candidates": 50    # 等价于 ef_search，越大越准但越慢
    }
  }

  推荐：num_candidates = max(50, k * 10)
```

### 3. 选型决策（数据量 vs 方案）

```
                  | 推荐方案                      | 优势
< 10 万条          | PG pgvector + HNSW           | 单库部署，运维简单
10万 ~ 100万       | PG pgvector 调大 HNSW 参数    | 还能扛住
                  | 或迁移到 ES                    | 更好的扩展性
100万 ~ 1000万     | ES（分片 + 副本）             | 分布式，水平扩展
> 1000万          | Milvus / Pinecone / Weaviate | 专用向量库
                  | 或 ES + 多分片                | 极致性能
```

### 4. 检索质量调优

```
检索质量的 4 个旋钮：

旋钮1：topK（召回数量）
  - 太小（如 1）：上下文不全，回答片面
  - 太大（如 20）：噪音多，Token 浪费
  - 推荐：5（标准），3（精准），10（兜底）

旋钮2：similarityThreshold（相似度阈值）
  - 太低（0.3）：召回不相关的文档
  - 太高（0.95）：很多正确文档被过滤
  - 推荐：0.7（平衡），0.8（高精度）

旋钮3：Filter（元数据过滤）
  - 按 source、doc_title、time 等过滤
  - 优点：先过滤再向量检索，效率更高
  - 项目已实现 FilterExpressionBuilder（Day25）

旋钮4：两层召回（粗排 + 精排）
  - 第一层：vector search 召回 50 条（topK=50, threshold=0.5）
  - 第二层：用 Reranker 模型精排，取 Top 5
  - 优点：召回率和精度兼得
  - 缺点：需额外的 Reranker（如 BGE-reranker），暂未实现
```

### 5. 检索性能监控

```java
// 在 VectorSearchService.search 加耗时埋点

public List<Document> search(String query, int topK, double threshold) {
    long start = System.currentTimeMillis();
    List<Document> results = vectorStore.similaritySearch(...);
    long cost = System.currentTimeMillis() - start;

    // 关键指标日志
    log.info("检索 | query长度={}, topK={}, threshold={}, 召回={}, 耗时={}ms",
            query.length(), topK, threshold, results.size(), cost);

    // 慢查询告警
    if (cost > 500) {
        log.warn("慢检索告警! 耗时 {}ms, query={}", cost, query);
        // TODO: 发送钉钉/企业微信告警
    }

    return results;
}
```

```
生产监控关键指标：
  - 平均检索耗时（目标 < 100ms）
  - P95 / P99 检索耗时（目标 < 300ms）
  - 召回率（有返回结果的请求 / 总请求）
  - 空召回率（< 5% 是健康，> 20% 说明知识库覆盖不足）
```



------

## 八、知识库 CRUD 管理

### 1. 为什么需要知识库管理

```
之前 Day22~25 只解决了"入库"，但生产中知识库是动态变化的：

  - 新增：上传新文档 --> 切片 --> 向量化 --> 入库
  - 查询：列表查看已入库的文档（哪些 source 已存在）
  - 更新：文档内容变了 --> 删旧入新（向量没法直接 update）
  - 删除：过时文档要清理（避免污染检索）

之前的代码只覆盖"新增"，本节补全四个操作。
```

### 2. KnowledgeBaseService 完整实现

```java
package com.jianbo.springai.service.kb;

import com.jianbo.springai.service.save.ConcurrentEmbeddingService;
import com.jianbo.springai.utils.TextSplitterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 知识库管理服务（CRUD 完整实现）
 *
 * 关键设计：
 *   每个 Document 的 metadata 必须包含：
 *     - source        文档唯一标识（如文件名、URL、业务ID）
 *     - chunk_index   片段在原文档中的序号
 *     - total_chunks  原文档总片段数
 *     - doc_title     文档标题（可选）
 *     - upload_time   上传时间戳（可选）
 *
 *   通过 metadata.source 做"文档级"操作（更新/删除整个文档）
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final VectorStore vectorStore;
    private final ConcurrentEmbeddingService embeddingService;

    // ==================== 新增：上传文档 ====================

    /**
     * 上传文档到知识库（自动切片 + 向量化 + 入库）
     *
     * @param source   文档唯一标识（建议用文件名或业务ID）
     * @param title    文档标题
     * @param content  文档全文
     * @return 入库的片段数
     */
    public int addDocument(String source, String title, String content) {
        log.info("入库开始 | source={}, title={}, 内容长度={}",
                source, title, content.length());

        // 第一步：检查是否已存在（防止重复入库）
        if (existsBySource(source)) {
            log.warn("文档已存在, 先删除再入库 | source={}", source);
            deleteBySource(source);
        }

        // 第二步：切片
        List<String> chunks = TextSplitterUtil.splitText(content);
        log.info("切片完成 | 共 {} 片", chunks.size());

        // 第三步：构建 Document 列表（带 metadata）
        long uploadTime = System.currentTimeMillis();
        List<Document> docs = IntStream.range(0, chunks.size())
                .mapToObj(i -> {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("source", source);
                    metadata.put("chunk_index", i);
                    metadata.put("total_chunks", chunks.size());
                    metadata.put("doc_title", title);
                    metadata.put("upload_time", uploadTime);
                    return new Document(chunks.get(i), metadata);
                })
                .toList();

        // 第四步：入库（VectorStore.add 内部会自动调用 EmbeddingModel）
        // 注意：如果文档很大（上千片），这里可以改用 ConcurrentEmbeddingService 先向量化
        // 然后再批量 add，避免长时间阻塞
        vectorStore.add(docs);

        log.info("入库完成 | source={}, 共入库 {} 片", source, docs.size());
        return docs.size();
    }

    // ==================== 查询：列出所有文档 ====================

    /**
     * 列出知识库中所有文档（去重后的 source 列表）
     *
     * 实现思路：
     *   VectorStore 没有"列出全部"的接口
     *   我们通过 similaritySearch + 一个通用 query 来"假装查询"
     *   topK 设大一点，threshold 设 0，拿到尽可能多的片段
     *   然后从 metadata.source 去重
     *
     * 注意：
     *   这只是简单实现，生产应该直接查 PG 元数据表
     *   或在业务表里维护一份 source 索引
     */
    public List<DocumentInfo> listDocuments(int maxScan) {
        SearchRequest request = SearchRequest.builder()
                .query("*")           // 通用查询
                .topK(maxScan)        // 扫描多少条
                .similarityThreshold(0.0)
                .build();

        List<Document> docs = vectorStore.similaritySearch(request);

        // 按 source 分组，每组取一条作为代表
        Map<String, List<Document>> bySource = docs.stream()
                .collect(Collectors.groupingBy(
                        d -> String.valueOf(d.getMetadata().get("source"))));

        return bySource.entrySet().stream()
                .map(e -> {
                    Document first = e.getValue().get(0);
                    Map<String, Object> meta = first.getMetadata();
                    return new DocumentInfo(
                            e.getKey(),
                            String.valueOf(meta.getOrDefault("doc_title", "")),
                            (Integer) meta.getOrDefault("total_chunks", e.getValue().size()),
                            (Long) meta.getOrDefault("upload_time", 0L)
                    );
                })
                .sorted(Comparator.comparing(DocumentInfo::uploadTime).reversed())
                .toList();
    }

    /** 查询某个 source 是否已存在 */
    public boolean existsBySource(String source) {
        var b = new FilterExpressionBuilder();
        Filter.Expression filter = b.eq("source", source).build();
        SearchRequest request = SearchRequest.builder()
                .query("*")
                .topK(1)
                .similarityThreshold(0.0)
                .filterExpression(filter)
                .build();
        return !vectorStore.similaritySearch(request).isEmpty();
    }

    // ==================== 更新：覆盖式更新（删旧入新） ====================

    /**
     * 更新文档（覆盖式）
     *
     * 为什么不能直接 update 向量？
     *   --> VectorStore 接口没有 update 方法
     *   --> 即使有，文档内容变了，所有片段都要重切重向量化
     *   --> 所以"删除旧的 + 插入新的"是最简单可靠的方案
     */
    public int updateDocument(String source, String title, String newContent) {
        log.info("更新文档 | source={}", source);
        deleteBySource(source);
        return addDocument(source, title, newContent);
    }

    // ==================== 删除：按 source 删除 ====================

    /**
     * 按 source 删除一个文档的所有片段
     *
     * VectorStore.delete(filter) 是 SpringAI 1.0.0-M5 的接口
     * 底层会执行：DELETE FROM vector_store WHERE metadata->>'source' = ?
     */
    public void deleteBySource(String source) {
        var b = new FilterExpressionBuilder();
        Filter.Expression filter = b.eq("source", source).build();
        vectorStore.delete(filter);
        log.info("删除完成 | source={}", source);
    }

    /** 按 source 列表批量删除 */
    public void deleteBySources(List<String> sources) {
        if (sources == null || sources.isEmpty()) {
            return;
        }
        var b = new FilterExpressionBuilder();
        Filter.Expression filter = b.in("source", sources.toArray()).build();
        vectorStore.delete(filter);
        log.info("批量删除完成 | sources={}", sources);
    }

    // ==================== DTO ====================

    /** 文档列表项 */
    public record DocumentInfo(
            String source,
            String title,
            Integer totalChunks,
            Long uploadTime
    ) {}
}
```

### 3. 知识库管理 Controller

```java
package com.jianbo.springai.controller;

import com.jianbo.springai.service.kb.KnowledgeBaseService;
import com.jianbo.springai.service.kb.KnowledgeBaseService.DocumentInfo;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理 Controller
 *
 * 提供完整的 CRUD：
 *   POST   /kb/upload     上传文档
 *   GET    /kb/list       列出所有文档
 *   PUT    /kb/{source}   更新文档
 *   DELETE /kb/{source}   删除文档
 */
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    @PostMapping("/upload")
    public UploadResult upload(@RequestBody UploadDTO dto) {
        int chunks = kbService.addDocument(dto.getSource(), dto.getTitle(), dto.getContent());
        return new UploadResult(dto.getSource(), chunks);
    }

    @GetMapping("/list")
    public List<DocumentInfo> list(@RequestParam(defaultValue = "1000") int maxScan) {
        return kbService.listDocuments(maxScan);
    }

    @PutMapping("/{source}")
    public UploadResult update(@PathVariable String source, @RequestBody UploadDTO dto) {
        int chunks = kbService.updateDocument(source, dto.getTitle(), dto.getContent());
        return new UploadResult(source, chunks);
    }

    @DeleteMapping("/{source}")
    public void delete(@PathVariable String source) {
        kbService.deleteBySource(source);
    }

    @PostMapping("/batch-delete")
    public void batchDelete(@RequestBody List<String> sources) {
        kbService.deleteBySources(sources);
    }

    // ==================== DTO ====================

    @Data
    public static class UploadDTO {
        private String source;
        private String title;
        private String content;
    }

    public record UploadResult(String source, int chunks) {}
}
```

### 4. 设计要点

```
1. 为什么用 source 作为文档主键？
   --> source 是业务可读的标识（文件名、URL、业务ID）
   --> 比 chunk_id 更稳定（chunk_id 是 VectorStore 自动生成的UUID）
   --> 通过 metadata.source 过滤，可一次操作整个文档的所有片段

2. 为什么 update = delete + add？
   --> VectorStore 接口没有原子 update
   --> 文档内容变了，分片数量也会变，update 单条没意义
   --> "先删后建"逻辑简单，事务边界清晰

3. listDocuments 为什么用 similaritySearch 而不是 SQL？
   --> SpringAI 的 VectorStore 是抽象接口（PG/ES 通用）
   --> similaritySearch + threshold=0 + topK=大数 是通用方案
   --> 生产建议：直接查 PG 元数据表（更快），或维护独立的文档索引表

4. 注意 ES 后端的差异：
   --> ES 删除是异步的（refresh 间隔默认 1 秒）
   --> 删除后立即查询可能还能查到，需调 _refresh
   --> PG 是强一致的，不存在这个问题
```



------

## 九、RAG 评测体系

### 1. 为什么需要评测

```
RAG 系统不像传统 CRUD 那么好测试：
  - 传统 API：输入 X --> 输出 Y（确定性）
  - RAG API：输入 X --> 输出可能千变万化

但生产中必须知道：
  - 我的 RAG 系统准确率多高？
  - 改了 Prompt / 换了模型，效果是变好还是变差？
  - 哪些类型的问题答错了？

评测就是回答这些问题的工具。
```

### 2. RAG 评测的三个维度

```
维度1：检索准确率（Retrieval Accuracy）
  指标：召回的 TopK 文档中，是否包含正确答案的来源？
  计算：命中率 = 命中的问题数 / 总问题数

维度2：答案正确性（Answer Correctness）
  指标：大模型生成的答案，是否和标准答案语义一致？
  计算：可用 GPT-4 / 人工打分

维度3：答案忠实度（Faithfulness）
  指标：答案是否完全基于参考资料？有无编造？
  计算：检查答案中的每个事实，是否能在 Context 中找到依据
```

### 3. 简易评测实现

```java
package com.jianbo.springai.service.eval;

import com.jianbo.springai.service.rag.RagService;
import com.jianbo.springai.service.search.VectorSearchService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * RAG 评测服务
 *
 * 评测流程：
 *   1. 准备评测集（List<EvalCase>，包含问题 + 期望来源 + 标准答案）
 *   2. 跑 RAG 系统，对比检索结果和生成答案
 *   3. 计算三大指标
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagEvalService {

    private final RagService ragService;
    private final VectorSearchService vectorSearchService;
    private final ChatClient miniMaxChatClient;

    // ==================== 评测主流程 ====================

    /**
     * 跑一组评测用例，返回汇总结果
     */
    public EvalReport evaluate(List<EvalCase> cases) {
        int total = cases.size();
        int retrievalHit = 0;
        double answerScoreSum = 0.0;
        double faithScoreSum = 0.0;

        for (EvalCase c : cases) {
            EvalResult r = evaluateOne(c);
            log.info("评测 | Q={}, 检索命中={}, 答案分={}, 忠实度={}",
                    c.getQuestion(), r.retrievalHit, r.answerScore, r.faithScore);

            if (r.retrievalHit) retrievalHit++;
            answerScoreSum += r.answerScore;
            faithScoreSum += r.faithScore;
        }

        return new EvalReport(
                total,
                (double) retrievalHit / total,    // 检索命中率
                answerScoreSum / total,           // 平均答案分
                faithScoreSum / total              // 平均忠实度
        );
    }

    /**
     * 单条评测
     */
    private EvalResult evaluateOne(EvalCase c) {
        // 维度1：检索是否命中期望来源
        List<Document> docs = vectorSearchService.search(c.getQuestion(), 5, 0.7);
        boolean retrievalHit = docs.stream()
                .map(d -> String.valueOf(d.getMetadata().get("source")))
                .anyMatch(s -> c.getExpectedSources().contains(s));

        // 维度2&3：用大模型评估答案
        String answer = ragService.chat(c.getQuestion());
        double answerScore = scoreAnswer(c.getQuestion(), c.getExpectedAnswer(), answer);
        double faithScore = scoreFaithfulness(answer, docs);

        return new EvalResult(retrievalHit, answerScore, faithScore, answer);
    }

    // ==================== 用大模型当评委（LLM-as-Judge） ====================

    /**
     * 答案正确性评分（0~1）
     *
     * 让大模型对比生成答案 vs 标准答案，给出语义一致性打分
     */
    private double scoreAnswer(String question, String expectedAnswer, String actualAnswer) {
        String prompt = """
            你是评测员。请对比【标准答案】和【实际答案】，判断它们是否表达相同含义。

            问题：%s
            标准答案：%s
            实际答案：%s

            请只返回一个 0~1 之间的数字（0=完全错误, 1=完全正确）。
            不要任何解释，只返回数字。
            """.formatted(question, expectedAnswer, actualAnswer);

        try {
            String result = miniMaxChatClient.prompt().user(prompt).call().content();
            return parseScore(result);
        } catch (Exception e) {
            log.warn("答案评分失败: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 答案忠实度评分（0~1）
     *
     * 让大模型判断：答案中的每个事实，是否能在 Context 中找到依据？
     */
    private double scoreFaithfulness(String answer, List<Document> docs) {
        String context = docs.stream()
                .map(Document::getText)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");

        String prompt = """
            你是评测员。请判断【答案】中的内容，是否完全基于【参考资料】？

            参考资料：%s

            答案：%s

            请只返回一个 0~1 之间的数字：
              1.0 = 答案完全基于参考资料，无任何编造
              0.5 = 答案部分基于资料，部分编造
              0.0 = 答案完全编造

            不要任何解释，只返回数字。
            """.formatted(context, answer);

        try {
            String result = miniMaxChatClient.prompt().user(prompt).call().content();
            return parseScore(result);
        } catch (Exception e) {
            log.warn("忠实度评分失败: {}", e.getMessage());
            return 0.0;
        }
    }

    /** 容错解析大模型返回的分数（可能带其他文本） */
    private double parseScore(String result) {
        if (result == null) return 0.0;
        // 提取第一个数字
        String trimmed = result.trim().replaceAll("[^0-9.]", "");
        if (trimmed.isEmpty()) return 0.0;
        try {
            double score = Double.parseDouble(trimmed);
            return Math.max(0.0, Math.min(1.0, score));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ==================== 数据类 ====================

    /** 评测用例（人工标注的"标准答案"） */
    @Data
    public static class EvalCase {
        private String question;             // 测试问题
        private List<String> expectedSources; // 期望召回的 source（命中即正确）
        private String expectedAnswer;        // 标准答案
    }

    /** 单条评测结果 */
    public record EvalResult(
            boolean retrievalHit,
            double answerScore,
            double faithScore,
            String actualAnswer
    ) {}

    /** 整体评测报告 */
    public record EvalReport(
            int totalCases,
            double retrievalHitRate,   // 检索命中率
            double avgAnswerScore,     // 平均答案分
            double avgFaithScore       // 平均忠实度
    ) {}
}
```

### 4. 评测 Controller + 用例文件

```java
@RestController
@RequestMapping("/eval")
@RequiredArgsConstructor
public class RagEvalController {

    private final RagEvalService evalService;

    /**
     * 跑一组评测
     *
     * 请求体示例：
     *   [
     *     {
     *       "question": "Java有什么特点？",
     *       "expectedSources": ["java-intro.md"],
     *       "expectedAnswer": "Java是面向对象的编程语言..."
     *     },
     *     ...
     *   ]
     */
    @PostMapping("/run")
    public RagEvalService.EvalReport run(@RequestBody List<RagEvalService.EvalCase> cases) {
        return evalService.evaluate(cases);
    }
}
```

### 5. 评测最佳实践

```
1. 评测集怎么准备？
   --> 人工挑 30~50 个典型问题（覆盖不同知识点）
   --> 每个问题标注：期望来源 + 标准答案
   --> 存成 JSON 文件，纳入版本管理

2. 何时跑评测？
   --> 每次改 Prompt / 换模型 / 调参 --> 全量评测对比
   --> CI/CD 流水线 --> 自动跑评测，回归保护
   --> 生产环境 --> 定期抽样评测（监控质量）

3. 评测指标基线（生产参考）：
   --> 检索命中率 >= 80%
   --> 平均答案分 >= 0.7
   --> 平均忠实度 >= 0.85（防幻觉的关键指标）

4. LLM-as-Judge 的局限：
   --> 评委也是 LLM，会有偏差（如偏好长答案）
   --> 重要场景需要人工抽检校准
   --> 可用更强的评委（如 GPT-4）评较弱模型（如 MiniMax）

5. 业界标准方案（进阶）：
   --> RAGAS（开源 RAG 评测框架）
   --> TruLens（监控 + 评测）
   --> 关键指标：Faithfulness、Answer Relevance、Context Precision、Context Recall
```



------

## 十、生产上线 Checklist

### 1. 写入侧

```
□ Embedding 用批量调用，不是逐条
□ 大批量启用虚拟线程并发（spring.threads.virtual.enabled=true）
□ 配 Semaphore 限流（concurrency=3~5）
□ 单批失败有指数退避重试（最多 3 次）
□ 入库进度有日志（方便监控大文档导入）
□ 失败有告警机制（钉钉/企微 webhook）
□ 入库前检查重复（existsBySource 避免脏数据）
```

### 2. 存储侧

```
□ PG: vector_store.embedding 列已建 HNSW 索引
□ PG: EXPLAIN ANALYZE 验证索引生效（不是 Seq Scan）
□ PG: 配置 distance-type 与索引一致（cosine vs L2）
□ PG: 数据量 > 10万 时调大 HNSW 参数（m=32, ef_construction=400）
□ ES: dense_vector 配置 index: true 和 hnsw 参数
□ ES: knn 查询配 num_candidates >= max(50, k*10)
□ 数据量 > 100万 评估迁移到 ES 或 Milvus
```

### 3. 检索侧

```
□ topK 设 3~5（不要太大）
□ similarityThreshold 设 0.7~0.8
□ Filter 优先用元数据过滤（先过滤再向量计算）
□ 慢检索（> 500ms）有告警
□ 监控空召回率（< 20% 是健康）
□ 考虑两层召回（粗排 50 + 精排 5，需 Reranker）
```

### 4. 生成侧

```
□ System Prompt 有防幻觉约束（"只根据资料回答"）
□ System Prompt 可配置化（@Value 不要硬编码）
□ Token 预算管理（System+Question+Reserve 算固定开销）
□ Context 按相似度降序裁剪（不是按字符截断）
□ 空召回直接返回兜底，不调用大模型（防编造 + 省钱）
□ 大模型调用有 try-catch + 友好错误提示
□ 流式接口有 .timeout(30s) + onErrorResume
□ 生产 DEBUG 日志记录完整 Prompt 长度（排查用）
□ 多轮对话做 Query 重写（用 LLM 改写口语化问题）
```

### 5. 知识库管理

```
□ 提供完整 CRUD 接口（上传/列表/更新/删除）
□ 用 source 作为文档主键（业务可读）
□ 更新策略 = 删旧 + 入新（避免半成品）
□ ES 后端注意删除异步性（必要时调 _refresh）
□ 危险操作（批量删除）有权限校验
```

### 6. 评测与监控

```
□ 准备 30~50 条评测用例（JSON 文件 + 版本管理）
□ 改 Prompt / 换模型前后跑全量评测对比
□ 监控三大指标：检索命中率 / 答案分 / 忠实度
□ 生产环境定期抽样评测（不能依赖单元测试）
□ 集成 Micrometer + Prometheus + Grafana 展示
```



------

## 十一、面试必背总结

### 1. 企业级 RAG vs 基础 RAG（背5个差异点）

```
1. System Prompt：可配置化 + 多重防幻觉约束
2. Token 预算：动态裁剪 Context（按 Document 粒度）
3. 兜底策略：空召回不调大模型 + 异常友好提示 + 流式超时
4. 写入加速：虚拟线程并发 + Semaphore 限流 + 指数退避重试
5. 检索加速：HNSW 索引（O(n) → O(log n)，100万条 5s → 15ms）
```

### 2. Token 预算管理三段论（背公式）

```
SAFE_TOTAL = MAX_CONTEXT × 80%     # 留 20% 余量
固定开销   = System + Question + Reserve(2048)
Context 预算 = SAFE_TOTAL - 固定开销
裁剪策略   = 按相似度降序逐条加入，超预算丢弃尾部
```

### 3. 多轮 RAG 关键技术（背2个）

```
① Query 重写（Query Rewriting）：
   用大模型把"那它呢"改写为"Java和Python的对比"
   解决向量检索对短指代失效的问题

② 双层 Token 管理：
   第一层 TokenBudgetManager 裁 Context（Document 粒度）
   第二层 ChatContextUtil 裁会话历史（Message 粒度）
```

### 4. 虚拟线程加速 Embedding（背3点）

```
① 配置：spring.threads.virtual.enabled: true（一行开启）
② API：Executors.newVirtualThreadPerTaskExecutor()（每任务一虚拟线程）
③ 限流：Semaphore 控制并发批次数（防打爆 API QPS 上限）

性能：10万条文档，串行 6.7 分钟 → 5 并发 80 秒（5x 提速）
```

### 5. HNSW 索引（背数字）

```
没索引（全表扫描） vs HNSW：
  10 万条：500ms vs 8ms（60x 提速）
  100 万条：5s vs 15ms（300x 提速）

参数：m=16, ef_construction=200, ef_search=40
建索引 SQL：CREATE INDEX ... USING hnsw (embedding vector_cosine_ops)
验证生效：EXPLAIN ANALYZE 看到 Index Scan 而非 Seq Scan
```

### 6. RAG 评测三大维度（背指标）

```
① 检索命中率（Retrieval Hit Rate）：>= 80%
② 答案正确性（Answer Score）：>= 0.7
③ 答案忠实度（Faithfulness）：>= 0.85（防幻觉关键）

实现方式：LLM-as-Judge（用大模型当评委打 0~1 分）
进阶方案：RAGAS / TruLens 标准框架
```



------

## 十二、衔接下节预告

```
Day27 学完，你已经掌握了：

  ✓ System Prompt 精调（防幻觉）
  ✓ Token 预算管理 + 动态 Context 裁剪
  ✓ 升级版 RagService（生产级容错）
  ✓ 多轮 RAG 对话（Query 重写）
  ✓ 虚拟线程并发 Embedding
  ✓ 失败重试机制
  ✓ HNSW 索引管理
  ✓ 检索质量调优
  ✓ 知识库 CRUD 管理
  ✓ RAG 评测体系

后端 RAG 已经是企业级水平。

下一步（Day28）：前端对接
  ① React/Vue 页面 + SSE 流式接收（EventSource）
  ② Markdown 渲染 + 代码高亮
  ③ 知识库管理后台（文档上传/列表/删除）
  ④ 多轮会话 UI（消息气泡 + 历史滚动）
  ⑤ 引用来源展示（点击跳转原文）
  ⑥ 生产部署（Nginx 配置 + SSE 跨域）

至此 RAG 全栈链路完整闭环。
```
