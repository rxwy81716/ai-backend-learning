package com.jianbo.gatewaysentinel.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order")
public class OrderController {

  // 资源名: order_create
  // QPS超过阈值,直接限流降级
  // 然后去 Sentinel 控制台配置：限流 QPS=2超过 2 次 / 秒，自动走兜底方法 orderBlock
  @SentinelResource(value = "order_create", blockHandler = "orderBlock")
  @PostMapping("/create")
  public String createOrder() {
    return "下单成功";
  }

  // 限流降级方法
  public String orderBlock() {
    return "下单失败，系统繁忙，请稍后再试";
  }

  // 控制台配置熔断：
  // 慢调用比例 / 异常比例
  // 熔断时间窗口：5~10 秒
  @SentinelResource(value = "pay", fallback = "payFallback")
  @GetMapping("/pay")
  public String pay() {
    // 模拟异常
    int a = 1 / 0;
    return "支付成功";
  }

  // 异常降级兜底
  public String payFallback(Throwable e) {
    return "支付繁忙，已降级，请稍后重试";
  }

  //控制台配置热点规则指定参数索引 0，设置阈值热门 ID 单独限流，不影响其他商品
  @SentinelResource(value = "hotGoods", blockHandler = "hotBlock")
  @GetMapping("/goods/{goodsId}")
  public String goods(@PathVariable Long goodsId) {
    return "商品查询成功";
  }

  public String hotBlock(Long goodsId, BlockException e) {
    return "热门商品繁忙，请排队";
  }
}
