package com.jianbo.localaiknowledge.constant;

public class TextSplitConstants {

  // 基础分块配置（保持向后兼容）
  public static final int BASE_CHUNK_SIZE = 800;
  public static final int BASE_OVERLAP_SIZE = 100;

  // 旧常量（保持向后兼容）
  @Deprecated
  public static final int MAX_CHUNK_SIZE = 800;
  @Deprecated
  public static final int OVERLAP_SIZE = 100;

  // 动态分块配置
  public static final int MAX_CHUNK_SIZE_LIMIT = 3000;  // 最大分块大小限制
  public static final int MAX_OVERLAP_SIZE_LIMIT = 400; // 最大重叠大小限制

  // 文件大小阈值（字符数）
  public static final int SMALL_FILE_THRESHOLD = 1_000_000;      // 1M
  public static final int MEDIUM_FILE_THRESHOLD = 5_000_000;    // 5M
  public static final int LARGE_FILE_THRESHOLD = 10_000_000;    // 10M

  // 动态分块倍数
  public static final double SMALL_FILE_MULTIPLIER = 1.0;       // 1M以下: 800字
  public static final double MEDIUM_FILE_MULTIPLIER = 1.5;      // 1M-5M: 1200字
  public static final double LARGE_FILE_MULTIPLIER = 2.0;       // 5M-10M: 1600字
  public static final double VERY_LARGE_FILE_MULTIPLIER = 2.5;  // 10M以上: 2000字

  /**
   * 根据文本长度动态计算分块大小
   * @param textLength 文本总长度
   * @return 动态计算的分块大小
   */
  public static int calculateDynamicChunkSize(int textLength) {
    double multiplier;
    if (textLength < SMALL_FILE_THRESHOLD) {
      multiplier = SMALL_FILE_MULTIPLIER;
    } else if (textLength < MEDIUM_FILE_THRESHOLD) {
      multiplier = MEDIUM_FILE_MULTIPLIER;
    } else if (textLength < LARGE_FILE_THRESHOLD) {
      multiplier = LARGE_FILE_MULTIPLIER;
    } else {
      multiplier = VERY_LARGE_FILE_MULTIPLIER;
    }

    int chunkSize = (int) (BASE_CHUNK_SIZE * multiplier);
    return Math.min(chunkSize, MAX_CHUNK_SIZE_LIMIT);
  }

  /**
   * 根据分块大小动态计算重叠大小（保持12.5%的比例）
   * @param chunkSize 分块大小
   * @return 动态计算的重叠大小
   */
  public static int calculateDynamicOverlapSize(int chunkSize) {
    int overlapSize = (int) (chunkSize * 0.125); // 12.5% 重叠
    return Math.min(overlapSize, MAX_OVERLAP_SIZE_LIMIT);
  }
}
