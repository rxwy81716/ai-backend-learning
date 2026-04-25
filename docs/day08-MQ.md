# RocketMQ & Kafka 架构 + 生产者消费者

1. ## 一、先懂：消息队列到底干嘛用？

   1. **异步解耦**：下单不用立刻扣库存、支付、物流，发给 MQ 就行
   2. **削峰填谷**：秒杀一瞬间流量太高，MQ 缓冲，下游慢慢消费
   3. **流量异步分发**：一条消息多个服务同时处理
   4. **重试兜底**：下游服务挂了，消息不会丢，恢复继续消费

   ------

   # 二、Kafka 深入浅出（自己理解版）

   ## 1. 整体架构角色

   1. **Producer 生产者**：发消息
   2. **Broker**：一台 Kafka 服务器
   3. **Topic 主题**：消息分类，比如订单消息、支付消息
   4. Partition 分区
      - 一个 Topic 拆成 N 个小队列
      - 分区越多，并发越高、消费越快
      - **分区内消息绝对有序，分区间无序**
   5. **Replica 副本**：主分区 + 副本分区，一台宕机不丢消息
   6. Consumer Group 消费组
      - 组内：1 个分区只能被**1 个消费者**消费（不重复）
      - 不同组：互不影响，同一条消息所有人都消费
   7. **Offset 偏移量**：消息编号，记录消费到哪一条了
   8. ZooKeeper：存集群元数据、消费位点、分区分配

   ## 2. Kafka 生产者流程（通俗理解）

   1. 生产者拿到 Broker 路由

   2. 根据消息 Key 哈希计算，发到

      指定 Partition

      - 无 Key：轮询发送，负载均衡

   3. 批量发送 + 压缩，提高吞吐量

   4. 等待 ISR 副本全部写入成功，才返回发送成功

   5. 失败自动重试

   ## 3. Kafka 消费者流程

   1. 消费者拉取自己分区的消息（Pull 模式）

   2. 消费完成，更新 Offset 消费位点

   3. 消费者上下线 → 

      Rebalance 重平衡

      重新分配分区给谁消费

   4. 分区内顺序消费，保证顺序

   ## 4. Kafka 底层特点（自己理解）

   - 顺序：**分区内有序，全局无序**
   - 吞吐极高，日志、大数据、埋点首选
   - 消息默认持久化磁盘，顺序写磁盘所以飞快
   - 不自带重试、死信、事务消息，需要业务自己实现

   ------

   # 三、RocketMQ 深入浅出（自己理解版）

   ## 1. 架构角色

   1. NameServer

      ：超轻注册中心

      只存 Broker 地址、路由信息，不做选举、不复杂

   2. Producer Group 生产者组

   3. Broker：Master 主节点、Slave 从节点

   4. Topic 主题

   5. **MessageQueue 队列** = Kafka Partition

   6. Consumer Group 消费组

   7. Tag：消息标签，同一个 Topic 按标签过滤

   ## 2. RocketMQ 生产者流程

   1. 先从 NameServer 获取 Broker 队列路由
   2. 轮询选择队列发送，均匀负载
   3. 支持三种发送方式
      - 同步：发完等结果，可靠
      - 异步：不等待，高吞吐
      - 单向：只管发，不确认
   4. 自带消息重试、失败重试机制

   ## 3. RocketMQ 消费者流程

   1. 底层 Pull，上层封装 Push，感觉像主动推给你
   2. **集群消费**：一个队列一个消费者，不重复消费
   3. **广播消费**：所有消费者都消费同一条消息
   4. 消费失败 → 自动进入重试队列
   5. 多次重试失败 → 进入**死信队列 DLQ**

   ## 4. RocketMQ 天生强大特性（理解记忆）

   1. 支持**事务消息**：分布式事务最终一致性
   2. 支持**定时 / 延时消息**：秒杀订单超时关闭
   3. 天然重试队列、死信队列
   4. 同一个队列严格全局顺序消息
   5. NameServer 简单稳定，不依赖 ZK，集群更稳

   ------

   # 四、两者核心区别（理解 + 面试两用）

   1. 注册中心

      - Kafka：Zookeeper，重、复杂、选举麻烦
      - RocketMQ：NameServer，轻量、高可用、简单

   2. 消息类型

      - Kafka：普通消息为主
      - RocketMQ：事务、延时、顺序消息全能

   3. 消费重试

      - Kafka：业务自己处理
      - RocketMQ：内置重试 + 死信

   4. 适用场景

      - Kafka：大数据日志、埋点、高吞吐流水线
      - RocketMQ：电商订单、秒杀、支付、金融业务

      

   ------

   # 五、MQ 必懂三大坑（必须吃透，不是死记）

   ## 1. 消息丢失

   - 生产者没刷盘、Broker 宕机
   - 消费者消费完没提交 offset，服务重启重复消费 / 丢失

   ## 2. 消息重复消费

   网络超时、重试机制导致

   **解决方案：业务幂等！唯一 ID 去重**

   ## 3. 消息积压

   消费速度 < 生产速度

   解决：增加消费者、优化消费逻辑、批量消费

## 一、消息重复消费 90% 业务必踩坑

### 为什么会重复？

1. 生产者发送消息，Broker**收到了但回执超时**
2. 生产者以为失败，**重试再次发送** → 两条一模一样消息
3. 消费者处理完业务，**还没提交 offset 就宕机**
4. 重启后 MQ 重新投递这条消息

### 根本原则：

**MQ 天生不保证不重复，只保证至少投递一次**

所以**所有 MQ 消费业务必须做幂等**

### 幂等实战方案（从小到大）

1. 数据库唯一主键

   订单号、消息 ID 建唯一索引，重复插入直接报错忽略

2. Redis 防重

   消费前 setnx msgId 过期一天，存在就不处理

3. 状态机判断

   订单只有待支付→已支付，重复支付直接跳过

   简单、稳定、零成本

------

## 二、消息丢失 全链路排查（RocketMQ/Kafka 通用）

MQ 消息丢失只会 3 个位置：

### 1. 生产者丢失

- 单向消息：发完不管，极易丢

- 同步刷盘关闭，Broker 宕机内存消息没进磁盘

  解决：同步刷盘 + 重试机制

### 2. Broker 服务丢失

- Master 宕机，消息没同步到 Slave

- Kafka ISR 收缩、副本同步失败

  解决：多副本架构，Master-Slave 高可用

### 3. 消费者丢失

消费逻辑执行完 → 业务成功

但是**还没提交 offset，服务宕机**

重启后 MQ 重新消费

**解决：先消费处理业务，业务成功再手动提交 offset**

------

## 三、消息大量积压 原理 + 根治方案

### 现象

生产速度 >>> 消费速度，队列越堆越多

### 为什么积压？

1. 消费者太少，分区 / 队列数量不够
2. 消费逻辑慢：查库、IO、网络调用耗时
3. 消费异常频繁重试，无限阻塞
4. 消费组 Rebalance 频繁抖动

### 正经解决方案（工作真实操作）

1. 增加消费者数量

   RocketMQ/Kafka：消费者 ≤ 队列数，多了没用

2. 批量消费，不要一条一条处理

3. 优化消费逻辑：异步、减少 DB 查询

4. 死信隔离：失败消息扔进死信，不堵正常队列

5. 紧急堆积：临时扩容分区，临时加消费节点

------

## 四、RocketMQ 事务消息（分布式最终一致性 核心）

电商下单：

下单成功 → 扣库存 → 支付

分布式事务，不能强一致，用 MQ 事务消息

### 流程理解

1. 生产者发送**半消息**（Broker 收到，但是消费者看不到）

2. 本地数据库业务执行

3. 执行成功：提交消息，消费者可见

4. 执行失败：回滚删除消息

5. Broker 定时回查生产者：本地事务到底成功没？

   最终保证：

   本地 DB 成功 ↔ MQ 消息一定投递

这就是阿里电商秒杀、支付标配方案

------

## 五、顺序消息 通俗易懂原理

### Kafka

- 一个 Partition 内**全局严格有序**
- 多个 Partition 无序
- 想要全局顺序：只能 1 个分区，并发直接废掉

### RocketMQ

- 同一个 MessageQueue 严格有序
- 相同业务 ID 路由到同一个队列
- 支持局部顺序，也支持全局顺序
- 秒杀下单、订单状态流转必须顺序消费

------

## 六、RocketMQ vs Kafka 深度理解（不是背诵）

1. **注册中心**

   Kafka ZK：重、强一致、集群选举复杂

   RocketMQ NameServer：极简，只存路由，无选举，超高可用

2. 吞吐

   Kafka 吞吐更高，顺序写极强，适合**日志、大数据采集**

   RocketMQ 吞吐够用，**金融可靠、业务场景更强**

3. 原生功能

   Kafka：几乎零内置功能，重试、死信、延时都要自己开发

   RocketMQ：延时消息、事务消息、重试队列、死信全部内置

4. 消费模式

   Kafka：只有集群消费

   RocketMQ：集群消费 + 广播消费

5. 顺序

   两者都是**分区 / 队列内有序，全局无序**

   

------

## 七、一句话底层灵魂总结

1. Kafka：**大数据流水线工具**，快、简单、海量日志
2. RocketMQ：**企业业务中间件**，稳、可靠、电商金融全能
3. MQ 所有问题绕不开：**重复、丢失、积压、顺序**
4. 重复靠幂等，丢失靠刷盘 + 副本，积压靠扩容 + 批量



# 四、幂等消费 3 套实战代码（SpringBoot+RocketMQ/Kafka 通用）

## 方案 1：数据库唯一索引（最稳、电商订单必用）

订单号做主键 / 唯一索引，重复插入直接报错忽略

```Java
@Service
public class OrderConsumer {

    @RocketMQMessageListener(topic = "order_topic", consumerGroup = "order_group")
    public void consumer(OrderMsg msg) {
        // 订单唯一编号
        String orderNo = msg.getOrderNo();
        
        // 插入消费记录表，唯一索引
        try {
            orderLogMapper.insert(new ConsumeLog(orderNo));
            // 执行业务逻辑：扣库存、改状态
            handleOrder(msg);
        } catch (DuplicateKeyException e) {
            // 唯一索引冲突 = 已经消费过，直接忽略
            log.info("消息重复，幂等跳过：{}", orderNo);
        }
    }
}
```

## 方案 2：Redis SETNX 幂等（高性能、无 DB 压力）

```Java
@RocketMQMessageListener(topic = "order_topic", consumerGroup = "order_group")
public void consumer(OrderMsg msg) {
    String orderNo = msg.getOrderNo();
    String key = "mq:consume:" + orderNo;

    // 设置过期时间，避免Redis永久堆积
    Boolean result = redisTemplate.opsForValue().setIfAbsent(key, "1", 24, TimeUnit.HOURS);

    if (Boolean.TRUE.equals(result)) {
        // 首次消费，执行业务
        handleOrder(msg);
    } else {
        // 重复消息，直接丢弃
        log.info("Redis幂等，重复消息跳过");
    }
}
```

## 方案 3：业务状态机幂等（最简单、不用额外表）

根据订单状态判断，只处理待处理状态

```Java
public void handleOrder(OrderMsg msg) {
    Order order = orderMapper.selectByNo(msg.getOrderNo());
    
    // 只有待支付才处理，已支付/已取消直接跳过
    if (!OrderStatus.WAIT_PAY.equals(order.getStatus())) {
        return;
    }
    
    // 执行业务
    order.setStatus(OrderStatus.PAID);
    orderMapper.updateById(order);
}
```

------

# 一句话面试总结

1. 消息丢失：生产者可靠发送 + Broker 多副本 + 消费成功再提交 offset
2. 消息重复：MQ 无法避免，**业务必须幂等**
3. 消息积压：加消费者、批量消费、死信隔离、优化消费速度
4. 幂等三板斧：**唯一索引、Redis SETNX、状态判断**