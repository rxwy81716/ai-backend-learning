package com.jianbo.algorthm.arrary;

import java.util.Arrays;
import java.util.Scanner;

/** */
public class Demo01 {

  public static void main(String[] args) {
    Demo01 demo = new Demo01();
    int[] nums = {-1, 0, 3, 5, 9, 12};
    int target = 9;
    int result = demo.search(nums, target);
    System.out.println(result);

    // 测试移除元素
    System.out.println("测试移除元素");
    int[] nums2 = {3, 2, 2, 3};
    int val = 3;
    int result2 = demo.removeElement(nums2, val);
    System.out.println(result2);

    // 非递减顺序排序的整数数组nums,返回每个数字的平方组成的新数组,要求也按非递减顺序排序,
    int[] nums3 = {-4, -1, 0, 3, 10};
    int[] result3 = demo.sortedSquares2(nums3);
    System.out.println(Arrays.toString(result3));

    // 符合条件的最小数组
    int[] nums4 = {2, 3, 1, 2, 4, 3};
    int s = 7;
    int re = demo.minSubArrayLen(nums4, s);
    System.out.println(re);

    // 螺旋矩阵II
    int n = 6;
    int[][] result4 = demo.generateMatrix(n);
    //    System.out.println(Arrays.deepToString(result4));
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

  /**
   * 二分查找(左闭右闭) 时间复杂度：O(log n) 空间复杂度：O(1)
   *
   * @param nums 有序数组升序
   * @param target 目标值
   * @return 目标值下标,否则返回-1
   */
  public int search(int[] nums, int target) {
    // 避免当 target 小于nums[0] nums[nums.length - 1]时多次循环运算
    if (target < nums[0] || target > nums[nums.length - 1]) {
      return -1;
    }
    int left = 0, right = nums.length - 1;
    while (left <= right) {
      // 防止溢出 等价于(left + right) / 2
      int mid = left + (right - left) / 2;
      // >>> 是无符号右移运算符：它会将 left + right 的和（即便溢出成了负数）当作无符号数处理，右移一位相当于除以 2，并忽略符号位。
      //      mid = (left + right) >>> 1;
      if (nums[mid] > target) {
        right = mid - 1;
      } else if (nums[mid] < target) {
        left = mid + 1;
      } else {
        return mid;
      }
    }
    return -1;
  }

  /**
   * 二分查找(左闭右开) 时间复杂度：O(log n) 空间复杂度：O(1)
   *
   * @param nums 有序数组升序
   * @param target 目标值
   * @return 目标值下标,否则返回-1
   */
  public int searchLeftCloseRightOpen(int[] nums, int target) {
    // 避免当 target 小于nums[0] nums[nums.length - 1]时多次循环运算
    if (target < nums[0] || target > nums[nums.length - 1]) {
      return -1;
    }
    int left = 0, right = nums.length;
    while (left < right) {
      int mid = left + (right - left) / 2;
      if (nums[mid] > target) {
        right = mid;
      } else if (nums[mid] < target) {
        left = mid + 1;
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

  /**
   * 移除元素
   *
   * @param nums 数组
   * @param val
   * @return
   */
  public int removeElement(int[] nums, int val) {
    //  时间复杂度：O(n^2)
    // 空间复杂度：O(1)
    int length = nums.length;
    // 双层for循环
    for (int i = 0; i < length; i++) {
      if (nums[i] == val) {
        for (int j = i + 1; j < length; j++) {
          nums[j - 1] = nums[j];
        }
        i--;
        length--;
      }
    }
    System.out.println(length);
    return length;
  }

  // 双指针法
  // 双指针法（快慢指针法）： 通过一个快指针和慢指针在一个for循环下完成两个for循环的工作。

  /**
   * 移除元素 时间复杂度：O(n) 空间复杂度：O(1)
   *
   * @param nums 数组
   * @param val
   * @return
   */
  public int removeElement2(int[] nums, int val) {
    int slow = 0;
    for (int fast = 0; fast < nums.length; fast++) {
      if (nums[fast] != val) {
        nums[slow] = nums[fast];
        slow++;
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

  /**
   * 非递减顺序排序的整数数组nums,返回每个数字的平方组成的新数组,要求也按非递减顺序排序, 暴力排序 O(n + nlog n)
   *
   * @param nums
   * @return
   */
  public int[] sortedSquares(int[] nums) {
    for (int i = 0; i < nums.length; i++) {
      nums[i] *= nums[i];
    }
    Arrays.sort(nums);
    return nums;
  }

  /**
   * 非递减顺序排序的整数数组nums,返回每个数字的平方组成的新数组,要求也按非递减顺序排序, 双指针法 O(n)
   *
   * @param nums
   * @return
   */
  public int[] sortedSquares2(int[] nums) {
    int[] result = new int[nums.length];
    int left = 0, right = nums.length - 1;
    int index = result.length - 1;
    while (left <= right) {
      if (nums[left] * nums[left] > nums[right] * nums[right]) {
        result[index--] = nums[left++];
      } else {
        result[index--] = nums[right--];
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
  public int minSubArrayLen(int[] nums, int s) {
    //  ================O(n^3)===============================
    //    int leng = 2;
    //    while (leng <= nums.length) {
    //      for (int i = 0; i <= nums.length - leng; i++) {
    //        int sum = 0;
    //        for (int j = 0; j < leng; j++) {
    //          sum += nums[i+j];
    //        }
    //        if (sum >= s) {
    //          return leng;
    //        }
    //      }
    //      leng++;
    //    }
    //    return 0;
    // =======================O(n^2)=========================================
    //    int n = nums.length;
    //    int result = Integer.MAX_VALUE; // 初始化为一个最大值
    //    for (int i = 0; i < n; i++) { // 1. 固定起点
    //      int sum = 0;
    //      for (int j = i; j < n; j++) { // 2. 延伸终点，并在过程中累加
    //        sum += nums[j];
    //        if (sum >= s) { // 3. 一旦满足条件
    //          int curLength = j - i + 1; // 计算当前长度
    //          result = Math.min(result, curLength); // 更新最小长度
    //          break; // 因为数组是正数，再往后加肯定也满足条件且长度更长，直接跳出内层循环
    //        }
    //      }
    //    }
    //
    //    return result == Integer.MAX_VALUE ? 0 : result;
    // ===========================O(n)=====================================
    //    窗口滑动
    //    右指针不停走，左指针看情况收。
    //    收的时候记长度，最后结果不用愁。
    int leng = Integer.MAX_VALUE;
    int sum = 0;
    int start = 0;
    for (int i = 0; i < nums.length; i++) {
      sum += nums[i];
      while (sum >= s) {
        leng = Math.min(leng, i - start + 1);
        sum -= nums[start];
        start++;
      }
    }
    return leng == Integer.MAX_VALUE ? 0 : leng;
  }

  // ===================螺旋矩阵==============================
  //  给定一个正整数n,生成一个包含1到n^2所有元素,且元素按顺时针顺序螺旋排列的正方形矩阵,
  //
  // 例如
  //
  // 输入: 3 输出: [ [ 1, 2, 3 ], [ 8, 9, 4 ], [ 7, 6, 5 ] ]
  public int[][] generateMatrix(int n) {
    int[][] matrix = new int[n][n];
    int startX = 0, startY = 0; // 定义每循环一个圈的起始位置
    int loop = 1; // 每个圈循环几次，例如n为奇数3，那么loop = 1 只是循环一圈，矩阵中间的值需要单独处理
    int mid = n / 2; // 矩阵中间的位置，例如：n为3， 中间的位置就是(1，1)，n为5，中间位置为(2, 2)
    int count = 1; // 用来给矩阵中每一个空格赋值
    int offset = 1; // 控制每一条边遍历的长度,每次循环右边界收缩一位
    int k, j; // i代表行 j代表列
    while (loop <= n / 2) {
      // 顶部
      // 左闭右开
      for (j = startY; j < n - offset; j++) {
        matrix[startX][j] = count++;
      }
      // 右列
      for (k = startX; k < n - offset; k++) {
        matrix[k][j] = count++;
      }

      // 底列
      for (; j > startY; j--) {
        matrix[k][j] = count++;
      }
      // 左列
      for (; k > startX; k--) {
        matrix[k][j] = count++;
      }
      startY++;
      startX++;
      offset++;
      loop++;
      // 每行垂直打印
      for (int i = 0; i < n; i++) {
        System.out.println(Arrays.toString(matrix[i]));
      }
      System.out.println("--------------------------------------------");
    }
    if (n % 2 == 1) { // n为奇数的时候 单独处理中心点
      matrix[mid][mid] = count;
    }
    return matrix;
  }
}
