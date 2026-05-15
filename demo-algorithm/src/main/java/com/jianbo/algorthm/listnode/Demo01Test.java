package com.jianbo.algorthm.listnode;

public class Demo01Test {
  public static void main(String[] args) {
    Demo01Test demo01Test = new Demo01Test();
    demo01Test.removeElements(null, 0);
  }

  //    给你一个链表的头节点 head 和一个整数 val ，请你删除链表中所有满足 Node.val == val 的节点，并返回 新的头节点 。
  public ListNode removeElements(ListNode head, int val) {
    // todo 删除元素
    ListNode listNode = new ListNode(0, head);
    ListNode cur = listNode;
    while (cur.next != null){
      if (cur.next.val == val){
        cur.next = cur.next.next;
      }else {
        cur = cur.next;
      }
    }
    return listNode.next;
  }

  // 题意：反转一个单链表。
  // 示例: 输入: 1->2->3->4->5->NULL 输出: 5->4->3->2->1->NULL
  public ListNode reverseList(ListNode head) {
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
  public ListNode swapPairs(ListNode head) {
    ListNode temp = new ListNode(0,head);
    ListNode cur = temp;
    while(cur.next != null && cur.next.next!= null){
      ListNode n1 = cur.next;
      ListNode n2 = cur.next.next;
      n1.next = n2.next;
      n2.next = n1;
      cur.next = n2;
      cur = cur.next.next;
    }
    return temp.next;
  }

  // 给你一个链表，删除链表的倒数第 n 个结点，并且返回链表的头结点。
  // 进阶：你能尝试使用一趟扫描实现吗？
  public ListNode removeNthFromEnd(ListNode head, int n) {
    ListNode listNode = new ListNode(0, head);
    ListNode p1 = listNode;
    ListNode p2 = listNode;
    for(int i = 0; i < n+1; i++) {
      p2 = p2.next;
    }
    while (p2!=null){
      p1 = p1.next;
      p2 = p2.next;
    }
    p1.next = p1.next.next;
    return listNode.next;
  }

  // 给你两个单链表的头节点 headA 和 headB ，请你找出并返回两个单链表相交的起始节点。如果两个链表没有交点，返回 null 。
  public ListNode getIntersectionNode(ListNode headA, ListNode headB) {
    ListNode p1 = headA;
    ListNode p2 = headB;
    while (p1!=p2){
      if (p1 == null) p1 = headB;
      else p1 = p1.next;
      if (p2 == null) p2 = headA;
      else p2= p2.next;
    }
    return p1;
  }

  // 给定一个链表，返回链表开始入环的第一个节点。 如果链表无环，则返回 null。
  //思路 走1 走2 还原A  走1
  public ListNode detectCycle(ListNode head) {
    ListNode slow = head;
    ListNode fast = head;
    while (fast != null && fast.next != null){
      slow = slow.next;
      fast = fast.next.next;
      if (slow == fast){
        slow = head;
        while (slow != fast){
          slow = slow.next;
          fast = fast.next;
        }
        return slow;
      }
    }
    return null;
  }
}
