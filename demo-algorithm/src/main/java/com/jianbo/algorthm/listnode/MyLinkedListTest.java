package com.jianbo.algorthm.listnode;

/**
 * 实现 MyLinkedList 类：
 *
 * <p>MyLinkedList() 初始化 MyLinkedList 对象。 int get(int index) 获取链表中下标为 index 的节点的值。如果下标无效，则返回 -1 。
 * void addAtHead(int val) 将一个值为 val 的节点插入到链表中第一个元素之前。在插入完成后，新节点会成为链表的第一个节点。 void addAtTail(int val)
 * 将一个值为 val 的节点追加到链表中作为链表的最后一个元素。 void addAtIndex(int index, int val) 将一个值为 val 的节点插入到链表中下标为 index
 * 的节点之前。如果 index 等于链表的长度，那么该节点会被追加到链表的末尾。如果 index 比长度更大，该节点将 不会插入 到链表中。 void deleteAtIndex(int
 * index) 如果下标有效，则删除链表中下标为 index 的节点。
 */
public class MyLinkedListTest {
  class Node {
    int val;
    Node next;

    Node(int val) {
      this.val = val;
    }

    Node(int val, Node next) {
      this.val = val;
      this.next = next;
    }
  }

  int size;
  Node node;

  MyLinkedListTest() {
    this.node = new Node(0);
    this.size = 0;
  }

  // 获取链表中下标为 index 的节点的值。如果下标无效，则返回 -1 。
  public int get(int index) {
    if (index < 0 || index >= size) {
      return -1;
    }
    Node cur = node;
    for (int i = 0; i <= index; i++) {
      cur = cur.next;
    }
    return cur.val;
  }

  //  将一个值为 val 的节点插入到链表中第一个元素之前。在插入完成后，新节点会成为链表的第一个节点。
  public void addAtHead(int val) {
    Node temp = new Node(val);
    temp.next = node.next;
    node.next = temp;
    size++;
  }

  // 将一个值为 val 的节点追加到链表中作为链表的最后一个元素
  public void addAtTail(int val) {
    Node cur = node;
    while (cur.next != null) {
      cur = cur.next;
    }
    cur.next = new Node(val);
    size++;
  }

  // 将一个值为 val 的节点插入到链表中下标为 index
  // * 的节点之前。如果 index 等于链表的长度，那么该节点会被追加到链表的末尾。如果 index 比长度更大，该节点将 不会插入 到链表中。
  public void addAtIndex(int index, int val) {
    if (index < 0 || index > size) {
      return;
    }
    Node cur = node;
    for (int i = 0; i < index; i++) {
      cur = cur.next;
    }
    Node temp = new Node(val);
    temp.next = cur.next;
    cur.next = temp;
    size++;
  }

  // 如果下标有效，则删除链表中下标为 index 的节点。
  public void deleteAtIndex(int index) {
    if (index < 0 || index >= size) {
      return;
    }
    Node cur = node;
    for(int i = 0; i < index; i++) {
      cur = cur.next;
    }
    cur.next = cur.next.next;
    size--;
  }
}
