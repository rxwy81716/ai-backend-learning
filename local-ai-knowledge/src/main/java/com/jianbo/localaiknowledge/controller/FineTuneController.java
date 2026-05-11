package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.service.finetune.FineTuneDataPrepService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 微调数据准备 API：生成训练数据 + RAG vs Fine-tuning 对比。
 *
 * <h2>端点列表</h2>
 * <ul>
 *   <li>{@code GET /api/finetune/comparison} — RAG vs 微调对比说明</li>
 *   <li>{@code GET /api/finetune/{taskId}/estimate} — 估算训练数据量</li>
 *   <li>{@code POST /api/finetune/{taskId}/generate} — 生成训练数据（JSONL 下载）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/finetune")
@RequiredArgsConstructor
public class FineTuneController {

    private final FineTuneDataPrepService fineTuneService;

    /** RAG vs Fine-tuning 对比说明 */
    @GetMapping("/comparison")
    public ResponseEntity<Map<String, Object>> getComparison() {
        return ResponseEntity.ok(fineTuneService.getComparisonInfo());
    }

    /** 估算指定文档的训练数据量（不实际生成） */
    @GetMapping("/{taskId}/estimate")
    public ResponseEntity<Map<String, Object>> estimate(@PathVariable String taskId) {
        return ResponseEntity.ok(fineTuneService.estimateTrainingData(taskId));
    }

    /**
     * 生成微调训练数据（JSONL 格式下载）。
     *
     * <p>从指定文档的分片中，用 LLM 生成 Q&A 对，
     * 输出为 OpenAI Fine-tuning 标准 JSONL 格式。</p>
     */
    @PostMapping("/{taskId}/generate")
    public ResponseEntity<byte[]> generate(
            @PathVariable String taskId,
            @RequestParam(required = false) String userId) {
        String jsonl = fineTuneService.generateTrainingData(taskId, userId);

        byte[] bytes = jsonl.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"training_data_" + taskId + ".jsonl\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(bytes);
    }
}
