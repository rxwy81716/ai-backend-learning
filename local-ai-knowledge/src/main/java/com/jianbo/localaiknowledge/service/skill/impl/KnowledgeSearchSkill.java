package com.jianbo.localaiknowledge.service.skill.impl;

import com.jianbo.localaiknowledge.service.HybridSearchService;
import com.jianbo.localaiknowledge.service.skill.Skill;
import com.jianbo.localaiknowledge.service.skill.SkillExecutor;
import com.jianbo.localaiknowledge.service.skill.SkillResult;
import com.jianbo.localaiknowledge.utils.RagFormatUtil;
import com.jianbo.localaiknowledge.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识库检索 Skill：封装混合检索能力。
 *
 * <p>同一个检索能力被复用于：
 * <ul>
 *   <li>内部 Agent（KnowledgeAgent → KnowledgeTools → HybridSearchService）</li>
 *   <li>MCP 工具（McpKnowledgeToolProvider → HybridSearchService）</li>
 *   <li>本 Skill（SkillRegistry → 本类 → HybridSearchService）</li>
 * </ul>
 * 三者共享同一套底层服务，Skill 层提供标准化的元数据描述和调用接口。
 */
@Skill(
        name = "knowledge_search",
        displayName = "知识库检索",
        description = "从企业私域文档中检索与问题最相关的内容片段，支持向量语义检索 + BM25 关键词检索 + RRF 融合排序",
        category = "retrieval",
        inputParams = {"query: 检索关键词（必填）", "userId: 用户ID（可选，用于数据隔离）", "topK: 返回数量（可选，默认5）"},
        outputFormat = "text"
)
@Component
@Slf4j
@RequiredArgsConstructor
public class KnowledgeSearchSkill implements SkillExecutor {

    private final HybridSearchService hybridSearchService;

    @Override
    public SkillResult execute(Map<String, Object> params) {
        String query = (String) params.get("query");
        if (query == null || query.isBlank()) {
            return SkillResult.fail("参数 query 不能为空");
        }

        // 优先使用传入的 userId，否则自动获取当前登录用户（支持前端不传 userId 也能查到自己的 PRIVATE 文档）
        String userId = (String) params.get("userId");
        if (userId == null || userId.isBlank()) {
            userId = SecurityUtil.getCurrentUserIdStr();
        }
        int topK = params.containsKey("topK") ? ((Number) params.get("topK")).intValue() : 5;

        log.info("[Skill] knowledge_search | query={}, userId={}, topK={}", query, userId, topK);
        List<Document> docs = hybridSearchService.searchWithOwnership(query, userId, topK);

        if (docs.isEmpty()) {
            return SkillResult.ok("知识库中未找到与 \"" + query + "\" 相关的内容。");
        }
        return SkillResult.ok(RagFormatUtil.formatDocs(docs));
    }
}
