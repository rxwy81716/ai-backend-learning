# 一、Sentinel 是什么

阿里开源流量防护组件

**流量限流、熔断降级、热点限流、系统负载保护、网关限流**

微服务 + Gateway 必备，秒杀高并发核心防护

轻量无侵入，几乎不用改业务代码

四大核心功能：

1. **限流**：限制接口 QPS，不让服务器打崩
2. **熔断**：下游故障，快速熔断，避免雪崩
3. **降级**：压力过高 / 异常过多，走兜底逻辑
4. **热点参数限流**：热门商品 ID 单独限流

# 二、核心概念理解（底层原理）

## 1、资源

被保护的接口、方法、Redis、MQ 都叫**资源**

## 2、流量规则

- QPS 限流：每秒最多允许多少请求
- 线程数限流：同时最多多少线程处理

## 3、熔断降级三种策略

1. **慢调用比例**：响应太慢，熔断
2. **异常比例**：报错太多，熔断
3. **异常数**：报错次数超标，熔断

熔断状态：

- 关闭 → 慢 / 错多 → **打开熔断**（所有请求直接降级）
- 等待窗口期 → **半开状态**（放行少量请求试探）
- 正常 → 恢复关闭；异常 → 再次打开

## 4、热点限流

根据**请求参数值**限流

比如商品 ID=1001 秒杀热门，单独限制 QPS

## 5、网关流控

SpringCloud Gateway 专用

按**路由、API、IP**限流

# 三、快速整合 SpringBoot / Cloud

## 1、pom 依赖

```xml
<!-- sentinel 微服务 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>

<!-- sentinel 网关限流专用 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel-gateway</artifactId>
</dependency>
```

## 2、yml 配置

yaml

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8080  # 控制台地址
      eager: true # 立即加载，不懒加载
```

启动控制台，访问：`http://localhost:8080`

默认账号密码：sentinel/sentinel

# 四、1、接口限流实战（注解方式 最简单）

```java
@RestController
@RequestMapping("/order")
public class OrderController {

    // 资源名：order_create
    // QPS超过阈值 直接限流降级
    @SentinelResource(value = "order_create", blockHandler = "orderBlock")
    @PostMapping("/create")
    public String createOrder() {
        return "下单成功";
    }

    // 限流降级兜底方法
    public String orderBlock(BlockException e) {
        return "系统繁忙，请稍后重试";
    }
}
```

然后去 Sentinel 控制台配置：**限流 QPS=2**

超过 2 次 / 秒，自动走兜底

------

# 五、2、熔断降级代码实战

```java
@SentinelResource(value = "pay", fallback = "payFallback")
@GetMapping("/pay")
public String pay() {
    // 模拟异常
    int a = 1/0;
    return "支付成功";
}

// 异常降级兜底
public String payFallback(Throwable e) {
    return "支付繁忙，已降级，请稍后重试";
}
```

控制台配置熔断：

- 慢调用比例 / 异常比例
- 熔断时间窗口：5~10 秒

------

# 六、3、热点参数限流（秒杀必用）

根据**商品 ID 参数**限流

```java
@SentinelResource(value = "hotGoods", blockHandler = "hotBlock")
@GetMapping("/goods/{goodsId}")
public String goods(@PathVariable Long goodsId) {
    return "商品查询成功";
}

public String hotBlock(Long goodsId, BlockException e) {
    return "热门商品繁忙，请排队";
}
```

控制台配置**热点规则**

指定参数索引 0，设置阈值

热门 ID 单独限流，不影响其他商品

------

# 七、4、Gateway 网关流控（重点高频）

网关支持 4 种限流维度

1. 路由维度限流（整个服务）
2. 接口 URL 维度限流
3. IP 来源限流
4. 自定义 API 分组限流

yml 无需额外代码，控制台直接配置

自动保护整个网关入口，防止秒杀流量冲垮服务

------

# 八、底层深度原理（源码级拆解）

1. Sentinel 基于

   滑动窗口算法

   统计 QPS

   不用定时器，性能极高，无毛刺

2. 限流底层：**令牌桶 + 滑动窗口**

3. 熔断状态机：关闭 → 打开 → 半开 → 关闭

4. 非阻塞统计，几乎不损耗接口性能

5. AOP 切面拦截所有 @SentinelResource 资源

6. 实时心跳上报控制台，动态修改规则**不用重启服务**

## 核心优势

- 轻量、无额外中间件
- 规则动态推送，不用重启项目
- 低性能损耗
- 网关 + 微服务统一管控

------

# 九、限流 vs 熔断 vs 降级 彻底分清（面试必考）

1. **限流**

   流量太大 → 直接拒绝新请求

   保护接口不被打满

2. **熔断**

   下游大量超时 / 报错 → 切断调用

   防止故障扩散，避免**服务雪崩**

3. **降级**

   压力过高，关闭非核心业务

   只保留核心下单、支付

   返回兜底文案，保证核心可用

一句话区分：

- 限流：挡**洪水流量**
- 熔断：救**下游故障**
- 降级：保**核心可用**

------

# 十、全套高频深度面试标准答案

## 1、Sentinel 和 Hystrix 区别？

- Hystrix 停止维护
- Sentinel 性能更高、轻量
- Sentinel 支持网关限流、热点限流
- Hystrix 只有熔断降级，无限流
- Sentinel 规则动态配置，Hystrix 要重启

## 2、限流算法有哪些？

计数器、滑动窗口、令牌桶、漏桶

Sentinel 默认：**滑动窗口**

## 3、熔断三个状态生命周期？

关闭 → 打开熔断 → 时间窗口到 → 半开探测 → 正常关闭 / 再次打开

## 4、blockHandler 和 fallback 区别？

- blockHandler：**限流、熔断触发**
- fallback：**业务异常报错触发**

## 5、Sentinel 网关限流怎么做？

整合 sentinel-gateway，按路由 / IP/API 配置规则

## 6、什么是服务雪崩？怎么解决？

多个服务层层调用，下游故障大量超时堆积

线程耗尽 → 整个集群瘫痪

解决：**超时控制 + 熔断降级 + 限流**

## 7、热点限流作用？

秒杀爆款商品流量极高

单独限制热门参数，不影响普通商品

## 8、Sentinel 规则持久化？

默认内存模式，重启丢失

用 Nacos/Apollo 配置中心持久化规则

------

# 十一、线上大坑必避

1. blockHandler、fallback 方法参数顺序不能错
2. 熔断时间太短，频繁抖动
3. 没配置持久化，重启规则全部消失
4. 网关限流粒度太粗，误拦截正常接口
5. 热点参数索引写错，不生效