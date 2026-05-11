package com.jianbo.localaiknowledge.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * META 信息构建器（与 Spring AI 版完全一致，无框架依赖）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MetaBuilder {

    private final ObjectMapper objectMapper;

    public String build(AgentType agentType, RagToolContext ctx, String chatMode) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", agentType.getSourceTag());
        meta.put("agent", agentType.name());
        meta.put("chatMode", chatMode);

        if (ctx != null) {
            if (!ctx.getInvokedTools().isEmpty()) {
                meta.put("tools", ctx.getInvokedTools());
            }
            if (ctx.getRewrittenQuery() != null) {
                meta.put("rewrittenQuery", ctx.getRewrittenQuery());
            }
            if (!ctx.getRetrievedDocs().isEmpty()) {
                meta.put("references", ctx.getRetrievedDocs());
            }
        }

        try {
            return "[META]" + objectMapper.writeValueAsString(meta) + "[/META]";
        } catch (Exception e) {
            log.warn("META 序列化失败: {}", e.getMessage());
            return "[META]{\"source\":\"" + agentType.getSourceTag() + "\"}[/META]";
        }
    }
}
