## 一、衔接回顾：入库和检索的关系

### 1. 先回顾：我们到哪了

```
Day22: 长文档 --> 文本切片 --> N 个文本片段
Day23: N 个文本片段 --> Embedding模型 --> N 个 float[1536] 向量
Day24: N 个向量 --> 存入数据库 --> 等待检索     <-- 上节做完了
Day25: 用户提问 --> 向量化 --> 数据库检索 --> 召回相似内容  <-- 今天做这一步
Day26: 召回的文档片段 --> 拼接 Prompt --> 大模型生成答案
```



### 2. 为什么要做检索

```
你可能会问：向量已经存进数据库了，怎么查？

回顾 Day24 入库的一条记录：
  ┌────────────────────────────────────────────────┐
  │ id:       550e8400-e29b-41d4-a716-446655440000  │
  │ content:  "Java是一门面向对象的编程语言..."      │
  │ embedding: [0.12, -0.34, 0.56, ...共1536维]    │
  │ metadata: {"source":"java.pdf","chunk_index":"3"}│
  └────────────────────────────────────────────────┘

用户问："Java和Python有什么区别"
         ↓
【第一步：用户问题向量化】
  "Java和Python有什么区别" --> Embedding模型 --> [0.15, -0.31, 0.52, ...]
         ↓
【第二步：向量数据库做相似度计算】
  问题向量 vs 库中每条记录的 embedding 字段 --> 计算余弦距离
         ↓
【第三步：按相似度排序，召回 TopN】
  距离升序（越小越相似）--> 取前 3 条
  --> 返回这 3 条的 content 字段（原始文本，不是向量！）

所以你不需要：
  x 逐条比对所有向量
  x 暴力计算余弦相似度
  x 自己写排序逻辑
  VectorStore.similaritySearch() 全帮你做了！
```



------

## 二、语义检索核心概念（零基础必看）

### 1. 什么是相似度检索

```
普通检索（MySQL LIKE）：
  用户问："Java怎么学"
  只能搜"Java"和"怎么学"这些字 → 找不到"Python入门教程"（虽然意思相关但用词不同）

语义检索（向量数据库）：
  用户问："Java怎么学"
  找到语义最相关的："Python入门指南"（向量距离近，虽然用词不同但意思相近）

本质区别：
  关键词检索：看"字一样"
  语义检索：看"意思一样"
```



### 2. 余弦相似度原理（面试重点）

```
余弦相似度：衡量两个向量"方向"有多相似

取值范围 [-1, 1]：
  余弦相似度 = 1    --> 方向完全相同（语义完全一致）
  余弦相似度 = 0    --> 方向垂直（语义完全不相关）
  余弦相似度 = -1   --> 方向完全相反（语义相反）

但 pgvector 用的是「余弦距离」= 1 - 余弦相似度：
  余弦距离 = 0    --> 完全相似（方向完全相同）
  余弦距离 = 1    --> 完全不相关（方向垂直）
  余弦距离 = 2    --> 完全相反（方向相反）

计算公式：
                   A · B              分子：点积（对应位相乘后求和）
  cos(theta) = ───────────
                |A| × |B|            分母：两个向量长度的乘积

pgvector 操作符：
  <=>  余弦距离（值越小越相似，检索用这个）
  <->  欧氏距离
  <#>  点积距离

面试回答"为什么用余弦距离"：
  "Embedding模型输出的向量，长度可能不同，但方向才代表语义。
   余弦距离只看两个向量的方向是否一致，不受向量长度影响，
   所以语义检索用余弦相似度（距离）最准确。"
```



### 3. TopK 召回机制

```
TopK = 召回最相似的 K 条记录

例如 TopK = 3：
  用户问："JVM怎么调优"
         ↓
  向量数据库返回（按距离升序）：
    第1名（距离0.05）："JVM内存模型调优技巧..."   <-- 最相关
    第2名（距离0.12）："JVM垃圾回收器选择..."    <-- 较相关
    第3名（距离0.18）："Java性能优化方法..."      <-- 有点相关
    第4名（距离0.45）："Spring Boot配置..."       <-- 不相关，舍去
    ...

TopK 不能太大：
  - K 太大 --> Token 消耗过多 --> 答案冗余
  - K 太小 --> 可能漏掉相关内容
  - 生产经验：K = 3 ~ 5（通用场景）
```



### 4. 相似度阈值 threshold

```
threshold = 相似度门槛，低于这个值的结果被过滤

pgvector 余弦距离范围 [0, 2]：
  距离 = 0    --> 完全相似
  距离 = 0.5  --> 较相似
  距离 = 1    --> 完全不相关
  距离 = 2    --> 完全相反

配置 threshold = 0.7 意味着：
  只召回余弦距离 < 0.7 的记录
  距离 >= 0.7 的被舍去（"不太相关"）

与 TopK 的区别：
  TopK：无论如何返回 K 条（不够就凑数）
  threshold：只返回达到相似度门槛的（可能返回 0 条）

组合使用，双重保险：
  SearchRequest.builder()
      .topK(5)                    // 最多返回5条
      .similarityThreshold(0.7)    // 只返回距离<0.7的
      .build()
  // 效果：最多5条，但只返回相关的（可能只有2条）
```



------

## 三、SpringAI VectorStore 检索 API 详解

### 1. 核心方法签名（背下来）

```java
// 方法1：最简单用法（问什么就搜什么，TopK默认5）
List<Document> similaritySearch(String query)

// 方法2：指定 TopK（推荐生产用）
List<Document> similaritySearch(String query, int topK)

// 方法3：完整配置（所有参数可调）
List<Document> similaritySearch(SimilaritySearchRequest request)

// 方法4：带过滤条件（按 metadata 过滤）
List<Document> similaritySearch(SimilaritySearchRequest request, FilterExpression filter)
```



### 2. SearchRequest 构建器（所有参数）

```java
// 完整参数构建示例
SearchRequest searchRequest = SearchRequest.builder()
    .query(userQuestion)                           // 用户问题（会自动向量化）
    .topK(5)                                      // 召回数量
    .similarityThreshold(0.7)                      // 相似度阈值（0~1）
    .filterExpression(
        new FilterExpressionBuilder()
            .eq("source", "java.pdf")
            .build()
    )                                             // 元数据过滤
    .build();

List<Document> results = vectorStore.similaritySearch(searchRequest);
```



### 3. SearchRequest 全部参数解释

```
query (String)
  --> 用户问题，SearchRequest 会自动调用 EmbeddingModel 转成向量
  --> 也可以直接传入 float[] 向量（不常用）

topK (int)
  --> 召回数量，默认 5
  --> 注意：这是"希望返回多少"，不是"一定返回多少"
  --> 如果库中只有 2 条，返回 2 条（不会返回空）

similarityThreshold (double)
  --> 相似度阈值，范围 0 ~ 1（会自动转为对应距离算法）
  --> 1.0 = 不过滤（返回所有）
  --> 0.0 = 严格过滤（只返回完全匹配的，可能返回空）
  --> 生产推荐：0.7 ~ 0.8

filterExpression (FilterExpression)
  --> 按 metadata 字段过滤
  --> 底层会拼成 SQL WHERE 或 ES filter
  --> 常用操作：
      Eq("source", "java.pdf")           --> 等于
      NotEq("source", "test.txt")        --> 不等于
      In("source", List.of("a.txt","b.txt")) --> 在列表中
      And(Eq(...), Eq(...))              --> 组合条件
      Or(Eq(...), Eq(...))              --> 或条件
```



### 4. Document 结构解析（返回结果）

```java
public class Document {
    String id;          // UUID，唯一标识
    String content;     // 原始文本内容（人类可读！）
    Map<String, Object> metadata;  // 元数据（入库时存的）

    // 重要：content 是给用户看的，metadata 用于追溯来源
}

// 使用示例
List<Document> docs = vectorStore.similaritySearch("JVM调优", 5);

for (Document doc : docs) {
    String text = doc.getContent();                           // 原始文本
    String source = doc.getMetadata().get("source");          // 来源
    String chunkIndex = doc.getMetadata().get("chunk_index"); // 第几段
    System.out.println("来源：" + source + " 片段：" + chunkIndex);
    System.out.println("内容：" + text);
}
```



------

## 四、PostgreSQL + pgvector 语义检索（完整代码）

### 1. 原生 SQL 检索方式（理解原理）

```sql
-- ===== 查看入库的向量 =====
SELECT id, LEFT(content, 50), metadata
FROM vector_store
LIMIT 3;

-- ===== 语义检索 SQL =====
-- 用户问题先通过 Embedding 模型得到向量，然后用这个 SQL 查

SELECT
    id,
    LEFT(content, 80) AS content_preview,
    embedding <=> '[0.12,-0.34,0.56,...]' AS distance  -- 余弦距离
FROM vector_store
ORDER BY embedding <=> '[0.12,-0.34,0.56,...]' ASC   -- 升序 = 越小越相似
LIMIT 5;                                              -- Top 5

-- ===== 带 metadata 过滤的检索 =====
SELECT *
FROM vector_store
WHERE metadata->>'source' = 'java.pdf'               -- 只查 java.pdf 来源
ORDER BY embedding <=> '[0.12,...]' ASC
LIMIT 5;

-- ===== 按相似度阈值过滤 =====
SELECT *
FROM vector_store
WHERE embedding <=> '[0.12,...]' < 0.7               -- 余弦距离小于0.7
ORDER BY embedding <=> '[0.12,...]' ASC
LIMIT 5;

-- ===== 带来源 + 阈值双重过滤 =====
SELECT *
FROM vector_store
WHERE metadata->>'source' = 'java.pdf'
  AND embedding <=> '[0.12,...]' < 0.8
ORDER BY embedding <=> '[0.12,...]' ASC
LIMIT 5;
```



### 2. SpringAI VectorStore 检索代码（推荐）

```java
package com.jianbo.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量检索服务（语义检索核心）
 *
 * <p>所有重载方法最终都委托给核心方法 search(query, sources, minChunks, topK, similarityThreshold)
 * 参数为 null 则自动跳过该条件
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VectorSearchService {

  private static final int DEFAULT_TOP_K = 5;
  private static final double DEFAULT_THRESHOLD = 0.0;

  private final VectorStore vectorStore;
  // ==================== 便捷重载（全部委托核心方法） ====================

  public List<Document> search(String query) {
    return search(query, null, null, null, null);
  }

  public List<Document> search(String query, int topK) {
    return search(query, null, null, topK, null);
  }

  public List<Document> search(String query, int topK, double similarityThreshold) {
    return search(query, null, null, topK, similarityThreshold);
  }

  public List<Document> search(String query, String source, int topK) {
    return search(query, List.of(source), null, topK, null);
  }

  public List<Document> search(String query, List<String> sources, Integer minChunks, Integer topK) {
    return search(query, sources, minChunks, topK, null);
  }

  // ==================== 核心方法（参数为 null 自动跳过） ====================

  /**
   * 统一检索入口
   *
   * @param query               用户问题（必填）
   * @param sources             文档来源列表（null = 不过滤来源）
   * @param minChunks           最少片段数（null = 不过滤片段数）
   * @param topK                召回数量（null = 默认5）
   * @param similarityThreshold 相似度阈值（null = 不过滤，0.0~1.0）
   * @return 满足条件的文档片段列表
   */
  public List<Document> search(String query,
                               List<String> sources,
                               Integer minChunks,
                               Integer topK,
                               Double similarityThreshold) {
    int k = (topK != null) ? topK : DEFAULT_TOP_K;
    double threshold = (similarityThreshold != null) ? similarityThreshold : DEFAULT_THRESHOLD;

    log.debug("语义检索 | query={}, sources={}, minChunks={}, topK={}, threshold={}",
            query, sources, minChunks, k, threshold);

    // 动态构建 filter：有值才拼，null 跳过
    Filter.Expression filter = buildFilter(sources, minChunks, null, null);

    // 构建 SearchRequest
    SearchRequest.Builder builder = SearchRequest.builder()
            .query(query)
            .topK(k)
            .similarityThreshold(threshold);

    if (filter != null) {
      builder.filterExpression(filter);
    }

    List<Document> results = vectorStore.similaritySearch(builder.build());
    log.debug("检索完成, 召回 {} 条", results.size());
    return results;
  }

  // ==================== 动态拼 Filter ====================

  /**
   * 动态拼 Filter（List 收集法，条件再多也不怕）
   * 非 null 的条件自动加入，最后用 AND 串联
   */
  private Filter.Expression buildFilter(List<String> sources,
                                        Integer minChunks,
                                        String docTitle,
                                        Integer minChunkIndex) {
    // ... 未来加新参数，这里加一个 if 就行
    var b = new FilterExpressionBuilder();
    List<FilterExpressionBuilder.Op> conditions = new ArrayList<>();

    if (sources != null && !sources.isEmpty()) {
      conditions.add(b.in("source", sources.toArray()));
    }
    if (minChunks != null) {
      conditions.add(b.gte("total_chunks", minChunks));
    }
    if (docTitle != null) {
      conditions.add(b.eq("doc_title", docTitle));
    }
    if (minChunkIndex != null) {
      conditions.add(b.gte("chunk_index", minChunkIndex));
    }
    // 未来加新条件：
    // if (xxx != null) { conditions.add(b.eq("xxx", xxx)); }

    // 空 → 不过滤
    if (conditions.isEmpty()) {
      return null;
    }
    // 1个 → 单条件；多个 → 逐个 AND 合并
    FilterExpressionBuilder.Op result = conditions.get(0);
    for (int i = 1; i < conditions.size(); i++) {
      result = b.and(result, conditions.get(i));
    }
    return result.build();
  }
}
```



### 3. PG 检索原理图（理解底层）

```
vectorStore.similaritySearch("JVM怎么调优", 5) 背后发生了什么？

         ┌─────────────────────────────────────┐
         │  SearchRequest.builder()             │
         │  .query("JVM怎么调优")               │
         │  .topK(5)                            │
         │  .similarityThreshold(0.7)           │
         └──────────────┬──────────────────────┘
                        v
         ┌─────────────────────────────────────┐
         │  内部自动调用 EmbeddingModel         │
         │  "JVM怎么调优" --> float[1536]      │
         └──────────────┬──────────────────────┘
                        v
         ┌─────────────────────────────────────┐
         │  拼 SQL：                           │
         │  SELECT id, content, metadata       │
         │  FROM vector_store                 │
         │  WHERE embedding <=> ? < 0.7        │
         │  ORDER BY embedding <=> ? ASC      │
         │  LIMIT 5                           │
         └──────────────┬──────────────────────┘
                        v
         ┌─────────────────────────────────────┐
         │  PostgreSQL + pgvector              │
         │  HNSW 索引快速搜索                  │
         │  返回匹配的文档记录                │
         └──────────────┬──────────────────────┘
                        v
         ┌─────────────────────────────────────┐
         │  SpringAI 包装成 Document 对象      │
         │  List<Document> 返回给业务层        │
         └─────────────────────────────────────┘

所以你不需要：
  x 手动把问题转成向量
  x 手动拼 SQL
  x 手动解析结果
  VectorStore 全帮你做了！
```



### 4. 检索后验证 SQL

```sql
-- ===== 查看检索结果是否正确 =====
-- 先查一条记录的向量
SELECT embedding FROM vector_store LIMIT 1;

-- 再查最相似的 3 条
SELECT
    id,
    LEFT(content, 80) AS content_preview,
    metadata->>'source' AS source,
    embedding <=> (SELECT embedding FROM vector_store LIMIT 1) AS distance
FROM vector_store
ORDER BY embedding <=> (SELECT embedding FROM vector_store LIMIT 1)
LIMIT 3;

-- ===== 按来源统计 =====
SELECT
    metadata->>'source' AS source,
    count(*) AS doc_count
FROM vector_store
GROUP BY metadata->>'source';

-- ===== 检查是否有低质量内容 =====
-- 找到相似度差距很大的"混入"文档
SELECT
    id,
    LEFT(content, 50),
    metadata->>'source'
FROM vector_store
WHERE metadata->>'total_chunks' = '1';  -- 只有1个片段的可能是噪音
```



------

## 五、Elasticsearch 语义检索（完整代码）

### 1. ES KNN 查询原理

```
ES 的向量检索用 knn（K-Nearest Neighbor）语法：

POST /vector_store_index/_search
{
  "knn": {
    "field": "embedding",       -- 向量字段名
    "query_vector": [0.12,...], -- 用户问题向量
    "k": 5,                    -- 召回 5 条
    "num_candidates": 50        -- 候选数量（越大越精准，越慢）
  },
  "_source": ["content", "metadata"]  -- 只返回需要的字段
}

knn 原理（类比 HNSW）：
  - ES 内部也是用 HNSW 索引
  - num_candidates = 候选范围（类似 ef_search 参数）
  - k = 5 意味着从候选中找最相似的 5 条返回

num_candidates 参数选择：
  - 候选太少（10）：速度快，可能漏掉相关结果
  - 候选适中（50）：速度精度平衡 <-- 推荐
  - 候选太多（200）：速度慢，精度提升有限
```



### 2. SpringAI ElasticsearchVectorStore 方式（推荐代码）

```java
package com.jianbo.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ES 向量检索服务
 *
 * 和 VectorSearchService（PG版）代码几乎一样
 * 唯一区别：注入的是 @Qualifier("esVectorStore") 的 ElasticsearchVectorStore
 * --> 面向接口编程，底层存储对上层透明
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EsVectorSearchService {

    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.0;

    @Qualifier("esVectorStore")
    private final VectorStore vectorStore;

    // ==================== 便捷重载 ====================

    public List<Document> search(String query) {
        return search(query, null, null, null, null);
    }

    public List<Document> search(String query, int topK) {
        return search(query, null, null, topK, null);
    }

    public List<Document> search(String query, int topK, double similarityThreshold) {
        return search(query, null, null, topK, similarityThreshold);
    }

    public List<Document> search(String query, String source, int topK) {
        return search(query, List.of(source), null, topK, null);
    }

    public List<Document> search(String query, List<String> sources, Integer minChunks, Integer topK) {
        return search(query, sources, minChunks, topK, null);
    }

    // ==================== 核心方法 ====================

    /**
     * 统一检索入口（参数为 null 自动跳过）
     *
     * @param query               用户问题（必填）
     * @param sources             文档来源列表（null = 不过滤）
     * @param minChunks           最少片段数（null = 不过滤）
     * @param topK                召回数量（null = 默认5）
     * @param similarityThreshold 相似度阈值（null = 不过滤）
     */
    public List<Document> search(String query,
                                 List<String> sources,
                                 Integer minChunks,
                                 Integer topK,
                                 Double similarityThreshold) {
        int k = (topK != null) ? topK : DEFAULT_TOP_K;
        double threshold = (similarityThreshold != null) ? similarityThreshold : DEFAULT_THRESHOLD;

        log.debug("ES语义检索 | query={}, sources={}, minChunks={}, topK={}, threshold={}",
                query, sources, minChunks, k, threshold);

        Filter.Expression filter = buildFilter(sources, minChunks);

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(k)
                .similarityThreshold(threshold);

        if (filter != null) {
            builder.filterExpression(filter);
        }

        List<Document> results = vectorStore.similaritySearch(builder.build());
        log.debug("ES检索完成, 召回 {} 条", results.size());
        return results;
    }

    // ==================== 动态 Filter ====================

    private Filter.Expression buildFilter(List<String> sources, Integer minChunks) {
        var b = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> conditions = new ArrayList<>();

        if (sources != null && !sources.isEmpty()) {
            conditions.add(b.in("source", sources.toArray()));
        }
        if (minChunks != null) {
            conditions.add(b.gte("total_chunks", minChunks));
        }
        // 未来加新条件：
        // if (xxx != null) { conditions.add(b.eq("xxx", xxx)); }

        if (conditions.isEmpty()) {
            return null;
        }
        FilterExpressionBuilder.Op result = conditions.get(0);
        for (int i = 1; i < conditions.size(); i++) {
            result = b.and(result, conditions.get(i));
        }
        return result.build();
    }
}
```



### 3. 手动 RestClient KNN 查询（自定义场景）

```java
package com.jianbo.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * ES 手动 KNN 检索（自定义场景）
 *
 * 适用场景：
 *   - 需要 ES 原生 query + knn 混合检索
 *   - 需要分页、聚合等高级特性
 *   - 需要自定义打分公式
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EsKnnSearchService {

    private final RestClient restClient;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private static final String INDEX_NAME = "my_documents_es";

    /**
     * 纯 KNN 向量检索
     */
    public List<Map<String, Object>> knnSearch(String query, int topK) throws IOException {
        // 1. 把问题转成向量
        float[] queryVector = embeddingModel.embed(query);

        // 2. 构建 KNN 请求
        Request request = new Request("POST", "/" + INDEX_NAME + "/_search");
        String knnBody = """
            {
              "knn": {
                "field": "embedding",
                "query_vector": %s,
                "k": %d,
                "num_candidates": 50
              },
              "_source": ["content", "source", "chunk_index"]
            }
            """.formatted(vectorToJson(queryVector), topK);

        request.setJsonEntity(knnBody);
        var response = restClient.performRequest(request);

        // 3. 解析结果（省略，实际用 objectMapper）
        log.info("ES KNN 检索完成, topK: {}", topK);
        return List.of();
    }

    /**
     * 混合检索：KNN 向量 + 全文关键词
     *
     * 适用场景：
     *   - 用户问题既想语义相关，又想包含某些关键词
     *   - 例如："JVM 调优" --> 语义相似 + 包含"JVM"关键词
     */
    public List<Map<String, Object>> hybridSearch(String query, int topK) throws IOException {
        float[] queryVector = embeddingModel.embed(query);

        Request request = new Request("POST", "/" + INDEX_NAME + "/_search");
        String hybridBody = """
            {
              "query": {
                "bool": {
                  "must": [
                    {
                      "match": {
                        "content": "%s"
                      }
                    }
                  ],
                  "should": [
                    {
                      "knn": {
                        "field": "embedding",
                        "query_vector": %s,
                        "k": %d,
                        "num_candidates": 50
                      }
                    }
                  ]
                }
              },
              "_source": ["content", "source", "chunk_index"]
            }
            """.formatted(query, vectorToJson(queryVector), topK);

        request.setJsonEntity(hybridBody);
        restClient.performRequest(request);

        log.info("ES 混合检索完成");
        return List.of();
    }

    /**
     * float[] 转 JSON 数组字符串
     */
    private String vectorToJson(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
```



### 4. ES 检索后验证

```bash
# ===== 查看 ES 索引中的文档数量 =====
curl http://localhost:9200/my_documents_es/_count

# ===== KNN 检索测试 =====
curl -X POST http://localhost:9200/my_documents_es/_search -H "Content-Type: application/json" -d '
{
  "knn": {
    "field": "embedding",
    "query_vector": [0.12, -0.34, 0.56, ...],
    "k": 3,
    "num_candidates": 50
  },
  "_source": ["content", "source"]
}'

# ===== 混合检索测试 =====
curl -X POST http://localhost:9200/my_documents_es/_search -H "Content-Type: application/json" -d '
{
  "query": {
    "bool": {
      "must": { "match": { "content": "JVM" } },
      "should": [
        { "knn": { "field": "embedding", "query_vector": [...], "k": 3, "num_candidates": 50 } }
      ]
    }
  }
}'
```



------

## 六、生产级检索调优（面试重点）

### 1. TopK 如何选

| 场景                   | TopK | 原因                    |
| :--------------------- | :--- | :---------------------- |
| 严格场景（考试/客服）  | 3    | 只给最相关的，Token最省 |
| 通用场景（知识库问答） | 5    | 平衡相关性和覆盖度      |
| 宽松场景（推荐/搜索）  | 10   | 需要更多上下文          |

### 2. similarity threshold 如何调

```
threshold 调参方法：

1. 先设 0.0（关闭过滤），看返回结果的分布
   --> 如果 topK=5 的最远距离是 0.45，说明 0.45 以上基本没相关内容

2. 再设 0.7（通用推荐），验证召回质量
   --> 人工检查召回的文档是否真的相关

3. 调整原则：
   - 漏召回（相关但被过滤）：降低 threshold
   - 混入噪音（不相关但被召回）：提高 threshold

生产配置建议：
  TopK = 5, threshold = 0.7（通用场景）
  TopK = 3, threshold = 0.8（严格场景）
```



### 3. HNSW 参数调优

```
HNSW 查询参数（ef_search）：

PG 配置：
  spring.ai.vectorstore.pgvector.hnsw.ef-search = 100  # 默认 100

ES 配置：
  num_candidates = 50  # 类似 ef_search

参数含义：
  ef_search 越大 --> 搜索范围越大 --> 精度越高 --> 速度越慢
  ef_search 越小 --> 搜索范围越小 --> 精度越低 --> 速度越快

选值建议：
  10-50：   快速搜索，精度一般
  100：     平衡模式 <-- 生产推荐
  200-500： 高精度搜索，速度慢

面试回答：
  "HNSW 的 ef_search 控制查询时的搜索宽度，
   值越大精度越高但越慢，生产一般设 100。"
```



### 4. 两层召回策略（生产标配）

```
为什么需要两层？

第一层（粗召回）：
  - 目的：快速从海量数据中捞出几十条可能相关的
  - 方法：向量检索，TopK=50，用 HNSW 索引
  - 特点：速度快，可能有噪音

第二层（精排）：
  - 目的：过滤噪音，只保留真正相关的
  - 方法：对第一层结果再做相似度打分 + 重排序
  - 特点：速度慢，但精度高

代码示例：
  // 第一层：粗召回 50 条
  List<Document> coarseResults = vectorStore.similaritySearch(query, 50);

  // 第二层：精排，只取最相关的 5 条
  List<Document> fineResults = rerank(coarseResults, query, 5);
```



### 5. 召回率 vs 精确率

```
衡量检索质量的两个指标：

召回率（Recall）= 找到的相关文档 / 库中所有相关文档
  --> 衡量"有没有漏掉"
  --> 召回率低 = 很多相关文档没被找到

精确率（Precision）= 找到的相关文档 / 总召回文档
  --> 衡量"有没有找错"
  --> 精确率低 = 召回的文档里混入了很多噪音

理想状态：
  召回率 = 1.0（所有相关都找到）
  精确率 = 1.0（找到的都相关）

实际权衡：
  TopK 大 --> 召回率高，但精确率可能低
  TopK 小 --> 精确率高，但召回率可能低
  threshold 高 --> 精确率高，但召回率低
  threshold 低 --> 召回率高，但精确率低

面试回答：
  "RAG 场景更看重召回率（不能漏掉重要参考资料），
   但也不能太低，否则会引入噪音。"
```



------

## 七、PG vs ES 检索对比

### 1. 检索语法对比

```
PG（pgvector）：
  SQL:  SELECT * FROM vector_store
        WHERE embedding <=> ? < 0.8
        ORDER BY embedding <=> ?
        LIMIT 5;

  SpringAI: vectorStore.similaritySearch(query, topK)

ES：
  JSON:  {
           "knn": {
             "field": "embedding",
             "query_vector": [...],
             "k": 5,
             "num_candidates": 50
           }
         }

  SpringAI: vectorStore.similaritySearch(query, topK)
  （和 PG 完全一样，底层实现不同而已）
```



### 2. 完整功能对比表

| 特性           | PG + pgvector      | Elasticsearch                  |
| :------------- | :----------------- | :----------------------------- |
| 检索语法       | SQL `ORDER BY <=>` | JSON `knn { field, k }`        |
| 相似度算法     | cosine / l2 / dot  | cosine / l2_norm / dot_product |
| 混合检索       | 需手写 SQL 组合    | 原生支持 `knn + query`         |
| 过滤条件       | SQL WHERE          | ES filter                      |
| 分页           | OFFSET             | from / size                    |
| 聚合           | GROUP BY           | aggregations                   |
| 性能（百万级） | ~30ms              | ~10ms                          |
| 性能（千万级） | ~200ms             | ~30ms                          |

### 3. 选型决策（背结论）

```
选 PG pgvector：
  ✓ 已有 PostgreSQL，不想加新组件
  ✓ 数据量 < 100万条
  ✓ 需要和其他表联合查询（JOIN）
  ✓ 团队熟悉 SQL

选 Elasticsearch：
  ✓ 数据量 > 100万条
  ✓ 需要全文检索 + 向量检索混合
  ✓ 需要水平扩展
  ✓ 团队有 ES 运维经验

面试回答模板：
  "中小规模用 PG pgvector，零额外运维，SQL 联合查询方便；
   大规模或需要混合检索用 ES，天然分布式，检索性能好。
   我们项目用的是 PG（/ES），原因是..."
```



------

## 八、完整 Controller + 测试代码

### 1. 检索测试 Controller

```java
package com.jianbo.springai.controller;

import com.jianbo.springai.service.VectorSearchService;
import com.jianbo.springai.service.EsVectorSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 向量检索测试接口
 *
 * 测试地址（端口 12115）：
 *   PG 简单检索：   GET  http://localhost:12115/search/pg?query=JVM是什么
 *   PG 指定TopK：  GET  http://localhost:12115/search/pg?query=Java&topK=3
 *   PG 完整配置：   GET  http://localhost:12115/search/pg/full?query=Spring&topK=5&threshold=0.7
 *   PG 按来源：    GET  http://localhost:12115/search/pg/source?query=Redis&source=java.pdf&topK=3
 *   ES 检索：     GET  http://localhost:12115/search/es?query=JVM调优
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final VectorSearchService vectorSearchService;
    private final EsVectorSearchService esVectorSearchService;

    // ==================== PG 检索接口 ====================

    /**
     * 简单语义检索
     * GET /search/pg?query=JVM是什么
     */
    @GetMapping("/pg")
    public Map<String, Object> pgSearch(@RequestParam String query) {
        List<Document> results = vectorSearchService.search(query);

        return Map.of(
            "store", "pgvector",
            "query", query,
            "count", results.size(),
            "results", formatResults(results)
        );
    }

    /**
     * 指定 TopK 检索
     * GET /search/pg?query=Java&topK=3
     */
    @GetMapping("/pg")
    public Map<String, Object> pgSearchWithTopK(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        List<Document> results = vectorSearchService.search(query, topK);

        return Map.of(
            "store", "pgvector",
            "query", query,
            "topK", topK,
            "count", results.size(),
            "results", formatResults(results)
        );
    }

    /**
     * 完整配置检索
     * GET /search/pg/full?query=Spring&topK=5&threshold=0.7
     */
    @GetMapping("/pg/full")
    public Map<String, Object> pgSearchFull(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.7") double threshold) {
        List<Document> results = vectorSearchService.search(query, topK, threshold);

        return Map.of(
            "store", "pgvector",
            "query", query,
            "topK", topK,
            "threshold", threshold,
            "count", results.size(),
            "results", formatResults(results)
        );
    }

    /**
     * 按来源过滤检索
     * GET /search/pg/source?query=Redis&source=java.pdf&topK=3
     */
    @GetMapping("/pg/source")
    public Map<String, Object> pgSearchWithSource(
            @RequestParam String query,
            @RequestParam String source,
            @RequestParam(defaultValue = "5") int topK) {
        List<Document> results = vectorSearchService.searchWithSource(query, source, topK);

        return Map.of(
            "store", "pgvector",
            "query", query,
            "source", source,
            "topK", topK,
            "count", results.size(),
            "results", formatResults(results)
        );
    }

    // ==================== ES 检索接口 ====================

    /**
     * ES 语义检索
     * GET /search/es?query=JVM调优
     */
    @GetMapping("/es")
    public Map<String, Object> esSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        List<Document> results = esVectorSearchService.search(query, topK);

        return Map.of(
            "store", "elasticsearch",
            "query", query,
            "topK", topK,
            "count", results.size(),
            "results", formatResults(results)
        );
    }

    // ==================== 辅助方法 ====================

    /**
     * 格式化检索结果（便于展示）
     */
    private List<Map<String, Object>> formatResults(List<Document> results) {
        return results.stream()
            .map(doc -> Map.of(
                "id", doc.getId(),
                "content", doc.getContent(),
                "source", doc.getMetadata().getOrDefault("source", "unknown"),
                "chunkIndex", doc.getMetadata().getOrDefault("chunk_index", "0")
            ))
            .collect(Collectors.toList());
    }
}
```



### 2. 检索单元测试

```java
package com.jianbo.springai;

import com.jianbo.springai.service.VectorSearchService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 向量检索完整测试
 *
 * 运行前确保：
 *   1. Day24 的入库测试已执行（vector_store 表有数据）
 *   2. PostgreSQL + pgvector 已启动
 *   3. MiniMax API Key 已配置
 */
@SpringBootTest
public class VectorSearchTest {

    @Resource
    private VectorSearchService vectorSearchService;

    /**
     * 测试1：简单语义检索
     */
    @Test
    void testSimpleSearch() {
        System.out.println("========== 简单语义检索 ==========");

        List<Document> results = vectorSearchService.search("JVM内存模型");

        System.out.println("问题：JVM内存模型");
        System.out.println("召回数量：" + results.size());
        printResults(results);
    }

    /**
     * 测试2：指定 TopK 检索
     */
    @Test
    void testSearchWithTopK() {
        System.out.println("========== 指定 TopK 检索 ==========");

        List<Document> results = vectorSearchService.search("Java编程", 3);

        System.out.println("问题：Java编程，TopK=3");
        System.out.println("召回数量：" + results.size());
        printResults(results);
    }

    /**
     * 测试3：带阈值的检索
     */
    @Test
    void testSearchWithThreshold() {
        System.out.println("========== 带阈值的检索 ==========");

        // threshold=0.9 非常严格，应该召回很少
        List<Document> results = vectorSearchService.search("Java", 5, 0.9);

        System.out.println("问题：Java，TopK=5，threshold=0.9（严格）");
        System.out.println("召回数量：" + results.size());
        printResults(results);

        // threshold=0.0 不过滤
        List<Document> results2 = vectorSearchService.search("Java", 5, 0.0);
        System.out.println("\n问题：Java，TopK=5，threshold=0.0（不过滤）");
        System.out.println("召回数量：" + results2.size());
        printResults(results2);
    }

    /**
     * 测试4：按来源过滤检索
     */
    @Test
    void testSearchWithSourceFilter() {
        System.out.println("========== 按来源过滤检索 ==========");

        // 只查 redis_guide.txt 来源的文档
        List<Document> results = vectorSearchService.searchWithSource(
            "缓存", "redis_guide.txt", 3);

        System.out.println("问题：缓存，来源：redis_guide.txt");
        System.out.println("召回数量：" + results.size());
        printResults(results);
    }

    /**
     * 测试5：语义相近但用词不同（验证语义检索能力）
     */
    @Test
    void testSemanticSearch() {
        System.out.println("========== 语义检索能力验证 ==========");

        // 问："堆和栈有什么区别"
        // 库中存的是："虚拟机栈与堆内存差异"
        List<Document> results = vectorSearchService.search("堆和栈有什么区别", 3);

        System.out.println("问题：堆和栈有什么区别");
        System.out.println("（库中存的是：虚拟机栈与堆内存差异）");
        System.out.println("召回数量：" + results.size());
        printResults(results);

        // 对比 MySQL like 查询（肯定搜不到）
        System.out.println("\n对比：MySQL LIKE 搜 '堆和栈有什么区别' --> 0 条");
    }

    // ========== 辅助方法 ==========

    private void printResults(List<Document> results) {
        for (int i = 0; i < results.size(); i++) {
            Document doc = results.get(i);
            System.out.println("--- 第" + (i + 1) + "条 ---");
            System.out.println("ID: " + doc.getId());
            System.out.println("来源: " + doc.getMetadata().get("source"));
            System.out.println("片段: " + doc.getMetadata().get("chunk_index"));
            System.out.println("内容: " + doc.getContent());
        }
    }
}
```



------

## 九、面试必背总结

### 1. 语义检索是什么（一句话）

```
语义检索 = 把用户问题转成向量，在向量数据库中按余弦相似度找出最相关的 TopN 文档片段
```



### 2. 余弦相似度原理（背公式）

```
余弦相似度 = cos(A,B) = (A·B) / (|A| × |B|)
余弦距离 = 1 - 余弦相似度（pgvector 用这个）

pgvector 操作符：
  <=>  余弦距离（越小越相似）
  <->  欧氏距离
  <#>  点积距离

语义检索用余弦距离，因为只关心方向不关心长度
```



### 3. TopK 和 Threshold（背配置）

```
TopK：召回数量，生产建议 3~5
  K太小：漏召回
  K太大：噪音多

threshold：相似度阈值，生产建议 0.7~0.8
  太高：漏召
  太低：噪音多

组合使用，双重保险
```



### 4. HNSW 查询原理（背参数）

```
HNSW 原理：多层图，上层粗定位下层精搜索

关键参数（PG）：
  m: 每个节点最大连接数（默认16）
  ef_construction: 建索引搜索宽度（默认200）
  ef_search: 查询搜索宽度（默认100）

ES 参数：
  num_candidates: 候选数量（类似 ef_search）

ef_search 越大精度越高越慢，生产设 100
```



### 5. VectorStore 检索 API（背方法签名）

```
简单检索：
  List<Document> similaritySearch(String query)
  List<Document> similaritySearch(String query, int topK)

完整检索：
  List<Document> similaritySearch(SimilaritySearchRequest request)

带过滤：
  List<Document> similaritySearch(SimilaritySearchRequest request, FilterExpression filter)

SearchRequest 构建：
  SearchRequest.builder()
      .query(query)
      .topK(5)
      .similarityThreshold(0.7)
      .filterExpression(filter)
      .build()
```



### 6. PG vs ES 选型（背结论）

```
PG pgvector：百万级以下、SQL联合查询、零额外运维
ES：百万级以上、混合检索、水平扩展

实际选型看场景和团队技术栈
```



### 7. 检索调优方法（背4条）

```
1. TopK：生产设 3~5，根据场景调整
2. threshold：生产设 0.7~0.8
3. HNSW ef_search：生产设 100，精度/性能平衡
4. 两层召回：粗召回50 + 精排5，精度最高
```



### 8. 检索流程（背完整链路）

```
用户问题
    ↓
EmbeddingModel 转成向量
    ↓
VectorStore.similaritySearch(query, topK)
    ↓
SQL: SELECT ... ORDER BY embedding <=> ? LIMIT topK
    ↓
HNSW 索引快速搜索
    ↓
返回 List<Document>
    ↓
取每个 Document 的 content（原始文本）
    ↓
拼接到 Prompt --> LLM 生成答案
```



------

## 十、衔接下节预告

```
Day25 学完，你已经掌握了：

  向量数据库检索：
    ✓ 用户问题向量化
    ✓ 余弦相似度计算
    ✓ TopN 召回
    ✓ PG 和 ES 两种实现

下一步（Day26）：完整 RAG 问答链路

  召回的文档片段
        ↓
  System Prompt（你是AI助手）
        ↓
  用户问题 + 召回的参考资料
        ↓
  拼接成一个完整的 Prompt
        ↓
  调用大模型 LLM 生成答案
        ↓
  返回给用户

Day26 核心代码：
  1. PromptTemplate 拼接（System + Context + Question）
  2. ChatClient 调用（SpringAI 统一接口）
  3. 流式返回（Streaming）
  4. 完整 RAG 问答 Controller
  5. 端到端测试
```