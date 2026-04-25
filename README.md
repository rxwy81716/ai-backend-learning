# AI Backend Learning (Java)

## 🎯 目标
从Java后端转型 AI应用工程，60天冲刺Java+AI后端求职

## 📚 学习内容
- Java并发编程（线程、线程池、锁、并发安全问题）
- Redis / RocketMQ 中间件深度实战
- 微服务网关、限流熔断降级
- AI核心（Spring AI + RAG检索增强生成）

## 🚀 实战项目
### 1. AI知识库（RAG）
- 长文档上传解析
- 向量语义检索
- 大模型智能问答

### 2. AI SQL助手
- 自然语言需求自动生成SQL
- SQL语法校验与优化

## 📝 学习记录
每日知识点总结、原理画图、面试口述话术，全部归档在 `docs/` 目录

ai-backend-learning/
├── docs/                  # 学习笔记（面试用🔥）
│   ├── java-concurrent/   # 并发编程：线程、线程池、锁、并发安全
│   ├── middleware/         # Redis、MQ、网关、Sentinel限流熔断
│   ├── jvm/               # JVM内存结构、GC、G1垃圾回收
│   └── ai-rag/            # Spring AI、RAG原理、架构图、面试话术
│
├── demo-thread/           # 并发编程实战Demo
├── demo-redis/            # Redis缓存、分布式锁、缓存三大问题Demo
├── demo-mq/                # MQ生产消费、消息可靠性、幂等消费Demo
├── demo-gateway-sentinel/  # 微服务网关、接口限流熔断Demo
│
├── ai-rag-project/        # 项目1 核心🔥 AI知识库RAG文档问答
├── ai-sql-project/        # 项目2 AI自然语言转SQL助手
│
├── .gitignore             # Git文件忽略配置
└── README.md              # 仓库总说明&60天学习路线

```
java-concurrent
Day01-线程基础.md
Day02-线程池.md
Day03-线程池底层原理.md
Day04-Java锁机制.md
Day05-经典并发坑.md

middleware
Day06-Redis基础.md
Day07-Redis三大缓存故障.md
Day08-分布式锁.md
Day09-MQ消息队列基础.md
Day10-MQ可靠性问题.md
Day11-Gateway网关.md
Day12-Sentinel限流熔断.md

jvm
Day13-JVM内存模型.md
Day14-JVM垃圾回收.md

spring-ai-rag
Day15-Day30 SpringAI+RAG全套笔记

project-optimize
Day31-Day45 线上优化+面试深度全套
```





## 📅 第一阶段 Day1–14 Java 后端高并发 & 中间件补强

### Day1 线程基础

学：线程 4 种创建方式、Runnable、Callable、Future

做：手写 3 种线程完整 Demo

面试必答：线程状态流转

### Day2 线程池

学：ThreadPoolExecutor 七大核心参数

做：线程池批量执行 1000 个任务

面试必答：corePoolSize 合理计算公式

### Day3 线程池底层原理

学：四种拒绝策略、队列类型、饱和机制

做：模拟任务堆积、队列满触发拒绝策略

### Day4 Java 锁机制

学：synchronized 锁升级、ReentrantLock 可重入 / 公平锁

做：高并发安全计数器 Demo

### Day5 经典并发坑

做：模拟商品库存超卖、线程安全问题复现

必口述：超卖根本原因、3 种解决方案

### Day6 Redis 基础

学：String、Hash 结构、过期淘汰策略、持久化

做：缓存查询、缓存更新完整 Demo

### Day7 Redis 三大缓存故障

学：缓存击穿、雪崩、穿透原理 + 解决方案

做：模拟击穿场景 + 布隆过滤器简单实现

### Day8 分布式锁

做：Redis Redisson 分布式锁实战

面试：锁超时、锁续期、重复解锁问题

### Day9 MQ 消息队列基础

学：RocketMQ/Kafka 架构、生产者消费者

做：最简生产消费 Demo

### Day10 MQ 可靠性问题

学：消息丢失、消息重复消费、消息积压

做：幂等消费解决方案代码

### Day11 Spring Cloud Gateway 网关

学：路由规则、断言、过滤器

做：自定义路由网关 Demo

### Day12 微服务限流熔断

学：Sentinel 流量控制、熔断降级

做：接口限流、热点参数限流实战

### Day13 JVM 内存模型

学：堆、栈、元空间、程序计数器、本地方法栈

### Day14 JVM 垃圾回收

学：G1 收集器原理、GC 垃圾回收流程

强制输出：**完整版 JVM 手写总结笔记 + 结构图**

------

## 📅 第二阶段 Day15–30 Spring AI + RAG 知识库项目 1

### Day15 LLM 大模型基础

学：大模型原理、Token 上下文长度、调用格式

输出：精简原理笔记

### Day16 Prompt 工程

做：同一需求不同 Prompt，对比回答效果差异

### Day17 Spring AI 环境搭建

做：SpringBoot 整合 AI 接口，通义 / 豆包 API 调用

### Day18 聊天接口开发

做：封装通用 /chat 对话接口

### Day19 多轮上下文对话

做：会话记忆、上下文窗口维护

### Day20 AI 项目复盘

输出：**AI 接口完整调用流程图**

### Day21 RAG 检索增强生成基础

学：向量数据库、语义检索、召回逻辑

### Day22 文档文本切片

做：长文本分段、清洗、切分工具类

### Day23 Embedding 向量嵌入

做：文本转为向量数据

### Day24 向量存储

做：ES Elasticsearch 向量入库存储

### Day25 相似度语义检索

做：关键词相似度匹配、召回文档

### Day26 全链路打通

文档切片→向量存储→语义检索→大模型作答

### Day27–30 项目 1 闭环完工

实现：文件上传 → 知识库问答

✅ 项目 1 交付：可运行完整 RAG 问答系统

------

## 📅 第三阶段 Day31–45 项目线上化优化 + 面试深度

### Day31–32 Redis 缓存优化

缓存 AI 问答结果，减少重复调用大模型

### Day33–34 权限登录体系

JWT 令牌登录、接口鉴权

### Day35–36 监控日志

接口耗时统计、全链路日志打印

### Day37–38 Prompt 精细化调优

提升问答准确率、减少幻觉

### Day39–40 性能压测优化

缩短 AI 响应时长、高并发兼容

### Day41–45 项目复盘背诵

必须熟练口述：

1. RAG 完整工作原理
2. 向量检索优势
3. 项目全链路优化点、踩坑方案

------

## 📅 第四阶段 Day46–60 项目 2 + 简历 + 全职面试备战

### Day46–50 项目 2：AI SQL 智能助手

需求：自然语言输入 → 自动生成标准 SQL

完整前后逻辑 + 异常处理 + SQL 校验

### Day51–55 求职简历打磨

- 提炼 2 个 AI + 后端核心项目亮点
- 手绘微服务 + RAG 整体架构图
- 高并发、中间件、项目难点话术

### Day56–60 高频面试突击

重点背诵刷题：Redis、Java 并发、JVM、MQ、RAG 原理、项目深挖

------

## 🌐 精选学习网站（不内卷、只看高频考点）

### Java 后端高并发 / 面试

1. JavaGuide（必刷）并发、JVM、Redis、MQ 全套面试
2. 芋道源码 SpringCloud 微服务实战源码
3. 编程导航 路线 + 历年 Java 面经合集

### AI 大模型 & RAG 方向

1. Spring AI 官方中文文档
2. LangChain 中文教程（RAG 逻辑必看）
3. 飞书 AI 知识库实战教程

### 视频 & 碎片化查阅

1. B 站：只看**实战项目**，不看纯理论
2. 掘金：RAG、Spring AI 深度文章
3. CSDN：仅查报错细节，不系统学习

------

## 🚨 三条生死执行纪律

1. **当天不写代码 = 当天白学**
2. 每 3 天产出一个可运行 Demo
3. 每周整理一段**面试口述话术**

------

## 🎯 30 天验收硬性标准

30 天后你必须张口就来：

1. 什么是 RAG 检索增强生成
2. 为什么要用向量检索，不用普通模糊查询
3. 你的 RAG 项目从头到尾完整运行流程