package com.jianbo.localaiknowledge.constant;

public class TextSplitConstants {

  //每段最大长度(汉字) 800字≈1个完整段落，兼顾语义完整性和Embedding质量
  public static final int MAX_CHUNK_SIZE = 800;
  //重叠长度(汉字 防止语义断裂) 100字确保跨片段关键信息不丢失
  public static final int OVERLAP_SIZE = 100;
}
