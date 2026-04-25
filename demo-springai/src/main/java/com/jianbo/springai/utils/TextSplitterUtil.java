package com.jianbo.springai.utils;

import com.jianbo.springai.constant.TextSplitConstants;

import java.util.ArrayList;
import java.util.List;

public class TextSplitterUtil {
  /**
   * 文本切片: 固定长度+重叠区 (最适合RAG)
   *
   * @param rawText 原始文本
   * @return 切片后的文本列表
   */
  public static List<String> splitText(String rawText) {
    List<String> chunks = new ArrayList<>();
    System.out.println(rawText);
    // 1.清洗文本
    String text = TextCleanUtil.clean(rawText);
    int length = text.length();
    int maxChunkSize = TextSplitConstants.MAX_CHUNK_SIZE;
    int overlapSize = TextSplitConstants.OVERLAP_SIZE;

    if (length <= maxChunkSize) {
      chunks.add(text);
      return  chunks;
    }
    // 2. 开始切片
    int index = 0;
    while (index < length) {
      // 1. 计算当前切片的结束位置
      int end = Math.min(index + maxChunkSize, length);
      // 2. 添加切片到列表
      chunks.add(text.substring(index, end));
      // 3. 更新索引位置
      index += maxChunkSize - overlapSize;
    }
    return chunks;
  }
}
