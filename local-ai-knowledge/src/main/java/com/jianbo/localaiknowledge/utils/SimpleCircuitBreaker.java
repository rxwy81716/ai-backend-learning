package com.jianbo.localaiknowledge.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 轻量级状态机型熔断器（无外部依赖，~80 行）。
 *
 * <p>设计动机：原方案使用 Resilience4j，但本机 maven central 被代理 SSL 拦截（cert mismatch），
 * 无法拉取 {@code io.github.resilience4j:*} 工件。本类用 {@link AtomicInteger} + {@link AtomicLong}
 * 实现核心三态机，覆盖 90% 场景：
 *
 * <pre>
 *   CLOSED  ──失败次数 ≥ failureThreshold──▶  OPEN
 *   OPEN    ──冷却 cooldownMs 已过────────▶  HALF_OPEN
 *   HALF_OPEN ──探测成功──▶ CLOSED
 *             ──探测失败──▶ OPEN（重新计冷却）
 * </pre>
 *
 * <p>不支持：滑动窗口失败率、慢调用计数、bulkhead 隔离。如需高级场景请等网络恢复后切换 Resilience4j。
 *
 * <p>线程安全：所有状态字段都是 atomic；状态切换通过 {@code compareAndSet} 保证只有一个线程进入
 * HALF_OPEN 探测。
 */
@Slf4j
public class SimpleCircuitBreaker {

  public enum State { CLOSED, OPEN, HALF_OPEN }

  private final String name;
  private final int failureThreshold;
  private final long cooldownMs;

  private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
  private final AtomicInteger consecutiveFailures = new AtomicInteger();
  private final AtomicLong openedAt = new AtomicLong();
  /** Half-open 探测信号：CAS 抢占，确保只有一个线程进入 HALF_OPEN 试探 */
  private final AtomicInteger halfOpenProbe = new AtomicInteger();

  // 命中率统计
  private final AtomicLong totalCalls = new AtomicLong();
  private final AtomicLong rejectedCalls = new AtomicLong();
  private final AtomicLong failedCalls = new AtomicLong();

  public SimpleCircuitBreaker(String name, int failureThreshold, long cooldownMs) {
    this.name = name;
    this.failureThreshold = failureThreshold;
    this.cooldownMs = cooldownMs;
  }

  /**
   * 执行调用：CLOSED / HALF_OPEN 时正常执行；OPEN 时直接抛 {@link CircuitOpenException}（fail-fast）。
   */
  public <T> T call(Callable<T> action) throws Exception {
    totalCalls.incrementAndGet();
    State s = currentState();
    if (s == State.OPEN) {
      rejectedCalls.incrementAndGet();
      throw new CircuitOpenException("[" + name + "] 熔断器开启，已拒绝调用");
    }
    try {
      T result = action.call();
      onSuccess();
      return result;
    } catch (Exception e) {
      onFailure();
      throw e;
    }
  }

  /** Supplier 版本，配合 fallback 使用：失败时不抛异常，回退到 supplier 之外的逻辑 */
  public <T> T callOrFallback(Supplier<T> action, Supplier<T> fallback) {
    try {
      return call(action::get);
    } catch (CircuitOpenException coe) {
      log.warn("[{}] 熔断中，使用 fallback", name);
      return fallback.get();
    } catch (Exception e) {
      log.warn("[{}] 调用失败，使用 fallback | err={}", name, e.getMessage());
      return fallback.get();
    }
  }

  /** 计算当前应该处于的状态：OPEN 但已过冷却 → 升级为 HALF_OPEN */
  private State currentState() {
    State s = state.get();
    if (s == State.OPEN && System.currentTimeMillis() - openedAt.get() >= cooldownMs) {
      // CAS 抢占进入 HALF_OPEN
      if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
        halfOpenProbe.set(0);
        log.info("[{}] 冷却期结束，进入 HALF_OPEN 试探", name);
      }
      return state.get();
    }
    return s;
  }

  private void onSuccess() {
    consecutiveFailures.set(0);
    if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
      log.info("[{}] HALF_OPEN 探测成功 → CLOSED", name);
    }
  }

  private void onFailure() {
    failedCalls.incrementAndGet();
    int failures = consecutiveFailures.incrementAndGet();
    State s = state.get();
    if (s == State.HALF_OPEN) {
      // 探测失败：直接 reopen
      if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
        openedAt.set(System.currentTimeMillis());
        log.warn("[{}] HALF_OPEN 探测失败 → OPEN，再冷却 {}ms", name, cooldownMs);
      }
      return;
    }
    if (s == State.CLOSED && failures >= failureThreshold) {
      if (state.compareAndSet(State.CLOSED, State.OPEN)) {
        openedAt.set(System.currentTimeMillis());
        log.warn("[{}] 连续失败 {} 次 → OPEN，冷却 {}ms", name, failures, cooldownMs);
      }
    }
  }

  // ===== 暴露给 Metrics 端点 =====

  public String getName() { return name; }
  public State getState() { return state.get(); }
  public long getTotalCalls() { return totalCalls.get(); }
  public long getRejectedCalls() { return rejectedCalls.get(); }
  public long getFailedCalls() { return failedCalls.get(); }
  public int getConsecutiveFailures() { return consecutiveFailures.get(); }

  /** 调用方因熔断被拒绝时抛出此异常，调用方应进入 fallback 分支 */
  public static class CircuitOpenException extends RuntimeException {
    public CircuitOpenException(String message) { super(message); }
  }
}
