package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.mapper.ChatConversationMapper;
import com.jianbo.localaiknowledge.service.ChatHistoryCacheService;
import com.jianbo.localaiknowledge.service.SystemPromptService;
import com.jianbo.localaiknowledge.model.ChatMessage;
import com.jianbo.localaiknowledge.model.SystemPrompt;
import com.jianbo.localaiknowledge.service.RagAgentService;
import com.jianbo.localaiknowledge.service.RagService;
import com.jianbo.localaiknowledge.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG 问答 & 会话管理 & Prompt 管理
 *
 * 智能路由（多智能体）：
 * POST   /api/rag/agent/chat           智能问答（自动路由 知识库/网络搜索，同步）
 * POST   /api/rag/agent/chat/stream    智能问答（SSE 流式）
 *
 * 基础 RAG：
 * POST   /api/rag/chat                单轮问答（同步）
 * POST   /api/rag/chat/stream         流式问答（SSE）
 * POST   /api/rag/multi-chat          多轮对话（同步）
 * POST   /api/rag/multi-chat/stream   多轮对话（SSE）
 *
 * 会话管理：
 * GET    /api/rag/sessions             获取所有会话
 * GET    /api/rag/history/{sessionId}  获取会话历史
 * DELETE /api/rag/session/{sessionId}  删除会话
 *
 * Prompt 管理：
 * GET    /api/rag/prompts              获取所有 SystemPrompt
 * POST   /api/rag/prompt               创建/更新 SystemPrompt
 * PUT    /api/rag/prompt/default/{name} 设置默认 Prompt
 */
@RestController
@RequestMapping("/api/rag")
@Slf4j
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;
    private final RagAgentService ragAgentService;
    private final ChatConversationMapper conversationMapper;
    private final ChatHistoryCacheService chatHistoryCache;
    private final SystemPromptService promptService;

    // ==================== 智能路由（推荐使用） ====================

    /**
     * 智能问答（同步）
     * 自动路由：知识库检索 → 网络搜索降级
     * 支持用户隔离：传 userId 只看自己的私有文档 + 公共文档
     */
    @PostMapping("/agent/chat")
    public ResponseEntity<?> agentChat(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "question 不能为空"));
        }
        String sessionId = body.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
        }
        // 优先从 JWT Token 获取 userId，未认证时降级为请求体参数
        String userId = SecurityUtil.getCurrentUserIdStr();
        if (userId == null) {
            userId = body.get("userId");
        }
        String promptName = body.get("promptName");

        Map<String, Object> result = ragAgentService.chat(sessionId, question, userId, promptName);
        return ResponseEntity.ok(result);
    }

    /**
     * 智能问答（SSE 流式）
     */
    @PostMapping(value = "/agent/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> agentChatStream(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            return Flux.just("[ERROR] question 不能为空");
        }
        String sessionId = body.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString().replace("-", "");
        }
        String userId = SecurityUtil.getCurrentUserIdStr();
        if (userId == null) {
            userId = body.get("userId");
        }
        String promptName = body.get("promptName");

        return ragAgentService.chatStream(sessionId, question, userId, promptName);
    }

    // ==================== 单轮问答 ====================

    /**
     * 单轮 RAG 问答（同步返回完整答案）
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "question 不能为空"));
        }
        String promptName = body.get("promptName");

        Map<String, Object> result = ragService.chat(question, promptName);
        return ResponseEntity.ok(result);
    }

    /**
     * 单轮 RAG 问答（SSE 流式返回，无会话）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            return Flux.just("[ERROR] question 不能为空");
        }
        String promptName = body.get("promptName");

        return ragService.chatStream(null, question, promptName);
    }

    // ==================== 多轮对话 ====================

    /**
     * 多轮对话（同步）
     * 如果不传 sessionId，自动生成一个新会话
     */
    @PostMapping("/multi-chat")
    public ResponseEntity<?> multiChat(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "question 不能为空"));
        }
        String sessionId = body.getOrDefault("sessionId",
                UUID.randomUUID().toString().replace("-", ""));
        String promptName = body.get("promptName");

        Map<String, Object> result = ragService.multiChat(sessionId, question, promptName);
        return ResponseEntity.ok(result);
    }

    /**
     * 多轮对话（SSE 流式）
     */
    @PostMapping(value = "/multi-chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> multiChatStream(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            return Flux.just("[ERROR] question 不能为空");
        }
        String sessionId = body.getOrDefault("sessionId",
                UUID.randomUUID().toString().replace("-", ""));
        String promptName = body.get("promptName");

        return ragService.chatStream(sessionId, question, promptName);
    }

    // ==================== 会话管理 ====================

    /**
     * 获取所有会话 ID 列表
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<String>> sessions() {
        return ResponseEntity.ok(conversationMapper.selectAllSessionIds());
    }

    /**
     * 获取会话历史消息
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<ChatMessage>> history(@PathVariable String sessionId) {
        return ResponseEntity.ok(conversationMapper.selectBySession(sessionId));
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<?> deleteSession(@PathVariable String sessionId) {
        chatHistoryCache.deleteSession(sessionId);
        return ResponseEntity.ok(Map.of("message", "会话已删除", "sessionId", sessionId));
    }

    // ==================== Prompt 管理 ====================

    /**
     * 获取所有 SystemPrompt
     */
    @GetMapping("/prompts")
    public ResponseEntity<List<SystemPrompt>> prompts() {
        return ResponseEntity.ok(promptService.getAll());
    }

    /**
     * 创建或更新 SystemPrompt
     */
    @PostMapping("/prompt")
    public ResponseEntity<?> savePrompt(@RequestBody SystemPrompt prompt) {
        if (prompt.getName() == null || prompt.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name 不能为空"));
        }
        if (prompt.getContent() == null || prompt.getContent().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "content 不能为空"));
        }

        SystemPrompt existing = promptService.getByName(prompt.getName());
        if (existing != null) {
            promptService.update(prompt);
            return ResponseEntity.ok(Map.of("message", "Prompt 已更新", "name", prompt.getName()));
        } else {
            if (prompt.getIsDefault() == null) {
                prompt.setIsDefault(false);
            }
            promptService.create(prompt);
            return ResponseEntity.ok(Map.of("message", "Prompt 已创建", "name", prompt.getName()));
        }
    }

    /**
     * 设置默认 Prompt
     */
    @PutMapping("/prompt/default/{name}")
    public ResponseEntity<?> setDefault(@PathVariable String name) {
        SystemPrompt prompt = promptService.getByName(name);
        if (prompt == null) {
            return ResponseEntity.notFound().build();
        }
        promptService.setDefault(name);
        return ResponseEntity.ok(Map.of("message", "已设为默认 Prompt", "name", name));
    }
}
