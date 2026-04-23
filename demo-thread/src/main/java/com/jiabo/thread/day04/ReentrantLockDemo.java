package com.jiabo.thread.day04;

import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockDemo {
    public static void main(String[] args) {
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try {
            // 代码逻辑
        } finally {
            lock.unlock();
        }
    }
}
