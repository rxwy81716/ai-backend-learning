package com.jiabo.thread.day04;

import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class UnSafeCount {
    // 共享变量
    private static int count = 0;

    public static void main(String[] args) throws InterruptedException {
        // 开启1000个线程，每个线程累加1000次
        Thread[] threads = new Thread[1000];
        for (int i = 0; i < 1000; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    count++;
                }
            });
            threads[i].start();
        }

        // 等待所有线程执行完毕
        for (Thread t : threads) {
            t.join();
        }

        // 预期：1000000，实际永远小于这个数
        System.out.println("最终计数：" + count);
        // 解释：多个线程同时访问和修改共享变量count，导致线程安全问题
        // 解决方法：使用synchronized关键字对count变量进行同步访问
    }
}
class SyncSafeCount {
    private static int count = 0;

    // 方法加锁，同一时间只有一个线程执行
    public synchronized static void add() {
        count++;
    }

    public static void main(String[] args) throws InterruptedException {
        Thread[] threads = new Thread[1000];
        for (int i = 0; i < 1000; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    add();
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) t.join();

        System.out.println("synchronized安全计数：" + count);
    }
}

class LockSafeCount {
    private static int count = 0;
    private static final Lock lock = new ReentrantLock();

    public static void add() {
        lock.lock(); // 上锁
        try {
            count++;
        } finally {
            lock.unlock(); // 必须finally释放，防止异常死锁
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread[] threads = new Thread[1000];
        for (int i = 0; i < 1000; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    add();
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) t.join();

        System.out.println("ReentrantLock安全计数：" + count);
    }
}

class LongAdderCount {
    private static final LongAdder count = new LongAdder();

    public static void main(String[] args) throws InterruptedException {
        Thread[] threads = new Thread[1000];
        for (int i = 0; i < 1000; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    count.increment();
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        System.out.println("LongAdder高性能计数：" + count.sum());
    }
}