package com.jianbo.springai.constant;

public class TextSplitConstants {

  //每段最大长度(汉字)
  public static final int MAX_CHUNK_SIZE = 500;
  //重叠长度(汉字 防止语义断裂)
  public static final int OVERLAP_SIZE = 50;
}
