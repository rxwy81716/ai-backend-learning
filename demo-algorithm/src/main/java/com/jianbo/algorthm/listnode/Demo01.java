package com.jianbo.algorthm.listnode;

public class Demo01 {

  public static void main(String[] args) {
    Demo01 demo = new Demo01();
    // 示例 1： 输入：head = [1,2,6,3,4,5,6], val = 6 输出：[1,2,3,4,5]
    ListNode listNode =
        new ListNode(
            1,
            new ListNode(
                2,
                new ListNode(
                    6, new ListNode(3, new ListNode(4, new ListNode(5, new ListNode(6)))))));
    demo.removeElements(listNode, 6);
    System.out.println(listNode.toString());

    System.out.println("反转链表");
    System.out.println(listNode.toString());
    System.out.println(demo.reverseList(listNode).toString());
  }

  // 题意：删除链表中等于给定值 val 的所有节点。
  // 示例 1： 输入：head = [1,2,6,3,4,5,6], val = 6 输出：[1,2,3,4,5]
  // 示例 2： 输入：head = [], val = 1 输出：[]
  // 示例 3： 输入：head = [7,7,7,7], val = 7 输出：[]
  public ListNode removeElements(ListNode head, int val) {
    if (head == null) {
      return null;
    }
    while (head != null && head.val == val) {
      head = head.next;
    }
    ListNode result = head;
    while (result != null && result.next != null && result.val != val) {
      int var = result.next.val;
      if (var == val) {
        result.next = result.next.next;
      } else {
        result = result.next;
      }
    }
    return head;
  }

  //  题意：反转一个单链表。
  // 示例: 输入: 1->2->3->4->5->NULL 输出: 5->4->3->2->1->NULL

  public ListNode reverseList(ListNode head) {
    if (head == null || head.next == null){
      return head;
    }
    ListNode result = reverseList(head.next);
    head.next.next = head;
    head.next = null;
    return result;
  }

  public ListNode reverseList2(ListNode head) {
    ListNode pre = null;
    ListNode curr = head;
    ListNode temp = null;
    while (curr != null){
       temp = curr.next;
       curr.next = pre;
       pre = curr;
       curr = temp;
    }
    return pre;
  }

//  给你一个链表，两两交换其中相邻的节点，并返回交换后链表的头节点。你必须在不修改节点内部的值的情况下完成本题（即，只能进行节点交换）。
  /**
 * 两两交换链表中的相邻节点，使用递归方式实现
 * @param head 链表的头节点
 * @return 交换相邻节点后的链表头节点
 */
public ListNode swapPairs(ListNode head) {
    if (head == null || head.next == null ){
      return head;
    }
    ListNode next = head.next;
    //进行递归
    ListNode newNode = swapPairs(next.next);
    // 这里进行交换
    next.next = head;
    head.next = newNode;

    return next;
  }

  /**
   * 两两交换链表中的相邻节点，使用虚拟头结点和迭代方式实现
   *
   * @param head 链表的头结点
   * @return 交换相邻节点后的链表头结点
   */
  public ListNode swapPairs2(ListNode head) {
    ListNode dumyhead = new ListNode(-1); // 设置一个虚拟头结点
   dumyhead.next = head; // 将虚拟头结点指向head，这样方便后面做删除操作
   ListNode cur = dumyhead;
   ListNode temp; // 临时节点，保存两个节点后面的节点
   ListNode firstnode; // 临时节点，保存两个节点之中的第一个节点
   ListNode secondnode; // 临时节点，保存两个节点之中的第二个节点
   while (cur.next != null && cur.next.next != null) {
     temp = cur.next.next.next;
     firstnode = cur.next;
     secondnode = cur.next.next;
     cur.next = secondnode;       // 步骤一
     secondnode.next = firstnode; // 步骤二
     firstnode.next = temp;      // 步骤三
     cur = firstnode; // cur移动，准备下一轮交换
   }
   return dumyhead.next;
  }

  /**
   * 两两交换链表中的相邻节点，使用迭代方式实现
   *
   * @param head 链表头节点
   * @return 交换相邻节点后的链表头节点
   */
  public ListNode swapPairs3(ListNode head) {
    ListNode dummy = new ListNode(0, head);
  ListNode cur = dummy;
  while (cur.next != null && cur.next.next != null) {
    ListNode node1 = cur.next;// 第 1 个节点
    ListNode node2 = cur.next.next;// 第 2 个节点
    cur.next = node2; // 步骤 1
    node1.next = node2.next;// 步骤 3
    node2.next = node1;// 步骤 2
    cur = cur.next.next;
  }
  return dummy.next;
 }


}
