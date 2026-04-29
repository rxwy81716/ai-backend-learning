package com.jianbo.localaiknowledge.consumer;

import com.jianbo.localaiknowledge.service.DocumentParseService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 队列消费者
 *
 * <p>应用启动后自动拉取 doc:parse:queue 中的 taskId 并执行解析。 使用固定线程池控制并发数（默认 2 个消费线程）。
 *
 * <p>优势（对比 @Async）： 1. 任务持久化在 Redis，JVM 重启后未消费的任务仍在队列中 2. 天然背压：消费者按自身速度拉取，不会被撑爆 3.
 * 多实例部署时自动竞争消费（分布式负载均衡）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DocParseQueueConsumer {

  private final RedissonClient redissonClient;
  private final DocumentParseService documentParseService;

  private static final int CONSUMER_THREADS = 2;
  private final ExecutorService executor = Executors.newFixedThreadPool(CONSUMER_THREADS);

  private volatile boolean running = true;

  /** 应用完全启动后开始消费 用 ApplicationReadyEvent 而非 @PostConstruct，确保所有 Bean 就绪 */
  @EventListener(ApplicationReadyEvent.class)
  public void startConsumers() {
    log.info("启动 {} 个文档解析消费线程", CONSUMER_THREADS);
    for (int i = 0; i < CONSUMER_THREADS; i++) {
      final int threadIndex = i;
      executor.submit(() -> consumeLoop(threadIndex));
    }
  }

  /** 单个消费者循环 take() 阻塞等待，有任务时立即执行 */
  private void consumeLoop(int threadIndex) {
    Thread.currentThread().setName("doc-consumer-" + threadIndex);
    RBlockingQueue<String> queue = redissonClient.getBlockingQueue(DocumentParseService.QUEUE_NAME);

    log.info("[消费者-{}] 开始监听队列: {}", threadIndex, DocumentParseService.QUEUE_NAME);

    while (running) {
      try {
        // 带超时的 take，便于优雅关闭时退出循环
        String taskId = queue.poll(5, TimeUnit.SECONDS);
        if (taskId == null) {
          continue; // 超时无任务，继续等
        }

        log.info("[消费者-{}] 取到任务: {}", threadIndex, taskId);
        documentParseService.parseAndImport(taskId);

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.info("[消费者-{}] 被中断，退出", threadIndex);
        break;
      } catch (Exception e) {
        log.error("[消费者-{}] 处理异常: {}", threadIndex, e.getMessage(), e);
        // 不退出循环，继续消费下一个
      }
    }

    log.info("[消费者-{}] 已停止", threadIndex);
  }

  /** 优雅关闭 */
  @PreDestroy
  public void shutdown() {
    log.info("正在关闭文档解析消费者...");
    running = false;
    executor.shutdown();
    try {
      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
    log.info("文档解析消费者已关闭");
  }
}
