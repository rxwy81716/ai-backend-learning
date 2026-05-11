package com.jianbo.localaiknowledge.service.mcp;

import com.jianbo.localaiknowledge.service.HotSearchService;
import com.jianbo.localaiknowledge.service.HybridSearchService;
import com.jianbo.localaiknowledge.service.WebSearchService;
import com.jianbo.localaiknowledge.utils.RagFormatUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具提供者：把知识库核心能力暴露为 MCP 协议接口。
 *
 * <h2>什么是 MCP？</h2>
 * <p>MCP（Model Context Protocol）是 Anthropic 推出的开放协议标准，
 * 定义了 AI 模型与外部工具/数据源之间的通信规范。
 * 类似 USB 协议让各种设备即插即用，MCP 让各种 AI 客户端（Claude Desktop、
 * Cursor、Windsurf 等）可以用统一方式调用外部工具。</p>
 *
 * <h2>与 Function Calling 的区别</h2>
 * <ul>
 *   <li><b>Function Calling</b>（项目已有的 @Tool）：工具定义在应用内部，
 *       只有本应用的 Agent 能调用</li>
 *   <li><b>MCP</b>（本类实现）：工具通过标准协议暴露，任何支持 MCP 的
 *       AI 客户端都能远程发现并调用</li>
 * </ul>
 *
 * <h2>暴露的工具</h2>
 * <ol>
 *   <li>{@code searchKnowledge} — 知识库混合检索（向量 + BM25 + RRF）</li>
 *   <li>{@code searchHotTopics} — 各平台实时热榜查询</li>
 *   <li>{@code searchWeb} — 网络搜索（Tavily API）</li>
 * </ol>
 *
 * <p>Spring AI 的 MCP Server Starter 会自动扫描所有 @Tool 方法，
 * 通过 SSE 传输层暴露为 MCP 接口。外部客户端连接 {@code /mcp/sse} 端点即可使用。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class McpKnowledgeToolProvider {

    private final HybridSearchService hybridSearchService;
    private final HotSearchService hotSearchService;
    private final WebSearchService webSearchService;

    /**
     * 知识库检索：从企业私域知识库中搜索与问题最相关的文档片段。
     *
     * <p>底层使用混合检索（向量语义 + BM25 关键词 + RRF 融合排序），
     * 比单纯的向量检索或关键词检索精度更高。</p>
     *
     * @param query  检索关键词，建议保留专有名词、人名、产品名
     * @param userId 用户ID，用于数据隔离（仅返回该用户有权访问的文档）
     * @param topK   返回结果数量，默认5
     * @return 检索到的文档片段（带来源标注）
     */
    @Tool(name = "searchKnowledge", description = """
        从企业私域知识库中检索与问题最相关的文档片段。
        支持 PDF、Word、PPT、TXT 等多种格式的上传文档。
        使用混合检索（向量语义 + BM25 关键词），比单一检索方式更精准。
        适用场景：查询产品文档、内部资料、合同条款、专业领域知识等。""")
    public String searchKnowledge(
            @ToolParam(description = "检索关键词") String query,
            @ToolParam(description = "用户ID（用于数据隔离，可选）", required = false) String userId,
            @ToolParam(description = "返回结果数量，默认5", required = false) Integer topK) {
        int k = (topK != null && topK > 0) ? Math.min(topK, 20) : 5;
        log.info("[MCP] searchKnowledge | query={}, userId={}, topK={}", query, userId, k);

        List<Document> docs = hybridSearchService.searchWithOwnership(query, userId, k);

        if (docs.isEmpty()) {
            return "知识库中未找到与 \"" + query + "\" 相关的内容。";
        }
        return RagFormatUtil.formatDocs(docs);
    }

    /**
     * 热榜查询：获取各平台（微博/知乎/B站/GitHub/抖音/小红书）的实时热搜数据。
     *
     * @param platform 平台名称（如"微博""B站"），留空则返回所有平台
     * @return 格式化的热榜数据
     */
    @Tool(name = "searchHotTopics", description = """
        查询各平台的实时热搜/热榜数据。
        支持平台：微博、知乎、B站(bilibili)、GitHub、抖音、小红书。
        可指定某个平台，也可不指定获取所有平台 Top 热门。""")
    public String searchHotTopics(
            @ToolParam(description = "平台名称（微博/知乎/B站/GitHub/抖音/小红书），留空获取全部") String platform) {
        String question = (platform != null && !platform.isBlank()) ? platform + "热搜" : "热搜热榜";
        log.info("[MCP] searchHotTopics | platform={}", platform);
        return hotSearchService.queryAndFormat(question);
    }

    /**
     * 网络搜索：使用搜索引擎获取实时互联网信息。
     *
     * @param query 搜索关键词
     * @return 搜索结果（标题 + 摘要 + 链接）
     */
    @Tool(name = "searchWeb", description = """
        使用搜索引擎获取实时互联网信息。
        适用于知识库中没有的时效性问题（新闻、最新版本、今年数据等）。
        注意：需要配置搜索 API Key 才能使用。""")
    public String searchWeb(
            @ToolParam(description = "搜索关键词") String query) {
        log.info("[MCP] searchWeb | query={}", query);

        if (!webSearchService.isEnabled()) {
            return "网络搜索功能未启用，请联系管理员配置搜索 API Key。";
        }

        List<Map<String, String>> results = webSearchService.search(query);
        if (results.isEmpty()) {
            return "未找到与 \"" + query + "\" 相关的网络搜索结果。";
        }
        return webSearchService.formatAsContext(results);
    }
}
