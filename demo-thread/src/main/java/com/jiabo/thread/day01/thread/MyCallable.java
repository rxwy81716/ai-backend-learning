package com.jiabo.thread.day01.thread;

import java.util.concurrent.Callable;

public class MyCallable implements Callable<String> {
    @Override
    public String call() throws Exception {
        System.out.println("线程3正在运行");
        return "线程3返回值";
    }
}
