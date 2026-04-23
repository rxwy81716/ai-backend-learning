package com.jiabo.thread.day05;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 库存超卖问题
 */
public class StockOverSell {
    //初始库存100
    private static int stock = 100;
    private static final Lock lock = new ReentrantLock();


    public static void main(String[] args) throws InterruptedException {
        //模拟1000个用户同时下单
        for (int i = 0; i < 1000; i++) {
            //超卖
            new Thread(StockOverSell::buy).start();
            //synchronized解决超卖问题
            new Thread(StockOverSell::syncBuy).start();
            //ReentrantLock解决超卖问题
            new Thread(StockOverSell::buyReentrantLock).start();
        }

        //等待所有线程执行完毕
        Thread.sleep(2000);
        // 最终库存：-129
        System.out.println("最终库存：" + stock);
    }

    private static void buy() {
        //每个用户尝试购买1件
        if (stock > 0) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            stock--;
            System.out.println("购买成功，剩余库存：" + stock);
        } else {
            System.out.println("购买失败，库存不足");
        }
    }

    // 同步方法：同一时间只有一个线程执行扣减逻辑
    public synchronized static void syncBuy() {
        if (stock > 0) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            stock--;
            System.out.println("购买成功，剩余库存：" + stock);
        } else {
            System.out.println("库存不足，购买失败");
        }
    }

    public static void buyReentrantLock() {
        lock.lock(); // 上锁
        try {
            if (stock > 0) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                stock--;
                System.out.println("购买成功，剩余库存：" + stock);
            } else {
                System.out.println("库存不足，购买失败");
            }
        } finally {
            lock.unlock(); // 必须finally释放锁
        }
    }
}
