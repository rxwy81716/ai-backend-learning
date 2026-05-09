package com.jianbo.algorthm.arrary;

import java.util.Scanner;

// ==================================================
//  题目描述
// 给定一个整数数组 Array，请计算该数组在每个指定区间内元素的总和。
// 输入描述
// 第一行输入为整数数组 Array 的长度 n，接下来 n 行，每行一个整数，表示数组的元素。随后的输入为需要计算总和的区间，直至文件结束。
// 输出描述
// 输出每个指定区间内元素的总和。
// 输入示例
// 5
// 1
// 2
// 3
// 4
// 5
// 0 1
// 1 3
// 输出示例
// 3
// 9
// 数据范围：
// 0 < n <= 100000
public class Demo01_Q6 {

  public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);
    int leng = scanner.nextInt();
    int[] arr = new int[leng];
    int[] sums = new int[leng];
    int sum = 0;
    for (int i = 0; i < leng; i++) {
      arr[i] = scanner.nextInt();
      sum += arr[i];
      sums[i] = sum;
    }
    while (scanner.hasNext()) {
      int l = scanner.nextInt();
      int r = scanner.nextInt();
      if (l == 0) {
        System.out.println(sums[r]);
      } else {
        System.out.println(sums[r] - sums[l - 1]);
      }
    }
    scanner.close();
  }
}
