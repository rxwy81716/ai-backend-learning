

## 一、什么是向量存储（通俗理解）

### 1. 先回顾：我们到哪了

```
Day22: 长文档 --> 文本切片 --> N 个文本片段
Day23: N 个文本片段 --> Embedding模型 --> N 个 float[1536] 向量
Day24: N 个向量 --> 存入数据库 --> 等待检索     <-- 今天做这一步
Day25: 用户提问 --> 向量化 --> 数据库检索 --> 召回相似内容
```

### 2. 为什么要存数据库

```
你可能会问：向量已经在内存里了，为什么还要存？

原因1：持久化
  --> 内存一断电就没了，数据库是永久存储的
  --> 下次重启应用，向量还在

原因2：海量数据
  --> 100万篇文档 x 平均10个片段 = 1000万条向量
  --> 全放内存？内存不够（每条6KB x 1000万 = 60GB）
  --> 数据库可以存磁盘 + 建索引 + 分页查

原因3：高效检索
  --> 向量数据库有专用索引（HNSW、IVFFlat）
  --> 从1000万条里找最相似的5条，毫秒级完成
  --> 内存暴力遍历？几十秒起步
```

### 3. 向量存储的本质

```
一条向量记录 = 原始文本 + 向量数组 + 元数据
例如：
  ┌──────────────────────────────────────────────────-┐
  │ id:        550e8400-e29b-41d4-a716-446655440000   │
  │ content:   "Java是一门面向对象的编程语言..."           │
  │ embedding: [0.12, -0.34, 0.56, ... 共1536维]       │
  │ metadata:  {"source":"java.pdf","chunk_index":"3"}│
  └──────────────────────────────────────────────────-┘
 
检索时：用向量字段做相似度计算
展示时：返回 content 字段（用户看的是文字，不是数字）
追溯时：看 metadata 知道内容来自哪个文档第几页
4. 两种向量存储方案
```

### 4. 两种向量存储方案

```
本项目实现两种方案：

方案一：PostgreSQL + pgvector（关系型 + 向量插件）
  --> 已有 PG 就能用，零额外运维
  --> SpringAI 官方适配，一行代码入库
  --> 适合：中小规模（百万级以下）

方案二：Elasticsearch（搜索引擎 + 向量能力）
  --> 天然支持全文检索 + 向量检索混合
  --> 分布式架构，海量数据水平扩展
  --> 适合：大规模（千万级以上）+ 需要全文搜索
```

## 二、向量存储核心概念（零基础必看）

### 1. 向量索引算法（面试重点）

```
问题：1000万条向量，找最相似的5条，怎么办？
暴力搜索（Brute Force）：
  逐条计算余弦相似度 --> O(N) --> 太慢！
向量索引（近似最近邻 ANN）：
  提前建好索引结构 --> O(logN) --> 毫秒级！
两种常用索引算法：
  HNSW（分层可导航小世界图）
    原理：多层图结构，上层粗定位、下层精定位
    优点：精度高、查询快
    缺点：建索引慢、占内存多
    适合：对精度要求高的场景（生产首选）
  IVFFlat（倒排文件 + 平面量化）
    原理：先聚类分桶，查询时只搜相关桶
    优点：建索引快、占内存少
    缺点：精度略低于HNSW
    适合：数据量大但对精度要求不极端的场景
```

### 2. 距离度量方式

```
pgvector 和 ES 都支持多种距离计算：
  余弦距离（Cosine）  --> 语义检索标配，我们项目用这个
    值越小越相似，范围 [0, 2]
    pgvector 操作符: <=>
    ES: cosinesimil
  欧氏距离（L2）      --> 几何距离
    值越小越相似
    pgvector 操作符: <->
    ES: l2_norm
  点积（Dot Product）  --> 推荐系统常用
    值越大越相似
    pgvector 操作符: <#>
    ES: dot_product
面试回答：语义检索用余弦距离（Cosine），因为它只关注向量方向不关注长度
```

## 三、PostgreSQL + pgvector 向量存储（完整代码）

见代码

```
application.yaml
VectorStoreService 
```

### VectorStore.add() 原理图

```
vectorStore.add(documents) 这一行背后发生了什么？

  ┌────────────────────────────────┐
  │ 你的代码                       	 │
  │ vectorStore.add(documents)     │
  └──────────────┬─────────────────┘
                 v
  ┌────────────────────────────────┐
  │ PgVectorStore.add()            │
  │ 1. 遍历 documents              │
  │ 2. 调 embeddingModel.call()    │
  │ 3. 得到 float[1536]            │
  └──────────────┬─────────────────┘
                 v
  ┌────────────────────────────────┐
  │ 拼 SQL 并执行                  │
  │ INSERT INTO vector_store       │
  │   (id, content, metadata,      │
  │    embedding)                  │
  │ VALUES (uuid, '原文',          │
  │   '{"source":...}',            │
  │   '[0.12,-0.34,...]')          │
  └──────────────┬─────────────────┘
                 v
  ┌────────────────────────────────┐
  │ PostgreSQL + pgvector           │
  │ 数据落盘，HNSW 索引自动更新    │
  └────────────────────────────────┘

所以你不需要：
  x 手动调 embeddingService.embed()
  x 手动拼 SQL
  x 手动处理 float[] 转字符串
```

###  PG 入库后验证 SQL

```sql
-- 查看入库记录数
SELECT count(*) FROM vector_store;

-- 查看前5条（内容 + 元数据 + 向量维度）
SELECT
    id,
    LEFT(content, 50) AS content_preview,
    metadata,
    vector_dims(embedding) AS dim
FROM vector_store
LIMIT 5;

-- 按来源统计
SELECT count(*), metadata->>'source' AS source
FROM vector_store
GROUP BY metadata->>'source';

-- 简单相似度检索（预告Day25）
SELECT
    id,
    LEFT(content, 80) AS content_preview,
    embedding <=> (SELECT embedding FROM vector_store LIMIT 1) AS distance
FROM vector_store
ORDER BY distance ASC
LIMIT 3;
```

## 四、Elasticsearch 向量存储（完整代码）

> ES 部分为新增功能，需要加依赖和配置

### 1. ES 基础概念速查

```
不熟悉 ES？先看这几个核心概念：

  Index（索引）     ≈ 数据库的 Table
  Document（文档）  ≈ 数据库的 Row
  Field（字段）     ≈ 数据库的 Column
  Mapping（映射）   ≈ 数据库的 Schema（定义字段类型）

  ES 对比 PG：
    PG: INSERT INTO vector_store (content, embedding) VALUES (...)
    ES: PUT /vector_index/_doc/1 {"content":"...", "embedding":[...]}

  ES 的优势：
    1. 天然支持全文检索（分词搜索）+ 向量检索 --> 混合检索
    2. 分布式架构，水平扩展，千万级数据轻松扛
    3. RESTful API，开箱即用
```

### 2. Maven 依赖（pom.xml 新增）

```
<!-- Elasticsearch 向量数据库 Starter（SpringAI 自动配置 ElasticsearchVectorStore） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-elasticsearch-store-spring-boot-starter</artifactId>
</dependency>
```

### 3. application.yaml 新增 ES 配置

```
spring:
  ai:
    vectorstore:
      elasticsearch:
        index-name: vector_store_index     # ES 索引名（相当于PG的表名）
        dimensions: 1536                   # 向量维度（必须与模型一致）
        similarity: cosine                 # 相似度算法：cosine / dot_product / l2_norm

  elasticsearch:
    uris: http://localhost:9200            # ES 地址
    username: elastic                      # ES 用户名（无认证可省略）
    password: your_password                # ES 密码（无认证可省略）
```

### 4. ES 配置项详解

```
index-name: vector_store_index
  --> ES 索引名，相当于PG的表名
  --> 启动时 SpringAI 会自动创建索引 + Mapping
dimensions: 1536
  --> 必须与 Embedding 模型输出维度一致
  --> 和 PG 一样的规则
similarity: cosine
  --> 余弦相似度，语义检索标配
  --> 可选：dot_product, l2_norm
uris: http://localhost:9200
  --> ES 服务地址
  --> 集群：http://node1:9200,http://node2:9200
```

### 5. ES Index Mapping（相当于 PG 建表）

```
PUT /vector_store_index
{
  "mappings": {
    "properties": {
      "content": {
        "type": "text",
        "analyzer": "standard"
      },
      "embedding": {
        "type": "dense_vector",
        "dims": 1536,
        "index": true,
        "similarity": "cosine"
      },
      "metadata": {
        "type": "object",
        "enabled": true
      }
    }
  },
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0
  }
}

字段解释：
  content:   type=text       --> 支持全文检索分词
  embedding: type=dense_vector --> 稠密向量（1536维）
             index=true       --> 建向量索引（可做 knn 搜索）
             similarity=cosine --> 余弦相似度
  metadata:  type=object      --> JSON 对象（source、chunk_index 等）

PG 对比：
  PG:  embedding VECTOR(1536)     --> pgvector 自定义类型
  ES:  "type": "dense_vector"     --> ES 原生支持

PG:  USING hnsw                  --> 手动建 HNSW 索引
  ES:  index=true + similarity    --> 自动建向量索引（内部也是 HNSW）
```

### 6. ES 入库方式一：SpringAI VectorStore（推荐）

### 7. ES 入库方式二：手动 RestClient（自定义场景）

```
com.jianbo.springai.service;
EsVectorStoreService 
```

### 8. ES 入库后验证

```bash
# 查看索引是否创建
curl http://localhost:9200/_cat/indices?v

# 查看文档数量
curl http://localhost:9200/vector_store_index/_count

# 查看前3条文档
curl http://localhost:9200/vector_store_index/_search?size=3

# 查看 Mapping
curl http://localhost:9200/vector_store_index/_mapping?pretty

# 简单 knn 向量检索测试（预告Day25）
curl -X POST http://localhost:9200/vector_store_index/_search -H "Content-Type: application/json" -d '{
  "knn": {
    "field": "embedding",
    "query_vector": [0.12, -0.34, ...填入1536个浮点数...],
    "k": 3,
    "num_candidates": 50
  }
}'
```

## 五、PG 和 ES 双存储共存（同一项目）

```
如果 pom.xml 同时引入 pgvector 和 elasticsearch starter：
  --> Spring 容器里有两个 VectorStore 实现
  --> @Autowired VectorStore 时报错：NoUniqueBeanDefinitionException
```

### 2. 解决：用 @Qualifier 区分

```
VectorStoreConfig 
```

### 3. 生产建议

```
只用一个（大多数场景）：
  中小项目 --> 只用 PG pgvector，运维简单
  大型项目 --> 只用 ES，扩展性好

双写（少数场景）：
  PG 做主存储（事务一致性好）
  ES 做检索索引（全文+向量混合搜索）
  --> 类似 MySQL + ES 的经典架构
```

## 六、PG vs ES 完整对比

### 1. 功能对比

```
| 特性           | PG + pgvector              | Elasticsearch              |
|---------------|---------------------------|---------------------------|
| 类型           | 关系型数据库 + 向量插件     | 分布式搜索引擎             |
| 向量索引       | HNSW / IVFFlat             | HNSW（内置）               |
| 全文检索       | 有（tsvector，较弱）       | 强（天然优势，分词、高亮）  |
| 混合检索       | 需手写SQL组合              | 原生支持（knn + query）     |
| 分布式         | 单机为主（Citus可分布式）   | 天然分布式，自动分片        |
| 数据一致性     | ACID 事务保证              | 最终一致性                  |
| 适合规模       | 百万级（单机极限）          | 千万~亿级                  |
| 运维成本       | 低（已有PG就行）           | 中高（需要独立ES集群）      |
| SpringAI适配   | PgVectorStore              | ElasticsearchVectorStore   |
| SQL/API        | 标准SQL                    | RESTful + JSON DSL         |
```

### 2. 性能对比

```
| 场景             | PG pgvector | Elasticsearch |
|-----------------|-------------|---------------|
| 10万条向量检索    | ~5ms        | ~3ms          |
| 100万条向量检索   | ~30ms       | ~10ms         |
| 1000万条向量检索  | ~200ms      | ~30ms         |
| 写入吞吐         | 中等        | 高（bulk API） |
| 全文+向量混合检索  | 慢          | 快             |
```

### 3. 选型决策（面试必背）

```
选 PG pgvector：
  ✓ 已有 PostgreSQL，不想加新组件
  ✓ 数据量 < 100万条
  ✓ 需要 ACID 事务
  ✓ 团队熟悉 SQL
 
选 Elasticsearch：
  ✓ 数据量 > 100万条
  ✓ 需要全文检索 + 向量检索混合
  ✓ 需要水平扩展
  ✓ 团队有 ES 运维经验
 
面试回答模板：
  "中小规模用 PG pgvector，零额外运维，事务保证强；
   大规模或需要混合检索用 ES，天然分布式，检索性能好。
   我们项目用的是 PG（/ES），原因是..."
```

## 七、完整 Controller + 测试代码

```

```

## 八、HNSW 索引原理详解（面试加分）

### 1. HNSW 图解

```
HNSW = Hierarchical Navigable Small World（分层可导航小世界）
 
想象一个多层地图：
 
  Layer 2（最粗）：只有 3 个节点（高速公路出口）
    [A] -------- [B] -------- [C]
 
  Layer 1（中等）：10 个节点（省道路口）
    [A]-[D]-[E]-[B]-[F]-[G]-[H]-[C]-[I]-[J]
 
  Layer 0（最细）：所有节点（乡间小路）
    [A][D][K][L][E][M][B][N][F][O][G][P][H][Q][C][R][I][S][J]
 
查询过程（找最近邻）：
  1. 从 Layer 2 开始，找到最近的入口 --> B
  2. 下降到 Layer 1，从 B 出发找更近的 --> F
  3. 下降到 Layer 0，从 F 出发精确搜索 --> O（最终结果）
 
  总共只访问了约 10 个节点（不是全部 19 个）
  --> 数据越多，节省的搜索量越大
 
关键参数：
  m: 每个节点的最大邻居数（我们设 16）
     越大 --> 图越稠密 --> 精度高但内存大
  ef_construction: 建图时的搜索宽度（我们设 200）
     越大 --> 建图越慢但质量越好
  ef_search: 查询时的搜索宽度（默认 100）
     越大 --> 查询越慢但精度越高
```

### 2. IVFFlat 图解

```
IVFFlat = 倒排文件 + 平面（不压缩）
 
原理：先把向量聚类分桶，查询时只搜相关桶
 
  训练阶段：把 100万 向量分成 1000 个桶
    桶1: [v1, v5, v89, v102, ...]    -- 这些向量彼此相似
    桶2: [v2, v7, v45, v200, ...]
    ...
    桶1000: [v3, v8, v99, v500, ...]
 
  查询阶段：
    1. 查询向量 q 和 1000 个桶心比较 --> 找最近的 3 个桶
    2. 只在这 3 个桶里搜索（约 3000 个向量）
    3. 不用搜全部 100万个

  --> 搜索量从 100万 降到 3000（速度提升 300 倍）
 
关键参数：
  lists: 桶的数量（推荐 sqrt(N)，100万数据用 1000 个桶）
  probes: 查询时搜几个桶（推荐 lists/10，即 100 个桶）
```

------

## 九、ES 安装速查（Docker 一键启动）

```
# 拉取 ES 8.x 镜像（支持向量检索）
docker pull elasticsearch:8.15.0
 
# 启动（单机开发模式，关闭安全认证方便测试）
docker run -d \
  --name elasticsearch \
  -p 9200:9200 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  elasticsearch:8.15.0
 
# 安装 IK 中文分词器（可选，中文全文检索需要）
docker exec -it elasticsearch \
  bin/elasticsearch-plugin install \
  https://get.infini.cloud/elasticsearch/analysis-ik/8.15.0
 
# 重启使分词器生效
docker restart elasticsearch
 
# 验证是否启动成功
curl http://localhost:9200
# 预期返回：{"name":"...","cluster_name":"docker-cluster","version":{"number":"8.15.0",...}}
```

------

## 十、面试必背总结

### 1. 向量存储是什么（一句话）

```
向量存储 = 把 Embedding 模型输出的 float[] 数组持久化到数据库
目的：持久化 + 海量数据 + 高效检索
```

### 2. VectorStore 接口设计（背API）

```
SpringAI VectorStore 接口：
  add(List<Document>)    --> 入库（自动 Embedding + INSERT）
  delete(List<String>)   --> 删除
  similaritySearch(...)  --> 检索（Day25 详讲）
 
实现类：
  PgVectorStore              --> PostgreSQL + pgvector
  ElasticsearchVectorStore   --> Elasticsearch
  ChromaVectorStore          --> Chroma
  MilvusVectorStore          --> Milvus
  ...
 
面向接口编程：换存储只换配置，业务代码不动
```

### 3. PG vs ES 选型（背结论）

```
PG pgvector：百万级以下，已有PG，要事务
ES：百万级以上，要混合检索，要水平扩展
```

### 4. 向量索引算法（背两种）

```
HNSW：多层图，精度高，查询快，内存大 --> 生产首选
IVFFlat：聚类分桶，建索引快，精度略低 --> 大数据量备选
 
面试追问"HNSW原理"：
  "多层图结构，上层粗定位下层精搜索，
   参数 m 控制图密度，ef_construction 控制建图质量"
```

### 5. VectorStore vs 手动方式（背选型）

```
VectorStore：代码少、自动Embedding、标准表结构 --> 推荐
手动JDBC/RestClient：自定义表/索引结构、可加业务字段 --> 复杂业务
 
关键区别：
  VectorStore.add() --> 内部自动调 EmbeddingModel
  手动方式         --> 需要自己调 embeddingService.embed()
```

### 6. 入库流程（背完整链路）

```
PG VectorStore：
  文档 --> 切片 --> Document封装 --> vectorStore.add()
  --> 自动Embedding --> INSERT INTO vector_store
 
PG JDBC：
  文档 --> 切片 --> embeddingService.embedBatch()
  --> float[] 转 pgvector 字符串 --> INSERT INTO my_documents
 
ES VectorStore：
  文档 --> 切片 --> Document封装 --> vectorStore.add()
  --> 自动Embedding --> PUT /{index}/_doc/{id}
 
ES RestClient：
  文档 --> 切片 --> embeddingService.embedBatch()
  --> float[] 放 JSON --> POST /_bulk
```

### 7. 关键注意点（背5条）

```
1. dimensions 必须和 Embedding 模型输出一致（1536/1024）
2. PG 用 initialize-schema:false 手动建表（生产可控）
3. ES 需要 8.x 版本才支持 dense_vector + knn
4. 大批量入库用 bulk/batch，别逐条 INSERT
5. metadata 存文档来源信息，检索时用于追溯
```

### 8. 双存储共存（背方案）

```
同一项目用两个 VectorStore：
  @Primary --> PG（默认）
  @Qualifier("elasticsearchVectorStore") --> ES
 
业务场景：PG 做主存储（事务），ES 做检索加速
```

------

## 下节预告：Day25 向量检索

```
入库完成，下一步就是检索：
 
  用户提问 --> 向量化 --> 数据库检索 --> 召回 TopN --> Prompt --> LLM 生成答案
 
核心问题（下节解决）：
  1. VectorStore.similaritySearch() 怎么用？
  2. PG: SELECT ... ORDER BY embedding <=> ? LIMIT 5
  3. ES: knn { field: "embedding", k: 5 }
  4. 相似度阈值、TopK、重排序调优
  5. 完整 RAG 问答链路代码
```