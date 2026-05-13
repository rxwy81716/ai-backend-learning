package com.jianbo.algorthm.hash;

import java.util.*;

public class Demo01 {
  public static void main(String[] args) {

    Demo01 demo01 = new Demo01();
    // nums = [-1,0,1,2,-1,-4];
    int[] nums = {-1, 0, 1, 2, -1, -4};
    List<List<Integer>> lists = demo01.threeSum(nums);
    System.out.println(lists);
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
    int[] record = new int[26];
    for (int i = 0; i < s.length(); i++) {
      // 映射到0-25; abcdefghijklmnopqrstuvwxyz 记录每个字符出现的次数
      record[s.charAt(i) - 'a']++;
    }
    for (int i = 0; i < t.length(); i++) {
      // 减去每个字符出现的次数
      record[t.charAt(i) - 'a']--;
    }

    for (int count : record) {
      // 如果存在有字符部位0 则代表异位
      if (count != 0) {
        return false;
      }
    }
    return true;
  }

  //  题意：给定两个数组，编写一个函数来计算它们的交集。
  // 输出结果中的每个元素一定是唯一的。 我们可以不考虑输出结果的顺序。
  // 两个数字的交集
  public int[] intersection(int[] nums1, int[] nums2) {
    if (nums1 == null || nums1.length == 0 || nums2 == null || nums2.length == 0) {
      return new int[0];
    }
    Set<Integer> set1 = new HashSet<>();
    Set<Integer> set2 = new HashSet<>();
    for (int i : nums1) {
      set1.add(i);
    }
    for (int i : nums2) {
      if (set1.contains(i)) {
        set2.add(i);
      }
    }
    //    int[] res = new int[set2.size()];
    //    int index =0;
    //    for (Integer i : set2) {
    //      res[index++] = i;
    //    }
    //    return res;
    return set2.stream().mapToInt(Integer::intValue).toArray();
  }

  public int[] intersection2(int[] nums1, int[] nums2) {
    int[] hash1 = new int[1002];
    int[] hash2 = new int[1002];
    for (int j : nums1) {
      hash1[j]++;
    }
    for (int j : nums2) {
      hash2[j]++;
    }
    List<Integer> res = new ArrayList<>();
    for (int i = 0; i < 1002; i++) {
      if (hash1[i] > 0 && hash2[i] > 0) {
        res.add(i);
      }
    }
    int[] result = new int[res.size()];
    int index = 0;
    for (Integer re : res) {
      result[index++] = re;
    }
    return result;
  }

  // 快乐数
  // 编写一个算法来判断一个数 n 是不是快乐数。
  // 「快乐数」定义为：对于一个正整数，每一次将该数替换为它每个位置上的数字的平方和，然后重复这个过程直到这个数变为 1，也可能是 无限循环 但始终变不到 1。如果 可以变为
  // 1，那么这个数就是快乐数。
  // 如果 n 是快乐数就返回 True ；不是，则返回 False 。
  public boolean isHappy(int n) {
    if (n <= 0) {
      return false;
    }
    Set<Integer> tempSet = new HashSet<>();
    while (n != 1 && !tempSet.contains(n)) {
      tempSet.add(n);
      n = getSum(n);
    }
    return true;
  }

  public int getSum(int n) {
    int sum = 0;
    while (n > 0) {
      int y = n % 10;
      sum += y * y;
      n = n / 10;
    }
    return sum;
  }

  // 两束之和
  // 给定一个整数数组 nums 和一个目标值 target，请你在该数组中找出和为目标值的那 两个 整数，并返回他们的数组下标。
  // 你可以假设每种输入只会对应一个答案。但是，数组中同一个元素不能使用两遍。
  // 你可以假设每种输入只会对应一个答案，并且你不能使用两次相同的元素。
  public int[] twoSum(int[] nums, int target) {
    int[] arr = new int[2];
    if (nums == null || nums.length == 0) {
      return arr;
    }
    Map<Integer, Integer> map = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
      if (map.containsKey(nums[i])) {
        arr[0] = map.get(nums[i]);
        arr[1] = i;
        return arr;
      }
      map.put(target - nums[i], i);
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
    Map<Integer, Integer> map = new HashMap<>();
    for (int i : nums1) {
      for (int i1 : nums2) {
        map.put(i + i1, map.getOrDefault(i + i1, 0) + 1);
      }
    }
    for (int i : nums3) {
      for (int i1 : nums4) {
        res += map.getOrDefault(-i - i1, 0);
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
    // shortcut
    if (ransomNote.length() > magazine.length()) {
      return false;
    }
    // 定义一个哈希映射数组
    int[] record = new int[26];
    // 遍历
    for (char c : magazine.toCharArray()) {
      record[c - 'a'] += 1;
    }
    for (char c : ransomNote.toCharArray()) {
      record[c - 'a'] -= 1;
    }
    // 如果数组中存在负数，说明ransomNote字符串中存在magazine中没有的字符
    for (int i : record) {
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
  // 双指针
  public List<List<Integer>> threeSum(int[] nums) {
    if (nums.length < 3) {
      return new ArrayList<>();
    }
    Arrays.sort(nums);
    List<List<Integer>> res = new ArrayList<>();
    //    int right = nums.length - 1;
    for (int i = 0; i < nums.length - 2; i++) {
      if (i > 0 && nums[i] == nums[i - 1]) {
        continue;
      }
      if (nums[i] + nums[i + 1] + nums[i + 2] > 0) {
        continue;
      }
      int left = i + 1;
      int right = nums.length - 1;
      while (left < right) {
        int sum = nums[i] + nums[left] + nums[right];
        if (sum == 0) {
          res.add(Arrays.asList(nums[i], nums[left], nums[right]));
          // 去重:跳过相同的left和right
          while (left < right && nums[left] == nums[left + 1]) {
            left++;
          }
          while (left < right && nums[right] == nums[right - 1]) {
            right--;
          }
          left++;
          right--;
        } else if (sum > 0) {
          right--;
        } else {
          left++;
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
    if (nums.length < 4) {
      return List.of();
    }
    List<List<Integer>> result = new ArrayList<>();
    Arrays.sort(nums);
    for (int i = 0; i < nums.length - 3; i++) {
      if (i > 0 && nums[i] == nums[i - 1]) {
        continue;
      }
      // --- 剪枝优化 1 ---
      if ((long) nums[i] + nums[i + 1] + nums[i + 2] + nums[i + 3] > target) break;
      if ((long) nums[i] + nums[nums.length - 3] + nums[nums.length - 2] + nums[nums.length - 1]
          < target) continue;
      for (int j = i + 1; j < nums.length - 2; j++) {
        if (j > i + 1 && nums[j] == nums[j - 1]) {
          continue;
        }
        // 第二层区间剪枝
        if ((long) nums[i] + nums[j] + nums[j + 1] + nums[j + 2] > target) break;
        if ((long) nums[i] + nums[j] + nums[nums.length - 2] + nums[nums.length - 1] < target)
          continue;
        int left = j + 1;
        int right = nums.length - 1;
        while (left < right) {
          long sum = (long) nums[i] + nums[j] + nums[left] + nums[right];
          if (sum == target) {
            result.add(List.of(nums[i], nums[j], nums[left], nums[right]));
            while (left < right && nums[left] == nums[left + 1]) left++;
            while (left < right && nums[right] == nums[right - 1]) right--;
            left++;
            right--;
          } else if (sum < target) {
            left++;
          } else {
            right--;
          }
        }
      }
    }
    return result;
  }
}
