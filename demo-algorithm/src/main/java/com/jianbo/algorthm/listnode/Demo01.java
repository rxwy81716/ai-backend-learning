package com.jianbo.algorthm.listnode;

import java.util.List;

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
    ListNode pre = null;
    ListNode curr = head;
    ListNode temp = null;
    while (curr != null) {
      temp = curr.next;
      curr.next = pre;
      pre = curr;
      curr = temp;
    }
    return pre;
  }

  //  给你一个链表，两两交换其中相邻的节点，并返回交换后链表的头节点。你必须在不修改节点内部的值的情况下完成本题（即，只能进行节点交换）。
  // 将步骤 2,3 交换顺序，这样不用定义 temp 节点
  public ListNode swapPairs(ListNode head) {
    ListNode dummy = new ListNode(0, head);
    ListNode cur = dummy;
    while (cur.next != null && cur.next.next != null) {
      ListNode node1 = cur.next; // 第 1 个节点
      ListNode node2 = cur.next.next; // 第 2 个节点
      cur.next = node2; // 步骤 1
      node1.next = node2.next; // 步骤 3
      node2.next = node1; // 步骤 2
      cur = cur.next.next;
    }
    return dummy.next;
  }

  // 给你一个链表，删除链表的倒数第 n 个结点，并且返回链表的头结点。
  // 进阶：你能尝试使用一趟扫描实现吗？
  public ListNode removeNthFromEnd(ListNode head, int n) {
    ListNode result = new ListNode(0, head);
    ListNode curr = head;
    ListNode l = result;
    ListNode r = result;
    for (int i = 0; i <= n; i++) {
      r = r.next;
    }
    while (r.next != null) {
      l = l.next;
      r = r.next;
    }
    if (l.next != null) {
      l.next = l.next.next;
    }
    return result.next;
  }

  // 给你两个单链表的头节点 headA 和 headB ，请你找出并返回两个单链表相交的起始节点。如果两个链表没有交点，返回 null 。
  public ListNode getIntersectionNode(ListNode headA, ListNode headB) {
    ListNode p1 = headA, p2 = headB;
    while (p1 != p2) {
      if (p1 == null) {
        p1 = headB;
      } else {
        p1 = p1.next;
      }
      if (p2 == null) {
        p2 = headA;
      } else {
        p2 = p2.next;
      }
    }
    return p1;
  }

  public ListNode getIntersectionNode2(ListNode headA, ListNode headB) {
    if (headA == null || headB == null) {
      return null;
    }
    ListNode a = headA;
    ListNode b = headB;
    int aSize = 0, bSize = 0;
    while (a != null) {
      aSize = aSize + 1;
      a = a.next;
    }

    while (b != null) {
      bSize = bSize + 1;
      b = b.next;
    }
    a = headA;
    b = headB;
    if (bSize > aSize) {
      int tempLen = aSize;
      aSize = bSize;
      bSize = tempLen;
      ListNode temp = a;
      a = b;
      b = temp;
    }
    int gap = aSize - bSize;
    while (gap-- > 0) {
      a = a.next;
    }
    while (a != null) {
      if (a == b) {
        return a;
      }
      a = a.next;
      b = b.next;
    }
    return null;
  }

  // 题意： 给定一个链表，返回链表开始入环的第一个节点。 如果链表无环，则返回 null。
  // 为了表示给定链表中的环，使用整数 pos 来表示链表尾连接到链表中的位置（索引从 0 开始）。 如果 pos 是 -1，则在该链表中没有环。
  // **说明**：不允许修改给定的链表。
  public ListNode detectCycle(ListNode head) {
    ListNode slow = head;
    ListNode fast = head;

    while (fast != null && fast.next != null) {
      slow = slow.next;
      fast = fast.next.next;
      if (slow == fast) {
        slow = head;
        while (slow != fast) {
          fast = fast.next;
          slow = slow.next;
        }
        return slow;
      }
    }
    return null;
  }
}
