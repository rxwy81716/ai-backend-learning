package com.jianbo.algorthm.str;

import java.util.Arrays;

public class Demo01 {
  public static void main(String[] args) {
    Demo01 demo01 = new Demo01();
    String s = "abcd";
    demo01.reverseStr(s, 2);
    //    Scanner scanner = new Scanner(System.in);
    //    String str = scanner.nextLine();
    //    System.out.println(demo01.replceNumber(str));
    s = "Hello World!";
    System.out.println(demo01.reverseWords(s));
    String haystack = "ababbababa", needle = "ababa";
    System.out.println(haystack);
    System.out.println(needle);
    int i = demo01.strStr(haystack, needle);
    System.out.println(i);

    System.out.println(demo01.repeatedSubstringPattern("asdfasdfasdf"));
  }

  // 反转字符串.
  // 编写一个函数，其作用是将输入的字符串反转过来。输入字符串以字符数组 char[] 的形式给出。
  // 不要给另外的数组分配额外的空间，你必须原地修改输入数组、使用 O(1) 的额外空间解决这一问题。
  // 你可以假设数组中的所有字符都是 ASCII 码表中的可打印字符。
  public void reverseString(char[] s) {
    int a = 0;
    int b = s.length - 1;
    while (a < b) {
      char temp = s[a];
      s[a] = s[b];
      s[b] = temp;
      a++;
      b--;
    }
  }

  public void reverseString2(char[] s) {
    int a = 0;
    int b = s.length - 1;
    while (a < b) {
      s[a] ^= s[b];
      s[a] ^= s[b];
      s[b] ^= s[a];
      a++;
      b--;
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
    char[] chars = s.toCharArray();
    for (int i = 0; i < chars.length; i += (k * 2)) {
      int start = i;
      int end = Math.min(chars.length - 1, i + k - 1);
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
    int length = s.length();
    int start = 0;
    while (start < length) {
      StringBuffer temp = new StringBuffer();
      int firstK = Math.min(start + k, length);
      int secordK = Math.min(start + (2 * k), length);
      temp.append(s, start, firstK);
      res.append(temp.reverse());
      if (firstK < secordK) {
        res.append(s, firstK, secordK);
      }
      start += (2 * k);
    }
    return res.toString();
  }

  // 给定一个字符串 s，它包含小写字母和数字字符，请编写一个函数，将字符串中的字母字符保持不变，而将每个数字字符替换为number。 例如，对于输入字符串 "a1b2c3"，函数应该将其转换为
  // "anumberbnumbercnumber"。
  public String replceNumber(String s) {
    StringBuffer res = new StringBuffer();
    char[] chars = s.toCharArray();
    for (char aChar : chars) {
      if (Character.isDigit(aChar)) {
        res.append("number");
      } else {
        res.append(aChar);
      }
    }
    return res.toString();
  }

  /**
   * 不使用Java内置方法实现
   *
   * <p>1.去除首尾以及中间多余空格 2.反转整个字符串 3.反转各个单词
   */
  public String reverseWords(String s) {
    // System.out.println("ReverseWords.reverseWords2() called with: s = [" + s + "]");
    // 1.去除首尾以及中间多余空格
    StringBuilder sb = removeSpace(s);
    System.out.println();
    // 2.反转整个字符串
    reverseString(sb, 0, sb.length() - 1);
    // 3.反转各个单词
    reverseEachWord(sb);
    return sb.toString();
  }

  private StringBuilder removeSpace(String s) {
    StringBuilder sb = new StringBuilder();
    int start = 0;
    int end = s.length() - 1;
    while (start <= end && s.charAt(start) == ' ') start++;
    while (end >= start && s.charAt(end) == ' ') end--;
    while (start <= end) {
      if (s.charAt(start) != ' ' || sb.charAt(sb.length() - 1) != ' ') {
        sb.append(s.charAt(start));
      }
      start++;
    }
    return sb;
  }

  /** 反转字符串指定区间[start, end]的字符 */
  public void reverseString(StringBuilder sb, int start, int end) {
    // System.out.println("ReverseWords.reverseString returned: sb = [" + sb + "]");
    while (start < end) {
      char temp = sb.charAt(start);
      sb.setCharAt(start, sb.charAt(end));
      sb.setCharAt(end, temp);
      start++;
      end--;
    }
  }

  private void reverseEachWord(StringBuilder sb) {
    int start = 0;
    int end = 1;
    int n = sb.length();
    while (start < n) {
      while (end < n && sb.charAt(end) != ' ') {
        end++;
      }
      reverseString(sb, start, end - 1);
      start = end + 1;
      end = start + 1;
    }
  }

  // 实现 strStr() 函数。
  // 给定一个 haystack 字符串和一个 needle 字符串，在 haystack 字符串中找出 needle 字符串出现的第一个位置 (从0开始)。如果不存在，则返回 -1。
  // 示例 1: 输入: haystack = "hello", needle = "ll" 输出: 2
  // 示例 2: 输入: haystack = "aaaaa", needle = "bba" 输出: -1
  // 说明: 当 needle 是空字符串时，我们应当返回什么值呢？这是一个在面试中很好的问题。 对于本题而言，当 needle 是空字符串时我们应当返回 0 。这与C语言的 strstr()
  // 以及 Java的 indexOf() 定义相符。
  public int strStr(String haystack, String needle) {
    if (needle.isEmpty()) return 0;
    int[] next = new int[needle.length()];
    getNext(next, needle);
    int j = 0;
    for (int i = 0; i < haystack.length(); i++) {
      while (j > 0 && needle.charAt(j) != haystack.charAt(i)) {
        j = next[j - 1];
      }
      if (needle.charAt(j) == haystack.charAt(i)) {
        j++;
      }
      if (j == needle.length()) {
        return i - needle.length() + 1;
      }
    }
    return -1;
  }

  private void getNext(int[] next, String s) {
    int j = 0;
    next[0] = 0;
    for (int i = 1; i < s.length(); i++) {
      while (j > 0 && s.charAt(j) != s.charAt(i)) {
        j = next[j - 1];
      }
      if (s.charAt(j) == s.charAt(i)) {
        j++;
      }
      next[i] = j;
    }
  }

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
    int n = s.length();
    int[] next = new int[n];
    getNext(next, s);
    System.out.println(Arrays.toString(next));
    int len = next[n - 1]; // 最长相等前后缀长度
    int unit = n - len; // 可能的最小重复单元长度
    return len > 0 && n % unit == 0;
  }
}
