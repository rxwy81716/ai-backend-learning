package com.jianbo.localaiknowledge.service;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 查询改写服务（对标 Spring AI 版）。
 *
 * <h2>差异</h2>
 * <pre>
 * Spring AI:    chatModel.call(new Prompt(messages)).getResult().getOutput().getText()
 * LangChain4j:  chatModel.chat(text)  // 简单多了
 * </pre>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class QueryRewriteService {

    private final ChatModel chatModel;
    private final ChatHistoryCacheService historyService;

    private static final String REWRITE_PROMPT = """
            你是一个查询改写助手。根据对话历史，将用户的最新问题改写为独立、完整的检索查询。
            规则：
            1. 如果问题已经很明确，原样返回
            2. 解决代词指代（"它""这个"→具体名词）
            3. 补充省略的上下文
            4. 只输出改写后的查询，不要任何解释
            
            对话历史：
            %s
            
            用户最新问题：%s
            改写后的查询：""";

    public String rewrite(String question, String sessionId) {
        try {
            var history = historyService.loadHistory(sessionId);
            if (history == null || history.size() < 2) {
                return question; // 无历史，不改写
            }

            StringBuilder historyStr = new StringBuilder();
            int start = Math.max(0, history.size() - 6);
            for (int i = start; i < history.size(); i++) {
                var msg = history.get(i);
                historyStr.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }

            String prompt = String.format(REWRITE_PROMPT, historyStr, question);
            String rewritten = chatModel.chat(prompt).trim();

            if (rewritten.isBlank() || rewritten.length() > question.length() * 3) {
                return question;
            }

            log.info("[Rewrite] {} → {}", question, rewritten);
            return rewritten;
        } catch (Exception e) {
            log.warn("[Rewrite] 失败，使用原始查询: {}", e.getMessage());
            return question;
        }
    }
}
