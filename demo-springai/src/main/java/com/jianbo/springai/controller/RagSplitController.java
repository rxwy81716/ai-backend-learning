package com.jianbo.springai.controller;

import com.jianbo.springai.utils.TextSplitterUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/rag")
public class RagSplitController {

  @GetMapping("/split")
  public List<String> testSplit(String content) {
    content = """
plaintext
原始长文档
    ↓
文本清洗（去空格、去空行、去乱码）
    ↓
固定长度切片（500字一段）
    ↓
重叠区保留（50字）
    ↓
得到 N 段干净文本
    ↓
下一步：Embedding 转向量
    ↓
存入 PostgreSQL + pgvector
""";
    return TextSplitterUtil.splitText(content);
  }


  @GetMapping("/split2")
  public List<String> testSplit2(String content) {
    content = """
plaintext；原始长文档；
文本清洗（去空格、去空行、去乱码）；
固定长度切片（500字一段）；
重叠区保留（50字）；
得到 N 段干净文本；
下一步：Embedding 转向量
存入 PostgreSQL + pgvector
""";
    return TextSplitterUtil.splitText(content);
  }


}
