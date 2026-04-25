package com.jianbo.springai.service;


import com.jianbo.springai.utils.TextCleanUtil;
import com.jianbo.springai.utils.TextSplitterUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档向量入库服务
 *
 * 完整流程：
 *   文档 → 切片 → 向量化 → 存入数据库
 */
@Service
@AllArgsConstructor
@Slf4j
public class DocumentVectorService {
    private final EmbeddingService embeddingService;

    /**
     * 文档全文入库
     *
     * @param document 原始文档（如：一篇完整的文章）
     * @return 入库的片段数量
     */

    public int importDocument(String document){
        String cleanText = TextCleanUtil.clean(document);

        //固定长度切片
        List<String> chunks = TextSplitterUtil.splitText(cleanText);

        //向量化
        List<float[]> vectors = embeddingService.embedBatch(chunks);

        int count = saveToVectorDb(chunks,vectors);
        return count;
    }

    //保存到数据库
    private int saveToVectorDb(List<String> chunks, List<float[]> vectors) {
        // TODO: 保存到数据库 实际保存到 pgvector

        return chunks.size();
    }
}
