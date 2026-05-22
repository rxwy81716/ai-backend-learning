package com.jianbo.localaiknowledge.utils;

import com.jianbo.localaiknowledge.constant.TextSplitConstants;

import java.util.ArrayList;
import java.util.List;

public class TextSplitterUtil {

  public static List<String> splitText(String text) {
    // 动态计算分块大小
    int dynamicChunkSize = TextSplitConstants.calculateDynamicChunkSize(text.length());
    int dynamicOverlapSize = TextSplitConstants.calculateDynamicOverlapSize(dynamicChunkSize);

    List<String> chunks = new ArrayList<>();

    // 1. 数据清洗（去多余空白、特殊符号）
    if (text.length() <= dynamicChunkSize) {
      chunks.add(text);
      return chunks;
    }

    // 2. 递归切分：段落(\n) → 句子(。！？；) → 子句(、：,) → 字符，逐级降级
    List<String> sentences = recursiveSplit(text, dynamicChunkSize);

    // 3. 动态合并句子
    StringBuilder currentChunk = new StringBuilder();
    for (int i = 0; i < sentences.size(); i++) {
      String sentence = sentences.get(i);

      // 如果单句就超过了限制（极端情况），强制按长度切分
      if (sentence.length() > dynamicChunkSize) {
        if (!currentChunk.isEmpty()) {
          chunks.add(currentChunk.toString());
          currentChunk.setLength(0);
        }
        chunks.addAll(forceSplit(sentence, dynamicChunkSize, dynamicOverlapSize));
        continue;
      }

      // 核心逻辑：当前块 + 新句子 <= 限制，则合并
      if (currentChunk.length() + sentence.length() <= dynamicChunkSize) {
        currentChunk.append(sentence);
      } else {
        // 存入当前块
        chunks.add(currentChunk.toString());

        // 处理重叠区 (Overlap)：回溯前面的句子
        currentChunk.setLength(0);
        currentChunk.append(findOverlapPrefix(sentences, i, dynamicOverlapSize));
        currentChunk.append(sentence);
      }
    }
    if (!currentChunk.isEmpty()) {
      chunks.add(currentChunk.toString());
    }
    return chunks;
  }

  // 递归切分：按多级分隔符自顶向下切，每级切完仍超长则继续向下降级。
  // 分隔符优先级（参考 LangChain RecursiveCharacterTextSplitter 思想）：
  //   L0 段落:   \n          （最强语义边界）
  //   L1 句子:   。！？!?     （句末标点）
  //   L2 子句:   ；;          （分号）
  //   L3 子句:   ，、:：,     （逗号/顿号/冒号；最弱，保留为最后退路）
  // 注意：逗号不再作为一级分隔，避免中文一句多逗号导致碎片化。
  private static final String[] SEPARATORS = {"\n", "[。！？!?]", "[；;]", "[，、:：,]"};

  /**
   * 递归把 text 切到所有片段 <= chunkSize，分隔符自上而下逐级降级
   */
  private static List<String> recursiveSplit(String text, int chunkSize) {
    return recursiveSplit(text, chunkSize, 0);
  }

  private static List<String> recursiveSplit(String text, int chunkSize, int level) {
    List<String> out = new ArrayList<>();
    if (text.length() <= chunkSize) {
      String t = text.trim();
      if (!t.isEmpty()) out.add(t);
      return out;
    }
    // 已用完所有分隔符 → 强制按字符切（兜底）
    if (level >= SEPARATORS.length) {
      out.add(text);
      return out;
    }
    // 用当前级分隔符切（保留分隔符在末尾，保持语义完整）
    String[] parts = text.split("(?<=" + SEPARATORS[level] + ")");
    for (String p : parts) {
      if (p.isEmpty()) continue;
      if (p.length() <= chunkSize) {
        out.add(p);
      } else {
        // 超长 → 用下一级分隔符继续切
        out.addAll(recursiveSplit(p, chunkSize, level + 1));
      }
    }
    if (out.isEmpty()) out.add(text); // 兜底
    return out;
  }

  // 处理重叠区：从当前索引往前找，直到凑满 overlapSize
  private static String findOverlapPrefix(List<String> sentences, int currentIndex, int overlapSize) {
    StringBuilder overlap = new StringBuilder();
    for (int j = currentIndex - 1; j >= 0; j--) {
      String s = sentences.get(j);
      if (overlap.length() + s.length() <= overlapSize) {
        overlap.insert(0, s);
      } else {
        break;
      }
    }
    return overlap.toString();
  }

  // 兜底方案：超长单句强制物理切分（防止死循环/OOM）
  private static List<String> forceSplit(String longText, int chunkSize, int overlapSize) {
    List<String> subChunks = new ArrayList<>();
    int start = 0;
    int step = chunkSize - overlapSize;

    while (start < longText.length()) {
      int end = Math.min(start + chunkSize, longText.length());
      subChunks.add(longText.substring(start, end));
      if (end == longText.length()) break;
      start += step;
    }
    return subChunks;
  }
}
