package com.jianbo.algorthm.arrary;

import java.util.Arrays;
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
public class Demo01_Q6_T {

  public static void main(String[] args) {
    Scanner scanner = new Scanner(System.in);
    // todo 区间和
    int len = scanner.nextInt();
    int[] arr = new int[len];
    int[] sums = new int[len];
    int sum = 0;
    for (int i = 0; i < len; i++) {
      arr[i] = scanner.nextInt();
      sum+=arr[i];
      sums[i] = sum;
    }
    System.out.println(Arrays.toString(arr));
    System.out.println(Arrays.toString(sums));
    while (scanner.hasNext()){
      int m = scanner.nextInt();
      int n = scanner.nextInt();
      if (m == 0){
        System.out.println(sums[n]);
      }else {
        System.out.println(sums[n] - sums[m-1]);
      }
    }
    scanner.close();
  }
}
