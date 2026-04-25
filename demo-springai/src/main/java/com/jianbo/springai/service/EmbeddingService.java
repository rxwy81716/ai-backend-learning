package com.jianbo.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {
  /** Spring AI M5版本使用 EmbeddingModel 之前的版本叫 EmbeddingClient，注意区别！ */
  private final EmbeddingModel embeddingModel;

  /**
   * 单条文本向量化
   *
   * @param text 输入文本
   * @return 浮点整数量, 维度取决于模型(例如：1536,1024,768 )
   *     <p>使用示例 float[] embedding = embeddingService.embed("输入文本");
   */
  public float[] embed(String text) {
    log.debug("开始向量化,文本长度:{}字符", text.length());
    long startTime = System.currentTimeMillis();
    // 构建请求
    EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);
    // 调用模型
    EmbeddingResponse response = embeddingModel.call(request);

    // 获取向量(List<float[]>)，这里只取第一个
    float[] vectorArray = response.getResult().getOutput();
    long cost = System.currentTimeMillis() - startTime;
    log.info("向量化完成，耗时: {}ms, 向量维度: {}", cost, vectorArray.length);
    return vectorArray;
  }

  /**
   * 批量文本向量化（效率更高）
   *
   * @param texts 文本列表
   * @return 向量列表
   */
  public List<float[]> embedBatch(List<String> texts) {
    log.debug("开始向量化,文本长度:{}字符", texts.size());

    long startTime = System.currentTimeMillis();

    //批量请求
    EmbeddingRequest request = new EmbeddingRequest(texts, null);
    EmbeddingResponse response = embeddingModel.call(request);
    List<Embedding> results = response.getResults();
    List<float[]> vectorArray = results.stream().map(Embedding::getOutput).toList();
    long cost = System.currentTimeMillis() - startTime;
    log.info("向量化完成，耗时: {}ms, 向量数量: {}", cost, vectorArray.size());
    return vectorArray;
  }

  /**
   * 大批量分批向量化（防API限流）
   *
   * @param texts 所有文本（如：1000条）
   * @param batchSize 每批数量（如：50条）
   * @return List<float[]> 所有向量
   *
   * 示例：
   *   texts = 1000条
   *   batchSize = 50
   *   → 分20批处理，避免单次请求过大
   */
  public List<float[]> embedBatchWithChunking(List<String> texts, int batchSize) {
    List<float[]> allVectors = new ArrayList<>();

    // 1. 分批处理
    for (int i = 0; i < texts.size(); i += batchSize) {
      // 2. 截取当前批次
      int end = Math.min(i + batchSize, texts.size());
      List<String> batch = texts.subList(i, end);

      // 3. 向量化当前批次
      List<float[]> batchVectors = embedBatch(batch);
      allVectors.addAll(batchVectors);

      System.out.println("已处理: " + end + "/" + texts.size());
    }

    return allVectors;
  }

  /**
   * 计算两个文本的余弦相似度
   *
   * @param text1 文本1
   * @param text2 文本2
   * @return 相似度 [0,1]，越大越相似
   */
  public float cosineSimilarity(String text1, String text2) {
    float[] embedding1 = embed(text1);
    float[] embedding2 = embed(text2);
    double result = cosineSimilarity(embedding1, embedding2);
    return (float) result;
  }

  /**
   * 余弦相似度计算
   * 公式：cos(θ) = (A·B) / (|A|×|B|)
   */
  private double cosineSimilarity(float[] v1, float[] v2) {
    if (v1.length != v2.length) {
      throw new IllegalArgumentException("向量维度不一致");
    }

    double dotProduct = 0.0;
    double norm1 = 0.0;
    double norm2 = 0.0;

    for (int i = 0; i < v1.length; i++) {
      dotProduct += v1[i] * v2[i];
      norm1 += Math.pow(v1[i], 2);
      norm2 += Math.pow(v2[i], 2);
    }

    if (norm1 == 0 || norm2 == 0) {
      return 0.0;
    }

    return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
  }
}
