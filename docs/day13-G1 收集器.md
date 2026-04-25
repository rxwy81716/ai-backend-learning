## 一、G1 整体原理（大白话 + 底层结构）

1. G1 = **Garbage First 优先回收垃圾最多区域**

2. 不再严格划分新生代、老年代

3. 把整个堆内存切成一个个

   大小相等 Region 区域

   每个 Region 1M~32M，默认 1/4 堆大小

4. 新生代、老年代只是**逻辑分区**，物理上都是 Region

5. 可精准控制**最大 GC 停顿毫秒数**，互联网秒杀首选

6. 全程**无内存碎片**

## 二、G1 核心结构图解（文字结构图，可直接画图）

```
堆内存 Heap
├─────────────────────────────--┐
│  Region A  Region B  Region C │ 新生代 Eden
│  Region D  Region E           │ Survivor
│                               │
│  Region F  Region G  Region H │ 老年代 Old
│                               │
│  Humongous 大对象Region（超大对象专用）
└────────────────────────────--─┘
```

- Eden 区：新建对象
- Survivor：熬过几次 MinorGC 存活对象
- Old：长期存活老年对象
- Humongous：超过 Region 一半大小 → 直接大对象区

## 三、G1 完整 GC 回收全流程（分步背诵版）

### 1、新生代回收 Young GC

1. 对象新建进入 Eden Region
2. Eden 满 → 触发 Young GC
3. 存活对象复制到 Survivor Region
4. 年龄 + 1，多次 GC 后晋升 Old 老年代 Region
5. 短暂 STW，速度极快

### 2、并发标记周期 Mixed GC（G1 灵魂流程）

1. **初始标记 STW**：标记 GC Roots 直接关联对象
2. **并发标记**：用户线程和 GC 线程一起跑，遍历全部引用链
3. **重新标记 STW**：修正并发期间变动引用，短暂停顿
4. **并发清理**：回收垃圾 Region，不暂停业务

### 3、筛选回收 Full GC 降级

老年代 Region 占用达到阈值 → 整体整理回收

JDK8 后 G1 很少 FullGC，基本不会阻塞业务

### 4、G1 核心策略

优先回收**垃圾占比最高 Region**，垃圾最多先清

用最短时间，释放最多内存 → 控制 STW 时长

## 四、G1 优缺点

✅ 优点

1. 可预测停顿时间
2. 无内存碎片
3. 新生代老年代统一回收
4. 支持超大堆内存
5. JDK9 默认垃圾收集器

❌ 缺点

1. 并发标记占用 CPU
2. 内存占用比 CMS 稍高

## 五、G1 vs CMS 面试必背对比

1. CMS 标记清除 → 内存碎片

   G1 标记整理 → 无碎片

2. CMS 不可控 STW

   G1 精确控制停顿毫秒

3. CMS 只处理老年代

   G1 全局统一回收

4. CMS 已废弃，G1 线上主流

------

# 六、Java 代码演示 G1 GC 参数 + 查看 GC 日志

## 1、JVM 启动参数 指定 G1 收集器

```
// VM参数
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200   // 最大GC停顿200ms
-Xms1G -Xmx1G
```

## 2、代码模拟内存垃圾，触发 G1 GC

```
public class G1GCDemo {
    public static void main(String[] args) throws InterruptedException {
        // 循环创建大量对象，制造垃圾
        while (true){
            byte[] arr = new byte[1024 * 1024]; // 1M对象
            Thread.sleep(10);
        }
    }
}
```

## 3、查看 GC 运行日志

```
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
-Xlog:gc*
```

输出能看到：

- Young GC
- Concurrent GC 并发标记
- Mixed GC 混合回收
- Region 回收信息

## 4、jcmd 命令查看 G1 堆分区

```
jcmd 进程号 VM.GCHeapInfo
```

------

# 七、极简结构图（手写画在笔记上）

```
【G1堆结构】
Heap = N个独立Region
Eden Region  ──┐
Survivor Region ─┤ 新生代
Old Region        ─老年代
Humongous Region  ─大对象

【GC流程】
Young GC → 晋升老年代
→ 堆占用阈值触发
并发标记 → 重新标记 → 筛选回收
→ 控制STW短停顿
```

------

# 八、一句话面试总结

G1 把堆拆成 Region，优先回收垃圾最多区块，并发标记、整理无碎片，可控制最大停顿时间，兼顾吞吐量与低延迟，是目前线上标准垃圾收集器。