package com.jianbo.gatewaysentinel.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class OrderLogFilter extends AbstractGatewayFilterFactory<Object> {
  @Override
  public GatewayFilter apply(Object config) {
    return (exchange, chain) -> {
      // 请求前置:进入网关之前
      System.out.println("订单接口请求进来：" + exchange.getRequest().getPath());
      // 转发微服务 + 后置处理
      return chain
          .filter(exchange)
          .then(
              Mono.fromRunnable(
                  () -> {
                    System.out.println("订单接口响应完成");
                  }));
    };
  }
}
