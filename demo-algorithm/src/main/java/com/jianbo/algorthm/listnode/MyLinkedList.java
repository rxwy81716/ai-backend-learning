package com.jianbo.algorthm.listnode;

/**
 * Your MyLinkedList object will be instantiated and called as such: MyLinkedList obj = new
 * MyLinkedList(); int param_1 = obj.get(index); obj.addAtHead(val); obj.addAtTail(val);
 * obj.addAtIndex(index,val); obj.deleteAtIndex(index);
 */
class MyLinkedList {
  class Node {
    int val;
    Node next;

    Node(int val) {
      this.val = val;
    }
  }

  int size;
  Node head;

  public MyLinkedList() {
    head = new Node(0); // 虚拟节点
    size = 0;
  }

  public int get(int index) {
    if (index < 0 || index >= size) {
      return -1;
    }
    Node curr = head;
    for (int i = 0; i <= index; i++) {
      curr = curr.next;
    }
    return curr.val;
  }

  public void addAtHead(int val) {
    Node node = new Node(val);
    node.next = head.next;
    head.next = node;
    size++;
  }

  public void addAtTail(int val) {
    Node curr = head;
    while (curr.next != null) {
      curr = curr.next;
    }
    curr.next = new Node(val);
    size++;
  }

  public void addAtIndex(int index, int val) {
    if (index < 0 || index > size) {
      return;
    }
    Node curr = head;
    for (int i = 0; i < index; i++) {
      curr = curr.next;
    }
    Node node = new Node(val);
    node.next = curr.next;
    curr.next = node;
    size++;
  }

  public void deleteAtIndex(int index) {
    if (index < 0 || index >= size) {
      return;
    }
    Node curr = head;
    for (int i = 0; i < index; i++) {
      curr = curr.next;
    }
    curr.next = curr.next.next;
    size--;
  }
}
