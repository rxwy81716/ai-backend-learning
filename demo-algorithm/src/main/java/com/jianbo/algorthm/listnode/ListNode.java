package com.jianbo.algorthm.listnode;

public class ListNode {
    int val;
    ListNode next;

    public ListNode() {
    }

    public ListNode(int var) {
        this.val = var;
    }

    public ListNode(int var, ListNode listNodeTest){
        this.val = var;
        this.next = listNodeTest;
    }


    @Override
    public String toString() {
        return  val + "->" + next;
    }
}
