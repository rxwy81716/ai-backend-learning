package com.jiabo.thread.day01;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * ExecutorServiceExample
 * 演示Java线程池的创建与使用
 * @author jiabo
 */
public class ExecutorServiceExample {
    public static void main(String[] args) throws Exception {

        // ==================== 线程池创建方式 ====================

        /**
         * 1. FixedThreadPool - 固定大小线程池
         * - 核心线程数 = 最大线程数 = 4
         * - 适合CPU密集型任务，固定并发数
         */
        ExecutorService fixedThreadPool = Executors.newFixedThreadPool(4);

        /**
         * 2. CachedThreadPool - 缓存线程池
         * - 核心线程数=0，最大线程数=Integer.MAX_VALUE
         * - 任务过多时会创建新线程，适合短时异步任务
         */
        ExecutorService cachedThreadPool = Executors.newCachedThreadPool();

        /**
         * 3. SingleThreadExecutor - 单线程池
         * - 只有一个工作线程，保证任务串行执行
         * - 适合需要线程安全的顺序执行场景
         */
        ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

        /**
         * 4. ScheduledThreadPool - 定时任务线程池
         * - 支持定时/周期性任务执行
         * - 核心线程数为2
         */
        ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(2);

        /**
         * 5. CustomPool - 自定义线程池（推荐生产使用）
         * 参数详解：
         *   corePoolSize: 核心线程数，池中保持的最小线程数
         *   maximumPoolSize: 最大线程数，池中允许的最大线程数
         *   keepAliveTime: 空闲线程存活时间
         *   unit: 时间单位
         *   workQueue: 任务队列（LinkedBlockingDeque无界队列）
         *   handler: 拒绝策略
         */
        ThreadPoolExecutor customPool = new ThreadPoolExecutor(
                2,              // corePoolSize: 核心线程数
                4,              // maximumPoolSize: 最大线程数
                60L,            // keepAliveTime: 空闲线程存活时间60秒
                TimeUnit.SECONDS, // unit: 时间单位
                new LinkedBlockingDeque<>(100), // workQueue: 队列容量100
                new ThreadPoolExecutor.CallerRunsPolicy() // handler: 拒绝策略-调用者执行
        );

        // ==================== 任务提交方式 ====================

        /**
         * submit() - 提交Runnable任务
         * 返回Future对象，可用于获取任务执行结果或取消任务
         */
        Future<?> future = fixedThreadPool.submit(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("任务完成");
        });

        // ==================== 批量任务提交 ====================

        /**
         * invokeAll() - 批量提交Callable任务
         * 阻塞等待所有任务完成，返回Future列表
         */
        List<Callable<Integer>> tasks = Arrays.asList(
                () -> 1,
                () -> 2,
                () -> 3
        );

        // 提交多个任务并获取结果
        List<Future<Integer>> results = fixedThreadPool.invokeAll(tasks);

        // 遍历获取结果
        for (Future<Integer> result : results) {
            System.out.println("任务结果: " + result.get());
        }

        // ==================== 定时任务执行 ====================

        /**
         * schedule() - 延迟执行任务
         * @param command 要执行的任务
         * @param delay 延迟时间
         * @param unit 时间单位
         */
        scheduledThreadPool.schedule(() ->
                System.out.println("延迟5秒后执行"), 5, TimeUnit.SECONDS);

        /**
         * scheduleAtFixedRate() - 固定频率周期执行
         * 无论上一个任务是否完成，下一个任务都会按固定间隔执行
         * @param command 要执行的任务
         * @param initialDelay 首次执行延迟
         * @param period 周期间隔
         * @param unit 时间单位
         */
        scheduledThreadPool.scheduleAtFixedRate(() ->
                System.out.println("每10秒执行一次"), 0, 10, TimeUnit.SECONDS);

        // ==================== 线程池关闭 ====================

        /**
         * shutdown() - 优雅关闭
         * - 不再接受新任务
         * - 等待已提交任务执行完成
         * - 已提交但未执行的任务将被取消
         */
        fixedThreadPool.shutdown();

        /**
         * awaitTermination() - 阻塞等待线程池终止
         * @param timeout 最大等待时间
         * @param unit 时间单位
         * @return true=已终止 false=超时
         */
        if (fixedThreadPool.awaitTermination(60, TimeUnit.SECONDS)) {
            // 线程池已终止
            System.out.println("线程池已正常终止");
        } else {
            // 超时，强制关闭
            fixedThreadPool.shutdownNow();
        }
    }
}
