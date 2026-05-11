package com.jianbo.localaiknowledge.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 服务（对标 Spring AI 版）。
 *
 * <h2>API 对比</h2>
 * <pre>
 * Spring AI:
 *   EmbeddingModel.embed(text)            → float[]
 *   EmbeddingModel.embed(List&lt;String&gt;)   → List&lt;float[]&gt;
 *
 * LangChain4j:
 *   EmbeddingModel.embed(text)            → Response&lt;Embedding&gt;
 *   EmbeddingModel.embedAll(List&lt;TextSegment&gt;) → Response&lt;List&lt;Embedding&gt;&gt;
 * </pre>
 *
 * <p>LangChain4j 用 {@code Embedding} 包装向量，需要 {@code .vector()} 取 float[]。
 * 入参也需要包装成 {@code TextSegment}。本服务屏蔽这些差异。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /** 单文本 embedding */
    public float[] embed(String text) {
        Response<Embedding> resp = embeddingModel.embed(text);
        return resp.content().vector();
    }

    /** 批量 embedding（分块防限流） */
    public List<float[]> embedBatchWithChunking(List<String> texts, int chunkSize) {
        List<float[]> results = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, texts.size());
            List<TextSegment> batch = texts.subList(i, end).stream()
                    .map(TextSegment::from)
                    .toList();

            Response<List<Embedding>> resp = embeddingModel.embedAll(batch);
            for (Embedding emb : resp.content()) {
                results.add(emb.vector());
            }

            if (end < texts.size()) {
                try { Thread.sleep(200); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return results;
    }

    /** 余弦相似度 */
    public double cosineSimilarity(String text1, String text2) {
        float[] v1 = embed(text1);
        float[] v2 = embed(text2);
        return cosineSimilarity(v1, v2);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
