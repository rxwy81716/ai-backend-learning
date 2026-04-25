##  一、衔接回顾：从检索到生成

### 1. 先回顾：我们到哪了

```
Day22: 长文档 --> 文本切片 --> N 个文本片段
Day23: N 个文本片段 --> Embedding模型 --> N 个 float[1536] 向量
Day24: N 个向量 --> 存入数据库（PG / ES）--> 等待检索
Day25: 用户提问 --> 向量化 --> 数据库检索 --> 召回相似内容
Day26: 召回的文档片段 --> 拼接 Prompt --> 大模型生成答案  <-- 今天做这一步
```



### 2. Day25 做完了什么

```
Day25 已经实现了：
  ✓ VectorSearchService  — PG 向量检索
  ✓ EsVectorSearchService — ES 向量检索
  ✓ EsKnnSearchService   — ES 原生 KNN / 混合检索
  ✓ SearchRequest + Filter 过滤
  ✓ TopK + Threshold 调优

但只拿到了"相关文档片段"，还没有：
  ✗ 把片段喂给大模型
  ✗ 让大模型基于这些片段回答用户问题
  ✗ 流式返回答案

这就是 Day26 要做的事 —— 打通最后一公里
```



### 3. RAG 全链路架构图（核心！背下来）

```
用户提问："Java和Python有什么区别？"
         │
         ▼
┌─────────────────────────────┐
│  ① Embedding 向量化          │  Day23 已实现
│  问题 --> float[1536]        │
└─────────┬───────────────────┘
          │
          ▼
┌─────────────────────────────┐
│  ② Vector Search 向量检索    │  Day25 已实现
│  在 PG/ES 中找 TopK 相似片段 │
│  召回 3~5 条文档片段          │
└─────────┬───────────────────┘
          │
          ▼
┌─────────────────────────────┐
│  ③ Prompt 拼接（今天重点）   │  <-- Day26 核心步骤 1
│                             │
│  System: "你是AI知识助手..."  │
│  Context: [召回的文档片段]    │
│  Question: "Java和Python..."│
│                             │
│  三段拼成一个完整 Prompt      │
└─────────┬───────────────────┘
          │
          ▼
┌─────────────────────────────┐
│  ④ ChatClient 调用大模型     │  <-- Day26 核心步骤 2
│  把拼好的 Prompt 发给 LLM    │
│  LLM 基于上下文生成答案      │
└─────────┬───────────────────┘
          │
          ▼
┌─────────────────────────────┐
│  ⑤ 返回答案                  │  <-- Day26 核心步骤 3
│  同步返回 / 流式 SSE 返回    │
└─────────────────────────────┘
```



### 4. 为什么要用 RAG，不直接问大模型？

```
直接问大模型的问题：

1. 知识过时：大模型训练数据有截止日期，不知道最新内容
   例：GPT-4 不知道你公司2024年的内部文档

2. 会编造答案（幻觉）：没有依据时，大模型会"一本正经胡说八道"
   例：问"我们公司请假流程"，它会编一个看起来很合理但完全错误的流程

3. 无法访问私有数据：大模型不知道你的数据库里存了什么

RAG 的解决方案：
  先从你的知识库里检索相关内容
  再把检索到的内容作为"参考资料"喂给大模型
  大模型基于这些真实资料回答 —— 有据可依，不再胡编

一句话总结：
  RAG = 检索增强生成 = 先查后答 = 开卷考试
  直接问 = 闭卷考试（容易瞎编）
```



------

## 二、Prompt 拼接原理（核心概念）

### 1. Prompt 三段式结构

```
一个完整的 RAG Prompt 由三部分组成：

┌─────────────────────────────────────────────┐
│  System Prompt（系统提示 / 角色设定）          │
│                                             │
│  "你是一个专业的知识库问答助手。               │
│   请严格根据【参考资料】回答用户问题。          │
│   如果参考资料中没有相关内容，请回答'不知道'。  │
│   不要编造答案。"                             │
├─────────────────────────────────────────────┤
│  Context（上下文 = 检索召回的文档片段）         │
│                                             │
│  【参考资料】：                               │
│  [1] Java是一门面向对象的编程语言，由Sun公司... │
│  [2] Python是一门解释型动态语言，语法简洁...    │
│  [3] Java需要编译，Python直接解释执行...       │
├─────────────────────────────────────────────┤
│  User Question（用户问题）                    │
│                                             │
│  "Java和Python有什么区别？"                   │
└─────────────────────────────────────────────┘

三段拼在一起 --> 发给大模型 --> 大模型根据参考资料生成答案
```

### 2. 为什么要三段式

```
System Prompt 的作用：
  - 限定大模型的行为（只能根据参考资料回答）
  - 防止幻觉（没有资料就说"不知道"）
  - 设定回答风格（简洁/详细/分点等）

Context 的作用：
  - 提供真实的知识依据（来自向量检索）
  - 大模型不需要"记住"所有知识，只需要读懂给它的资料
  - 相当于开卷考试的"参考书"

User Question 的作用：
  - 用户的原始问题
  - 大模型需要回答的目标

面试回答：
  "RAG 的 Prompt 由 System + Context + Question 三段构成。
   System 限定角色和行为规则，
   Context 注入检索到的文档片段，
   Question 是用户原问题。
   三段拼接后发给 LLM，实现有据回答。"
```



### 3. Prompt 拼接代码实现

```
SpringAI 中拼接 Prompt 有两种方式：

方式一：ChatClient 链式 API（推荐，项目中使用）
  chatClient
      .prompt()
      .system(systemPrompt)     // 系统提示
      .user(userPrompt)         // 用户问题 + 上下文
      .call()                   // 同步调用
      .content()                // 获取文本结果

方式二：PromptTemplate 模板（适合复杂场景）
  PromptTemplate template = new PromptTemplate(templateString);
  Prompt prompt = template.create(Map.of("context", context, "question", question));
  ChatResponse response = chatModel.call(prompt);

本项目使用方式一，因为更简洁直观。
方式二适合 Prompt 模板需要动态加载或热更新的场景。
```



------

## 三、ChatClient 核心 API（已有基础，快速回顾）

### 1. ChatClient 配置（已有）

```
项目中已经在 ChatClientConfig 配置了 ChatClient Bean：
```

```java
package com.jianbo.springai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean("MiniMaxChatClient")
    public ChatClient miniMaxChatClient(MiniMaxChatModel miniMaxChatModel) {
        return ChatClient.create(miniMaxChatModel);
    }
}
```

```
关键点：
  - ChatClient.create(chatModel) 创建客户端
  - 底层用的是 MiniMaxChatModel（MiniMax 大模型）
  - 之前的 MiniMaxAiService 已经用过同步/流式调用
  - 今天在此基础上加 RAG 上下文注入
```

### 2. ChatClient 三种调用方式

```
① 同步调用（阻塞等待，最简单）：
  String answer = chatClient
      .prompt()
      .system("角色设定")
      .user("用户问题")
      .call()          // 阻塞，直到拿到完整结果
      .content();      // 返回 String

② 流式调用（SSE 逐字推送，生产必用）：
  Flux<String> stream = chatClient
      .prompt()
      .system("角色设定")
      .user("用户问题")
      .stream()        // 不阻塞，返回 Flux 流
      .content();      // 每个元素是一小段文本

③ 带 Prompt 对象调用（多轮对话场景）：
  Prompt prompt = new Prompt(messages);  // messages 包含历史对话
  String answer = chatClient
      .prompt(prompt)
      .call()
      .content();

Day26 重点用 ① 和 ②，③ 在 Day 多轮对话中已讲过。
```



------

## 四、RAG Service 核心实现

### 1. 设计思路

```
RagService 要做的事情（三步）：

  输入：用户问题（String query）
  
  第一步：调 VectorSearchService 检索相关文档片段
          query --> 向量化 --> 在 PG/ES 中检索 --> 召回 List<Document>

  第二步：把召回的 Document 列表拼接成 Context 字符串
          List<Document> --> 拼接成带编号的参考资料文本

  第三步：组装 System + Context + Question，调 ChatClient 生成答案
          同步 --> 返回 String
          流式 --> 返回 Flux<String>

类依赖关系：
  RagService
    ├── VectorSearchService   （Day25 已实现，负责检索）
    ├── ChatClient            （已配置，负责调用大模型）
    └── 内部方法               （负责拼接 Prompt）
```

### 2. 完整 RagService 代码

```java
package com.jianbo.springai.service.rag;

import com.jianbo.springai.service.search.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * RAG 问答服务（全链路核心）
 *
 * 流程：检索 --> 拼接 Prompt --> 调用大模型 --> 返回答案
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagService {

    private final VectorSearchService vectorSearchService;
    private final ChatClient miniMaxChatClient;

    // ==================== System Prompt 模板 ====================

    /**
     * 系统提示：限定大模型行为
     *
     * 关键约束：
     *   1. 只能根据参考资料回答（防幻觉）
     *   2. 没有相关内容就说"不知道"（防编造）
     *   3. 标注来源编号（可追溯）
     */
    private static final String SYSTEM_PROMPT = """
        你是一个专业的知识库问答助手。请严格遵守以下规则：
        1. 只能根据【参考资料】中的内容回答用户问题
        2. 如果参考资料中没有相关内容，请直接回答"根据现有资料，暂无相关信息"
        3. 不要编造、推测或添加参考资料中没有的内容
        4. 回答时请标注参考来源的编号，如 [1]、[2]
        5. 回答语言简洁清晰，分点陈述
        """;

    // ==================== 默认检索参数 ====================

    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.7;

    // ==================== 同步问答（阻塞） ====================

    /**
     * RAG 问答（同步）
     *
     * @param query 用户问题
     * @return 大模型生成的答案
     */
    public String chat(String query) {
        return chat(query, DEFAULT_TOP_K, DEFAULT_THRESHOLD);
    }

    /**
     * RAG 问答（同步，可调参数）
     *
     * @param query     用户问题
     * @param topK      召回数量
     * @param threshold 相似度阈值
     * @return 大模型生成的答案
     */
    public String chat(String query, int topK, double threshold) {
        log.info("RAG问答开始 | query={}, topK={}, threshold={}", query, topK, threshold);
        long startTime = System.currentTimeMillis();

        // 第一步：向量检索，召回相关文档片段
        List<Document> docs = vectorSearchService.search(query, topK, threshold);
        log.info("检索完成, 召回 {} 条文档", docs.size());

        if (docs.isEmpty()) {
            return "根据现有资料，暂无与您问题相关的内容。请尝试换个问法或补充知识库。";
        }

        // 第二步：拼接上下文
        String context = buildContext(docs);

        // 第三步：拼接完整 Prompt，调用大模型
        String userPrompt = buildUserPrompt(context, query);

        String answer = miniMaxChatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        long cost = System.currentTimeMillis() - startTime;
        log.info("RAG问答完成, 耗时: {}ms", cost);

        return answer;
    }

    // ==================== 流式问答（SSE 逐字推送） ====================

    /**
     * RAG 问答（流式）
     *
     * 流式返回的好处：
     *   - 用户不用等全部生成完，看到第一个字就开始展示
     *   - 体验像 ChatGPT 逐字打印效果
     *   - 对于长答案，减少用户等待焦虑
     *
     * @param query 用户问题
     * @return Flux<String> 文本流，前端通过 SSE 接收
     */
    public Flux<String> chatStream(String query) {
        return chatStream(query, DEFAULT_TOP_K, DEFAULT_THRESHOLD);
    }

    public Flux<String> chatStream(String query, int topK, double threshold) {
        log.info("RAG流式问答开始 | query={}, topK={}, threshold={}", query, topK, threshold);

        // 第一步：向量检索（同步，因为检索很快）
        List<Document> docs = vectorSearchService.search(query, topK, threshold);
        log.info("检索完成, 召回 {} 条文档", docs.size());

        if (docs.isEmpty()) {
            return Flux.just("根据现有资料，暂无与您问题相关的内容。");
        }

        // 第二步：拼接上下文
        String context = buildContext(docs);
        String userPrompt = buildUserPrompt(context, query);

        // 第三步：流式调用大模型（核心区别：.stream() 替代 .call()）
        return miniMaxChatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .stream()       // <-- 流式调用，返回 Flux
                .content();     // <-- 每个元素是一小段文本
    }

    // ==================== Prompt 拼接工具方法 ====================

    /**
     * 把检索到的 Document 列表拼成带编号的参考资料文本
     *
     * 拼接效果示例：
     *   [1] (来源: java.pdf) Java是一门面向对象的编程语言...
     *   [2] (来源: python.pdf) Python是一门解释型语言...
     *   [3] (来源: compare.pdf) Java需要编译，Python直接解释执行...
     */
    private String buildContext(List<Document> docs) {
        return IntStream.range(0, docs.size())
                .mapToObj(i -> {
                    Document doc = docs.get(i);
                    // 从 metadata 中提取来源信息
                    String source = String.valueOf(doc.getMetadata().getOrDefault("source", "未知来源"));
                    String content = doc.getText();
                    return "[%d] (来源: %s) %s".formatted(i + 1, source, content);
                })
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 拼接用户 Prompt = 参考资料 + 用户问题
     *
     * 为什么不把 Context 放到 System Prompt 里？
     *   - System Prompt 是固定的角色设定，不应该每次都变
     *   - Context 每次查询都不同，放在 User Prompt 更合理
     *   - 这也是业界 RAG 的标准做法
     */
    private String buildUserPrompt(String context, String query) {
        return """
            【参考资料】：
            %s
            
            【用户问题】：
            %s
            
            请根据参考资料回答用户问题。
            """.formatted(context, query);
    }
}
```

### 3. 代码逐行解析

```
关键设计决策：

1. System Prompt 为什么用 static final 常量？
   --> 角色设定是固定的，不随请求变化
   --> 如果需要动态切换，可以改为配置文件或数据库读取

2. 检索为什么不用流式？
   --> 向量检索通常 50~200ms，非常快
   --> 检索结果需要一次性拼接成 Context
   --> 不像 LLM 生成那样需要逐字返回

3. buildContext 为什么用 IntStream？
   --> 需要编号 [1] [2] [3]，IntStream 可以拿到下标
   --> 编号方便大模型标注引用来源

4. 为什么 Context 放 User Prompt 而不是 System Prompt？
   --> System = 固定角色（不变）
   --> User = 动态内容（每次查询的上下文 + 问题）
   --> 这是 OpenAI 和主流框架的推荐做法

5. 空结果为什么直接返回，不调用大模型？
   --> 没有参考资料，大模型只能猜测或编造
   --> 直接返回"暂无相关内容"，比编一个错误答案好得多
```



------

## 五、RAG Controller（对外接口）

### 1. 完整 Controller 代码

```java
package com.jianbo.springai.controller;

import com.jianbo.springai.service.rag.RagService;
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
```

### 2. 接口对比

```
                   同步接口                          流式接口
路径：         GET /rag/chat                   GET /rag/chat-stream
返回类型：     String（完整答案）               Flux<String>（逐字推送）
Content-Type： application/json               text/event-stream
等待时间：     全部生成后一次性返回              立即开始逐字返回
适用场景：     后端调用、测试                   前端页面、ChatGPT 体验
生产推荐：     内部 API 调用                    面向用户的接口

面试回答：
  "生产环境 RAG 一般用流式返回（SSE），
   用户不用等全部生成完，第一个字出来就开始展示，
   体验类似 ChatGPT 逐字打印。"
```



### 3. SSE 流式原理图解

```
传统同步（一次性返回）：
  用户请求 ----[等3秒]----> 完整答案一次返回
  
  时间线：|===等待===等待===等待===|答案|
  用户体验：3秒空白，然后突然出现一大段文字

SSE 流式（逐字推送）：
  用户请求 --> 第1个字 --> 第2个字 --> ... --> 最后一个字
  
  时间线：|字|字|字|字|字|字|字|字|字|...|完|
  用户体验：0.3秒就看到第一个字，逐字显示像打字机

底层机制：
  1. 浏览器发起 GET 请求（Accept: text/event-stream）
  2. 服务端不关闭连接，持续写入数据
  3. 每个数据块格式：data: 你好\n\n
  4. 浏览器的 EventSource API 自动解析每个块
  5. 大模型生成完毕，服务端关闭连接

SpringAI 中的实现：
  chatClient.prompt().stream().content()
  --> 底层调用大模型的 Streaming API
  --> 大模型每生成一个 token 就推送一次
  --> Flux<String> 自动适配 SSE 协议
```



------

## 六、端到端测试

### 1. 启动前检查清单

```
确保以下服务已启动：

  ✓ PostgreSQL（端口 5432）  — 向量数据库
  ✓ Redis（端口 6379）       — 会话缓存
  ✓ Elasticsearch（端口 9200）— ES 向量存储（可选）
  ✓ MiniMax API Key 已配置   — application.yaml 中

确保知识库已入库（Day24 做过的事）：
  ✓ 至少有几条文档已经切片、向量化、存入 PG/ES
  ✓ 可以通过 Day25 的 /search/pg 接口验证检索正常
```

### 2. 同步接口测试

```bash
# ===== 基础问答测试 =====
curl "http://localhost:12115/rag/chat?query=Java%E5%92%8CPython%E6%9C%89%E4%BB%80%E4%B9%88%E5%8C%BA%E5%88%AB"

# ===== 指定 topK 和 threshold =====
curl "http://localhost:12115/rag/chat?query=Spring%E6%A1%86%E6%9E%B6%E7%9A%84%E6%A0%B8%E5%BF%83%E7%89%B9%E6%80%A7&topK=3&threshold=0.8"

# ===== 预期返回 =====
# 根据参考资料 [1][2]，Java和Python的主要区别在于：
# 1. Java是编译型语言，需要先编译再运行；Python是解释型语言...
# 2. Java是强类型，需要声明变量类型；Python是动态类型...
# ...
```

### 3. 流式接口测试

```bash
# ===== 流式问答测试（curl 实时显示推送内容）=====
curl -N "http://localhost:12115/rag/chat-stream?query=Java%E5%92%8CPython%E6%9C%89%E4%BB%80%E4%B9%88%E5%8C%BA%E5%88%AB"

# -N 参数：禁用缓冲，curl 收到数据立即输出
# 你会看到逐字逐字输出的效果：
#   data:根
#   data:据
#   data:参
#   data:考
#   data:资料
#   ...
```

### 4. 单元测试代码

```java
package com.jianbo.springai.service.rag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest
class RagServiceTest {

    @Autowired
    private RagService ragService;

    /**
     * 测试同步 RAG 问答
     */
    @Test
    void testChat() {
        String answer = ragService.chat("Java有什么特点？");
        System.out.println("=== RAG 同步答案 ===");
        System.out.println(answer);

        // 验证：答案非空，且包含参考标注
        assert answer != null && !answer.isEmpty();
    }

    /**
     * 测试空结果场景
     */
    @Test
    void testChatNoResult() {
        // 用一个知识库肯定没有的问题
        String answer = ragService.chat("今天天气怎么样？", 5, 0.95);
        System.out.println("=== 空结果测试 ===");
        System.out.println(answer);

        // threshold 设很高，大概率召回为空，应该返回兜底文案
    }

    /**
     * 测试流式 RAG 问答
     */
    @Test
    void testChatStream() {
        Flux<String> stream = ragService.chatStream("Java和Python的区别？");

        // 收集所有流式片段，拼接成完整答案
        StringBuilder fullAnswer = new StringBuilder();
        stream.doOnNext(chunk -> {
            System.out.print(chunk);  // 逐字打印
            fullAnswer.append(chunk);
        }).blockLast();  // 阻塞等待流结束

        System.out.println("\n=== 流式完整答案 ===");
        System.out.println(fullAnswer);
    }

    /**
     * 测试不同 topK 参数对答案质量的影响
     */
    @Test
    void testTopKComparison() {
        String query = "什么是向量数据库？";

        String answer3 = ragService.chat(query, 3, 0.7);
        String answer10 = ragService.chat(query, 10, 0.5);

        System.out.println("=== TopK=3, threshold=0.7 ===");
        System.out.println(answer3);
        System.out.println("\n=== TopK=10, threshold=0.5 ===");
        System.out.println(answer10);

        // 观察：topK 大 + threshold 低 --> 更多上下文，答案可能更全面但可能有噪音
    }
}
```

### 5. 测试观察要点

```
运行测试后，观察控制台日志：

1. 检索日志（Day25 的 VectorSearchService 打印）：
   语义检索 | query=Java有什么特点？, topK=5, threshold=0.7
   检索完成, 召回 3 条

2. RAG 日志（RagService 打印）：
   RAG问答开始 | query=Java有什么特点？, topK=5, threshold=0.7
   检索完成, 召回 3 条文档
   RAG问答完成, 耗时: 2345ms

3. 注意耗时分布：
   检索：50~200ms（很快）
   大模型生成：1~5秒（主要耗时）
   --> 这就是为什么流式返回很重要

4. 验证答案质量：
   ✓ 答案是否基于参考资料
   ✓ 是否标注了来源 [1] [2]
   ✓ 是否有编造的内容
   ✗ 如果答案和参考资料无关 --> 检查 System Prompt
```



------

## 七、生产级优化（面试加分）

### 1. System Prompt 优化技巧

```
基础版（我们写的）：
  "只能根据参考资料回答，不要编造"

生产进阶版（可选增强）：
  1. 角色细化：
     "你是{公司名}的技术文档问答助手，专注于{Java/Spring}领域"
  
  2. 输出格式约束：
     "请用 Markdown 格式回答，代码用代码块包裹"
  
  3. 多语言支持：
     "请用与用户问题相同的语言回答"
  
  4. 长度控制：
     "回答控制在 500 字以内，重点突出"

  5. 拒绝策略细化：
     "如果参考资料只部分相关，请明确说明哪些是基于资料的，哪些是你的推断"

优化原则：
  --> System Prompt 越精确，大模型越不容易"发散"
  --> 但也不要太长，超过 500 token 的 System Prompt 效果反而下降
```

### 2. Context 长度控制（防止 Token 爆炸）

```
问题：如果召回了 10 条文档，每条 500 字，Context = 5000 字
     加上 System Prompt + 用户问题 + 生成空间
     --> 轻松超过模型 Token 上限（如 32K）

解决方案：

方案一：限制 TopK（最简单）
  topK = 3~5，只取最相关的几条
  --> 大部分场景够用

方案二：截断 Context（保险策略）
  if (context.length() > MAX_CONTEXT_LENGTH) {
      context = context.substring(0, MAX_CONTEXT_LENGTH);
  }
  --> 简单粗暴，但可能截断关键信息

方案三：Token 计数 + 动态截断（生产推荐）
  项目中已有 ChatContextUtil.countTotalToken() 方法（Day 多轮对话实现的）
  可以复用：
    int contextTokens = chatContextUtil.estimate(context);
    int remainTokens = MAX_TOKENS - systemTokens - questionTokens - reserveTokens;
    if (contextTokens > remainTokens) {
        // 按 Document 粒度裁剪，优先保留相似度高的
        // 而不是按字符截断
    }

面试回答：
  "生产环境需要做 Token 预算管理：
   给 System 留 200 token，给生成留 2048 token，
   剩余空间分配给 Context，超出就按相似度降序裁剪文档片段。"
```

### 3. 答案质量评估

```
人工评估（小规模）：
  1. 准确性：答案是否正确
  2. 相关性：答案是否回答了用户问题
  3. 可追溯：是否标注了来源
  4. 无幻觉：是否有编造的内容

自动评估（大规模）：
  1. ROUGE 分数：和标准答案的重叠度
  2. BLEU 分数：翻译/生成质量
  3. 人工打分 + GPT-4 评估

生产监控指标：
  - 检索召回率（有多少问题能找到相关文档）
  - 空结果率（多少问题没有召回任何文档）
  - 平均响应时间（检索 + 生成的总耗时）
  - 用户满意度（点赞/点踩比例）
```



------

## 八、常见问题排查

### 1. 问答结果不准确

```
症状：大模型的回答和参考资料无关，或者编造了内容

排查步骤：

  Step 1: 检查检索结果
    先调 /search/pg?query=xxx 看看召回了什么
    --> 如果召回的文档就不相关 --> 问题在检索层，不是生成层

  Step 2: 检查 threshold
    threshold 太低（如 0.3）--> 混入了不相关的噪音文档
    --> 提高到 0.7

  Step 3: 检查 System Prompt
    如果没有"不要编造"的约束 --> 大模型会自由发挥
    --> 加上严格的约束规则

  Step 4: 检查 Context 拼接
    打日志看拼接后的完整 Prompt
    --> log.debug("完整Prompt: {}", userPrompt);
    --> 确认参考资料确实被正确注入
```

### 2. 流式接口不工作

```
症状：curl 看到的不是逐字输出，而是一次性返回

可能原因：

  1. Nginx/网关缓冲
     --> 在 Nginx 配置中加：
         proxy_buffering off;
         proxy_cache off;

  2. 前端没用 EventSource
     --> 普通 fetch 不支持 SSE
     --> 要用 new EventSource(url) 或 fetch + ReadableStream

  3. produces 没设置
     --> Controller 必须有：
         produces = MediaType.TEXT_EVENT_STREAM_VALUE

  4. Spring MVC 异步支持
     --> spring-boot-starter-web 默认支持 Flux 返回
     --> 不需要额外配置（WebFlux starter 不是必须的）
     --> Spring AI 的 minimax starter 已经引入了 reactor-core
```

### 3. Token 超限报错

```
症状：调用大模型时报错 "maximum context length exceeded"

解决：
  1. 减少 topK（如从 10 改为 3）
  2. 缩短每条文档的长度（Day22 切片时控制 chunk_size）
  3. 加 Token 预算管理（见 7.2 节）
  4. 换更大上下文的模型（如 128K 模型）

快速定位：
  打日志看 Context 长度
  log.info("Context长度: {} 字符, 约 {} tokens",
      context.length(), context.length() / 2);
  // 中文大约 1字 ≈ 1~2 tokens
```



------

## 九、面试必背总结

### 1. RAG 是什么（背定义）

```
RAG = Retrieval-Augmented Generation = 检索增强生成

一句话：先从知识库检索相关内容，再把内容喂给大模型生成答案
类比：开卷考试 —— 先翻书找答案，再组织语言回答

解决的问题：
  1. 大模型知识过时 --> 用实时知识库补充
  2. 大模型幻觉 --> 有参考资料兜底
  3. 私有数据 --> 大模型读不到你的数据库，RAG 帮它"看到"
```



### 2. RAG 全链路（背5步流程）

```
用户问题
  │
  ├─ ① Embedding 向量化（EmbeddingModel.call）
  │     问题文本 --> float[1536]
  │
  ├─ ② Vector Search 向量检索（VectorStore.similaritySearch）
  │     float[] --> 在 PG/ES 中找 TopK 相似片段
  │
  ├─ ③ Prompt 拼接（System + Context + Question）
  │     System: 角色设定 + 行为约束
  │     Context: 检索到的文档片段（带编号）
  │     Question: 用户原始问题
  │
  ├─ ④ LLM 生成（ChatClient.prompt().call() 或 .stream()）
  │     大模型基于上下文生成答案
  │
  └─ ⑤ 返回答案
        同步 String / 流式 Flux<String>（SSE）
```



### 3. Prompt 三段式（背结构）

```
System Prompt（固定）：角色 + 规则 + 约束
  "你是知识库助手，只根据参考资料回答，不编造"

User Prompt（动态）：上下文 + 问题
  "【参考资料】：[1]... [2]... 【用户问题】：xxx"

为什么 Context 放 User 不放 System？
  System 是固定角色设定，不应该每次都变
  Context 每次查询不同，放 User Prompt 更合理
```



### 4. 流式返回（背 SSE 原理）

```
为什么用流式？
  同步：等 3 秒才看到完整答案（体验差）
  流式：0.3 秒看到第一个字，逐字显示（ChatGPT 体验）

SpringAI 实现：
  chatClient.prompt().stream().content()  --> Flux<String>
  Controller 返回 Flux<String> + produces = TEXT_EVENT_STREAM_VALUE

前端接收：
  EventSource API 或 fetch + ReadableStream
```



### 5. 防幻觉策略（背3条）

```
1. System Prompt 约束：
   "只根据参考资料回答，没有相关内容就说不知道"

2. 空结果兜底：
   检索没召回任何文档时，直接返回"暂无相关信息"
   不调用大模型（避免无依据的编造）

3. 来源标注：
   要求大模型标注 [1] [2] 引用编号
   方便人工核实答案是否有据可依
```



### 6. 生产优化（背3个关键点）

```
1. Token 预算管理：
   System(200) + Context(动态) + Question(用户) + Reserve(2048生成)
   Context 超限 --> 按相似度降序裁剪文档

2. 检索质量调优（详见 Day25）：
   TopK=3~5, threshold=0.7, 两层召回

3. System Prompt 精调：
   角色越具体，约束越明确 --> 大模型越不容易发散
   但 System Prompt 不要超过 500 token
```



------

## 十、企业级 RAG 生产方案（进阶必读）

```
前面 Day22~26 实现的是 RAG 核心链路（能跑通）。
但要在生产环境跑稳、跑快、扛住量，还需要关注两个方向：

  写入侧（Write Path）：文档入库怎么更快
  读取侧（Read Path）：检索怎么更快、更准
```

### 1. 写入侧：批量 Embedding + 虚拟线程加速

```
问题场景：
  导入 10 万条文档，每条要调 Embedding API 转向量
  串行处理：1条 ≈ 100ms，10万条 ≈ 2.7 小时
  --> 生产不可接受

解决方案：批量 + 并发

方案一：分批调用（项目已有 embedBatchWithChunking）
  1000条文档，batchSize=50 --> 20次 API 调用
  每次 50 条打包成 1 个 HTTP 请求（比 1000 次快 50 倍）
  --> 已在 Day23 EmbeddingService 实现

方案二：虚拟线程并发（Spring Boot 4 + Java 21）
  分批之后，每批之间还是串行的
  用虚拟线程让多批并发执行，IO 等待时不占用平台线程

  开启方式（application.yaml 一行配置）：
    spring:
      threads:
        virtual:
          enabled: true

  开启后：
  - Tomcat 的每个请求自动运行在虚拟线程上
  - @Async 任务也运行在虚拟线程上
  - Embedding API 调用是纯 IO 操作，虚拟线程最适合

  为什么虚拟线程适合 Embedding？
    传统线程：1 个平台线程 ≈ 1MB 栈内存，开 1000 个就 1GB
    虚拟线程：1 个虚拟线程 ≈ 几 KB，开 10 万个也没压力
    Embedding 调用 99% 时间在等 HTTP 响应（IO 密集型）
    --> 虚拟线程在 IO 等待时自动让出 CPU，不浪费资源
```

```java
// ==================== 虚拟线程并发 Embedding 示例 ====================

/**
 * 并发分批向量化（虚拟线程加速版）
 *
 * 原理：
 *   1. 把 1000 条文档分成 20 批（每批 50 条）
 *   2. 用虚拟线程池同时执行多批 Embedding 调用
 *   3. 等全部完成，按顺序合并结果
 *
 * 对比：
 *   串行分批：20批 × 200ms = 4秒
 *   并发分批（5并发）：20批 / 5 × 200ms ≈ 0.8秒
 */
public List<float[]> embedBatchConcurrent(List<String> texts, int batchSize,
                                           int concurrency) {
    // 1. 分批
    List<List<String>> batches = new ArrayList<>();
    for (int i = 0; i < texts.size(); i += batchSize) {
        batches.add(texts.subList(i, Math.min(i + batchSize, texts.size())));
    }

    // 2. 用虚拟线程并发执行
    //    Executors.newVirtualThreadPerTaskExecutor() 是 Java 21 新增 API
    //    每个任务自动分配一个虚拟线程，无需手动管理线程池大小
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        // Semaphore 控制并发度，防止打爆 Embedding API 限流
        Semaphore semaphore = new Semaphore(concurrency);

        List<Future<List<float[]>>> futures = batches.stream()
                .map(batch -> executor.submit(() -> {
                    semaphore.acquire();
                    try {
                        return embedBatch(batch);
                    } finally {
                        semaphore.release();
                    }
                }))
                .toList();

        // 3. 按顺序收集结果（保证和输入顺序一致）
        List<float[]> allVectors = new ArrayList<>();
        for (Future<List<float[]>> future : futures) {
            allVectors.addAll(future.get());
        }
        return allVectors;
    }
}
```

```
关键设计：

1. Executors.newVirtualThreadPerTaskExecutor()
   --> Java 21 新增，每个 submit 自动创建虚拟线程
   --> 不需要指定线程池大小，JVM 自动调度
   --> Spring Boot 4 开启 virtual.enabled=true 后，@Async 也走虚拟线程

2. Semaphore 限流
   --> Embedding API 通常有 QPS 限制（如 MiniMax 每秒 50 次）
   --> 虚拟线程可以轻松开 1 万个，但 API 会被限流
   --> 用 Semaphore 控制"同时在跑的批次数"（如 concurrency=5）

3. 顺序保证
   --> futures 列表和 batches 顺序一致
   --> 按顺序 future.get() 保证结果和输入文本一一对应

生产配置建议：
  batchSize = 50~100（取决于 API 单次上限）
  concurrency = 3~5（取决于 API QPS 限制）
  10 万条文档，50/批，5 并发 ≈ 2000批 / 5 × 200ms ≈ 80秒
  对比串行：2000批 × 200ms ≈ 400秒（快了 5 倍）
```



### 2. 读取侧：HNSW 索引是生死线

```
这是最容易被忽视、后果最严重的问题：

没有 HNSW 索引时，PG 向量检索走全表扫描：
  数据量     | 无索引          | 有 HNSW 索引
  1 万条     | 50ms（还行）    | 5ms
  10 万条    | 500ms（慢了）   | 8ms
  100 万条   | 5秒（不可用！）  | 15ms

结论：
  几千条 --> 无索引也能用（开发测试）
  几万条 --> 开始变慢，应该加索引
  几十万+ --> 没索引直接废掉，从毫秒掉到秒级

为什么会这样？
  无索引 = 暴力扫描，逐条计算余弦距离 --> O(n)
  HNSW 索引 = 图结构近似搜索 --> O(log n)
  100 万条时：O(n) vs O(log n) ≈ 1000000 vs 20
```

```
项目中已配置 HNSW（application.yaml）：

  spring.ai.vectorstore.pgvector:
    index-type: hnsw           # ✓ 已配置
    hnsw:
      m: 16                    # 每个节点最大连接数
      ef-construction: 200     # 建索引时的搜索宽度

但要注意：SpringAI 的 initialize-schema: false 时不会自动建索引！
需要手动执行 SQL：
```

```sql
-- ===== 手动创建 HNSW 索引（生产必做）=====

-- 检查是否已有索引
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'vector_store';

-- 如果没有 embedding 列的索引，手动创建：
CREATE INDEX IF NOT EXISTS vector_store_embedding_hnsw_idx
ON vector_store
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 200);

-- 建索引后，验证是否生效：
EXPLAIN ANALYZE
SELECT * FROM vector_store
ORDER BY embedding <=> '[0.1, 0.2, ...]'::vector
LIMIT 5;

-- 看执行计划，应该出现 "Index Scan using vector_store_embedding_hnsw_idx"
-- 如果看到 "Seq Scan" 说明索引没生效
```

```
HNSW 参数选择（生产推荐）：

  参数              | 含义                    | 推荐值   | 说明
  m                | 每节点最大连接数         | 16      | 越大精度越高，索引越大
  ef_construction  | 建索引时搜索宽度         | 200     | 越大建索引越慢，但质量越好
  ef_search        | 查询时搜索宽度           | 100     | 越大查询越准，但越慢

  10 万以下：m=16, ef_construction=200（默认就行）
  100 万级：m=32, ef_construction=400（加大参数）
  1000 万级：考虑 ES 或专用向量数据库（Milvus/Pinecone）
```



### 3. ES 侧：同样需要关注索引配置

```
ES 的向量索引是自动创建的（mapping 中 dense_vector 类型自带 HNSW）。
但默认参数可能不是最优的：

PUT /my_documents_es
{
  "mappings": {
    "properties": {
      "embedding": {
        "type": "dense_vector",
        "dims": 1536,
        "index": true,
        "similarity": "cosine",
        "index_options": {
          "type": "hnsw",
          "m": 16,
          "ef_construction": 200
        }
      }
    }
  }
}

ES 查询时调 ef_search：
  knn.num_candidates = 50~100（等价于 ef_search）

ES vs PG 选型补充（详见 Day24）：
  10 万以下 --> PG pgvector 够用，运维简单
  10~100 万 --> PG 调大 HNSW 参数，或迁移到 ES
  100 万以上 --> ES（分布式扩展），或专用向量数据库
```



### 4. 企业级 RAG 完整架构图

```
                        ┌─────────────────────────────────┐
                        │        企业级 RAG 系统            │
                        └─────────────┬───────────────────┘
                                      │
          ┌───────────────────────────┼───────────────────────────┐
          │                           │                           │
    ┌─────▼─────┐             ┌──────▼──────┐             ┌─────▼─────┐
    │ 写入管道   │             │ 检索管道     │             │ 生成管道   │
    │ Write Path │             │ Read Path   │             │ Gen Path  │
    └─────┬─────┘             └──────┬──────┘             └─────┬─────┘
          │                          │                          │
    ┌─────▼──────────┐     ┌────────▼─────────┐     ┌─────────▼──────────┐
    │ ① 文档解析      │     │ ① 问题向量化      │     │ ① Prompt 拼接       │
    │  PDF/Word/HTML  │     │  EmbeddingModel  │     │  System+Context+Q   │
    │  (Tika Reader)  │     │                  │     │                     │
    ├────────────────┤     ├──────────────────┤     ├─────────────────────┤
    │ ② 文本切片      │     │ ② 向量检索        │     │ ② ChatClient 调用   │
    │  500字+50重叠   │     │  HNSW 索引加速    │     │  同步 / 流式 SSE    │
    │  (TextSplitter) │     │  (VectorStore)   │     │  (MiniMax LLM)     │
    ├────────────────┤     ├──────────────────┤     ├─────────────────────┤
    │ ③ 批量向量化    │     │ ③ 结果过滤        │     │ ③ 答案返回          │
    │  虚拟线程并发    │     │  threshold+filter │     │  来源标注+防幻觉    │
    │  Semaphore限流  │     │  (SearchRequest)  │     │                     │
    ├────────────────┤     └──────────────────┘     └─────────────────────┘
    │ ④ 入库          │
    │  PG/ES 双写     │
    │  HNSW 索引      │
    └────────────────┘

    关键瓶颈和优化点：
      写入：Embedding API 调用（IO 密集）--> 虚拟线程 + 批量 + 限流
      检索：向量距离计算（CPU 密集）   --> HNSW 索引（O(n) → O(log n)）
      生成：LLM 推理（GPU 密集）       --> 流式返回 + Token 预算管理
```



### 5. 生产 Checklist（上线前必查）

```
写入侧 ✓：
  □ Embedding 是否用批量调用（不是逐条）
  □ 大批量是否用虚拟线程并发（spring.threads.virtual.enabled=true）
  □ 是否有 Semaphore 限流（防打爆 API）
  □ 是否有进度日志（方便监控入库进度）
  □ 是否有失败重试（某批失败不影响整体）

存储侧 ✓：
  □ PG: embedding 列是否有 HNSW 索引（EXPLAIN ANALYZE 验证）
  □ PG: HNSW 参数 m/ef_construction 是否合理
  □ ES: dense_vector mapping 是否配置了 index: true
  □ ES: num_candidates 是否合理（50~100）
  □ 数据量超 100 万是否考虑了分库/ES 分片

检索侧 ✓：
  □ topK 是否合理（3~5，不要太大）
  □ threshold 是否合理（0.7~0.8）
  □ 空结果是否有兜底返回
  □ 检索耗时是否 < 200ms（如果 > 500ms 查索引）

生成侧 ✓：
  □ System Prompt 是否有防幻觉约束
  □ Context 是否有 Token 长度控制
  □ 是否用流式返回（面向用户的接口）
  □ 是否有超时兜底（LLM 挂了怎么办）
  □ 是否有日志记录完整 Prompt（排查用）
```



------

## 十一、衔接下节预告

```
Day26 学完，你已经掌握了完整的 RAG 链路：

  Day22: 文档切片       ✓
  Day23: 向量化         ✓
  Day24: 向量入库       ✓
  Day25: 向量检索       ✓
  Day26: Prompt拼接+LLM生成+流式返回+企业级优化  ✓  <-- 今天完成

  恭喜！RAG 核心链路已全部打通。

后续可扩展方向：
  - 多轮 RAG 对话（结合 ChatSessionService 的会话管理）
  - RAG + Function Calling（大模型调用外部工具）
  - RAG 评测体系（自动化测试答案质量）
  - 知识库管理（增删改查、权限控制）
  - 前端对接（SSE + React/Vue 页面）
```
