package com.jiabo.redis.service;

import com.google.common.hash.BloomFilter;
import com.jiabo.redis.entity.User;
import com.jiabo.redis.utils.RedisUtil;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class UserService {

    private final RedisTemplate<String, Object> redisTemplate;
    @Resource
    private BloomFilter<String> bloomFilter;

    //模拟从数据库获取用户信息
    public User getFromDB(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setAge((int) (Math.random() * 100));
        user.setName("user" + userId);
        return user;
    }

    // ==================== 1. String 结构：缓存查询 ====================
    public User getUserById(Long userId) {
        String key = "user:string:" + userId;
        User cacheUser = RedisUtil.toBean(redisTemplate.opsForValue().get(key), User.class);
        // 缓存中存在
        if (cacheUser != null) {
            return cacheUser;
        }
        //2. 查数据库
        User user = getFromDB(userId);

        //缓存空值 防止缓存穿透
        if (user == null) {
            redisTemplate.opsForValue().set(key, new User(), 10, TimeUnit.MINUTES);
            return null;
        }

        //写入缓存+设置过期时间
        redisTemplate.opsForValue().set(key, user, 10, TimeUnit.MINUTES);
        return user;
    }

    // ==================== 2. Hash 结构：缓存查询 ====================
    public User getUserByHash(Long userId) {
        String key = "user:hash:" + userId;

        // 1. 查 Hash
        User user = RedisUtil.toBean(redisTemplate.opsForHash().get(key, "info"), User.class);

        if (user != null) {
            return user;
        }

        // 2. 查数据库
        User dbUser = getFromDB(userId);

        // 缓存空值 防止缓存穿透
        if (dbUser == null) {
            redisTemplate.opsForHash().put(key, "info", new User());
            redisTemplate.expire(key, 1, TimeUnit.MINUTES);
            return null;
        }

        // 3. 写入 Hash
        redisTemplate.opsForHash().put(key, "info", dbUser);
        redisTemplate.expire(key, 30, TimeUnit.MINUTES);
        return dbUser;
    }

    // ==================== 3. 缓存更新（最标准方案：先更新库，再删缓存） ====================
    public void updateUser(User user) {
        // 1. 更新数据库
        System.out.println("更新数据库成功：" + user);

        // 2. 删除缓存（安全！避免脏数据）
        String key = "user:string:" + user.getId();
        redisTemplate.delete(key);

        String hashKey = "user:hash:" + user.getId();
        redisTemplate.delete(hashKey);
    }

    //    =====================初始化布隆过滤器 项目启动时调用=====================
    public void initBloomFilter() {
        //吧数据库中所有存在的ID放入布隆过滤器
        for (int i = 0; i < 1000; i++) {
            bloomFilter.put("user:" + i);
        }
        System.out.println("布隆过滤器初始化完成");
    }

    //1.解决缓存穿透
    public User getUser(Long userId) {
        String key = "user:string:" + userId;

        //布隆过滤器 判断用户是否存在
        if (!bloomFilter.mightContain("user:" + userId)) {
            System.out.println("布隆过滤器拦截,用户不存在:" + userId);
            return null;
        }

        User user = RedisUtil.toBean(redisTemplate.opsForValue().get(key), User.class);
        if (user != null) {
            //空值对象直接返回
            if (user.getId() == null) return null;
            return user;
        }

        //互斥锁 解决 缓存击穿
        synchronized (this){
            //二次检查缓存
            user = RedisUtil.toBean(redisTemplate.opsForValue().get(key), User.class);
            if (user != null) {
                return user;
            }
            user = getFromDB(userId);
            if (user == null) {
                //缓存空值 防止缓存穿透
                redisTemplate.opsForValue().set(key, new User(), 10, TimeUnit.MINUTES);
                return null;
            }

            //随机过期时间 防止缓存雪崩
            // 30-40分钟随机
            long expire = 30 + (long) (Math.random() * 10);
            redisTemplate.opsForValue().set(key, user, expire, TimeUnit.MINUTES);
            return user;
        }

    }
}
