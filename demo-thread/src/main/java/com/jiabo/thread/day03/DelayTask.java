package com.jiabo.thread.day03;

import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 延迟队列 演示
 * 延迟队列 DelayQueue
 * 延迟队列中的元素必须实现Delayed接口，可比较，可计算剩余延迟时间
 */
public class DelayTask implements Runnable, Delayed{
    private long delayTime; // 延迟时间（毫秒）
    private long executeTime; // 实际执行时间
    private String taskName;

    public DelayTask(long delayTime, String taskName) {
        this.delayTime = delayTime;
        this.taskName = taskName;
        this.executeTime = System.currentTimeMillis() + delayTime;
    }

    // 计算剩余延迟时间
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(executeTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    // 按延迟时间排序
    @Override
    public int compareTo(Delayed o) {
        return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
    }

    @Override
    public void run() {
        System.out.println("延迟" + delayTime + "ms执行任务：" + taskName);
    }
}

// 线程池使用延迟队列
 class DelayQueueDemo {
    public static void main(String[] args) {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        // 提交延迟任务
        executor.schedule(new DelayTask(1000, "任务1"), 1000, TimeUnit.MILLISECONDS);
        executor.schedule(new DelayTask(3000, "任务2"), 3000, TimeUnit.MILLISECONDS);
        executor.shutdown();
    }
}
