package com.jiabo.redis.utils;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisUtil {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 万能类型转换 → 彻底解决 LinkedHashMap 转 User 异常
    public static <T> T toBean(Object obj, Class<T> clazz) {
        return obj == null ? null : OBJECT_MAPPER.convertValue(obj, clazz);
    }
}
