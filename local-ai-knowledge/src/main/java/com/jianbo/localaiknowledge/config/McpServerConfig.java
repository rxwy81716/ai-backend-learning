package com.jianbo.localaiknowledge.config;

import com.jianbo.localaiknowledge.service.mcp.McpKnowledgeToolProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 配置。
 *
 * <h2>MCP 协议简介</h2>
 * <p>MCP（Model Context Protocol）是一套标准化的 AI 工具对接协议，定义了：
 * <ul>
 *   <li><b>工具发现</b>：客户端可以查询服务端有哪些可用工具</li>
 *   <li><b>工具调用</b>：客户端发送 JSON 请求 → 服务端执行 → 返回结果</li>
 *   <li><b>传输层</b>：支持 stdio（本地进程）和 SSE（HTTP 远程）两种传输</li>
 * </ul>
 *
 * <h2>本项目的 MCP 架构</h2>
 * <pre>
 * 外部 AI 客户端（Claude Desktop / Cursor / 自定义客户端）
 *         │
 *         │ MCP 协议（SSE 传输）
 *         ▼
 * ┌─────────────────────────────────┐
 * │  Spring AI MCP Server (本应用)  │
 * │  端点: /mcp/sse                │
 * │                                │
 * │  注册工具:                      │
 * │  ├ searchKnowledge (知识库检索)  │
 * │  ├ searchHotTopics (热榜查询)   │
 * │  └ searchWeb (网络搜索)         │
 * └─────────────────────────────────┘
 *         │
 *         ▼
 *  HybridSearchService / HotSearchService / WebSearchService
 * </pre>
 *
 * <h2>客户端接入示例</h2>
 * <p>在 Claude Desktop 的 {@code claude_desktop_config.json} 中添加：</p>
 * <pre>
 * {
 *   "mcpServers": {
 *     "knowledge-base": {
 *       "url": "http://localhost:8080/mcp/sse"
 *     }
 *   }
 * }
 * </pre>
 *
 * <h2>与原有 Function Calling 的关系</h2>
 * <ul>
 *   <li>原有的 {@code KnowledgeTools} 仍然服务于内部 Agent（KnowledgeAgent / PlannerAgent）</li>
 *   <li>本 MCP Server 是<b>额外</b>暴露给外部客户端的接口，两者并行不冲突</li>
 *   <li>MCP 工具和内部工具共享同一套底层服务（HybridSearchService 等）</li>
 * </ul>
 */
@Configuration
public class McpServerConfig {

    /**
     * 注册 MCP 工具回调。
     *
     * <p>Spring AI MCP Server Starter 会自动通过 SSE 传输层暴露这些工具。
     * 外部 MCP 客户端连接 {@code /mcp/sse} 后可以：
     * <ol>
     *   <li>发送 {@code tools/list} 请求 → 获取所有工具的名称、描述、参数 schema</li>
     *   <li>发送 {@code tools/call} 请求 → 调用指定工具并获取结果</li>
     * </ol>
     */
    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(McpKnowledgeToolProvider toolProvider) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(toolProvider)
                .build();
    }
}
