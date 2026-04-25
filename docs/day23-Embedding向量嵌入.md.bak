# Day23 Embedding向量嵌入

> 前置知识：Day22 文档文本切片（把长文档切成 N 个小片段）
>
> 本章目标：把切好的文本片段，转成 AI 能理解的「数字向量」，存入向量数据库
>
> 下节预告：向量检索（从向量库中找出最相关内容）

---

## 一 什么是 Embedding（通俗理解）

### 1. 先看一个生活例子

```
人类理解词语：靠「意思」和「上下文」

  苹果 ← 水果？手机？公司？

  「苹果」+「吃」→ 水果（能吃的东西）
  「苹果」+「打电话」→ 手机品牌
  「苹果」+「公司」→ Apple公司

词语的含义，取决于它周围的「上下文」
```

### 2. Embedding 的本质

```
文字（人类语言）  →  数字向量（机器语言）

    "苹果"      →   [0.23, -0.45, 0.87, 0.12, ...]
    "香蕉"      →   [0.25, -0.38, 0.79, 0.15, ...]
    "手机"      →   [-0.12, 0.56, -0.23, 0.89, ...]
```

- **Embedding = 把文字转成一串固定长度的数字**
- **这串数字 = 词语的「语义坐标」**
- **语义越接近的词，向量越接近**

### 3. Embedding 模型的作用

```
一句话总结：
Embedding模型 = 翻译官（把人类的语言翻译成机器的数学语言）

它能理解：
- 「苹果」和「香蕉」意思接近 → 向量距离近
- 「电脑」和「手机」是同类 → 向量距离近
- 「天气」和「编程」无关 → 向量距离远
```

---

## 二 向量的底层原理（面试重点）

### 1. 什么是向量维度

```
二维向量（x, y）：
  [1, 2]  → 平面上的一个点

三维向量（x, y, z）：
  [1, 2, 3]  → 3D空间中的一个点

==========================

Embedding 向量：通常 512维 / 768维 / 1024维 / 1536维

例如 text-embedding-3-small 模型：
  "苹果"  →  [0.12, -0.34, 0.56, -0.78, ...共1536个数...]

每个数字 = 语义特征的一个维度
1536个数字 = 从1536个不同角度描述这个词的含义
```

### 2. 向量的几何含义

```
假设我们只有2个维度（方便画图理解）：

                    ↑ 水果类别
                    |
              苹果  |
               ●    |
                    |
              香蕉  ●
                    |
    ← ─ ─ ─ ─ ─ ─ ─ ─ → 甜度维度
               ●    |
          西瓜       |

向量方向：
  - 苹果和香蕉方向接近（都是水果，都甜）
  - 手机和电脑方向接近（都是电子产品）
  - 水果和电子产品的向量方向完全不同
```

### 3. 语义空间的概念

```
把向量想象成一片「语义宇宙」：

  - 所有动物词汇聚在一起（猫、狗、鸟、鱼...）
  - 所有编程词汇聚在一起（Java、Python、数据库...）
  - 所有情感词汇聚在一起（开心、悲伤、愤怒...）

空间中的「距离」= 语义的「相似度」
距离越近 → 语义越接近
```

---

## 三 文本转向量的完整流程

### 1. 单条文本向量化流程

```
【输入】"Java是面向对象的编程语言"

         ↓

【第一步：分词】
"Java / 是 / 面向 / 对象 / 的 / 编程 / 语言"

         ↓

【第二步：查词表，获取每个词的向量】
Java    → [0.23, -0.45, ...]
是      → [0.11, 0.22, ...]
面向    → [-0.34, 0.56, ...]
对象    → [0.45, -0.23, ...]
...

         ↓

【第三步：聚合（平均池化/CLS标记）】
所有词向量 → 平均池化 → 一整个句子的向量

         ↓

【输出】[0.15, -0.12, 0.34, ...共1536维...]

整个过程由Embedding模型（如 text-embedding-3-small）一键完成
```

### 2. 批量文档向量化流程

```
多篇文档：
  文档1 → [向量1]
  文档2 → [向量2]
  文档3 → [向量3]
       ...
  文档N → [向量N]
         ↓
存入向量数据库（同时保存原文 + 向量）
```

### 3. Embedding 在 RAG 中的位置

```
【离线预处理阶段】
  文档切片 → 每个片段 → Embedding模型 → 向量 + 原文 → 向量数据库

【实时问答阶段】
  用户问题 → Embedding模型 → 问题向量 → 向量库检索 → 召回TopN → 拼接Prompt → LLM生成答案
```

---

## 四 余弦相似度原理（检索核心）

### 1. 什么是余弦相似度

```
衡量两个向量「方向」有多相似，取值范围 [-1, 1]

  余弦相似度 = 1   → 方向完全相同（完全相似）
  余弦相似度 = 0   → 方向垂直（完全不相关）
  余弦相似度 = -1  → 方向相反（语义相反）

计算公式：
                 A · B
  cos(θ) = ─────────────
           |A| × |B|

其中：
  A · B = 两个向量的点积（对应元素相乘后求和）
  |A| = 向量A的长度
  |B| = 向量B的长度
```

### 2. 余弦相似度举例

```
向量A（苹果的向量）：[0.8, 0.2, 0.1]
向量B（香蕉的向量）：[0.7, 0.3, 0.1]
向量C（手机的向量）：[-0.3, 0.8, 0.5]

计算相似度：
  cos(苹果, 香蕉) = 0.95  → 非常接近（都是水果）
  cos(苹果, 手机) = 0.15  → 距离很远（不同类别）
```

### 3. 为什么用余弦而不是欧氏距离

```
欧氏距离 = 绝对距离，衡量「长短」
余弦相似度 = 方向相似，衡量「语义是否同向」

举例：
  向量A = [100, 0]
  向量B = [1, 0]

  欧氏距离很远，但方向完全相同，语义高度相似
  余弦相似度 = 1.0（完全一致）

结论：语义任务用余弦更准确
```

---

## 五 SpringAI 整合 Embedding（完整代码）

### 1. 环境准备（Maven依赖）

```xml
<!-- SpringAI 的 Embedding 支持 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-open-spring-boot-starter</artifactId>
    <version>1.0.0-M6</version>
</dependency>

<!-- MiniMax/OpenAI 等Embedding模型厂商适配器 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-minimax-spring-boot-starter</artifactId>
    <version>1.0.0-M6</version>
</dependency>
```

### 2. application.yaml 配置

```yaml
spring:
  ai:
    miniimax:
      api-key: ${MINIMAX_API_KEY}      # MiniMax API Key
      embedding:
        model: embo-01                  # Embedding模型名称
```

### 3. Embedding 配置类

```java
package com.jianbo.springai.config;

import org.springframework.ai_embedding.BedrockTitanEmbeddingModel;
import org.springframework.ai_embedding.EmbeddingModel;
import org.springframework.ai.minimax.MiniMaxEmbeddingModel;
import org.springframework.ai.minimax.MiniMaxEmbeddingOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Embedding 向量嵌入配置
 *
 * 作用：配置 Embedding 模型 Bean，供 Service 层注入使用
 */
@Configuration
public class EmbeddingConfig {

    /**
     * MiniMax Embedding 模型配置
     *
     * @return MiniMaxEmbeddingModel 实例
     */
    @Bean
    public MiniMaxEmbeddingModel miniMaxEmbeddingModel() {
        // 1. 构建Embedding选项
        MiniMaxEmbeddingOptions options = MiniMaxEmbeddingOptions.builder()
                .model("embo-01")  // 指定Embedding模型
                .build();

        // 2. 返回配置好的MiniMax Embedding模型
        return new MiniMaxEmbeddingModel(options);
    }
}
```

---

## 六 单条文本向量化（工具类）

### 1. 单条文本转向量核心代码

```java
package com.jianbo.springai.service;

import jakarta.annotation.Resource;
import org.springframework.ai.minimax.MiniMaxEmbeddingModel;
import org.springframework.ai.minimax.MiniMaxEmbeddingOptions;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Embedding 向量服务
 *
 * 核心功能：把文本转成向量（一串数字）
 */
@Service
public class EmbeddingService {

    @Resource
    private MiniMaxEmbeddingModel embeddingModel;

    /**
     * 单条文本向量化
     *
     * @param text 输入文本（如：一个文档片段）
     * @return float[] 向量数组（如：1536维向量）
     *
     * 使用场景：
     *   - 用户提问时，把问题转成向量
     *   - 单个文档片段向量化
     */
    public float[] embedText(String text) {
        try {
            // 1. 构建Embedding选项
            MiniMaxEmbeddingOptions options = MiniMaxEmbeddingOptions.builder()
                    .model("embo-01")
                    .build();

            // 2. 调用Embedding模型，获取EmbeddingResponse
            var response = embeddingModel.call(
                // 3. 把文本包装成 Prompt 格式
                new org.springframework.ai.model.ModelOptions() {
                    @Override
                    public String getModel() {
                        return "embo-01";
                    }
                },
                // 4. 输入文本（可以是单条或批量）
                List.of(text)
            );

            // 5. 获取第一个结果（因为是单条文本）
            return response.getResult().getOutput().getItems().get(0).getEmbedding();

        } catch (Exception e) {
            // 异常处理：记录日志，返回null
            System.err.println("向量化失败: " + e.getMessage());
            return null;
        }
    }
}
```

### 2. 简化版单条向量化

```java
/**
 * 简化版：单条文本向量化
 *
 * @param text 输入文本
 * @return float[] 向量数组
 */
public float[] embed(String text) {
    // 直接调用MiniMax Embedding API
    return embeddingModel.embed(text);
}
```

---

## 七 批量文本向量化（生产必备）

### 1. 批量向量化工具类

```java
package com.jianbo.springai.utils;

import jakarta.annotation.Resource;
import org.springframework.ai.minimax.MiniMaxEmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding 批量向量化工具
 *
 * 功能：一次性把 N 个文本片段转成 N 个向量
 *
 * 使用场景：
 *   - 文档切片后，批量入库
 *   - 离线预处理阶段
 */
@Component
public class EmbeddingUtil {

    @Resource
    private MiniMaxEmbeddingModel embeddingModel;

    /**
     * 批量文本向量化
     *
     * @param texts 文本列表（如：["片段1", "片段2", "片段3"...])
     * @return List<float[]> 向量列表（每个文本对应一个向量）
     *
     * 注意事项：
     *   - MiniMax单次批量上限约96条（看API限制）
     *   - 如果文本太多，需要分批处理
     */
    public List<float[]> embedBatch(List<String> texts) {
        // 防御：空列表检查
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // 1. 调用批量Embedding接口
            var response = embeddingModel.embed(texts);

            // 2. 提取所有向量结果
            List<float[]> results = new ArrayList<>();
            response.getResult().getOutput().getItems()
                    .forEach(item -> results.add(item.getEmbedding()));

            return results;

        } catch (Exception e) {
            System.err.println("批量向量化失败: " + e.getMessage());
            return new ArrayList<>();
        }
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
}
```

### 2. 结合文本切片一起使用

```java
package com.jianbo.springai.service;

import com.jianbo.springai.utils.EmbeddingUtil;
import com.jianbo.springai.utils.TextSplitterUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档向量入库服务
 *
 * 完整流程：
 *   文档 → 切片 → 向量化 → 存入数据库
 */
@Service
public class DocumentVectorService {

    @Resource
    private EmbeddingUtil embeddingUtil;

    @Resource
    private TextSplitterUtil textSplitterUtil;

    /**
     * 文档全文入库
     *
     * @param document 原始文档（如：一篇完整的文章）
     * @param maxChunkSize 每段最大字数（如：500）
     * @param overlapSize 重叠字数（如：50）
     * @return 入库的片段数量
     */
    public int importDocument(String document, int maxChunkSize, int overlapSize) {
        // 步骤1：文本清洗（去空格、去乱码）
        String cleanedText = cleanText(document);

        // 步骤2：固定长度切片
        List<String> chunks = textSplitterUtil.splitText(
            cleanedText,
            maxChunkSize,
            overlapSize
        );

        // 步骤3：批量向量化
        List<float[]> vectors = embeddingUtil.embedBatch(chunks);

        // 步骤4：批量存入数据库（见下一章节）
        int count = saveToVectorDb(chunks, vectors);

        return count;
    }

    /**
     * 模拟保存到向量数据库
     * 真实实现见 PostgreSQL + pgvector 章节
     */
    private int saveToVectorDb(List<String> chunks, List<float[]> vectors) {
        // 伪代码：实际保存到 pgvector
        for (int i = 0; i < chunks.size(); i++) {
            // saveChunk(chunks.get(i), vectors.get(i));
        }
        return chunks.size();
    }

    /**
     * 文本清洗
     */
    private String cleanText(String text) {
        if (text == null) return "";
        return text
            .replaceAll("\\s+", " ")    // 多个空格合并成一个
            .replaceAll("[\\r\\n]+", "\n") // 统一换行符
            .trim();
    }
}
```

---

## 八 PostgreSQL + pgvector 向量入库（完整代码）

### 1. 为什么用 pgvector

```
向量数据库选项：
  - Milvus：专业向量库，功能强大，部署复杂
  - Elasticsearch：自带向量能力，已有ES可用
  - Redis Vector：简单，内存存储
  - pgvector：PostgreSQL插件，SQL数据库 + 向量能力二合一

企业选择：已有PostgreSQL → 直接加pgvector插件，不用额外维护
```

### 2. 数据库表结构设计

```sql
-- 1. 安装 pgvector 扩展（PostgreSQL 15+）
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 创建文档切片表
CREATE TABLE document_chunks (
    id            BIGSERIAL PRIMARY KEY,           -- 主键自增
    document_id   VARCHAR(64) NOT NULL,           -- 文档ID（关联原始文档）
    chunk_index   INTEGER NOT NULL,               -- 切片序号（第几段）
    content       TEXT NOT NULL,                  -- 原始文本内容
    embedding     VECTOR(1536) NOT NULL,          -- 向量（1536维）
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 索引：加速向量相似度检索（HNSW算法，生产推荐）
    CONSTRAINT embedding_hnsw_idx
        EXCLUDE USING hnsw (embedding WITH vector_cosine_ops)
        WITH (m = 16, ef_construction = 200)
);

-- 3. 创建普通索引（按文档ID查询）
CREATE INDEX idx_document_id ON document_chunks(document_id);
```

### 3. JPA 实体类

```java
package com.jianbo.springai.entity;

import io.hypersistence.vaidataannotations.Column;
import io.hypersistence.vaidataannotations.Entity;
import io.hypersistence.vaidataannotations.GeneratedValue;
import io.hypersistence.vaidataannotations.Id;
import io.hypersistence.vaidataannotations.Table;

import java.time.LocalDateTime;

/**
 * 文档切片实体（对应 document_chunks 表）
 *
 * 存储：原文内容 + 对应的向量
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 文档ID（关联原始文档来源）
     * 例如：一篇PDF文档的ID是 "doc_20240101_001"
     */
    @Column(name = "document_id")
    private String documentId;

    /**
     * 切片序号（第几段）
     * 例如：第0段、第1段、第2段...
     */
    @Column(name = "chunk_index")
    private Integer chunkIndex;

    /**
     * 原始文本内容（用户可见的检索结果）
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * 向量数据（存储为字符串，数据库用 pgvector 类型）
     * 格式："[0.12, -0.34, 0.56, ...]"（1536维数组的JSON字符串）
     */
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private String embedding;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ========== Getter/Setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

### 4. Repository 接口

```java
package com.jianbo.springai.repository;

import com.jianbo.springai.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档切片 Repository
 *
 * 关键方法：向量相似度检索
 */
@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /**
     * 根据文档ID查询所有切片
     *
     * @param documentId 文档ID
     * @return 该文档的所有切片（按顺序排列）
     */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(String documentId);

    /**
     * 删除某文档的所有切片
     *
     * @param documentId 文档ID
     */
    void deleteByDocumentId(String documentId);

    // ========== 向量检索相关（Native Query）==========

    /**
     * 余弦相似度检索（最相似的内容）
     *
     * 原理：
     *   1. 用 pgvector 的 <=> 操作符计算余弦距离
     *   2. ORDER BY 距离 ASC（距离越小越相似）
     *   3. LIMIT 5 取最相似的5条
     *
     * @param embeddingStr 目标向量（字符串格式：'[0.12,-0.34,...]'）
     * @param limit 返回数量
     * @return 最相似的切片列表
     */
    @Query(value = """
        SELECT id, document_id, chunk_index, content, embedding, created_at,
               embedding <=> CAST(:embedding AS vector) AS distance
        FROM document_chunks
        ORDER BY embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findTopSimilarChunks(
        @Param("embedding") String embeddingStr,
        @Param("limit") int limit
    );
}
```

### 5. 向量入库完整服务

```java
package com.jianbo.springai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.springai.entity.DocumentChunk;
import com.jianbo.springai.repository.DocumentChunkRepository;
import com.jianbo.springai.utils.EmbeddingUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文档向量入库完整服务
 *
 * 全流程：
 *   原始文档 → 文本切片 → 向量化 → JSON序列化 → 存入PG表
 */
@Service
public class VectorStorageService {

    @Resource
    private EmbeddingUtil embeddingUtil;

    @Resource
    private DocumentChunkRepository chunkRepository;

    @Resource
    private ObjectMapper objectMapper;  // JSON序列化

    /**
     * 单文档完整入库
     *
     * @param documentId 文档唯一标识
     * @param content 原始文档内容
     * @param maxChunkSize 每段最大字数
     * @param overlapSize 重叠字数
     * @return 入库切片数量
     */
    @Transactional  // 事务保证：入库失败自动回滚
    public int importDocument(String documentId, String content,
                               int maxChunkSize, int overlapSize) {
        // 步骤1：文本清洗
        String cleaned = cleanText(content);

        // 步骤2：固定长度切片
        List<String> chunks = splitText(cleaned, maxChunkSize, overlapSize);

        // 步骤3：批量向量化
        List<float[]> vectors = embeddingUtil.embedBatch(chunks);

        // 步骤4：向量入库
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(documentId);
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));

            // 把 float[] 转成 pgvector 格式字符串
            // [0.12, -0.34, ...] → '[0.12,-0.34,...]'
            chunk.setEmbedding(floatArrayToPgVector(vectors.get(i)));

            chunk.setCreatedAt(LocalDateTime.now());

            chunkRepository.save(chunk);
        }

        return chunks.size();
    }

    /**
     * 批量文档入库
     *
     * @param documents Map<文档ID, 文档内容>
     * @return 总入库切片数量
     */
    @Transactional
    public int importDocuments(java.util.Map<String, String> documents) {
        int totalCount = 0;
        for (var entry : documents.entrySet()) {
            int count = importDocument(
                entry.getKey(),
                entry.getValue(),
                500,   // 默认500字一段
                50     // 默认重叠50字
            );
            totalCount += count;
        }
        return totalCount;
    }

    // ========== 私有辅助方法 ==========

    /**
     * 文本清洗
     */
    private String cleanText(String text) {
        if (text == null) return "";
        return text
            .replaceAll("\\s+", " ")
            .replaceAll("[\\r\\n]+", "\n")
            .trim();
    }

    /**
     * 固定长度切片
     */
    private List<String> splitText(String text, int maxChunkSize, int overlapSize) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int index = 0;

        while (index < length) {
            int end = Math.min(index + maxChunkSize, length);
            chunks.add(text.substring(index, end));
            if (end >= length) break;
            index = end - overlapSize;  // 滑动窗口：回退重叠部分
        }

        return chunks;
    }

    /**
     * float[] 转 pgvector 格式字符串
     *
     * 例如：
     *   float[]{0.12f, -0.34f, 0.56f}
     *   → "[0.12,-0.34,0.56]"
     */
    private String floatArrayToPgVector(float[] vector) {
        // 方案1：手动拼接（性能好）
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();

        // 方案2：Jackson序列化（通用但稍慢）
        // return objectMapper.writeValueAsString(vector);
    }
}
```

---

## 九 向量维度概念详解

### 1. 什么是向量维度

```
Embedding 模型输出向量的「长度」

text-embedding-3-small → 1536维
text-embedding-3-large → 3072维
MiniMax emb-01         → 1536维

维度含义：
  - 维度越高 → 能表达的语义特征越丰富 → 越精准
  - 维度越高 → 存储空间越大 → 检索更慢
```

### 2. 维度与精度、存储的关系

| 模型 | 向量维度 | 精度 | 单条存储空间 | 检索速度 |
|------|---------|------|-------------|---------|
| text-embedding-3-small | 1536 | 中 | ~6KB | 快 |
| text-embedding-3-large | 3072 | 高 | ~12KB | 慢 |
| emb-01 | 1536 | 中高 | ~6KB | 快 |

### 3. 维度选择建议

```
生产环境推荐：
  - 通用场景：1536维（够用且高效）
  - 高精度场景：3072维（但成本翻倍、检索慢）
  - 简单场景：768维（速度最快，但可能丢失细节）
```

---

## 十 面试必背总结

### 1. Embedding 是什么

```
一句话：
Embedding = 把文字转成固定长度数字数组的模型

作用：
  - 机器无法直接理解文字，但能理解数字
  - Embedding 把文字「翻译」成向量，让AI能处理文本语义
```

### 2. 向量的核心特性

```
1. 语义相似 → 向量距离近（余弦相似度高）
2. 语义无关 → 向量距离远
3. 向量维度越高 → 表达能力越强
4. Embedding 是单向的（文字→向量），不可逆
```

### 3. 余弦相似度计算

```
公式：cos(θ) = (A·B) / (|A|×|B|)

含义：
  - 分子：两个向量的点积（对应位相乘后相加）
  - 分母：两个向量长度的乘积
  - 结果：-1 到 1 之间，越接近1越相似
```

### 4. RAG 中 Embedding 的位置

```
离线预处理：文档 → 切片 → Embedding → 向量数据库
实时问答：用户问题 → Embedding → 向量检索 → 召回TopN → 拼接Prompt → LLM回答
```

### 5. 生产注意要点

```
1. 切片大小：500字左右一段（太大向量不准，太小丢失上下文）
2. 重叠区域：50字左右（避免边界内容被切断）
3. 批量向量化：注意API批量上限，分批处理
4. 向量入库：原始文本 + 向量一起存（检索用向量，展示用原文）
5. 向量维度：1536维是性价比最优选择
```

---

## 十一 完整调用示例

```java
package com.jianbo.springai;

import com.jianbo.springai.service.VectorStorageService;
import com.jianbo.springai.utils.EmbeddingUtil;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

/**
 * Embedding + 向量入库完整测试
 */
@SpringBootTest
public class EmbeddingDemoTest {

    @Resource
    private EmbeddingUtil embeddingUtil;

    @Resource
    private VectorStorageService vectorStorageService;

    /**
     * 测试1：单条文本向量化
     */
    @Test
    void testSingleEmbed() {
        String text = "Java是世界上最流行的编程语言之一";

        float[] vector = embeddingUtil.embed(text);

        System.out.println("文本: " + text);
        System.out.println("向量维度: " + vector.length);
        System.out.println("向量前5维: " + toString(vector, 5));
    }

    /**
     * 测试2：批量文本向量化
     */
    @Test
    void testBatchEmbed() {
        List<String> chunks = List.of(
            "Java是一种面向对象的编程语言",
            "Python适合数据分析和小脚本",
            "Redis是高性能的内存数据库"
        );

        List<float[]> vectors = embeddingUtil.embedBatch(chunks);

        System.out.println("切片数量: " + chunks.size());
        System.out.println("向量数量: " + vectors.size());
        vectors.forEach(v -> System.out.println("向量维度: " + v.length));
    }

    /**
     * 测试3：文档完整入库
     */
    @Test
    void testDocumentImport() {
        String docId = "doc_" + System.currentTimeMillis();

        String content = """
            Java是一门面向对象的编程语言，由Sun公司于1995年发布。
            Java具有跨平台、安全性高、面向对象、多线程等特性。
            Java广泛应用于企业级应用、Android开发、大数据等领域。
            JVM是Java虚拟机，是Java跨平台的核心实现。
            Spring是Java最流行的企业级开发框架。
            MyBatis是Java生态中的持久层框架。
            Redis是一款高性能的内存数据库，常用于缓存和分布式锁。
            """;

        int count = vectorStorageService.importDocument(
            docId, content,  // 文档ID和内容
            500,             // 每段500字
            50               // 重叠50字
        );

        System.out.println("入库切片数量: " + count);
    }

    // ========== 辅助方法 ==========

    private String toString(float[] vector, int limit) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%.4f", vector[i]));
            if (i < limit - 1) sb.append(", ");
        }
        sb.append(", ...]");
        return sb.toString();
    }
}
```

---

## 下节预告：向量检索

```
入库之后，下一步就是检索：
  用户问题 → 向量化 → 在向量库中找最相似的 N 条 → 作为参考资料 → 拼接Prompt → LLM回答

核心问题：
  1. 如何计算相似度？（余弦相似度 / 欧氏距离）
  2. 如何加速检索？（HNSW / IVF 等向量索引算法）
  3. 如何召回 TopK ？（向量库 SQL 或 API 查询）
```
