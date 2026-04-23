package com.jiabo.redis.service;

import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;


/**
 * 分布式锁 业务实战(库存扣减)
 */
@Service
@AllArgsConstructor
public class StockService {
    private final RedissonClient redissonClient;

    public void deductStock() {
        //  锁的key
        String lockKey = "stock:lock:1001";
        //  获取锁
        RLock lock = redissonClient.getLock(lockKey);
        //  上锁 自动看门狗续期
        lock.lock();
        //  加锁成功
        try {
            // 业务逻辑
            //模拟库存扣减
            System.out.println("线程" + Thread.currentThread().getName() + "获取锁,执行库存扣减");
            Thread.sleep(5000);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //  释放锁
            //  判断锁是否是当前线程持有的锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
