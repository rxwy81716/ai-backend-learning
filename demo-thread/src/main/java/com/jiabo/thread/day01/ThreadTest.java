package com.jiabo.thread.day01;

import com.jiabo.thread.day01.thread.MyCallable;
import com.jiabo.thread.day01.thread.MyRunnable;
import com.jiabo.thread.day01.thread.MyThread1;

import java.util.concurrent.*;

public class ThreadTest {

    public static void main(String[] args) {

        System.out.println("Hello World!");
        // Thread 1: 继承Thread类
        MyThread1 myThread1 = new MyThread1();
        // Thread 2: 实现Runnable接口
        Thread myThread2 = new Thread(new MyRunnable());
        // Thread 3: 实现Callable接口
        FutureTask<String> futureTask = new FutureTask<>(new MyCallable());
        // Thread 4: 实现Callable接口
        Thread myThread3 = new Thread(futureTask);
        myThread1.start();
        myThread2.start();
        myThread3.start();
        try {
            System.out.println(futureTask.get());
            // 使用ExecutorService 创建线程并提交Callable任务
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<String> future = executor.submit(new MyCallable());
            String result = future.get();  // Blocks until complete
            System.out.println(result);
            executor.shutdown();

        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }


    }

}
