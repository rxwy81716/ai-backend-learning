# Day23 Embedding 向量嵌入（完整版）

> 前置知识：Day22 文档文本切片（把长文档切成 N 个小片段）
>
> 本章目标：把切好的文本片段，转成 AI 能理解的「数字向量」，存入 PostgreSQL 向量数据库
>
> 下节预告：Day24 向量检索（从向量库中找出最相关内容，实现 RAG 问答）

---

## 一、什么是 Embedding（通俗理解）

### 1. 先看一个生活例子

```
人类理解词语：靠「意思」和「上下文」

  苹果 -- 水果？手机？公司？

  「苹果」+「吃」   --> 水果（能吃的东西）
  「苹果」+「打电话」--> 手机品牌
  「苹果」+「股票」  --> Apple公司

词语的含义，取决于它周围的「上下文」
但是！机器看不懂文字，只认识数字
所以需要一个「翻译官」把文字翻译成数字 --> 这就是 Embedding
```

### 2. Embedding 的本质

```
文字（人类语言）  -->  数字向量（机器语言）

    "苹果"      -->   [0.23, -0.45, 0.87, 0.12, ...]   （一串浮点数）
    "香蕉"      -->   [0.25, -0.38, 0.79, 0.15, ...]   （另一串浮点数）
    "手机"      -->   [-0.12, 0.56, -0.23, 0.89, ...]  （完全不同的数）
```

- **Embedding = 把文字转成一串固定长度的浮点数数组**
- **这串数字 = 词语的「语义坐标」**
- **语义越接近的词，数组里的数字越接近（向量距离越近）**

### 3. Embedding 模型的作用

```
一句话总结：
  Embedding模型 = 翻译官（把人类的语言翻译成机器的数学语言）

它能理解：
  - 「苹果」和「香蕉」意思接近 --> 向量距离近（都是水果）
  - 「电脑」和「手机」是同类   --> 向量距离近（都是电子产品）
  - 「天气」和「编程」无关     --> 向量距离远（完全不同的领域）

常见 Embedding 模型：
  - OpenAI  text-embedding-3-small    （1536维，国外最常用）
  - MiniMax embo-01 / MiniMax-M2.7    （1536维，国内企业级）
  - DeepSeek deepseek-embed           （1024维，性价比高）
  - 通义千问 text-embedding-v2        （1536维，阿里系）
```

### 4. 名词通俗解释（零基础必看）

```
向量（Vector）：一串有序的数字数组，例如 [0.1, 0.3, -0.5]
维度（Dimension）：数组里有几个数字，就是几维。1536维 = 1536个浮点数
嵌入（Embedding）：把文字「嵌入」到数学空间中，变成坐标点
语义（Semantic）：文字的「含义」，不是字面意思
余弦相似度（Cosine Similarity）：衡量两个向量方向是否相同的数学公式
```

---

## 二、向量的底层原理（面试重点）

### 1. 什么是向量维度

```
二维向量（x, y）：
  [1, 2]  --> 平面上的一个点

三维向量（x, y, z）：
  [1, 2, 3]  --> 3D空间中的一个点

==========================

Embedding 向量：通常 768维 / 1024维 / 1536维 / 3072维

例如 MiniMax Embedding 模型：
  "苹果"  -->  [0.12, -0.34, 0.56, -0.78, ...共1536个浮点数...]

每个数字 = 语义特征的一个维度
1536个数字 = 从1536个不同角度描述这个词的含义
维度越多，描述越精细（但也越占空间）
```

### 2. 向量的几何含义（画图理解）

```
假设我们只有2个维度（方便画图）：

                ↑ 维度2（水果程度）
                |
          苹果  ●
                |    ● 香蕉
                |
                |         ● 西瓜
                |
   ─────────────┼──────────────→ 维度1（甜度）
                |
                |
        手机 ●  |  ● 电脑
                |

观察：
  - 苹果、香蕉、西瓜 聚在上方（都是水果）
  - 手机、电脑 聚在下方（都是电子产品）
  - 水果和电子产品的向量方向完全不同

真实Embedding有1536个维度，无法画图
但原理一样：语义相似 = 空间中距离近
```

### 3. 语义空间的概念

```
把所有文字的向量放在一起，形成一片「语义宇宙」：

  - 所有动物词汇聚在一起（猫、狗、鸟、鱼...）
  - 所有编程词汇聚在一起（Java、Python、数据库...）
  - 所有情感词汇聚在一起（开心、悲伤、愤怒...）
  - 同义词几乎重合（快乐 ≈ 开心 ≈ 高兴）

空间中的「距离」= 语义的「相似度」
  距离越近 --> 语义越接近
  距离越远 --> 语义越不相关
```

---

## 三、文本转向量的完整流程

### 1. 单条文本向量化流程

```
【输入】"Java是面向对象的编程语言"

         ↓

【第一步：分词（Tokenize）】
  "Java" / "是" / "面向" / "对象" / "的" / "编程" / "语言"

         ↓

【第二步：每个词查向量表（Word Embedding）】
  Java    --> [0.23, -0.45, ...]
  是      --> [0.11, 0.22, ...]
  面向    --> [-0.34, 0.56, ...]
  对象    --> [0.45, -0.23, ...]
  ...

         ↓

【第三步：聚合所有词向量（Pooling）】
  所有词向量 --> 平均池化 / CLS标记 --> 一整个句子的向量

         ↓

【输出】[0.15, -0.12, 0.34, ...共1536维...]

  整个过程由 Embedding 模型一键完成
  你只需要：传入文本 --> 拿到向量数组
```

### 2. 批量文档向量化流程（生产环境）

```
Day22切好的文档片段：
  片段1: "Java是一门面向对象语言..."   --> [向量1]
  片段2: "JVM是Java虚拟机..."         --> [向量2]
  片段3: "Spring是企业级框架..."       --> [向量3]
       ...
  片段N: "Redis用于缓存..."           --> [向量N]

         ↓

全部存入向量数据库：
  每条记录 = 原文内容 + 对应向量（一起存，检索用向量，展示用原文）
```

### 3. Embedding 在 RAG 中的位置（必背）

```
【离线预处理阶段】（提前做好，只做一次）
  本地文档 --> Day22文本切片 --> Day23 Embedding向量化 --> 存入pgvector

【实时问答阶段】（用户每次提问都走）
  用户问题 --> Embedding向量化 --> pgvector余弦检索 --> 召回TopN片段
           --> 拼接到Prompt    --> 大模型LLM生成答案 --> 返回用户
```

---

## 四、余弦相似度原理（检索核心算法）

### 1. 什么是余弦相似度

```
衡量两个向量「方向」有多相似

取值范围 [-1, 1]：
  余弦相似度 = 1    --> 方向完全相同（语义完全一致）
  余弦相似度 = 0    --> 方向垂直（语义完全不相关）
  余弦相似度 = -1   --> 方向完全相反（语义相反）

计算公式：
                   A · B              分子：点积（对应位相乘后求和）
  cos(theta) = ───────────
                |A| x |B|            分母：两个向量长度的乘积
```

### 2. 手算举例（帮助理解）

```
向量A（苹果）：[0.8,  0.2, 0.1]
向量B（香蕉）：[0.7,  0.3, 0.1]
向量C（手机）：[-0.3, 0.8, 0.5]

第一步：算点积（A·B）
  A·B = 0.8*0.7 + 0.2*0.3 + 0.1*0.1 = 0.56 + 0.06 + 0.01 = 0.63

第二步：算向量长度
  |A| = sqrt(0.8^2 + 0.2^2 + 0.1^2) = sqrt(0.69) = 0.83
  |B| = sqrt(0.7^2 + 0.3^2 + 0.1^2) = sqrt(0.59) = 0.77

第三步：算余弦相似度
  cos(A,B) = 0.63 / (0.83 * 0.77) = 0.99   --> 非常相似（都是水果）

同理：
  cos(A,C) = cos(苹果,手机) = 0.15           --> 很不相似（不同类别）
```

### 3. 为什么用余弦而不是欧氏距离

```
欧氏距离 = 看「绝对距离」（两点之间直线距离）
余弦相似度 = 看「方向」（两个向量是否同向）

举例：
  向量A = [100, 0]   （很长的向量）
  向量B = [1, 0]     （很短的向量）

  欧氏距离 = 99（很远！）
  余弦相似度 = 1.0（完全一致！方向相同）

原因：
  Embedding模型输出的向量，长度可能不同
  但方向才代表「语义」
  所以语义检索用余弦相似度更准确

结论（背）：语义相似度任务，用余弦相似度
```

### 4. pgvector 中的余弦距离

```
pgvector 用的是「余弦距离」，不是「余弦相似度」

  余弦距离 = 1 - 余弦相似度

  余弦距离 = 0   --> 完全相似
  余弦距离 = 1   --> 完全不相关
  余弦距离 = 2   --> 完全相反

pgvector 操作符：
  <=>  余弦距离（值越小越相似，ORDER BY ASC）
  <->  欧氏距离
  <#>  内积距离

我们项目用的：
  ORDER BY embedding <=> '目标向量' ASC
  （按余弦距离升序 = 最相似的排前面）
```

---

## 五、SpringAI 整合 Embedding（完整可运行代码）

> 以下代码全部基于本项目 demo-springai 真实代码，可直接复制运行

### 1. Maven 依赖（pom.xml 关键部分）

```xml
<properties>
    <java.version>21</java.version>
    <spring-ai.version>1.0.0-M5</spring-ai.version>
</properties>

<!-- Spring AI BOM 统一版本管理 -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- MiniMax 大模型 Starter（包含 Chat + Embedding 能力） -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-minimax-spring-boot-starter</artifactId>
        <version>${spring-ai.version}</version>
    </dependency>

    <!-- DeepSeek 模型（备选 Embedding 方案，性价比高） -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-deepseek</artifactId>
        <version>1.1.0-M1-PLATFORM</version>
    </dependency>

    <!-- SpringAI 核心包 -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-core</artifactId>
    </dependency>

    <!-- pgvector 向量数据库 Starter（自动配置 VectorStore） -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
    </dependency>

    <!-- PostgreSQL 驱动 -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>compile</scope>
    </dependency>

    <!-- 文档解析（PDF/Word 读取） -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-tika-document-reader</artifactId>
    </dependency>

    <!-- Lombok（简化代码） -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>

<!-- 必须加 Spring Milestones 仓库（M5是里程碑版本，不在Maven中央仓库） -->
<repositories>
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>
```

### 2. application.yaml 配置

```yaml
spring:
  ai:
    # ===== MiniMax 模型配置 =====
    minimax:
      api-key: ${MINIMAX_API_KEY:你的MiniMax-API-Key}
      chat:
        options:
          model: MiniMax-M2.7         # 聊天模型
          temperature: 0.3
          max-tokens: 2048
      embedding:
        options:
          model: MiniMax-M2.7         # Embedding模型（MiniMax用同一个模型支持Embedding）

    # ===== DeepSeek 模型配置（备选） =====
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:你的DeepSeek-API-Key}
      chat:
        options:
          model: DeepSeek-V4-Flash
          temperature: 0.3
          max-tokens: 2048

    # ===== pgvector 向量数据库配置 =====
    vectorstore:
      pgvector:
        index-type: hnsw              # 索引类型：HNSW（推荐，速度快精度高）
        distance-type: COSINE_DISTANCE # 距离算法：余弦相似度（语义检索标配）
        dimensions: 1536              # 向量维度（必须与Embedding模型输出一致！）
        initialize-schema: false      # 是否自动建表（false=手动建，生产推荐）
        schema-name: public           # PostgreSQL Schema
        table-name: vector_store      # 向量表名
        hnsw:
          m: 16                       # HNSW: 每个节点最大连接数
          ef-construction: 200        # HNSW: 构建索引时的搜索范围
        similarity-threshold: 0.7     # 默认相似度阈值（低于此值不召回）
        top-k: 5                      # 默认召回数量

  # ===== PostgreSQL 数据源 =====
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://localhost:5432/vector_db
    username: myuser
    password: 525826

# 日志配置（调试Embedding请求用）
logging:
  level:
    org.springframework.ai: DEBUG
    org.springframework.jdbc: DEBUG
```

### 3. 配置项名词解释（零基础必看）

```
spring.ai.minimax.api-key
  --> MiniMax 平台申请的密钥，用于调用API（类似密码）

spring.ai.minimax.embedding.options.model
  --> 用哪个模型做Embedding，MiniMax-M2.7 同时支持聊天和向量化

spring.ai.vectorstore.pgvector.dimensions = 1536
  --> 向量维度，必须和Embedding模型输出一致！
  --> MiniMax 输出1536维，这里就填1536
  --> 如果用DeepSeek（1024维），这里要改成1024

spring.ai.vectorstore.pgvector.distance-type = COSINE_DISTANCE
  --> 用余弦距离做相似度计算（语义检索的标准选择）

spring.ai.vectorstore.pgvector.index-type = hnsw
  --> HNSW = 分层可导航小世界图（一种向量索引算法）
  --> 特点：检索速度快 + 精度高，生产环境首选
  --> 另一个选择：ivfflat（速度稍快但精度稍低）

spring.ai.vectorstore.pgvector.initialize-schema = false
  --> false = 需要手动建表（生产推荐，可控）
  --> true = SpringAI自动建表（开发方便，但不可控）
```

---

## 六、Embedding 配置类（多模型切换）

### 1. EmbeddingModelConfig.java（项目真实代码）

```java
package com.jianbo.springai.config;

import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.minimax.MiniMaxEmbeddingModel;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Embedding 模型配置类
 *
 * 作用：注册 Embedding 模型 Bean，供 EmbeddingService 注入使用
 * 支持多模型：MiniMax（主力） + DeepSeek（备选）
 *
 * 关键知识点：
 *   - EmbeddingModel 是 SpringAI 的统一接口（面向接口编程）
 *   - 不管底层是 MiniMax、OpenAI 还是 DeepSeek，上层代码不用改
 *   - @Primary 标记默认使用哪个模型
 */
@Configuration
public class EmbeddingModelConfig {

    // 从 application.yaml 读取 API Key
    @Value("${spring.ai.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${spring.ai.minimax.api-key:}")
    private String minimaxApiKey;

    /**
     * MiniMax Embedding 模型（主力模型）
     *
     * 特点：中文能力强，企业级服务稳定
     * 维度：1536维
     *
     * @Primary 注解的作用：
     *   当有多个 EmbeddingModel Bean 时，默认注入这个
     *   相当于：@Autowired 时优先选这个
     */
    @Bean
    @Primary
    public EmbeddingModel miniMaxEmbeddingModel() {
        // 1. 用 API Key 创建 MiniMax API 客户端
        MiniMaxApi miniMaxApi = new MiniMaxApi(minimaxApiKey);
        // 2. 基于 API 客户端创建 Embedding 模型
        return new MiniMaxEmbeddingModel(miniMaxApi);
    }

    /**
     * DeepSeek Embedding 模型（备选模型）
     *
     * 特点：性价比高，兼容 OpenAI 协议
     * 维度：1024维（注意！和MiniMax不同）
     *
     * 使用时需要指定 Bean 名称：
     *   @Qualifier("deepSeekEmbeddingModel")
     *
     * 注意：如果切换到DeepSeek，pgvector的dimensions要改成1024
     */
    @Bean
    public EmbeddingModel deepSeekEmbeddingModel() {
        DeepSeekApi api = new DeepSeekApi.Builder()
                .apiKey(deepseekApiKey)
                .build();
        // TODO: 替换为 DeepSeek 专用 Embedding 实现
        // DeepSeek 兼容 OpenAI Embedding 协议
        return new MiniMaxEmbeddingModel(new MiniMaxApi(minimaxApiKey));
    }
}
```

### 2. 配置类关键注解解释

```
@Configuration
  --> 告诉Spring：这是一个配置类，里面的 @Bean 方法会创建对象放入容器

@Bean
  --> 告诉Spring：把这个方法的返回值注册为一个Bean（可被其他类注入使用）

@Primary
  --> 当有多个同类型Bean时，默认注入这个
  --> 本项目有两个 EmbeddingModel：miniMax 和 deepSeek
  --> @Primary 标在 miniMax 上，所以 @Autowired 默认用 miniMax

@Value("${spring.ai.minimax.api-key:}")
  --> 从 yaml 配置文件读取值
  --> 冒号后面是默认值（这里默认空字符串）

EmbeddingModel（接口）
  --> SpringAI 统一的 Embedding 接口
  --> MiniMaxEmbeddingModel 实现了这个接口
  --> 面向接口编程：Service 层注入 EmbeddingModel，不关心具体是哪个厂商
```

---

## 七、单条文本 + 批量文本向量化（EmbeddingService 完整代码）

### 1. EmbeddingService.java（项目真实代码，可直接运行）

```java
package com.jianbo.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Embedding 向量化服务（核心工具类）
 *
 * 职责：
 *   1. 单条文本 --> 向量（用户提问时用）
 *   2. 批量文本 --> 向量列表（文档入库时用）
 *   3. 计算两段文本的余弦相似度（测试/调试用）
 *
 * 注入的是 EmbeddingModel 接口（不是具体实现类）
 *   --> 底层用哪个模型，由 EmbeddingModelConfig 的 @Primary 决定
 *   --> 换模型不用改这里的代码（面向接口编程的好处）
 */
@Service
@Slf4j                    // Lombok: 自动生成 log 对象（log.info/log.debug）
@RequiredArgsConstructor  // Lombok: 自动生成构造函数注入（替代 @Autowired）
public class EmbeddingService {

    /**
     * SpringAI M5版本使用 EmbeddingModel 接口
     * （之前的老版本叫 EmbeddingClient，面试注意区别！）
     *
     * @RequiredArgsConstructor 会自动生成：
     *   public EmbeddingService(EmbeddingModel embeddingModel) {
     *       this.embeddingModel = embeddingModel;
     *   }
     */
    private final EmbeddingModel embeddingModel;

    // ==================== 单条文本向量化 ====================

    /**
     * 单条文本向量化
     *
     * @param text 输入文本（如："Java是面向对象的编程语言"）
     * @return float[] 向量数组（如：[0.12, -0.34, 0.56, ...] 共1536维）
     *
     * 使用场景：
     *   - 用户提问时，把问题转成向量，用于检索
     *   - 单个文档片段向量化
     *
     * 调用示例：
     *   float[] vector = embeddingService.embed("Java是什么");
     *   // vector.length = 1536
     */
    public float[] embed(String text) {
        log.debug("开始向量化, 文本长度: {}字符", text.length());
        long startTime = System.currentTimeMillis();

        // 1. 构建 EmbeddingRequest 请求对象
        //    参数1: List<String> 文本列表（单条也要包成List）
        //    参数2: EmbeddingOptions 选项（null = 用yaml默认配置）
        EmbeddingRequest request = new EmbeddingRequest(List.of(text), null);

        // 2. 调用 Embedding 模型 API
        //    底层会发HTTP请求到 MiniMax 服务器
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
     *
     * 为什么要批量？
     *   - 单条调用：每条文本发一次HTTP请求（慢！）
     *   - 批量调用：N条文本打包成一次HTTP请求（快N倍！）
     *
     * 注意事项：
     *   - 大部分Embedding API 单次批量上限约 96~256 条
     *   - 如果超过上限，需要分批处理（见下方 embedBatchSafe）
     *
     * 调用示例：
     *   List<String> chunks = TextSplitterUtil.splitText(longDocument);
     *   List<float[]> vectors = embeddingService.embedBatch(chunks);
     *   // chunks.size() == vectors.size()（一一对应）
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
        List<float[]> vectorArray = results.stream()
                .map(Embedding::getOutput)  // 提取每个结果的 float[]
                .toList();

        long cost = System.currentTimeMillis() - startTime;
        log.info("批量向量化完成, 耗时: {}ms, 向量数量: {}", cost, vectorArray.size());

        return vectorArray;
    }

    // ==================== 大批量安全向量化（防限流） ====================

    /**
     * 大批量分批向量化（防API限流，生产必备）
     *
     * @param texts     所有文本片段（可能有上千条）
     * @param batchSize 每批数量（推荐50~100）
     * @return 所有向量（顺序和输入文本一一对应）
     *
     * 原理：
     *   texts = 1000条，batchSize = 50
     *   --> 分成 20 批，每批 50 条
     *   --> 第1批 [0,49]  --> 向量化 --> 结果加入总列表
     *   --> 第2批 [50,99] --> 向量化 --> 结果加入总列表
     *   --> ...
     *   --> 第20批 [950,999] --> 全部完成
     */
    public List<float[]> embedBatchSafe(List<String> texts, int batchSize) {
        java.util.List<float[]> allVectors = new java.util.ArrayList<>();

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
     *
     * 使用场景：
     *   - 测试两段文本是否语义相近
     *   - 验证Embedding模型效果
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
     * 公式：cos(theta) = (A·B) / (|A| x |B|)
     *
     * A·B = 点积 = sum(a[i] * b[i])
     * |A| = 向量长度 = sqrt(sum(a[i]^2))
     */
    private double cosineSimilarity(float[] v1, float[] v2) {
        // 防御：维度必须一致
        if (v1.length != v2.length) {
            throw new IllegalArgumentException(
                "向量维度不一致! v1=" + v1.length + ", v2=" + v2.length);
        }

        double dotProduct = 0.0;  // 点积（分子）
        double norm1 = 0.0;       // v1的长度平方
        double norm2 = 0.0;       // v2的长度平方

        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];       // 对应位相乘后累加
            norm1 += Math.pow(v1[i], 2);       // 每个分量的平方累加
            norm2 += Math.pow(v2[i], 2);
        }

        // 防止除以零
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
```

### 2. SpringAI Embedding 核心类关系（必背）

```
EmbeddingModel（接口）
  |
  |-- MiniMaxEmbeddingModel（MiniMax实现）    <-- 我们项目用这个
  |-- OpenAiEmbeddingModel（OpenAI实现）
  |-- OllamaEmbeddingModel（本地Ollama实现）
  |-- ...更多厂商实现

调用链路：
  EmbeddingService
    --> embeddingModel.call(EmbeddingRequest)   // 调用模型
    --> 返回 EmbeddingResponse                  // 响应对象
    --> response.getResult().getOutput()         // 提取单条向量 float[]
    --> response.getResults()                    // 提取批量 List<Embedding>

关键类：
  EmbeddingRequest  = 请求（包含文本列表 + 选项）
  EmbeddingResponse = 响应（包含向量结果列表）
  Embedding         = 单个向量结果（包含 float[] 和 index）
```

### 3. EmbeddingService 四个方法速查

```
  embed(String text)
    --> 单条文本向量化
    --> 用于：用户提问转向量

  embedBatch(List<String> texts)
    --> 批量文本向量化（一次HTTP请求）
    --> 用于：文档切片后批量入库

  embedBatchSafe(List<String> texts, int batchSize)
    --> 大批量安全向量化（分批 + 防限流）
    --> 用于：超大文档（1000+片段）

  cosineSimilarity(String text1, String text2)
    --> 计算两段文本的语义相似度
    --> 用于：测试 / 验证 / 调试
```

---

## 八、结合 Day22 文本切片 完整向量化流程

### 1. 切片 + 向量化 一条龙调用

```java
package com.jianbo.springai.service;

import com.jianbo.springai.utils.TextSplitterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档向量化服务（切片 + Embedding 一条龙）
 *
 * 完整流程：
 *   原始长文档 --> Day22文本清洗 --> 固定长度切片 --> Day23批量向量化
 *
 * 衔接关系：
 *   Day22: TextSplitterUtil.splitText() 把长文档切成 List<String>
 *   Day23: EmbeddingService.embedBatch() 把 List<String> 转成 List<float[]>
 *   Day24: 把向量存入 pgvector，实现语义检索（下节内容）
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentEmbeddingService {

    private final EmbeddingService embeddingService;

    /**
     * 文档完整向量化（切片 + 向量化）
     *
     * @param rawDocument 原始文档全文（如：一篇PDF的全部文字）
     * @return 向量化结果（片段列表 + 向量列表，一一对应）
     */
    public DocumentEmbeddingResult processDocument(String rawDocument) {
        // ===== 第一步：文本切片（Day22 的工具类） =====
        List<String> chunks = TextSplitterUtil.splitText(rawDocument);
        log.info("文本切片完成, 共 {} 段", chunks.size());

        // ===== 第二步：批量向量化（Day23 核心） =====
        List<float[]> vectors;
        if (chunks.size() <= 100) {
            // 片段少，直接批量
            vectors = embeddingService.embedBatch(chunks);
        } else {
            // 片段多，分批防限流
            vectors = embeddingService.embedBatchSafe(chunks, 50);
        }
        log.info("向量化完成, 共 {} 个向量, 每个 {} 维",
                vectors.size(), vectors.get(0).length);

        // ===== 第三步：封装结果 =====
        return new DocumentEmbeddingResult(chunks, vectors);
    }

    /**
     * 文档向量化结果（片段和向量一一对应）
     *
     * chunks.get(0)  对应  vectors.get(0)
     * chunks.get(1)  对应  vectors.get(1)
     */
    public record DocumentEmbeddingResult(
            List<String> chunks,
            List<float[]> vectors
    ) {}
}
```

### 2. 切片 + 向量化 流程图（手绘笔记图）

```
原始长文档（如5000字的技术文章）
        |
        v
  ┌─────────────────────────┐
  │  Day22 文本清洗          │
  │  去空格、去乱码、统一格式 │
  └─────────────────────────┘
        |
        v
  ┌─────────────────────────┐
  │  Day22 固定长度切片       │
  │  每500字一段，重叠50字    │
  │  得到 N 个干净文本片段    │
  └─────────────────────────┘
        |
        v
  ┌─────────────────────────────────────┐
  │  Day23 Embedding 向量化              │
  │  embeddingService.embedBatch(chunks) │
  │  N 个文本 --> N 个 float[1536]       │
  └─────────────────────────────────────┘
        |
        v
  ┌─────────────────────────────────────┐
  │  Day23 存入 PostgreSQL + pgvector    │
  │  每条记录 = 原文 + 向量              │
  │  （本章第九节详细代码）               │
  └─────────────────────────────────────┘
        |
        v
  ┌─────────────────────────────────────┐
  │  Day24 向量检索（下节内容）           │
  │  用户问题向量 <=> 库中向量            │
  │  余弦距离排序 --> 召回 Top5           │
  └─────────────────────────────────────┘
```

---

## 九、PostgreSQL + pgvector 向量入库（完整代码）

### 1. 为什么选 pgvector（企业选型）

```
向量数据库对比：

  Milvus        专业向量库，功能最强大，但部署复杂、要额外运维
  Elasticsearch 自带向量能力，已有ES的团队可以用
  Redis Vector  简单，内存存储，适合小数据量
  Chroma        Python生态，本地测试方便，Java不推荐
  pgvector      PostgreSQL插件，SQL + 向量 二合一

企业选择逻辑：
  已有 PostgreSQL --> 直接装 pgvector 插件 --> 零额外运维成本
  SpringAI 官方适配 --> 开箱即用
  会SQL就能做向量检索 --> 零学习成本

结论：Java + SpringAI 项目，pgvector 是最优解
```

### 2. 手动建表 SQL（生产推荐）

```sql
-- ===== 1. 安装 pgvector 扩展 =====
CREATE EXTENSION IF NOT EXISTS vector;

-- ===== 2. 创建 SpringAI 标准向量表 =====
-- 表名: vector_store（和 application.yaml 中 table-name 一致）
CREATE TABLE IF NOT EXISTS vector_store (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),  -- UUID主键
    content   TEXT,                                        -- 原始文本内容
    metadata  JSON,                                        -- 元数据JSON
    embedding VECTOR(1536)                                 -- 向量（1536维）
);

-- ===== 3. 创建 HNSW 向量索引（加速余弦检索） =====
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

-- 参数解释：
--   vector_cosine_ops: 使用余弦距离（和yaml配置一致）
--   m = 16:            每个节点最多16个邻居（越大越精准，越占内存）
--   ef_construction = 200: 建索引时搜索范围（越大质量越好，速度越慢）
```

### 3. 表结构字段详解

```
vector_store 表各字段含义：

  id（UUID）
    --> 每条记录的唯一标识，gen_random_uuid() 自动生成
    --> 用 UUID 而不是自增ID（分布式友好）

  content（TEXT）
    --> 原始文本内容（人类可读的文字）
    --> 比如："Java是一门面向对象的编程语言，由Sun公司..."

  metadata（JSON）
    --> 元数据，记录文本的来源信息
    --> 比如：{"source": "java_guide.pdf", "chunk_index": "5"}

  embedding（VECTOR(1536)）
    --> pgvector 专用类型，存储1536维浮点数向量
    --> 检索时通过这个字段计算余弦距离
```

### 4. SpringAI VectorStore 入库代码（推荐方式）

```java
package com.jianbo.springai.service;

import com.jianbo.springai.utils.TextSplitterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 向量入库服务（SpringAI VectorStore 方式 -- 推荐）
 *
 * 核心依赖：spring-ai-pgvector-store-spring-boot-starter
 *   --> 自动配置 PgVectorStore Bean（实现了 VectorStore 接口）
 *   --> 自动处理：向量化 + 入库（一步到位！）
 *
 * VectorStore.add(documents) 时 SpringAI 会自动：
 *   1. 调用 EmbeddingModel 把文本转成向量
 *   2. 把 content + embedding + metadata 一起存入 PostgreSQL
 *   你不需要手动调用 embeddingService.embed()！
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VectorStoreService {

    /**
     * SpringAI 的 VectorStore 接口
     * 实际注入的是 PgVectorStore（由 pgvector starter 自动配置）
     * 内部已持有 EmbeddingModel，add() 时自动向量化
     */
    private final VectorStore vectorStore;

    /**
     * 文档入库（切片 + 向量化 + 存入pgvector 一条龙）
     *
     * @param rawText    原始文档全文
     * @param sourceName 文档来源名称（如："java_guide.pdf"）
     * @return 入库的切片数量
     */
    public int importDocument(String rawText, String sourceName) {
        // ===== 第一步：文本切片（复用 Day22 工具类） =====
        List<String> chunks = TextSplitterUtil.splitText(rawText);
        log.info("文本切片完成: {} 段, 来源: {}", chunks.size(), sourceName);

        // ===== 第二步：封装成 SpringAI Document 对象 =====
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            // Document 参数1: 文本内容（会被向量化）
            // Document 参数2: 元数据Map（不会被向量化，但存入数据库）
            Document doc = new Document(
                chunks.get(i),
                Map.of(
                    "source", sourceName,
                    "chunk_index", String.valueOf(i),
                    "total_chunks", String.valueOf(chunks.size())
                )
            );
            documents.add(doc);
        }

        // ===== 第三步：一键入库（自动 Embedding + INSERT） =====
        vectorStore.add(documents);
        log.info("向量入库完成: {} 条记录已存入 pgvector", documents.size());

        return documents.size();
    }

    /**
     * 批量文档入库
     * @param documentMap  Map<文档来源名, 文档全文>
     * @return 总入库切片数量
     */
    public int importDocuments(Map<String, String> documentMap) {
        int totalCount = 0;
        for (var entry : documentMap.entrySet()) {
            int count = importDocument(entry.getValue(), entry.getKey());
            totalCount += count;
        }
        log.info("批量入库完成, 共 {} 个文档, {} 条切片", documentMap.size(), totalCount);
        return totalCount;
    }

    /**
     * 删除指定文档的所有向量（文档更新时用：先删旧的再导新的）
     */
    public void deleteDocuments(List<String> documentIds) {
        vectorStore.delete(documentIds);
        log.info("已删除 {} 条向量记录", documentIds.size());
    }
}
```

### 5. VectorStore 入库原理详解（零基础必看）

```
核心只有一行：vectorStore.add(documents);

这一行的背后，SpringAI 自动完成了：

  第1步：遍历 documents 列表
  第2步：对每个 Document 的 content 调用 EmbeddingModel.call()
  第3步：得到 float[1536] 向量数组
  第4步：拼接 SQL:
         INSERT INTO vector_store (id, content, metadata, embedding)
         VALUES (uuid, '原始文本', '{"source":"xx.pdf"}', '[0.12,-0.34,...]')
  第5步：执行 SQL，数据落盘 PostgreSQL

所以你完全不需要：
  x 手动调用 embeddingService.embed()
  x 手动拼接 SQL
  x 手动处理 float[] 转字符串
  VectorStore 全帮你做了！

Document 对象三要素：
  - content（必须）：文本内容，会被向量化
  - metadata（可选）：元数据，不会被向量化，但会存入数据库
  - id（自动生成）：UUID，唯一标识
```

### 6. 手动方式入库（原生JDBC，自定义表结构）

> 适用场景：需要自定义表结构、或存更多业务字段时使用

```java
package com.jianbo.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 手动向量入库（原生JDBC方式）
 *
 * 自定义表 my_documents 建表SQL：
 * CREATE TABLE my_documents (
 *     id          BIGSERIAL PRIMARY KEY,
 *     doc_title   VARCHAR(200),
 *     chunk_index INTEGER,
 *     content     TEXT NOT NULL,
 *     embedding   VECTOR(1536) NOT NULL,
 *     source      VARCHAR(100),
 *     created_at  TIMESTAMP DEFAULT NOW()
 * );
 * CREATE INDEX idx_my_docs_embedding
 *     ON my_documents USING hnsw (embedding vector_cosine_ops);
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ManualVectorStorageService {

    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 手动入库：切片文本 --> 向量化 --> INSERT
     */
    @Transactional
    public int importChunks(String docTitle, String source, List<String> chunks) {
        // 1. 批量向量化
        List<float[]> vectors = embeddingService.embedBatch(chunks);

        // 2. 逐条INSERT
        String sql = """
            INSERT INTO my_documents (doc_title, chunk_index, content, embedding, source)
            VALUES (?, ?, ?, ?::vector, ?)
            """;

        for (int i = 0; i < chunks.size(); i++) {
            jdbcTemplate.update(sql,
                docTitle,                              // 文档标题
                i,                                     // 片段序号
                chunks.get(i),                         // 原始文本
                floatArrayToPgVector(vectors.get(i)),  // 向量字符串
                source                                 // 来源
            );
        }

        log.info("手动入库完成: {} 条, 文档: {}", chunks.size(), docTitle);
        return chunks.size();
    }

    /**
     * float[] 转 pgvector 格式字符串
     *
     * 输入：float[]{0.12f, -0.34f, 0.56f}
     * 输出："[0.12,-0.34,0.56]"
     *
     * SQL中用 ?::vector 做类型转换
     */
    private String floatArrayToPgVector(float[] vector) {
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

### 7. 两种入库方式对比

```
方式一：VectorStore（推荐）
  优点：代码少、自动Embedding、SpringAI标准
  缺点：表结构固定（id/content/metadata/embedding）
  适合：标准RAG项目、快速开发

方式二：手动JDBC
  优点：完全自定义表结构、可加业务字段
  缺点：代码多、需手动调Embedding、手动拼SQL
  适合：复杂业务、自定义表结构

生产建议：
  优先用 VectorStore 方式
  如果需要额外字段 --> 元数据放 metadata JSON 里
  实在不够用 --> 再用手动方式
```

---

## 十、完整调用示例（Controller + 单元测试）

### 1. Embedding 测试 Controller

```java
package com.jianbo.springai.controller;

import com.jianbo.springai.service.EmbeddingService;
import com.jianbo.springai.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding + 向量入库 测试接口
 *
 * 测试地址（项目端口 12115）：
 *   单条向量化：GET  http://localhost:12115/embedding/single?text=Java是什么
 *   相似度：   GET  http://localhost:12115/embedding/similarity?t1=Java&t2=Python
 *   批量向量化：POST http://localhost:12115/embedding/batch
 *   文档入库：  POST http://localhost:12115/embedding/import?source=test.txt
 */
@RestController
@RequestMapping("/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    /**
     * 测试1：单条文本向量化
     * GET /embedding/single?text=Java是面向对象的编程语言
     */
    @GetMapping("/single")
    public Map<String, Object> singleEmbed(@RequestParam String text) {
        float[] vector = embeddingService.embed(text);

        Map<String, Object> result = new HashMap<>();
        result.put("text", text);
        result.put("dimensions", vector.length);
        result.put("vector_preview",
            Arrays.toString(Arrays.copyOf(vector, 5)) + "...");
        return result;
    }

    /**
     * 测试2：计算两段文本的语义相似度
     * GET /embedding/similarity?t1=Java编程&t2=Python编程
     */
    @GetMapping("/similarity")
    public Map<String, Object> similarity(
            @RequestParam String t1, @RequestParam String t2) {
        float sim = embeddingService.cosineSimilarity(t1, t2);

        Map<String, Object> result = new HashMap<>();
        result.put("text1", t1);
        result.put("text2", t2);
        result.put("similarity", sim);
        result.put("judge", sim > 0.8 ? "高度相似"
                          : sim > 0.5 ? "中等相似" : "不太相似");
        return result;
    }

    /**
     * 测试3：批量文本向量化
     * POST /embedding/batch
     * Body: ["Java是编程语言", "Python适合数据分析"]
     */
    @PostMapping("/batch")
    public Map<String, Object> batchEmbed(@RequestBody List<String> texts) {
        List<float[]> vectors = embeddingService.embedBatch(texts);

        Map<String, Object> result = new HashMap<>();
        result.put("input_count", texts.size());
        result.put("output_count", vectors.size());
        result.put("dimensions", vectors.get(0).length);
        return result;
    }

    /**
     * 测试4：文档入库（切片+向量化+存pgvector）
     * POST /embedding/import?source=test_doc.txt
     * Body: "Java是一门面向对象的编程语言...（长文本）"
     */
    @PostMapping("/import")
    public Map<String, Object> importDoc(
            @RequestBody String content,
            @RequestParam(defaultValue = "test_doc.txt") String source) {
        int count = vectorStoreService.importDocument(content, source);

        Map<String, Object> result = new HashMap<>();
        result.put("source", source);
        result.put("chunks_imported", count);
        result.put("status", "success");
        return result;
    }
}
```

### 2. 单元测试

```java
package com.jianbo.springai;

import com.jianbo.springai.service.EmbeddingService;
import com.jianbo.springai.service.VectorStoreService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Embedding + 向量入库 完整测试
 *
 * 运行前确保：
 *   1. MiniMax API Key 已配置到环境变量或yaml
 *   2. PostgreSQL + pgvector 已启动
 *   3. vector_store 表已创建（见第九节建表SQL）
 */
@SpringBootTest
public class EmbeddingTest {

    @Resource
    private EmbeddingService embeddingService;

    @Resource
    private VectorStoreService vectorStoreService;

    /**
     * 测试1：单条文本向量化
     */
    @Test
    void testSingleEmbed() {
        String text = "Java是世界上最流行的编程语言之一";
        float[] vector = embeddingService.embed(text);

        System.out.println("========== 单条文本向量化 ==========");
        System.out.println("文本: " + text);
        System.out.println("向量维度: " + vector.length);
        System.out.println("前5维: " + vectorPreview(vector, 5));
    }

    /**
     * 测试2：批量文本向量化
     */
    @Test
    void testBatchEmbed() {
        List<String> chunks = List.of(
            "Java是一种面向对象的编程语言",
            "Python适合数据分析和机器学习",
            "Redis是高性能的内存数据库",
            "Spring是Java最流行的企业级框架"
        );
        List<float[]> vectors = embeddingService.embedBatch(chunks);

        System.out.println("========== 批量文本向量化 ==========");
        System.out.println("输入文本数: " + chunks.size());
        System.out.println("输出向量数: " + vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            System.out.println("片段" + i + " 维度: " + vectors.get(i).length);
        }
    }

    /**
     * 测试3：语义相似度验证
     */
    @Test
    void testSimilarity() {
        System.out.println("========== 语义相似度测试 ==========");

        // 相似文本（预期 > 0.8）
        float sim1 = embeddingService.cosineSimilarity("Java编程", "Java开发");
        System.out.println("Java编程 vs Java开发: " + sim1);

        // 同类文本（预期 0.5~0.8）
        float sim2 = embeddingService.cosineSimilarity("Java编程", "Python编程");
        System.out.println("Java编程 vs Python编程: " + sim2);

        // 无关文本（预期 < 0.5）
        float sim3 = embeddingService.cosineSimilarity("Java编程", "今天天气很好");
        System.out.println("Java编程 vs 今天天气很好: " + sim3);
    }

    /**
     * 测试4：文档完整入库（切片+向量化+pgvector）
     */
    @Test
    void testImportDocument() {
        String content = """
            Java是一门面向对象的编程语言，由Sun公司于1995年发布。
            Java具有跨平台、安全性高、面向对象、多线程等特性。
            Java广泛应用于企业级应用、Android开发、大数据等领域。
            JVM是Java虚拟机，是Java跨平台的核心实现。
            Spring是Java最流行的企业级开发框架，简化了Java开发。
            MyBatis是Java生态中的持久层框架，用于数据库操作。
            Redis是一款高性能的内存数据库，常用于缓存和分布式锁。
            PostgreSQL是强大的关系型数据库，配合pgvector可做向量检索。
            """;

        int count = vectorStoreService.importDocument(content, "java_knowledge.txt");

        System.out.println("========== 文档入库完成 ==========");
        System.out.println("入库切片数: " + count);
    }

    // ========== 辅助方法 ==========

    private String vectorPreview(float[] vector, int limit) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(limit, vector.length); i++) {
            sb.append(String.format("%.4f", vector[i]));
            if (i < limit - 1) sb.append(", ");
        }
        sb.append(", ...]");
        return sb.toString();
    }
}
```

---

## 十一、向量维度与模型选型

### 1. 维度与精度、存储的关系

| 模型 | 向量维度 | 精度 | 单条存储 | 速度 | 价格 |
|------|---------|------|---------|------|------|
| text-embedding-3-small | 1536 | 中 | ~6KB | 快 | 低 |
| text-embedding-3-large | 3072 | 高 | ~12KB | 慢 | 高 |
| MiniMax-M2.7 | 1536 | 中高 | ~6KB | 快 | 中 |
| DeepSeek-embed | 1024 | 中 | ~4KB | 快 | 最低 |

### 2. 维度选择建议

```
生产环境推荐：
  - 通用场景：1536维（够用且高效，MiniMax/OpenAI默认）
  - 预算紧张：1024维（DeepSeek，性价比最高）
  - 高精度场景：3072维（成本翻倍、检索变慢，一般不用）

关键：yaml 中 dimensions 必须和模型输出一致！
  MiniMax  --> dimensions: 1536
  DeepSeek --> dimensions: 1024
  不一致会报错！
```

---

## 十二、面试必背总结

### 1. Embedding 是什么（一句话）

```
Embedding = 把文字转成固定长度浮点数数组的模型
作用：机器不认字，只认数字，Embedding 做翻译
```

### 2. 向量的核心特性（背4条）

```
1. 语义相似 --> 向量距离近（余弦相似度高）
2. 语义无关 --> 向量距离远
3. 维度越高 --> 表达能力越强（但更占空间）
4. Embedding 是单向的（文字-->向量），不可逆（向量不能还原成文字）
```

### 3. 余弦相似度（背公式和含义）

```
公式：cos(theta) = (A·B) / (|A| x |B|)

含义：
  - 分子：两个向量的点积（对应位相乘后相加）
  - 分母：两个向量长度的乘积
  - 结果：-1 到 1，越接近1越相似
  - pgvector 用余弦距离 = 1 - 余弦相似度（越小越相似）
```

### 4. RAG 中 Embedding 的位置（背流程）

```
离线阶段：文档 --> 切片 --> Embedding --> pgvector
实时阶段：用户问题 --> Embedding --> 向量检索 --> 召回TopN --> Prompt --> LLM
```

### 5. SpringAI Embedding 关键类（背API）

```
EmbeddingModel     --> 统一接口（面向接口编程）
EmbeddingRequest   --> 请求对象（文本列表 + 选项）
EmbeddingResponse  --> 响应对象（向量结果列表）
Embedding          --> 单个结果（float[] + index）
VectorStore        --> 向量库接口（add/delete/search）
PgVectorStore      --> pgvector 实现（自动Embedding+入库）

M5版本叫 EmbeddingModel，老版本叫 EmbeddingClient
```

### 6. 生产注意要点（背5条）

```
1. 切片大小：500字左右（太大向量不准，太小丢失上下文）
2. 重叠区域：50字左右（防止句子被切断）
3. 批量向量化：注意API单次上限，超过要分批
4. 向量入库：原文 + 向量一起存（检索用向量，展示用原文）
5. 维度一致：yaml的dimensions必须和模型输出一致（1536/1024）
```

### 7. VectorStore vs 手动JDBC（背选型）

```
VectorStore（推荐）：代码少、自动Embedding、标准表结构
手动JDBC：自定义表结构、可加业务字段、代码多

面试回答：优先VectorStore，复杂业务用JDBC
```

---

## 下节预告：Day24 向量检索

```
入库完成后，下一步就是检索：

  用户提问 --> 向量化 --> pgvector 余弦检索 --> 召回最相似的 TopN
           --> 拼接到 Prompt --> 大模型生成答案

核心问题（下节解决）：
  1. 如何用 VectorStore 做语义检索？
  2. HNSW / IVFFlat 向量索引算法原理？
  3. 如何调优：相似度阈值、TopK、重排序？
  4. 完整 RAG 问答链路代码
```

---
