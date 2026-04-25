## 学习目标

1. 理解：大模型**无原生记忆**，多轮对话底层原理
2. 实战：手动拼接上下文、会话记忆
3. 落地：Redis 存储会话、上下文窗口维护、Token 截断防溢出
4. 面试：上下文超限问题、会话隔离、多轮消息结构

## 一、核心原理（0 基础必懂）

### 1、大模型没有长期记忆

LLM 单次调用是**无状态**的

你发一次问题 → 模型回答一次 → 连接断开

**不主动保存任何聊天记录**

### 2、多轮对话怎么实现？

每一次新请求，必须手动携带：

```
系统角色 + 历史用户提问 + 历史AI回答 + 当前新问题
```

把所有上下文一次性传给大模型，模型才知道前面聊了什么。

### 3、核心结构 三种消息

```
SystemMessage   系统提示（固定角色、规则）
UserMessage     用户每一轮提问
AssistantMessage AI每一轮回复
```

多轮 = 以上三类消息有序拼接。

### 4、上下文窗口限制（关键坑）

所有消息累加 Token 不能超过模型上限（8K/32K）

超限后果：

- 直接报错
- 自动截断最前面记录
- 回答失忆、逻辑混乱、乱答

## 二、开发实现方案

1. 每个用户 / 会话分配唯一 `sessionId`
2. 用 Redis 序列化存储当前会话完整消息列表
3. 每次提问：
   - 根据 sessionId 查出历史消息
   - 追加当前用户问题
   - 校验、裁剪上下文（防止超 Token）
   - 调用大模型
   - 拿到 AI 回答，存入消息列表
   - 重新写回 Redis，完成记忆续存

## 三、完整代码实战（可直接运行）

详见
D:\work\ai-backend-learning\demo-springai\src\main\java\com\jianbo\springai\controller\ChatSessionController
ChatSessionCache 

## 四、核心 Service：拼接上下文 + 窗口维护

ChatSessionService 


## 六、上下文窗口 两种维护方案（工作必用）

### 方案 1：按消息条数限制（简易版，上面代码已实现）

- 限制最大聊天轮次
- 优点：简单、性能高
- 缺点：不能精准控制 Token

### 方案 2：按 Token 数量截断（生产高级版）

1. 每次对话统计所有消息总 Token
2. 接近模型上限 80% 时
3. 移除最早一轮问答
4. 循环裁剪直到安全范围

> 企业级 RAG 项目必用，防止超限报错

------

## 七、面试必背总结

1. 多轮对话底层原理

   大模型无状态、无记忆；通过**拼接历史消息 + 当前问题**实现上下文，会话数据缓存到 Redis。

   

2. 三种消息作用

   

- SystemMessage：固定角色、约束回答规范
- UserMessage：用户每轮提问
- AssistantMessage：AI 历史回复，补齐对话上下文

1. 上下文窗口维护怎么做

   限制消息数量 / 统计 Token 裁剪旧内容，避免超出模型上下文长度，防止回答失忆、报错。

   

2. 为什么要用 sessionId

   隔离不同用户、不同会话，保证每个人聊天记忆互不干扰。



# **按 Token 数量精准截断【生产级完整版】**

零基础能看懂 + 原理 + 完整可运行代码 + 面试必背

区别于简单「按条数截断」，这是企业 RAG / 多轮对话 标准做法。

------

## 一、为什么不能只按消息条数截断

1. 每条对话长短不一样

- 有的问题几十个字，有的上千字
- 固定保留 15 条，依然会 **Token 爆掉、超限报错**

1. 模型有

   最大上下文窗口

   （8K/32K/128K）

   必须

   实时计算总 Token

   ，超过阈值就删掉最旧对话

2. 大厂生产规范：

   以 Token 为单位控制上下文，不是消息条数

------

## 二、核心原理（必记）

1. 引入 **Tokenizer 分词器**，每条消息精确算出 Token 数量

2. 设定**安全阈值**（例如 32K 模型，用到 80% 就裁剪）

3. 计算：

   ```
   总Token = 系统提示Token + 所有历史问答Token + 当前问题Token
   ```

4. 超限逻辑：

- 永远保留 **System 系统提示** 不删
- 从**最早的一轮问答**开始删除（user+assistant 成对删除）
- 循环裁剪，直到总 Token ＜ 安全阈值

------

## 三、SpringAI 自带 Token 计算器（直接用）

SpringAI 内置 `Tokenizer`，不需要自己写分词算法

```
// 注入系统自带分词器
@Resource
private Tokenizer tokenizer;
```

核心方法：

```
// 文本 → 计算当前多少 Token
int tokenCount = tokenizer.count(text);
```

------

## 四、完整工具类：计算消息列表总 Token



```
/**
 * 计算整条会话所有消息总Token
 */
public int countAllMessagesToken(List<Message> messageList) {
    int total = 0;
    for (Message msg : messageList) {
        total += tokenizer.count(msg.getContent());
    }
    return total;
}
```

------

## 五、生产级：按 Token 截断完整逻辑代码

### 1、配置全局常量

```
// 模型最大上下文 32K
private static final int MAX_CONTEXT_TOKEN = 32768;
// 安全水位：用到80%就裁剪（防止溢出）
private static final int SAFE_TOKEN_LIMIT = 26214;
```

### 2、核心裁剪逻辑（重点）

```
/**
 * 按Token数量截断上下文
 * 规则：
 * 1. 保留 System 系统提示不删除
 * 2. 从最旧的一轮问答开始成对删除
 * 3. 循环裁剪到总Token小于安全阈值
 */
public List<Message> trimMessagesByToken(List<Message> messageList) {
    // 循环判断：只要超阈值就裁剪
    while (countAllMessagesToken(messageList) > SAFE_TOKEN_LIMIT) {
        // 找到第一条非System消息（最早的用户提问）
        int firstUserIndex = -1;
        for (int i = 0; i < messageList.size(); i++) {
            Message msg = messageList.get(i);
            if (!(msg instanceof SystemMessage)) {
                firstUserIndex = i;
                break;
            }
        }

        // 找不到可删除内容，直接退出
        if (firstUserIndex == -1) {
            break;
        }

        // 成对删除：user + 后面对应的assistant
        // 先删最早user
        messageList.remove(firstUserIndex);
        // 再删下一条assistant
        if (firstUserIndex < messageList.size()) {
            messageList.remove(firstUserIndex);
        }
    }
    return messageList;
}
```

------

## 六、整合进多轮对话完整流程

```
// 1. 拼接 system + 历史 + 当前问题
List<Message> messageList = buildMessage(historyList);

// 2. 【关键】按Token自动裁剪上下文
messageList = trimMessagesByToken(messageList);

// 3. 构造Prompt调用大模型
Prompt prompt = new Prompt(messageList);
ChatResponse response = chatModel.call(prompt);
```

------

## 七、两种截断方案对比（面试必考）

|         方案          |        实现         |             优点             |              缺点               |
| :-------------------: | :-----------------: | :--------------------------: | :-----------------------------: |
|    按消息条数截断     |  限制保留 10/15 条  |       代码简单、性能高       | 长短文本不可控，容易 Token 溢出 |
| **按 Token 数量截断** | 分词计算 + 阈值裁剪 | 精准可控、生产标准、永不超限 |  多一步分词计算，消耗极小性能   |

> 面试标准答案：
>
> 多轮会话上下文维护，**采用 Token 粒度裁剪**，保留系统提示，老旧问答成对删除，保证不超模型上下文窗口，避免幻觉、截断报错。

------

## 八、极简背诵版（面试直接背）

1. 利用模型 Tokenizer 实时统计所有消息 Token 总数。
2. 设置上下文安全阈值，达到阈值触发裁剪。
3. 固定保留 System 系统提示。
4. 从最早的「用户提问 + AI 回复」成对移除。
5. 循环裁剪，直到总 Token 在安全范围内。

### 核心常量 + Token 工具 + 上下文裁剪工具

```java
com.jianbo.springai.utils.ChatContextUtil;
```

## 八、完整执行流程

1. 前端传入 `sessionId + 问题`
2. 根据 sessionId 从 Redis 读取历史对话
3. 不存在则初始化系统角色提示词
4. 追加当前用户提问
5. 第一层：限制最大消息条数，防止无限膨胀
6. 转为标准 Message 集合
7. 第二层：Tokenizer 统计总 Token，超阈值**成对删除老旧问答**
8. 拼接完整上下文请求大模型
9. 接收 AI 回答，存入会话
10. Redis 更新会话记录，维持长期记忆

------

## 九、面试必背完整版

1. 多轮对话如何实现？

   大模型无状态，通过 `sessionId` 隔离会话，Redis 持久化保存 System、User、Assistant 历史消息，每次请求拼接完整上下文实现记忆。

2. 上下文溢出怎么解决？

   双层方案：

- 轻量兜底：限制单会话最大消息数量；
- 精准控制：使用 SpringAI Tokenizer 实时计算总 Token，设定安全水位，**保留系统提示、成对删除最早问答**，循环裁剪保证不超上下文窗口。

1. 为什么要成对删除 User+Assistant？

   一轮完整对话是用户提问 + 模型回答，单独删除会导致上下文断裂、语义错乱。

2. Token 截断和条数截断区别？

   条数截断简单性能高，无法控制文本长短；Token 截断精准匹配模型窗口上限，是线上 RAG、多轮对话标准方案。