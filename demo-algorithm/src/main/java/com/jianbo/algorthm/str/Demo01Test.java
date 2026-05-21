package com.jianbo.algorthm.str;

import java.util.Scanner;

public class Demo01Test {
  public static void main(String[] args) {
    Demo01Test demo01 = new Demo01Test();
    String s = "abcdefg";
    System.out.println(demo01.reverseStr(s, 2));
    System.out.println(demo01.reverseStr2(s, 2));
    //    Scanner scanner = new Scanner(System.in);
    String str = "a1b2m3k3";
    System.out.println(demo01.replceNumber(str));
    s = "Hello World!";
    System.out.println(demo01.reverseWords(s));
    s = "abcdefg";
    System.out.println(demo01.reverseRight(s, 2));
    String haystack = "ababbababa", needle = "ababa";
    System.out.println(haystack);
    System.out.println(needle);
    int i = demo01.strStr(haystack, needle);
    System.out.println(i);
  }

  // 反转字符串.
  // 编写一个函数，其作用是将输入的字符串反转过来。输入字符串以字符数组 char[] 的形式给出。
  // 不要给另外的数组分配额外的空间，你必须原地修改输入数组、使用 O(1) 的额外空间解决这一问题。
  // 你可以假设数组中的所有字符都是 ASCII 码表中的可打印字符。
  public void reverseString(char[] s) {
    // todo
    int left = 0;
    int right = s.length - 1;
    while (left < right) {
      s[left] ^= s[right];
      s[right] ^= s[left];
      s[left] ^= s[right];
      left++;
      right--;
    }
  }

  // 反转字符串II
  // 给定一个字符串 s 和一个整数 k，从字符串开头算起, 每计数至 2k 个字符，就反转这 2k 个字符中的前 k 个字符。
  // 如果剩余字符少于 k 个，则将剩余字符全部反转。
  // 如果剩余字符小于 2k 但大于或等于 k 个，则反转前 k 个字符，其余字符保持原样。
  // 示例:
  // 输入: s = "abcdefg", k = 2
  // 输出: "bacdfeg"
  public String reverseStr(String s, int k) {
    // todo
    char[] chars = s.toCharArray();
    for (int i = 0; i < chars.length; i += 2 * k) {
      int start = i;
      int end = Math.min(i + k - 1, chars.length - 1);
      while (start < end) {
        chars[start] ^= chars[end];
        chars[end] ^= chars[start];
        chars[start] ^= chars[end];
        start++;
        end--;
      }
    }
    return new String(chars);
  }

  public String reverseStr2(String s, int k) {
    StringBuffer res = new StringBuffer();
    // todo
    int start = 0;
    while (start < s.length()) {
      int first = Math.min(start + k, s.length());
      int secord = Math.min(start + 2 * k, s.length());
      StringBuilder temp = new StringBuilder();
      temp.append(s, start, first);
      res.append(temp.reverse());
      if (first < secord) {
        res.append(s, first, secord);
      }
      start += 2 * k;
    }
    return res.toString();
  }

  // 给定一个字符串 s，它包含小写字母和数字字符，请编写一个函数，将字符串中的字母字符保持不变，而将每个数字字符替换为number。 例如，对于输入字符串 "a1b2c3"，函数应该将其转换为
  // "anumberbnumbercnumber"。
  // 数组处理
  public String replceNumber(String s) {
    //    //todo
    char[] chars = s.toCharArray();
    int count = 0;
    for (char aChar : chars) {
      if (Character.isDigit(aChar)) {
        count++;
      }
    }
    char[] newChars = new char[s.length() + 5 * count];
    for (int i = 0, j = 0; i < chars.length && j < newChars.length; i++) {
      char aChar = chars[i];
      if (Character.isDigit(aChar)) {
        newChars[j++] = 'n';
        newChars[j++] = 'u';
        newChars[j++] = 'm';
        newChars[j++] = 'b';
        newChars[j++] = 'e';
        newChars[j++] = 'r';
      } else {
        newChars[j++] = chars[i];
      }
    }
    return new String(newChars);
  }

  /**
   * 不使用Java内置方法实现
   *
   * <p>1.去除首尾以及中间多余空格 2.反转整个字符串 3.反转各个单词
   */
  public String reverseWords(String s) {
    // todo
    StringBuilder removed = removeSpace(s);
    System.out.println("删除空格:" + removed);
    reverseString(removed, 0, removed.length() - 1);
    System.out.println("反转字符串:" + removed);
    reverseEachWord(removed);
    System.out.println("反转单词:" + removed);
    return removed.toString();
  }

  private StringBuilder removeSpace(String s) {
    // todo 删除多余的空格
    StringBuilder res = new StringBuilder();

    return res;
  }

  /** 反转字符串指定区间[start, end]的字符 */
  public void reverseString(StringBuilder sb, int start, int end) {
    // todo 反转指定区间的字符
  }

  private void reverseEachWord(StringBuilder sb) {
    // todo 反转单词;
  }

  // 右旋字符串字符串的右旋转操作是把字符串尾部的若干个字符转移到字符串的前面。给定一个字符串 s 和一个正整数 k，请编写一个函数，将字符串中的后面 k
  // 个字符移到字符串的前面，实现字符串的右旋转操作。
  // 例如，对于输入字符串 "abcdefg" 和整数 2，函数应该将其转换为 "fgabcde"。
  // 输入：输入共包含两行，第一行为一个正整数 k，代表右旋转的位数。第二行为字符串 s，代表需要旋转的字符串。
  // 输出：输出共一行，为进行了右旋转操作后的字符串。
  public String reverseRight(String s, int k) {
    StringBuilder builder = new StringBuilder(s);
    reverseString(builder, 0, builder.length() - 1);
    reverseString(builder, 0, k - 1);
    reverseString(builder, k, builder.length() - 1);
    return builder.toString();
  }

  // 实现 strStr() 函数。
  // 给定一个 haystack 字符串和一个 needle 字符串，在 haystack 字符串中找出 needle 字符串出现的第一个位置 (从0开始)。如果不存在，则返回 -1。
  // 示例 1: 输入: haystack = "hello", needle = "ll" 输出: 2
  // 示例 2: 输入: haystack = "aaaaa", needle = "bba" 输出: -1
  // 说明: 当 needle 是空字符串时，我们应当返回什么值呢？这是一个在面试中很好的问题。 对于本题而言，当 needle 是空字符串时我们应当返回 0 。这与C语言的 strstr()
  // 以及 Java的 indexOf() 定义相符。
  public int strStr(String haystack, String needle) {

    return -1;
  }

  private void getNext(int[] next, String s) {}

  // 给定一个非空的字符串，判断它是否可以由它的一个子串重复多次构成。给定的字符串只含有小写英文字母，并且长度不超过10000。
  // 示例 1:
  // 输入: "abab"
  // 输出: True
  // 解释: 可由子字符串 "ab" 重复两次构成。
  // 示例 2:
  // 输入: "aba"
  // 输出: False
  // 示例 3:
  // 输入: "abcabcabcabc"
  // 输出: True
  // 解释: 可由子字符串 "abc" 重复四次构成。 (或者子字符串 "abcabc" 重复两次构成。)
  public boolean repeatedSubstringPattern(String s) {
    return false;
  }
}
