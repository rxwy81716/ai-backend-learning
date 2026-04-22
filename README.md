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