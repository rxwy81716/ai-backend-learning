package com.jiabo.thread.day02;

import org.springframework.scheduling.config.Task;

import java.util.concurrent.*;

public class ThreadPoolDemo {
    public static void main(String[] args) throws InterruptedException {
        //创建线程池 核心线程数4，最大线程数8，线程空闲时间30秒，使用LinkedBlockingQueue队列
        ExecutorService pool = new ThreadPoolExecutor(4, 8, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10), Executors.defaultThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());

        for (int i = 1; i <= 20; i++) {
            final int taskId = i;
            pool.execute(() -> {
                System.out.println("任务 " + taskId + " 执行中，线程：" + Thread.currentThread().getName());
                try {
                    Thread.sleep(1000); // 模拟任务耗时
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }

        ThreadPoolExecutor executor = (ThreadPoolExecutor) pool;
        System.out.println("核心线程数: " + executor.getCorePoolSize());
        System.out.println("活跃线程数: " + executor.getActiveCount());
        System.out.println("任务完成数: " + executor.getCompletedTaskCount());

        pool.shutdown();
        pool.awaitTermination(1, TimeUnit.MINUTES);
        System.out.println("所有任务执行完毕");
    }
}
