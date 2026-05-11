# 微调（Fine-tuning）vs RAG 对比

## 本项目采用的方案：RAG（检索增强生成）

RAG 的核心思路：**不改模型本身**，每次用户提问时，先从知识库检索相关文档，把检索结果注入到 Prompt 中，让模型基于真实数据回答。

## 微调的核心思路

微调是把私有数据"烧"进模型权重。通过在预训练模型基础上，用特定领域的 Q&A 数据继续训练，让模型"记住"这些知识。

## 对比表

| 维度 | RAG（本项目） | 微调 |
|------|--------------|------|
| **知识更新** | ✅ 实时（上传即可用） | ❌ 需要重新训练（小时级） |
| **可追溯性** | ✅ 可标注来源文档 | ❌ 知识融入权重，不可追溯 |
| **成本** | ✅ 只需推理成本 | ❌ 需要 GPU 训练成本 |
| **幻觉控制** | ✅ 有检索结果约束 | ⚠️ 仍可能产生幻觉 |
| **风格定制** | ⚠️ 靠 Prompt 调整 | ✅ 可深度定制语气/格式 |
| **私有数据安全** | ✅ 数据留在本地检索 | ⚠️ 数据需上传到训练平台 |
| **适用场景** | 知识密集型问答、需要引用来源 | 特定语气/格式、高频固定问答 |

## 推荐策略

大多数企业知识库场景推荐 **RAG**。仅当以下条件同时满足时才考虑微调：
1. 对回答的语气/格式有极高一致性要求
2. 知识库内容不经常更新
3. 有 GPU 训练资源

**最佳实践**：RAG + 微调混合使用 —— 用微调定制回答风格，用 RAG 注入最新知识。

## 本项目新增的微调支持

### 训练数据准备工具

`FineTuneDataPrepService` 可以从知识库已有文档自动生成微调训练数据：

```
文档分片 → LLM 生成 Q&A 对 → OpenAI JSONL 格式输出
```

### API 端点

| 端点 | 说明 |
|------|------|
| `GET /api/finetune/comparison` | 获取 RAG vs 微调对比信息 |
| `GET /api/finetune/{taskId}/estimate` | 估算某文档的训练数据量 |
| `POST /api/finetune/{taskId}/generate` | 生成训练数据并下载 JSONL |

### 输出格式（OpenAI Fine-tuning JSONL）

每行一个 JSON 对象：
```json
{"messages":[{"role":"system","content":"你是一个企业知识库问答助手..."},{"role":"user","content":"Spring AI 如何配置 Embedding？"},{"role":"assistant","content":"Spring AI 配置 Embedding 需要..."}]}
```

### 使用流程

1. 上传文档到知识库（通过 `/api/documents/upload`）
2. 等待解析完成
3. 调用 `/api/finetune/{taskId}/estimate` 预估数据量
4. 调用 `/api/finetune/{taskId}/generate` 下载 JSONL 文件
5. 将 JSONL 上传到 OpenAI / 其他平台进行微调训练
6. 用微调后的模型回答同样的问题，对比 RAG 效果
