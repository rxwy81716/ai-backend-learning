package com.jianbo.springai.controller;

import com.jianbo.springai.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * RAG 问答 Controller
 *
 * 提供两个接口：
 *   GET /rag/chat          — 同步问答（返回完整 JSON）
 *   GET /rag/chat-stream   — 流式问答（SSE 逐字推送）
 */
@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    /**
     * 同步 RAG 问答
     *
     * 调用示例：
     *   GET /rag/chat?query=Java和Python有什么区别&topK=5&threshold=0.7
     *
     * @param query     用户问题（必填）
     * @param topK      召回数量（可选，默认5）
     * @param threshold 相似度阈值（可选，默认0.7）
     * @return 大模型生成的答案文本
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String query,
                       @RequestParam(defaultValue = "5") int topK,
                       @RequestParam(defaultValue = "0.7") double threshold) {
        return ragService.chat(query, topK, threshold);
    }

    /**
     * 流式 RAG 问答（SSE）
     *
     * 调用示例：
     *   GET /rag/chat-stream?query=Java和Python有什么区别
     *
     * 前端接收方式（JavaScript）：
     *   const evtSource = new EventSource('/rag/chat-stream?query=...');
     *   evtSource.onmessage = (e) => { document.body.innerText += e.data; };
     *
     * produces = TEXT_EVENT_STREAM_VALUE 告诉 Spring MVC：
     *   这个接口返回 SSE 流，不是普通 JSON
     *   响应头自动设为 Content-Type: text/event-stream
     *
     * @param query 用户问题
     * @return SSE 文本流
     */
    @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam String query,
                                   @RequestParam(defaultValue = "5") int topK,
                                   @RequestParam(defaultValue = "0.7") double threshold) {
        return ragService.chatStream(query, topK, threshold);
    }
}