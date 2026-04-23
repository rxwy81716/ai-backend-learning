# 一、Gateway 是什么 & 定位

微服务**唯一统一入口**

所有前端请求 → 先走网关 → 再转发到各个微服务

作用：

1. 路由转发、负载均衡

2. 统一鉴权 Token

3. 统一跨域、日志

4. 限流、熔断、防刷

5. 隐藏后端服务 IP，安全隔离

   底层：

   Netty + WebFlux + Reactor 异步非阻塞

   单机 QPS 10 万 +，吊打 Zuul1

------

# 二、三大核心组件（原理底层）

## 1、Route 路由

路由 ID + 转发地址 uri + 断言条件 + 过滤器链

结构：

- id：路由唯一编号
- uri：转发地址 `lb://服务名`（Nacos 负载均衡）
- predicates：匹配规则
- filters：过滤规则

## 2、Predicate 断言（匹配规则）

满足所有条件，才转发路由

内置常用全部：

- Path=/api/** 路径匹配
- Method=GET,POST 请求方式
- Header=token,.* 请求头匹配
- Cookie=name,xxx Cookie 匹配
- Query=id 参数匹配
- Before/After/Between 时间区间匹配

底层原理：**函数式断言工厂**，yml 自动解析断言规则

## 3、Filter 过滤器（责任链模式）

### ① GatewayFilter 局部过滤器

只对**当前配置路由生效**

### ② GlobalFilter 全局过滤器

**所有路由全部生效**

执行顺序：`@Order` 数值越小，越先执行

# 三、标准 YAML 配置

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service-route
          uri: lb://order-service
          predicates:
            - Path=/api/order/**
            - Method=GET,POST
          filters:
            # 去掉前缀 /api
            - StripPrefix=1
      # 统一跨域配置
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "*"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
```

