package com.jianbo.localaiknowledge.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

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
    log.debug("开始向量化, 文本长度: {}字符", text.length());
    long startTime = System.currentTimeMillis();

    // 1. 构建 EmbeddingRequest 请求对象
    //    参数1: List<String> 文本列表（单条也要包成List）
    //    参数2: EmbeddingOptions 选项（null = 用yaml默认配置）
    EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);

    // 2. 调用 Embedding 模型 API
    //    底层会发HTTP请求到 SiliconFlow 服务器
    //    服务器返回：文本对应的向量数组
    EmbeddingResponse response = embeddingModel.call(request);

    // 3. 从响应中提取向量
    //    response.getResult() --> 第一个结果（因为只传了一条文本）
    //    .getOutput()         --> 获取向量 float[]
    float[] vectorArray = response.getResult().getOutput();

    long cost = System.currentTimeMillis() - startTime;
    log.info("向量化完成, 耗时: {}ms, 向量维度: {}", cost, vectorArray.length);

    return vectorArray;
  }

  // ==================== 批量文本向量化 ====================

  /**
   * 批量文本向量化（效率更高，生产必用）
   *
   * @param texts 文本列表（如：Day22切好的N个片段）
   * @return List<float[]> 向量列表（每个文本对应一个float[]）
   *     <p>为什么要批量？ - 单条调用：每条文本发一次HTTP请求（慢！） - 批量调用：N条文本打包成一次HTTP请求（快N倍！）
   *     <p>注意事项： - 大部分Embedding API 单次批量上限约 96~256 条 - 如果超过上限，需要分批处理（见下方 embedBatchSafe）
   *     <p>调用示例： List<String> chunks = TextSplitterUtil.splitText(longDocument); List<float[]>
   *     vectors = embeddingService.embedBatch(chunks); // chunks.size() == vectors.size()（一一对应）
   */
  public List<float[]> embedBatch(List<String> texts) {
    log.debug("开始批量向量化, 文本数量: {}", texts.size());
    long startTime = System.currentTimeMillis();

    // 1. 构建批量请求（传入整个文本列表）
    EmbeddingRequest request = new EmbeddingRequest(texts, null);

    // 2. 一次性调用（一个HTTP请求，返回所有向量）
    EmbeddingResponse response = embeddingModel.call(request);

    // 3. 提取所有向量结果
    //    response.getResults() --> List<Embedding>（多个结果）
    //    每个 Embedding 对象的 .getOutput() --> float[]
    List<Embedding> results = response.getResults();
    List<float[]> vectorArray =
        results.stream()
            .map(Embedding::getOutput) // 提取每个结果的 float[]
            .toList();

    long cost = System.currentTimeMillis() - startTime;
    log.info("批量向量化完成, 耗时: {}ms, 向量数量: {}", cost, vectorArray.size());

    return vectorArray;
  }

  // ==================== 大批量安全向量化（防限流） ====================

  /**
   * 大批量分批向量化（防API限流，生产必备）
   *
   * @param texts 所有文本片段（可能有上千条）
   * @param batchSize 每批数量（推荐50~100）
   * @return 所有向量（顺序和输入文本一一对应）
   *     <p>原理： texts = 1000条，batchSize = 50 --> 分成 20 批，每批 50 条 --> 第1批 [0,49] --> 向量化 --> 结果加入总列表
   *     --> 第2批 [50,99] --> 向量化 --> 结果加入总列表 --> ... --> 第20批 [950,999] --> 全部完成
   */
  public List<float[]> embedBatchWithChunking(List<String> texts, int batchSize) {
    List<float[]> allVectors = new java.util.ArrayList<>();

    for (int i = 0; i < texts.size(); i += batchSize) {
      // 截取当前批次
      int end = Math.min(i + batchSize, texts.size());
      List<String> batch = texts.subList(i, end);

      // 向量化当前批次
      List<float[]> batchVectors = embedBatch(batch);
      allVectors.addAll(batchVectors);

      log.info("批量向量化进度: {}/{}", end, texts.size());
    }

    return allVectors;
  }

  // ==================== 余弦相似度计算 ====================

  /**
   * 计算两个文本的余弦相似度
   *
   * @param text1 文本1（如："Java编程"）
   * @param text2 文本2（如："Python编程"）
   * @return 相似度 [0,1]，越大越相似
   *     <p>使用场景： - 测试两段文本是否语义相近 - 验证Embedding模型效果
   */
  public float cosineSimilarity(String text1, String text2) {
    float[] embedding1 = embed(text1);
    float[] embedding2 = embed(text2);
    double result = cosineSimilarity(embedding1, embedding2);
    return (float) result;
  }

  /**
   * 余弦相似度计算（纯数学）
   *
   * <p>公式：cos(theta) = (A·B) / (|A| x |B|)
   *
   * <p>A·B = 点积 = sum(a[i] * b[i]) |A| = 向量长度 = sqrt(sum(a[i]^2))
   */
  private double cosineSimilarity(float[] v1, float[] v2) {
    // 防御：维度必须一致
    if (v1.length != v2.length) {
      throw new IllegalArgumentException("向量维度不一致! v1=" + v1.length + ", v2=" + v2.length);
    }

    double dotProduct = 0.0; // 点积（分子）
    double norm1 = 0.0; // v1的长度平方
    double norm2 = 0.0; // v2的长度平方

    for (int i = 0; i < v1.length; i++) {
      dotProduct += v1[i] * v2[i]; // 对应位相乘后累加
      norm1 += Math.pow(v1[i], 2); // 每个分量的平方累加
      norm2 += Math.pow(v2[i], 2);
    }

    // 防止除以零
    if (norm1 == 0 || norm2 == 0) {
      return 0.0;
    }

    return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
  }
}
