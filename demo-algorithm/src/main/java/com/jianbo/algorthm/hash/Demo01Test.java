package com.jianbo.algorthm.hash;

import java.util.*;

public class Demo01Test {
  public static void main(String[] args) {
    Demo01Test demo01Test = new Demo01Test();
    System.out.println(demo01Test.isHappy(19));
  }

  // 给定两个字符串 s 和 t ，编写一个函数来判断 t 是否是 s 的字母异位词。
  //
  // 示例 1: 输入: s = "anagram", t = "nagaram" 输出: true
  //
  // 示例 2: 输入: s = "rat", t = "car" 输出: false
  //
  // 说明: 你可以假设字符串只包含小写字母
  // 有效的字母异位词
  public boolean isAnagram(String s, String t) {
    // todo 有效的字母异位词
    int[] hash = new int[26];
    for (int i = 0; i < s.length(); i++) {
      hash[s.charAt(i) - 'a']++;
    }
    for (int i = 0; i < t.length(); i++) {
      hash[t.charAt(i) - 'a']--;
    }
    for (int i : hash) {
      if (i != 0) {
        return false;
      }
    }
    return true;
  }

  //  题意：给定两个数组，编写一个函数来计算它们的交集。
  // 输出结果中的每个元素一定是唯一的。 我们可以不考虑输出结果的顺序。
  // 两个数字的交集
  public int[] intersection(int[] nums1, int[] nums2) {
    // todo 两个数字的交集 hashset
    Set<Integer> set = new HashSet<>();
    Set<Integer> res = new HashSet<>();
    for (int i : nums1) {
      set.add(i);
    }
    for (int i : nums2) {
      if (set.contains(i)) {
        res.add(i);
      }
    }
    return res.stream().mapToInt(Integer::intValue).toArray();
  }

  // 1 <= nums1.length, nums2.length <= 1000
  // 0 <= nums1[i], nums2[i] <= 1000
  // hash解法
  public int[] intersection2(int[] nums1, int[] nums2) {
    // todo 两个数字的交集 hash数组解法
    int[] hash1 = new int[1002];
    int[] hash2 = new int[1002];
    for (int i : nums1) {
      hash1[i]++;
    }
    for (int i : nums2) {
      hash2[i]++;
    }
    Set<Integer> resSet = new HashSet<>();
    for (int i = 0; i < 1002; i++) {
      if (hash1[i] > 0 && hash2[i] > 0) {
        resSet.add(i);
      }
    }
    return resSet.stream().mapToInt(Integer::intValue).toArray();
  }

  // 快乐数
  // 编写一个算法来判断一个数 n 是不是快乐数。
  // 「快乐数」定义为：对于一个正整数，每一次将该数替换为它每个位置上的数字的平方和，然后重复这个过程直到这个数变为 1，也可能是 无限循环 但始终变不到 1。如果 可以变为
  // 1，那么这个数就是快乐数。
  // 如果 n 是快乐数就返回 True ；不是，则返回 False 。
  public boolean isHappy(int n) {
    // todo 快乐数
    Set<Integer> sums = new HashSet<>();
    while (n > 0 && n != 1) {
      sums.add(n);
      n = getSum(n);
      if (sums.contains(n)) {
        return false;
      }
    }
    return true;
  }

  public int getSum(int n) {
    int sum = 0;
    while (n > 0) {
      int yushu = n % 10;
      sum += yushu * yushu;
      n = n / 10;
    }
    return sum;
  }

  // 两数之和
  // 给定一个整数数组 nums 和一个目标值 target，请你在该数组中找出和为目标值的那 两个 整数，并返回他们的数组下标。
  // 你可以假设每种输入只会对应一个答案。但是，数组中同一个元素不能使用两遍。
  // 你可以假设每种输入只会对应一个答案，并且你不能使用两次相同的元素。
  public int[] twoSum(int[] nums, int target) {
    int[] arr = new int[2];
    // todo   两数之和
    Map<Integer, Integer> map = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
      int num = nums[i];
      if (map.containsKey(target - num)) {
        arr[0] = map.get(target - num);
        arr[1] = i;
        break;
      }
      map.put(num, i);
    }
    return arr;
  }

  // 给定四个包含整数的数组列表 A , B , C , D ,计算有多少个元组 (i, j, k, l) ，使得 A[i] + B[j] + C[k] + D[l] = 0。
  // 为了使问题简单化，所有的 A, B, C, D 具有相同的长度 N，且 0 ≤ N ≤ 500 。所有整数的范围在 -2^28 到 2^28 - 1 之间，最终结果不会超过 2^31 - 1
  // 。
  // 例如:
  // 输入:
  // A = [ 1, 2]
  // B = [-2,-1]
  // C = [-1, 2]
  // D = [ 0, 2]
  // 输出:
  // 2
  // 四数相加
  public int fourSumCount(int[] nums1, int[] nums2, int[] nums3, int[] nums4) {
    int res = 0;
    // todo 四数相加
    HashMap<Integer, Integer> map = new HashMap<>();
    for (int i : nums1) {
      for (int j : nums2) {
        map.put(i + j, map.getOrDefault(i + j, 0) + 1);
      }
    }

    for (int k : nums3) {
      for (int l : nums4) {
        res += map.getOrDefault(-k - l, 0);
      }
    }
    return res;
  }

  // 赎金信
  // 给你两个字符串：ransomNote 和 magazine ，判断 ransomNote 能不能由 magazine 里面的字符构成。
  // 如果可以，返回 true ；否则返回 false 。
  // magazine 中的每个字符只能在 ransomNote 中使用一次。
  // 1 <= ransomNote.length, magazine.length <= 105
  // ransomNote 和 magazine 由小写英文字母组成
  public boolean canConstruct(String ransomNote, String magazine) {
    // todo 赎金信
    int[] arr = new int[26];
    for (int i = 0; i < magazine.length(); i++) {
      arr[magazine.charAt(i) - 'a']++;
    }
    for (int i = 0; i < ransomNote.length(); i++) {
      arr[ransomNote.charAt(i) - 'a']--;
    }
    for (int i : arr) {
      if (i < 0) {
        return false;
      }
    }
    return true;
  }

  // 三数之和
  // 给你一个整数数组 nums ，判断是否存在三元组 [nums[i], nums[j], nums[k]] 满足 i != j、i != k 且 j != k ，同时还满足 nums[i] +
  // nums[j] + nums[k] == 0 。请你返回所有和为 0 且不重复的三元组。
  // 注意：答案中不可以包含重复的三元组
  public List<List<Integer>> threeSum(int[] nums) {
    // todo 三数之和
    Arrays.sort(nums);
    List<List<Integer>> res = new ArrayList<>();
    int len = nums.length;
    for (int a = 0; a < len - 2; a++) {
      if (a > 0 && nums[a] == nums[a - 1]) continue;
      if (nums[a] + nums[a + 1] + nums[a + 2] > 0) break;
      if (nums[a] + nums[len - 1] + nums[len - 2] < 0) continue;
      int b = a + 1, c = len - 1;
      while (b < c) {
        long sum = (long) nums[a] + nums[b] + nums[c];
        if (sum == 0) {
          res.add(Arrays.asList(nums[a], nums[b], nums[c]));
          while (b < c && nums[b] == nums[b + 1]) b++;
          while (b < c && nums[c] == nums[c - 1]) c--;
          b++;
          c--;
        } else if (sum > 0) {
          c--;
        } else {
          b++;
        }
      }
    }
    return res;
  }

  // 四数之和
  // 给你一个由 n 个整数组成的数组 nums ，和一个目标值 target 。请你找出并返回满足下述全部条件且不重复的四元组 [nums[a], nums[b], nums[c],
  // nums[d]] （若两个四元组元素一一对应，则认为两个四元组重复）：
  // 0 <= a, b, c, d < n
  // a、b、c 和 d 互不相同
  // nums[a] + nums[b] + nums[c] + nums[d] == target
  // 你可以按 任意顺序 返回答案
  public List<List<Integer>> fourSum(int[] nums, int target) {
    // todo四数之和
    List<List<Integer>> res = new ArrayList<>();
    Arrays.sort(nums);
    int len = nums.length;
    for (int a = 0; a < len - 3; a++) {
      if (a > 0 && nums[a] == nums[a - 1]) continue;
      if ((long) nums[a] + nums[a + 1] + nums[a + 2] + nums[a + 3] > target) break;
      if ((long) nums[a] + nums[len - 3] + nums[len - 2] + nums[len - 1] < target) continue;
      for (int b = a + 1; b < len - 2; b++) {
        if (b > a + 1 && nums[b] == nums[b - 1]) continue;
        if ((long) nums[a] + nums[b] + nums[b + 1] + nums[b + 2] > target) break;
        if ((long) nums[a] + nums[b] + nums[len - 1] + nums[len - 2] < target) continue;
        int c = b + 1, d = len - 1;
        while (c < d) {
          long sum = (long) nums[a] + nums[b] + nums[c] + nums[d];
          if (sum == target) {
            res.add(Arrays.asList(nums[a], nums[b], nums[c], nums[d]));
            while (c < d && nums[c] == nums[c + 1]) c++;
            while (c < d && nums[d] == nums[d - 1]) d--;
            c++;
            d--;
          } else if (sum < target) {
            c++;
          } else {
            d--;
          }
        }
      }
    }
    return res;
  }
}
