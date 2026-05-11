package com.jianbo.localaiknowledge.utils;

import com.jianbo.localaiknowledge.service.EsVectorSearchService.SearchDoc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagFormatUtilTest {

    @Test
    @DisplayName("格式化多个搜索结果")
    void formatMultiple() {
        List<SearchDoc> docs = List.of(
                new SearchDoc("内容一", Map.of("source", "a.pdf")),
                new SearchDoc("内容二", Map.of("source", "b.pdf"))
        );
        String result = RagFormatUtil.formatDocs(docs);
        assertThat(result)
                .contains("doc_1").contains("a.pdf").contains("内容一")
                .contains("doc_2").contains("b.pdf").contains("内容二");
    }

    @Test
    @DisplayName("空列表 → 空字符串")
    void formatEmpty() {
        assertThat(RagFormatUtil.formatDocs(List.of())).isEmpty();
    }
}
