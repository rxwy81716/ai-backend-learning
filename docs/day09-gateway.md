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

# 四、手写自定义全局过滤器（Token 鉴权实战）

## 统一网关鉴权，所有服务不用再写 Token 校验

```Java
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Order(-100) // 最高优先级，最先执行
public class AuthTokenGlobalFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 1. 获取请求头Token
        String token = request.getHeaders().getFirst("token");

        // 2. 无token直接拒绝
        if (token == null || token.isEmpty()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 3. Token传递给下游微服务
        ServerHttpRequest newRequest = request.mutate()
                .header("gateway-token", token)
                .build();

        // 4. 放行继续执行过滤器链
        return chain.filter(exchange.mutate().request(newRequest).build());
    }
}
```

# 五、手写自定义局部路由过滤器

只作用订单服务，不影响用户、支付服务

```java
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderLogFilter extends AbstractGatewayFilterFactory<Object> {

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            // 请求前置：进入网关之前
            System.out.println("订单接口请求进来："+exchange.getRequest().getPath());

            // 转发微服务 + 后置处理
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                // 响应回来之后执行
                System.out.println("订单接口响应完成");
            }));
        };
    }
}
```

yml 绑定：

```yml
filters:
  - OrderLogFilter
```

# 五、底层深度完整执行流程（拆解源码级）

1. 客户端 HTTP 请求到达 Netty 端口
2. 交给 `DispatcherHandler` 网关中央调度器
3. 匹配所有 Route 路由信息
4. 依次执行**所有 Predicate 断言**，全部满足才匹配成功
5. 按 Order 排序，执行**所有前置过滤器**
6. Netty 异步转发请求到对应微服务
7. 微服务处理业务返回响应
8. 网关执行**所有后置过滤器**
9. 响应结果返回前端

底层架构：

- 线程模型：Netty EventLoop 事件驱动
- 非阻塞 IO：一个 IO 线程处理成千上万个连接
- 责任链：过滤器链式执行，不阻塞主线程

# 六、Gateway 高频深度面试题（满分标准答案）

## 1、Gateway 和 Zuul1 区别？

- Zuul1：Tomcat 同步阻塞 IO，线程池有限，QPS 低
- Gateway：Netty 异步非阻塞，QPS 是 Zuul1 **5~10 倍**
- Zuul 不支持长连接、不支持 WebSocket
- Gateway 原生支持 Sentinel 限流熔断

## 2、GlobalFilter 和 GatewayFilter 区别？

1. 作用范围：全局 vs 单个路由
2. 配置方式：代码注入自动生效 vs yml 绑定路由
3. 执行顺序统一由 Order 决定

## 3、lb:// 负载均衡原理？

整合 Nacos + Ribbon

自动拉取服务实例列表、健康检查

默认轮询、支持权重、随机负载

## 4、为什么请求 Body 只能读一次？

Gateway 底层是**Flux 数据流**，字节流一次性消费，读完关闭

解决方案：配置内置`ReadBodyPredicateFactory`缓存请求体

## 5、网关怎么解决跨域？

网关统一配置 CORS，一次配置所有服务生效

不用每个微服务单独配置跨域注解

## 6、网关怎么做限流？

整合 Sentinel

- IP 限流
- 路由维度限流
- 接口 QPS 限流
- 熔断降级

## 7、Gateway 线程模型？

Netty IO 线程池 + 业务线程池分离

IO 不阻塞业务，高并发不积压

## 8、StripPrefix 作用？

截断 URL 前缀，前端带 /api，后端服务不带 /api

避免 404 路径不匹配

## 9、网关如何保证高可用？

网关部署**多节点集群**，前端 Nginx 负载均衡网关

网关无状态，随便扩容

## 10、Gateway 同步还是异步？

全链路异步 WebFlux，不阻塞 Tomcat 线程

高并发秒杀、大流量首选

------

# 七、线上必踩大坑（必学）

1. 过滤器 Order 顺序错乱，鉴权执行太晚
2. 重复读取请求 Body 报错
3. 超时时间太短，慢接口直接断开
4. 没加 StripPrefix 全部 404
5. 网关没集群，单点故障
6. Token 不向下游传递，微服务鉴权失败
7. 跨域重复配置，浏览器报错

------

# 八、Gateway 配套必学下一个：Sentinel

限流 + 熔断 + 降级 + 热点参数限流 + 网关流控