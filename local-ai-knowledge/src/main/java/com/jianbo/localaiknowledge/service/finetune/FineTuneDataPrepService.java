package com.jianbo.localaiknowledge.service.finetune;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.localaiknowledge.mapper.DocumentChunkMapper;
import com.jianbo.localaiknowledge.mapper.DocumentTaskMapper;
import com.jianbo.localaiknowledge.model.DocumentChunk;
import com.jianbo.localaiknowledge.model.DocumentTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微调数据准备服务：从知识库已有数据生成微调训练集。
 *
 * <h2>什么是微调（Fine-tuning）？</h2>
 * <p>微调是在预训练大模型的基础上，用特定领域数据继续训练，让模型"记住"这些知识。</p>
 *
 * <h2>微调 vs RAG 对比</h2>
 * <table>
 *   <tr><th>维度</th><th>RAG（本项目主要方案）</th><th>微调</th></tr>
 *   <tr><td>知识更新</td><td>✅ 实时（上传即可用）</td><td>❌ 需要重新训练</td></tr>
 *   <tr><td>可追溯性</td><td>✅ 可标注来源文档</td><td>❌ 知识融入权重，不可追溯</td></tr>
 *   <tr><td>成本</td><td>✅ 只需推理成本</td><td>❌ 需要 GPU 训练</td></tr>
 *   <tr><td>幻觉控制</td><td>✅ 有检索结果约束</td><td>⚠️ 仍可能产生幻觉</td></tr>
 *   <tr><td>风格定制</td><td>⚠️ 靠 Prompt 调整</td><td>✅ 可深度定制语气/格式</td></tr>
 *   <tr><td>适用场景</td><td>知识密集型问答</td><td>风格/格式要求极高的场景</td></tr>
 * </table>
 *
 * <h2>本服务的用途</h2>
 * <p>从知识库中已有的文档片段 + LLM 辅助生成 Q&A 对，
 * 输出为 OpenAI 微调格式的 JSONL 文件，可直接用于微调训练。
 * 这样用户可以对比：同一批知识，用 RAG 检索回答 vs 用微调后的模型回答，效果有何差异。</p>
 *
 * <h2>输出格式（OpenAI Fine-tuning JSONL）</h2>
 * <pre>
 * {"messages":[
 *   {"role":"system","content":"你是一个企业知识助手..."},
 *   {"role":"user","content":"生成的问题"},
 *   {"role":"assistant","content":"基于文档的回答"}
 * ]}
 * </pre>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FineTuneDataPrepService {

    private final DocumentTaskMapper taskMapper;
    private final DocumentChunkMapper chunkMapper;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = "你是一个企业知识库问答助手，请基于提供的资料准确回答问题。";

    private static final String QA_GEN_PROMPT = """
            基于以下文档片段，生成 3 个高质量的问答对。要求：
            1. 问题应该是用户可能真实会问的
            2. 答案必须完全基于文档内容，不要编造
            3. 用 JSON 数组格式输出：[{"q":"问题","a":"答案"}, ...]
            
            文档片段：
            %s
            """;

    /**
     * 从指定文档生成微调训练数据。
     *
     * <p>流程：
     * <ol>
     *   <li>从 PG 加载该文档的所有切片</li>
     *   <li>对每个切片调用 LLM 生成 Q&A 对</li>
     *   <li>格式化为 OpenAI JSONL 微调格式</li>
     * </ol>
     *
     * @param taskId 文档任务 ID
     * @param userId 用户 ID（权限校验）
     * @return JSONL 格式的训练数据（每行一个 JSON 对象）
     */
    public String generateTrainingData(String taskId, String userId) {
        DocumentTask task = taskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        // 权限校验：非管理员只能操作自己的文档
        if (userId != null && !userId.isBlank()) {
            String taskUserId = task.getUserId();
            if (taskUserId != null && !taskUserId.equals(userId)) {
                throw new IllegalArgumentException("无权操作他人的文档");
            }
        }

        List<DocumentChunk> chunks = chunkMapper.selectByTaskId(taskId);
        if (chunks.isEmpty()) {
            throw new IllegalStateException("文档无分片数据: " + taskId);
        }

        log.info("[FineTune] 开始生成训练数据 | taskId={}, chunks={}, userId={}", taskId, chunks.size(), userId);

        StringBuilder jsonl = new StringBuilder();
        int totalQA = 0;

        for (DocumentChunk chunk : chunks) {
            String content = chunk.getContent();
            if (content == null || content.length() < 50) continue; // 片段过短跳过

            try {
                List<Map<String, String>> qaPairs = generateQAPairs(content);
                for (Map<String, String> qa : qaPairs) {
                    String line = formatAsJsonl(qa.get("q"), qa.get("a"));
                    if (line != null) {
                        jsonl.append(line).append("\n");
                        totalQA++;
                    }
                }
                // 避免 API 限流
                Thread.sleep(500);
            } catch (Exception e) {
                log.warn("[FineTune] 片段生成失败，跳过 | err={}", e.getMessage());
            }
        }

        log.info("[FineTune] 训练数据生成完成 | taskId={}, QA对数={}", taskId, totalQA);
        return jsonl.toString();
    }

    /**
     * 生成训练数据统计信息（不实际生成数据，只估算）。
     */
    public Map<String, Object> estimateTrainingData(String taskId) {
        DocumentTask task = taskMapper.selectByTaskId(taskId);
        if (task == null) {
            return Map.of("error", "任务不存在");
        }

        List<DocumentChunk> chunks = chunkMapper.selectByTaskId(taskId);
        int validChunks = (int) chunks.stream()
                .filter(c -> c.getContent() != null && c.getContent().length() >= 50)
                .count();

        Map<String, Object> estimate = new LinkedHashMap<>();
        estimate.put("taskId", taskId);
        estimate.put("fileName", task.getFileName());
        estimate.put("totalChunks", chunks.size());
        estimate.put("validChunks", validChunks);
        estimate.put("estimatedQAPairs", validChunks * 3); // 每片段约3个QA对
        estimate.put("estimatedCost", "约 " + (validChunks * 200) + " token（LLM 调用）");
        return estimate;
    }

    /**
     * 返回 RAG vs 微调的对比说明。
     */
    public Map<String, Object> getComparisonInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "RAG vs Fine-tuning 对比");
        info.put("currentApproach", "RAG（检索增强生成）");

        List<Map<String, String>> comparison = new ArrayList<>();
        comparison.add(Map.of("dimension", "知识更新", "rag", "✅ 实时（上传即可用）", "finetune", "❌ 需要重新训练（小时级）"));
        comparison.add(Map.of("dimension", "可追溯性", "rag", "✅ 可标注来源文档", "finetune", "❌ 知识融入权重，不可追溯"));
        comparison.add(Map.of("dimension", "成本", "rag", "✅ 只需推理成本", "finetune", "❌ 需要 GPU 训练成本"));
        comparison.add(Map.of("dimension", "幻觉控制", "rag", "✅ 有检索结果约束", "finetune", "⚠️ 仍可能产生幻觉"));
        comparison.add(Map.of("dimension", "风格定制", "rag", "⚠️ 靠 Prompt 调整", "finetune", "✅ 可深度定制语气/格式"));
        comparison.add(Map.of("dimension", "私有数据安全", "rag", "✅ 数据留在本地检索", "finetune", "⚠️ 数据需上传到训练平台"));
        comparison.add(Map.of("dimension", "适用场景", "rag", "知识密集型问答、需要引用来源", "finetune", "特定语气/格式、高频固定问答"));
        info.put("comparison", comparison);

        info.put("recommendation", "大多数企业知识库场景推荐 RAG（本项目方案）。仅当需要极致的回答风格一致性、"
                + "且知识不常更新时，才考虑微调。最佳实践是 RAG + 微调混合使用。");

        info.put("trainingDataFormat", Map.of(
                "format", "OpenAI JSONL",
                "example", "{\"messages\":[{\"role\":\"system\",\"content\":\"...\"},{\"role\":\"user\",\"content\":\"问题\"},{\"role\":\"assistant\",\"content\":\"回答\"}]}"
        ));
        return info;
    }

    // ==================== 内部方法 ====================

    private List<Map<String, String>> generateQAPairs(String content) {
        String prompt = String.format(QA_GEN_PROMPT, content.length() > 1000 ? content.substring(0, 1000) : content);

        String response;
        try {
            response = chatModel.call(new Prompt(List.of(
                    new SystemMessage("你是一个训练数据生成助手。请严格按 JSON 数组格式输出。"),
                    new UserMessage(prompt)
            ))).getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("[FineTune] LLM 调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }

        // 提取 JSON 数组
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            log.warn("[FineTune] LLM 返回格式错误，未找到 JSON 数组 | response={}", response.substring(0, Math.min(200, response.length())));
            return List.of();
        }
        String jsonArray = response.substring(start, end + 1);

        List<Map<String, String>> result = new ArrayList<>();
        List<Map<String, Object>> parsed;
        try {
            parsed = objectMapper.readValue(
                    jsonArray, objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            log.warn("[FineTune] QA JSON 解析失败: {} | jsonArray={}", e.getMessage(), jsonArray.substring(0, Math.min(200, jsonArray.length())));
            return List.of();
        }
        for (Map<String, Object> item : parsed) {
            String q = String.valueOf(item.getOrDefault("q", ""));
            String a = String.valueOf(item.getOrDefault("a", ""));
            if (!q.isBlank() && !a.isBlank()) {
                result.add(Map.of("q", q, "a", a));
            }
        }
        return result;
    }

    private String formatAsJsonl(String question, String answer) {
        try {
            Map<String, Object> line = Map.of("messages", List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", question),
                    Map.of("role", "assistant", "content", answer)
            ));
            return objectMapper.writeValueAsString(line);
        } catch (Exception e) {
            return null;
        }
    }
}
