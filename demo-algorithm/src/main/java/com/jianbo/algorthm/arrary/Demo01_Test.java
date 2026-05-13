package com.jianbo.algorthm.arrary;

import java.util.Arrays;

public class Demo01_Test {

  public static void main(String[] args) {
    Demo01_Test demo = new Demo01_Test();
    int[] nums = {-1, 0, 3, 5, 9, 12};
    int target = 9;
    int result = demo.search(nums, target);
    System.out.println("二分查找");
    System.out.println(result);

    int[] nums2 = {0, 1, 2, 2, 3, 0, 4, 2};
    int val = 2;
    int result2 = demo.remove(nums2, val);
    System.out.println("移除元素");
    System.out.println(result2);

    int[] nums3 = {-7, -3, 2, 3, 11};
    int[] result3 = demo.pingfang(nums3);
    System.out.println("平方排序");
    System.out.println(Arrays.toString(result3));

    int[] nums4 = {2, 3, 1, 2, 4, 3};
    int s = 7;
    int res = demo.minSubArrayLen(nums4, s);
    System.out.println("最小子数组");
    System.out.println(res);

    int[][] matrix = demo.generateMatrix(3);
    System.out.println("螺旋矩阵");
    for (int i = 0; i < matrix.length; i++) {
      System.out.println(Arrays.toString(matrix[i]));
    }
  }

  //   ========================================================
  /* * 二分查找 给定一个n个元素有序的(升序)整型数组nums和一个目标值target,写一个函数搜索nums中的target,如果目标值存在返回下标,否则返回-1.
   *
   * <p>示例
   *
   * <p>```plaintext 输入: nums = [-1,0,3,5,9,12], target = 9 输出: 4 解释: 9 出现在 nums 中并且下标为 4
   *
   * <p>输入: nums = [-1,0,3,5,9,12], target = 2 输出: -1 解释: 2 不存在 nums 中因此返回 -1 ```
   *
   * <p>提示
   *
   * <p>- 你可以假设 nums 中的所有元素是不重复的。 - n 将在 [1, 10000]之间。 - nums 的每个元素都将在 [-9999, 9999]之间.*/

  public int search(int[] nums, int target) {
    // todo 二分查找
    int l = 0, r = nums.length - 1;
    while (l <= r) {
      int mid = l + (r - l) / 2;
      if (nums[mid] > target) {
        r = mid - 1;
      } else if (nums[mid] < target) {
        l = mid + 1;
      } else {
        return mid;
      }
    }
    return -1;
  }

  // =========================================================
  /*给你一个数组nums和一个值val,你需要原地溢出所有数值等于val的元素,并返回移除后数组的新长度;

  不需要使用额外的数组空间,你必须仅使用O(1)额外空间并原地修改输入数组;

  元素的顺序可以改变,不需要考虑数组中超出新长度后的元素.

  示例 1: 给定 nums = [3,2,2,3], val = 3, 函数应该返回新的长度 2, 并且 nums 中的前两个元素均为 2。 你不需要考虑数组中超出新长度后面的元素。

  示例 2: 给定 nums = [0,1,2,2,3,0,4,2], val = 2, 函数应该返回新的长度 5, 并且 nums 中的前五个元素为 0, 1, 3, 0, 4。*/
  public int remove(int[] nums, int val) {
    // todo 数组移除元素
    int slow = 0;
    for (int fast = 0; fast < nums.length; fast++) {
      if (nums[fast] != val) {
        nums[slow++] = nums[fast];
      }
    }
    return slow;
  }

  // ================================================================================
  // 给你一个按 非递减顺序排序的整数数组nums,返回每个数字的平方组成的新数组,要求也按非递减顺序排序,
  //
  // 示例 1：
  //
  // - 输入：nums = [-4,-1,0,3,10]
  // - 输出：[0,1,9,16,100]
  // - 解释：平方后，数组变为 [16,1,0,9,100]，排序后，数组变为 [0,1,9,16,100]
  //
  // 示例 2：
  //
  // - 输入：nums = [-7,-3,2,3,11]
  // - 输出：[4,9,9,49,121]
  public int[] pingfang(int[] nums) {
    // todo  数组 平方
    int[] result = new int[nums.length];
    int l = 0, r = nums.length - 1;
    int index = nums.length - 1;
    while (l <= r) {
      if (nums[l] * nums[l] > nums[r] * nums[r]) {
        result[index--] = nums[l] * nums[l++];
      } else {
        result[index--] = nums[r] * nums[r--];
      }
    }
    return result;
  }

  // ==================================================给定一个含有n个正整数的数组和一个正整数s,找出该数组中满足其和>=s的长度最小的连续子数组,并返回其长度,如果不存在符合条件的子数组,返回0;
  //
  // 示例：
  //
  // - 输入：s = 7, nums = [2,3,1,2,4,3]
  // - 输出：2
  // - 解释：子数组 [4,3] 是该条件下的长度最小的子数组。
  //
  // 提示：
  //
  // - 1 <= target <= 10^9
  // - 1 <= nums.length <= 10^5
  // - 1 <= nums[i] <= 10^5

  public int minSubArrayLen(int[] nums, int target) {
    // todo 最小子数组
    int len = Integer.MAX_VALUE;
    int start = 0;
    int sum = 0;
    for (int i = 0; i < nums.length; i++) {
      sum += nums[i];
      while (sum >= target) {
        len = Math.min(len, i - start + 1);
        sum -= nums[start];
        start++;
      }
    }
    return len == Integer.MAX_VALUE ? 0 : len;
  }

  // ===================螺旋矩阵==============================
  //  给定一个正整数n,生成一个包含1到n^2所有元素,且元素按顺时针顺序螺旋排列的正方形矩阵,
  //
  // 例如
  //
  // 输入: 3 输出: [ [ 1, 2, 3 ], [ 8, 9, 4 ], [ 7, 6, 5 ] ]
  public int[][] generateMatrix(int n) {
    // todo 螺旋矩阵II
    int[][] res = new int[n][n];
    int startX = 0, startY = 0;
    int loop = 1;
    int offset = 1;
    int count = 1;
    int i, j;
    int lo = n / 2;
    while (loop <= lo) {
      //上面的一行
      for(j = startY; j < n-offset; j++) {
        res[startX][j] = count++;
      }

      //右侧一列
      for(i = startX; i < n-offset; i++) {
        res[i][j] = count++;
      }

      //底下一行 倒序
      for (;j>startY;j--){
        res[i][j] = count++;
      }

      //左侧一列 倒序
      for (;i> startX;i--){
        res[i][j] = count++;
      }
      startX++;
      startY++;
      loop++;
      offset++;
    }
    if (n%2 == 1){
      res[lo][lo] = n*n;
    }

    return res;
  }
}
