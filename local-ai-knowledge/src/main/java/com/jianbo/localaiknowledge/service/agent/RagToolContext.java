package com.jianbo.localaiknowledge.service.agent;

import lombok.Getter;
import org.springframework.ai.document.Document;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RAG Tool 调用上下文（ThreadLocal）
 *
 * <p>背景：Tool Calling 改造后，LLM 自主决定调哪个工具。但是：
 * <ul>
 *   <li>{@code searchKnowledgeBase} 工具需要 {@code userId} 做用户隔离过滤，
 *       不能让 LLM 通过参数提供（安全性 + LLM 不应感知）</li>
 *   <li>外层需要知道"实际调用了哪些工具 + 检索到哪些文档"，用于构建响应里的
 *       {@code source} / {@code references} 字段</li>
 * </ul>
 *
 * <p>解决方案：每次请求开始 {@link #begin(String)}，结束 {@link #clear()}，
 * 工具方法内部通过 {@link #current()} 读 userId、记录调用与命中文档。
 *
 * <p><b>限制</b>：流式响应下若 Spring AI 在异步线程执行工具，ThreadLocal 可能不传播。
 * 当前实现假设单线程内同步执行工具调用（Spring AI 1.0.0-M5 默认行为），
 * 后续如出现线程切换问题，可改为 {@code Reactor Context} 或 {@code InheritableThreadLocal}。
 */
@Getter
public class RagToolContext {

    private static final ThreadLocal<RagToolContext> HOLDER = new ThreadLocal<>();

    /** 当前请求的用户 ID（null = 未登录，仅看公共文档） */
    private final String userId;

    /** 实际被 LLM 调用过的工具名称（按调用顺序、去重）—— 线程安全包装防止并行 Tool Calling 竞态 */
    private final Set<String> invokedTools = Collections.synchronizedSet(new LinkedHashSet<>());

    /** 知识库工具命中的文档（用于构建 references）—— 写少读多，CopyOnWriteArrayList 合适 */
    private final List<Document> retrievedDocs = new CopyOnWriteArrayList<>();

    private RagToolContext(String userId) {
        this.userId = userId;
    }

    public static RagToolContext begin(String userId) {
        RagToolContext ctx = new RagToolContext(userId);
        HOLDER.set(ctx);
        return ctx;
    }

    /** 仅创建上下文实例，不绑定 ThreadLocal（流式场景下显式通过闭包传递更安全） */
    public static RagToolContext create(String userId) {
        return new RagToolContext(userId);
    }

    /** 获取当前线程上下文，未初始化时返回 null（工具方法需做空值降级） */
    public static RagToolContext current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public void recordInvocation(String toolName) {
        invokedTools.add(toolName);
    }

    public void addDocs(List<Document> docs) {
        if (docs != null && !docs.isEmpty()) {
            retrievedDocs.addAll(docs);
        }
    }
}
