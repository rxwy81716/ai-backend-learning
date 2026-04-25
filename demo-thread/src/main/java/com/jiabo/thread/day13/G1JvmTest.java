package com.jiabo.thread.day13;

public class G1JvmTest {
  public static void main(String[] args) throws InterruptedException {
    // 循环创建大量对象，制造垃圾
    while (true) {
      byte[] arr = new byte[1024 * 1024]; // 1M对象
    }
  }
}
