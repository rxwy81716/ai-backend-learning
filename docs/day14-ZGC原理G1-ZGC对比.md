## 一、ZGC 是什么

ZGC = Z Garbage Collector

JDK11 推出，JDK17 默认垃圾收集器

**超低延迟、TB 级超大堆、几乎无 STW**

阿里、字节、腾讯线上大规模标配

## 二、ZGC 核心结构（结构图）

```
ZGC 堆内存
├─ 多个 ZPage 内存页
├─ 不分新生代老年代（无分代）
├─ 支持超大堆：8MB ~ 16TB
└─ 着色指针、读屏障 核心黑科技
```

### ZGC 三大逆天特点

1. **STW 极低：＜1ms**
2. 堆无限大：支持 **16TB 超大内存**
3. 几乎不影响业务 QPS，并发全程不卡顿

## 三、ZGC 底层核心原理（染色指针 + 读屏障）

1. **着色指针 Color Pointer**

   对象指针里额外存 4 位标记信息

   不用遍历对象头部，**直接看指针就知道是不是垃圾**

   速度极快

2. **读屏障 Load Barrier**

   读取对象时轻微拦截处理

   保证并发标记不乱

3. 不分代、不分区 Region

   整体连续整理，**零内存碎片**

## 四、ZGC 完整回收流程

1. 并发标记
2. 并发转移存活对象
3. 并发重定位
4. 并发释放垃圾内存

全程**只有初始标记极短 STW**

其余所有步骤 和业务线程并发执行

## 五、G1 VS ZGC 终极对比（面试必背）

1. STW 停顿

- G1：几毫秒～几十毫秒
- ZGC：**＜1ms，肉眼无感**

1. 堆大小

- G1：最大几 GB~ 几十 GB
- ZGC：**最高 16TB**

1. 是否分代

- G1：逻辑分新生代、老年代
- ZGC：**不分代**

1. 内存碎片

- G1：基本无碎片
- ZGC：完全无碎片

1. CPU 消耗

- G1 中等
- ZGC 更低

1. JDK 版本

- G1 JDK9 默认
- ZGC JDK17 默认

## 六、CMS / G1 / ZGC 三代演进路线

CMS → 并发低延迟，有碎片

G1 → 分区回收，可控停顿，无碎片

ZGC → 极致低延迟，超大堆，毫秒级 STW

## 七、JVM GC 全套代码演示（直接复制运行）

### 1、指定 G1 运行

```
-Xms1G -Xmx1G
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-Xlog:gc*
```

### 2、指定 ZGC 运行（JDK17+）

```
-Xms4G -Xmx4G
-XX:+UseZGC
-Xlog:gc*
```

### 3、代码疯狂产生垃圾，触发 GC

```
public class GcTest {
    public static void main(String[] args) {
        while (true){
            // 不断new大对象
            byte[] b = new byte[1024*1024*2];
        }
    }
}
```

### 4、线上查看 GC 命令

```
jps                    # 看进程
jcmd 进程 GC.heap_info # 看堆详情
jstat -gc 进程 1000    # 每秒打印GC次数
```

## 八、JVM 内存 + GC 手写终极思维导图结构

1. 运行时 5 大内存区域

   程序计数器、栈、本地方法栈、堆、元空间

2. 垃圾判定

   可达性分析、GC Roots、四大引用

3. GC 算法

   标记清除、标记复制、标记整理

4. 收集器三代

   CMS → G1 → ZGC

5. YoungGC / MixedGC / FullGC

6. STW 停顿问题与优化