package com.jianbo.springai.service;

import com.jianbo.springai.service.search.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * RAG 问答服务（全链路核心）
 *
 * <p>流程：检索 --> 拼接 Prompt --> 调用大模型 --> 返回答案
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagService {
  private final VectorSearchService vectorSearchService;
  private final ChatClient miniMaxChatClient;

  // ==================== System Prompt 模板 ====================

  /**
   * 系统提示：限定大模型行为
   *
   * <p>关键约束： 1. 只能根据参考资料回答（防幻觉） 2. 没有相关内容就说"不知道"（防编造） 3. 标注来源编号（可追溯）
   */
  private static final String SYSTEM_PROMPT =
      """
        你是一个专业的知识库问答助手。请严格遵守以下规则：
        1. 只能根据【参考资料】中的内容回答用户问题
        2. 如果参考资料中没有相关内容，请直接回答"根据现有资料，暂无相关信息"
        3. 不要编造、推测或添加参考资料中没有的内容
        4. 回答时请标注参考来源的编号，如 [1]、[2]
        5. 回答语言简洁清晰，分点陈述
        """;

  // ==================== 默认检索参数 ====================

  private static final int DEFAULT_TOP_K = 5;
  private static final double DEFAULT_THRESHOLD = 0.7;

  // ==================== 同步问答（阻塞） ====================

  /**
   * RAG 问答（同步）
   *
   * @param query 用户问题
   * @return 大模型生成的答案
   */
  public String chat(String query) {
    return chat(query, DEFAULT_TOP_K, DEFAULT_THRESHOLD);
  }

  /**
   * RAG 问答（同步，可调参数）
   *
   * @param query 用户问题
   * @param topK 召回数量
   * @param threshold 相似度阈值
   * @return 大模型生成的答案
   */
  public String chat(String query, int topK, double threshold) {
    log.info("RAG问答开始 | query={}, topK={}, threshold={}", query, topK, threshold);
    long startTime = System.currentTimeMillis();

    // 第一步：向量检索，召回相关文档片段
    List<Document> docs = vectorSearchService.search(query, topK, threshold);
    log.info("检索完成, 召回 {} 条文档", docs.size());

    if (docs.isEmpty()) {
      return "根据现有资料，暂无与您问题相关的内容。请尝试换个问法或补充知识库。";
    }

    // 第二步：拼接上下文
    String context = buildContext(docs);

    // 第三步：拼接完整 Prompt，调用大模型
    String userPrompt = buildUserPrompt(context, query);

    String answer = miniMaxChatClient
            .prompt()
            .system(SYSTEM_PROMPT)
            .user(userPrompt)
            .call()
            .content();

    long cost = System.currentTimeMillis() - startTime;
    log.info("RAG问答完成, 耗时: {}ms", cost);

    return answer;
  }

  // ==================== 流式问答（SSE 逐字推送） ====================

  /**
   * RAG 问答（流式）
   *
   * 流式返回的好处：
   *   - 用户不用等全部生成完，看到第一个字就开始展示
   *   - 体验像 ChatGPT 逐字打印效果
   *   - 对于长答案，减少用户等待焦虑
   *
   * @param query 用户问题
   * @return Flux<String> 文本流，前端通过 SSE 接收
   */
  public Flux<String> chatStream(String query) {
    return chatStream(query, DEFAULT_TOP_K, DEFAULT_THRESHOLD);
  }

  public Flux<String> chatStream(String query, int topK, double threshold) {
    log.info("RAG流式问答开始 | query={}, topK={}, threshold={}", query, topK, threshold);

    // 第一步：向量检索（同步，因为检索很快）
    List<Document> docs = vectorSearchService.search(query, topK, threshold);
    log.info("检索完成, 召回 {} 条文档", docs.size());

    if (docs.isEmpty()) {
      return Flux.just("根据现有资料，暂无与您问题相关的内容。");
    }

    // 第二步：拼接上下文
    String context = buildContext(docs);
    String userPrompt = buildUserPrompt(context, query);

    // 第三步：流式调用大模型（核心区别：.stream() 替代 .call()）
    return miniMaxChatClient
            .prompt()
            .system(SYSTEM_PROMPT)
            .user(userPrompt)
            .stream()       // <-- 流式调用，返回 Flux
            .content();     // <-- 每个元素是一小段文本
  }

  // ==================== Prompt 拼接工具方法 ====================

  /**
   * 把检索到的 Document 列表拼成带编号的参考资料文本
   *
   * 拼接效果示例：
   *   [1] (来源: java.pdf) Java是一门面向对象的编程语言...
   *   [2] (来源: python.pdf) Python是一门解释型语言...
   *   [3] (来源: compare.pdf) Java需要编译，Python直接解释执行...
   */
  private String buildContext(List<Document> docs) {
    return IntStream.range(0, docs.size())
            .mapToObj(i -> {
              Document doc = docs.get(i);
              // 从 metadata 中提取来源信息
              String source = String.valueOf(doc.getMetadata().getOrDefault("source", "未知来源"));
              String content = doc.getText();
              return "[%d] (来源: %s) %s".formatted(i + 1, source, content);
            })
            .collect(Collectors.joining("\n\n"));
  }

  /**
   * 拼接用户 Prompt = 参考资料 + 用户问题
   *
   * 为什么不把 Context 放到 System Prompt 里？
   *   - System Prompt 是固定的角色设定，不应该每次都变
   *   - Context 每次查询都不同，放在 User Prompt 更合理
   *   - 这也是业界 RAG 的标准做法
   */
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