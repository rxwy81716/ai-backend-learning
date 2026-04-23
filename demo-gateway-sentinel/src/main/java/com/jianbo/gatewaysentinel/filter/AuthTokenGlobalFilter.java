package com.jianbo.gatewaysentinel.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-100)
public class AuthTokenGlobalFilter implements GlobalFilter {
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    // 获取请求头中的token
    ServerHttpRequest request = exchange.getRequest();
    String token = request.getHeaders().getFirst("token");

    // 如果token为空 则拒绝
    if (token == null) {
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }

    // 添加新的请求头 传递给下游服务
    ServerHttpRequest newRequest = request.mutate().header("gateway-token", token).build();
    // 继续执行后续过滤器
    return chain.filter(exchange.mutate().request(newRequest).build());
  }
}
