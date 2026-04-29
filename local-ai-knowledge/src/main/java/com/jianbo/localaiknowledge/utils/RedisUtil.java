package com.jianbo.localaiknowledge.utils;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class RedisUtil {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  // 万能类型转换 → 彻底解决 LinkedHashMap 转 User 异常
  public static <T> T toBean(Object obj, Class<T> clazz) {
    return obj == null ? null : OBJECT_MAPPER.convertValue(obj, clazz);
  }

  /** 列表类型转换（彻底解决泛型擦除问题） */
  public static <T> List<T> toList(Object obj, Class<T> clazz) {
    if (obj == null) {
      return null;
    }
    // 💡 核心修复：构建一个 List<T> 的完整类型引用
    JavaType type = OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz);
    return OBJECT_MAPPER.convertValue(obj, type);
  }
}
