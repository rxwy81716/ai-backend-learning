package com.jianbo.localaiknowledge.service.agent;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 工具上下文（与 Spring AI 版功能一致，无框架依赖变化）。
 *
 * <p>在 Spring AI 版中通过 ToolContext 隐式传递 userId，
 * LangChain4j 版直接在本类中持有 userId，各 Tool 方法从这里取。</p>
 */
@Slf4j
@Getter
public class RagToolContext {

    private final String userId;
    @Setter private String rewrittenQuery;
    private final List<String> invokedTools = new ArrayList<>();
    private final List<Map<String, String>> retrievedDocs = new ArrayList<>();
    private final Sinks.Many<String> stepSink;

    public RagToolContext(String userId) {
        this.userId = userId;
        this.stepSink = Sinks.many().unicast().onBackpressureBuffer();
    }

    public void addInvokedTool(String toolName) {
        invokedTools.add(toolName);
    }

    public void addRetrievedDoc(String source, String snippet) {
        var doc = new LinkedHashMap<String, String>();
        doc.put("source", source);
        doc.put("snippet", snippet.length() > 200 ? snippet.substring(0, 200) + "..." : snippet);
        retrievedDocs.add(doc);
    }

    /** 发送步骤事件给前端（SSE step 事件） */
    public void emitStep(String step) {
        stepSink.tryEmitNext("[STEP]" + step + "[/STEP]");
    }

    public boolean hasRetrievedDocs() {
        return !retrievedDocs.isEmpty();
    }
}
