package com.jianbo.springai.utils;

import com.jianbo.springai.constant.TextSplitConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public class SentenceSplitter {

    // 中文句子结束符号
        private static final String[] SENTENCE_SEPARATORS = {"。", "！", "？", "；", "\n"};

    public static List<String> splitText(String rawText) {
        List<String> chunks = new ArrayList<>();

        // 1. 数据清洗（去多余空白、特殊符号）
        String text = cleanText(rawText);
        if (text.length() <= TextSplitConstants.MAX_CHUNK_SIZE) {
            chunks.add(text);
            return chunks;
        }

        // 2. 将文本拆分为原子级的“句子”
        List<String> sentences = getSentences(text);

        // 3. 动态合并句子
        StringBuilder currentChunk = new StringBuilder();
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);

            // 如果单句就超过了限制（极端情况），强制按长度切分
            if (sentence.length() > TextSplitConstants.MAX_CHUNK_SIZE) {
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString());
                    currentChunk.setLength(0);
                }
                chunks.addAll(forceSplit(sentence));
                continue;
            }

            // 核心逻辑：当前块 + 新句子 <= 限制，则合并
            if (currentChunk.length() + sentence.length() <= TextSplitConstants.MAX_CHUNK_SIZE) {
                currentChunk.append(sentence);
            } else {
                // 存入当前块
                chunks.add(currentChunk.toString());

                // 处理重叠区 (Overlap)：回溯前面的句子
                currentChunk.setLength(0);
                currentChunk.append(findOverlapPrefix(sentences, i));
                currentChunk.append(sentence);
            }
        }
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString());
        }
        return chunks;
    }

    // 提取句子
    private static List<String> getSentences(String text) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = java.util.regex.Pattern.compile(String.join("", SENTENCE_SEPARATORS)).matcher(text);
        while (matcher.find()) {
            sentences.add(matcher.group());
        }
        if (sentences.isEmpty()) sentences.add(text); // 兜底
        return sentences;
    }

    // 处理重叠区：从当前索引往前找，直到凑满 overlapSize
    private static String findOverlapPrefix(List<String> sentences, int currentIndex) {
        StringBuilder overlap = new StringBuilder();
        for (int j = currentIndex - 1; j >= 0; j--) {
            String s = sentences.get(j);
            if (overlap.length() + s.length() <= TextSplitConstants.OVERLAP_SIZE) {
                overlap.insert(0, s);
            } else {
                break;
            }
        }
        return overlap.toString();
    }

    // 兜底方案：超长单句强制物理切分（防止死循环/OOM）
    private static List<String> forceSplit(String longText) {
        List<String> subChunks = new ArrayList<>();
        int start = 0;
        int step = TextSplitConstants.MAX_CHUNK_SIZE - TextSplitConstants.OVERLAP_SIZE;

        while (start < longText.length()) {
            int end = Math.min(start + TextSplitConstants.MAX_CHUNK_SIZE, longText.length());
            subChunks.add(longText.substring(start, end));
            if (end == longText.length()) break;
            start += step;
        }
        return subChunks;
    }

    private static String cleanText(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
