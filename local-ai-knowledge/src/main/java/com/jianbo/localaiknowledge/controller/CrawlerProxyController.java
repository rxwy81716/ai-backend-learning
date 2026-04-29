package com.jianbo.localaiknowledge.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

/**
 * 爬虫服务代理控制器（仅管理员可用）
 *
 * <p>将前端请求转发到 local-ai-crawler 服务（:12117）， 避免前端直连爬虫服务，统一走 knowledge 的认证和鉴权。
 *
 * <p>接口前缀：/api/admin/crawler/**
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/crawler")
public class CrawlerProxyController {

  private final RestClient crawlerClient;

  private static final ParameterizedTypeReference<Object> OBJECT_TYPE =
      new ParameterizedTypeReference<>() {};

  public CrawlerProxyController(@Value("${app.crawler.base-url}") String crawlerBaseUrl) {
    this.crawlerClient = RestClient.builder().baseUrl(crawlerBaseUrl).build();
  }

  // ==================== 数据源 ====================

  /** 查看所有已注册的数据来源 */
  @GetMapping("/sources")
  public Object listSources() {
    return crawlerClient
        .get()
        .uri("/api/crawler/sources")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(OBJECT_TYPE);
  }

  // ==================== 手动触发 ====================

  /** 手动触发指定来源的完整流水线 */
  @PostMapping("/execute/{source}")
  public Object execute(@PathVariable String source) {
    log.info("管理员手动触发爬虫：{}", source);
    return crawlerClient
        .post()
        .uri("/api/crawler/execute/{source}", source)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(OBJECT_TYPE);
  }

  /** 手动触发全量采集 */
  @PostMapping("/execute-all")
  public Object executeAll() {
    log.info("管理员手动触发全量爬虫");
    return crawlerClient
        .post()
        .uri("/api/crawler/execute-all")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(OBJECT_TYPE);
  }

  // ==================== 运行状态 ====================

  /** 查看爬虫运行状态 */
  @GetMapping("/stats")
  public Object stats() {
    return crawlerClient
        .get()
        .uri("/api/crawler/stats")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(OBJECT_TYPE);
  }

  // ==================== 任务日志 ====================

  /** 查询最近任务日志 */
  @GetMapping("/logs")
  public Object logs(@RequestParam(defaultValue = "50") int limit) {
    return crawlerClient
        .get()
        .uri("/api/crawler/logs?limit={limit}", limit)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(OBJECT_TYPE);
  }

  /** 查询指定来源日志 */
  @GetMapping("/logs/{source}")
  public Object logsBySource(
      @PathVariable String source, @RequestParam(defaultValue = "30") int limit) {
    return crawlerClient
        .get()
        .uri("/api/crawler/logs/{source}?limit={limit}", source, limit)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(OBJECT_TYPE);
  }

  /** 今日执行统计 */
  @GetMapping("/logs/today-stats")
  public Object logsTodayStats() {
    return crawlerClient
        .get()
        .uri("/api/crawler/logs/today-stats")
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(OBJECT_TYPE);
  }
}
