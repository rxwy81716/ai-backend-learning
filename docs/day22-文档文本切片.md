## 一、为什么要做文档切片？（必学）

1. 大模型上下文有限，**不能把整篇文档丢进去**
2. 向量检索：**片段越短，语义越精准**
3. 太长的文本 → 向量混乱 → 检索召回失败
4. 切片后才能存入 PostgreSQL 向量表

### 切片三大原则（背）

- 不破坏语义
- 控制单段长度（300~800 字最合适）
- 保留重叠区（防止上下文断裂）

## 二、文本切片策略（企业标准 3 种）

### 1. 固定长度切片（最简单、最常用）

按字数切，比如每 500 字一段

适合：笔记、md、txt、技术文档

### 2. 分隔符切片（按段落 / 换行切）

按 `\n\n` 双换行分段

适合：结构化强的文档

### 3. 语义切片（高级）

按句意智能切（本次先不用，复杂）

## 三、文本清洗规则（必须做）

1. 去掉多余空行
2. 去掉空格、制表符
3. 去掉特殊符号、乱码
4. 合并换行、统一格式

## 四、完整版 文本清洗 + 切片工具类（直接复制运行）

### 1. 切片常量配置

```
package com.jianbo.springai.constant; 
TextSplitConstants 
```

### 2. 文本清洗工具

```
package com.jianbo.springai.utils;
TextCleanUtil
```

### 3. 核心切片工具类（固定长度 + 重叠区）

```
package com.jianbo.springai.utils;

public class TextSplitterUtil 
```

## 五、使用示例（Controller 测试）

```
package com.jianbo.springai.controller;

public class RagSplitController
```

## 六、切片流程（手绘笔记图

```
原始长文档
    ↓
文本清洗（去空格、去空行、去乱码）
    ↓
固定长度切片（500字一段）
    ↓
重叠区保留（50字）
    ↓
得到 N 段干净文本
    ↓
下一步：Embedding 转向量
    ↓
存入 PostgreSQL + pgvector
```

------

## 七、企业 RAG 切片规范（背）

1. 单段长度：**300~800 字**
2. 重叠区：**50~100 字**
3. 必须清洗空白符号
4. 不切分句子中间（尽量保证句子完整）

## 八 智能切片 

```
package com.jianbo.springai.utils;
public class SentenceSplitter
```

