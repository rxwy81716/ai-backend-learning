package com.jiabo.thread.day01;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * SynchronizationExample
 * 演示线程同步的示例
 * @author jiabo
 */
public class SynchronizationExample {

    /** 共享变量，多线程访问时需要同步 */
    private int count = 0;

    /** 对象锁，用于synchronized代码块同步 */
    private final Object lock = new Object();

    // ============ 方式一：synchronized关键字同步 ============

    /**
     * 同步方法 - 使用synchronized修饰方法
     * 等同于 synchronized(this)，锁对象为当前实例
     * 适用于简单场景，自动获取/释放锁
     */
    public synchronized void incrementSync() {
        count++;
    }

    // ============ 方式二：synchronized代码块同步 ============

    /**
     * 同步代码块 - 使用任意对象作为锁
     * 比同步方法更灵活，可控制细粒度的同步范围
     * 这里使用独立的lock对象，避免锁定this
     */
    public void incrementLock() {
        synchronized (lock) {
            count++;
        }
    }

    // ============ 方式三：ReentrantLock可重入锁 ============

    /** ReentrantLock：可重入锁，比synchronized更灵活 */
    private final ReentrantLock reentrantLock = new ReentrantLock();

    /**
     * 使用ReentrantLock手动加锁/解锁
     * 必须finally中释放锁，推荐使用这种方式
     * 特性：可中断、可超时、支持公平/非公平锁
     */
    private void incrementReentrantLock() {
        reentrantLock.lock();
        try {
            count++;
        } finally {
            // 必须在finally中释放，确保异常时也能释放锁
            reentrantLock.unlock();
        }
    }

    // ============ 方式四：ReadWriteLock读写锁 ============

    /** 读写锁：读读不互斥，写写/读写互斥 */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * 读操作 - 获取读锁
     * 读锁之间不互斥，多线程可以同时读取
     * 适合读多写少场景，提升并发性能
     * @return count当前值
     */
    public int readCount() {
        rwLock.readLock().lock();
        try {
            return count;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * 写操作 - 获取写锁
     * 写锁与其他任何锁都互斥，保证数据一致性
     * @param value 要写入的值
     */
    public void writeCount(int value) {
        rwLock.writeLock().lock();
        try {
            count = value;
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
