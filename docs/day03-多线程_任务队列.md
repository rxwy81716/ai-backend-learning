# 线程池任务队列全解析：类型、原理与实战选型

### 1. 线程池队列任务的核心作用

> 线程池核心是线程复用+任务缓冲,任务队列的核心职责是
>
> 1. 当核心线程已满,临时存储待执行的任务
> 2. 配合拒绝策略,控制任务的接受上线,避免系统过载
> 3. 配合不同的队列调度策略(如FIFO,优先级),满足不同业务需求
> 4. 减少线程创建/销毁的开销,提升系统的响应速度.

### 2. 常见任务队列类型详解

#### 2.1 无界队列:LinkedBlockingQueue

> 核心特性:
>
> - **实现原理**：基于链表的阻塞队列，默认构造函数为无界队列（容量Integer.MAX_VALUE），也可指定容量变为有界队列；
> - **调度策略**：FIFO（先进先出），保证任务执行顺序与提交顺序一致；
> - **阻塞特性**：当队列空时，获取任务的线程会阻塞；当队列满时（仅指定容量时），添加任务的线程会阻塞。
>
> 源码关键逻辑
>
> ```java
> // 无界队列构造
> public LinkedBlockingQueue() {
>     this(Integer.MAX_VALUE); // 默认容量2^31-1
> }
> 
> // 入队操作（非阻塞，失败返回false）
> public boolean offer(E e) {
>     if (e == null) throw new NullPointerException();
>     final AtomicInteger count = this.count;
>     if (count.get() == capacity)
>         return false;
>     int c = -1;
>     Node<E> node = new Node<E>(e);
>     final ReentrantLock putLock = this.putLock;
>     putLock.lock();
>     try {
>         if (count.get() < capacity) {
>             enqueue(node);
>             c = count.getAndIncrement();
>             if (c + 1 < capacity)
>                 notFull.signal(); // 唤醒等待入队的线程
>         }
>     } finally {
>         putLock.unlock();
>     }
>     if (c == 0)
>         signalNotEmpty(); // 唤醒等待出队的线程
>     return c >= 0;
> }
> 
> ```
>
> 适用场景与注意事项
>
> 适用场景：任务提交频率稳定、任务执行时间较短的场景（如普通接口请求处理）；
> 优点：无界队列可避免任务被拒绝，保证任务不丢失；链表结构支持高效的入队 / 出队操作；
> 风险：若任务执行速度慢于提交速度，队列会持续扩容，可能导致 JVM 内存溢出（OOM）；
> 典型应用：Executors.newFixedThreadPool()默认使用无界LinkedBlockingQueue。

#### 2.2 有界队列: ArrayBlockingQueue

> 核心特性:
>
> - **实现原理**：基于数组的有界阻塞队列，必须指定队列容量，数组结构保证随机访问的高效性；
> - **调度策略**：FIFO，支持公平锁 / 非公平锁（默认非公平锁）；
> - **阻塞特性**：队列满时入队线程阻塞，队列空时出队线程阻塞。
>
> 源码关键逻辑
>
> ```java
> // 必须指定容量，可选公平锁
> public ArrayBlockingQueue(int capacity, boolean fair) {
>     if (capacity <= 0)
>         throw new IllegalArgumentException();
>     this.items = new Object[capacity];
>     lock = new ReentrantLock(fair); // 公平锁保证线程按等待顺序获取锁
>     notEmpty = lock.newCondition();
>     notFull = lock.newCondition();
> }
> 
> // 入队操作（阻塞）
> public void put(E e) throws InterruptedException {
>     checkNotNull(e);
>     final ReentrantLock lock = this.lock;
>     lock.lockInterruptibly();
>     try {
>         while (count == items.length)
>             notFull.await(); // 队列满时阻塞
>         enqueue(e);
>     } finally {
>         lock.unlock();
>     }
> }
> 
> ```
>
> 适用场景与注意事项
>
> 适用场景：系统资源有限、需要严格控制任务队列大小的场景（如高并发写入数据库）；
> 优点：固定容量避免内存溢出，数组结构的入队 / 出队操作效率高于链表（无节点创建开销）；
> 缺点：队列满时新任务会被阻塞或拒绝，需配合合理的拒绝策略；
> 公平锁说明：公平锁可避免线程饥饿，但会降低吞吐量，仅在对任务执行顺序有严格要求时使用

#### 2.3 优先级队列：PriorityBlockingQueue

> 核心特性
>
> 实现原理：基于二叉堆的无界阻塞队列，支持按任务优先级排序执行；
> 调度策略：优先级排序（默认自然排序，或通过Comparator自定义排序），优先级高的任务先执行；
> 阻塞特性：仅在队列空时出队线程阻塞，入队操作无阻塞（无界队列）。
> 源码关键逻辑
>
> ```java
> // 自定义优先级示例
> public class PriorityTask implements Runnable, Comparable<PriorityTask> {
>     private int priority;
>     private String taskName;
> 
>     @Override
>     public int compareTo(PriorityTask o) {
>         // 降序排列：优先级数值越大，执行越靠前
>         return Integer.compare(o.priority, this.priority);
>     }
> 
>     @Override
>     public void run() {
>         System.out.println("执行任务：" + taskName + "，优先级：" + priority);
>     }
> }
> 
> // 队列使用
> PriorityBlockingQueue<PriorityTask> queue = new PriorityBlockingQueue<>();
> queue.put(new PriorityTask(3, "任务A"));
> queue.put(new PriorityTask(1, "任务B"));
> queue.put(new PriorityTask(2, "任务C"));
> // 执行顺序：任务A → 任务C → 任务B
> 
> ```
>
> 
>
> 适用场景与注意事项
>
> 适用场景：任务有明确优先级区分的场景（如紧急订单处理、核心接口请求优先于普通请求）；
> 优点：保证高优先级任务优先执行，无界队列避免任务丢失；
> 风险：无界队列可能导致 OOM；优先级排序会增加任务入队 / 出队的时间复杂度（O (log n)），吞吐量低于 FIFO 队列；
> 注意：若所有任务优先级相同，等价于普通 FIFO 队列，建议直接使用LinkedBlockingQueue。

#### 2.4 延迟队列 DelayQueue

> **核心特性**
>
> - **实现原理**：基于PriorityBlockingQueue的无界阻塞队列，任务需实现Delayed接口，支持延迟执行；
> - **调度策略**：按任务延迟时间排序，延迟时间最短的任务先执行；
> - **阻塞特性**：任务未到延迟时间时，出队线程阻塞；入队无阻塞。
>
> 实战案例
>
> ```Java
> // 延迟任务类
> public class DelayTask implements Runnable, Delayed {
>     private long delayTime; // 延迟时间（毫秒）
>     private long executeTime; // 实际执行时间
>     private String taskName;
> 
>     public DelayTask(long delayTime, String taskName) {
>         this.delayTime = delayTime;
>         this.taskName = taskName;
>         this.executeTime = System.currentTimeMillis() + delayTime;
>     }
> 
>     // 计算剩余延迟时间
>     @Override
>     public long getDelay(TimeUnit unit) {
>         return unit.convert(executeTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
>     }
> 
>     // 按延迟时间排序
>     @Override
>     public int compareTo(Delayed o) {
>         return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
>     }
> 
>     @Override
>     public void run() {
>         System.out.println("延迟" + delayTime + "ms执行任务：" + taskName);
>     }
> }
> 
> // 线程池使用延迟队列
> public class DelayQueueDemo {
>     public static void main(String[] args) {
>         ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
>         // 提交延迟任务
>         executor.schedule(new DelayTask(1000, "任务1"), 1000, TimeUnit.MILLISECONDS);
>         executor.schedule(new DelayTask(3000, "任务2"), 3000, TimeUnit.MILLISECONDS);
>         executor.shutdown();
>     }
> }
> ```

#### 2.5 同步移交队列: SynchronousQueue

>**核心特性**
>
>- **实现原理**：无缓冲的阻塞队列，队列本身不存储任何任务，仅负责 “移交” 任务；
>- **调度策略**：直接移交（有空闲线程时立即将任务交给线程执行，无空闲线程时入队失败）；
>- **阻塞特性**：无空闲线程时，入队线程会阻塞直到有线程接收任务；无任务时，出队线程会阻塞直到有任务提交。
>
>源码关键逻辑
>
>```Java
>// 入队操作（非阻塞）
>public boolean offer(E e) {
>    if (e == null) throw new NullPointerException();
>    return transferer.transfer(e, false, 0);
>}
>
>// 核心移交逻辑（简化）
>private static abstract class Transferer<E> {
>    abstract boolean transfer(E e, boolean timed, long nanos);
>}
>
>// 非公平模式移交实现
>static final class TransferStack<E> extends Transferer<E> {
>    @Override
>    boolean transfer(E e, boolean timed, long nanos) {
>        // 尝试匹配空闲线程，直接移交任务
>        // 无匹配线程则入栈，等待线程获取
>        // ... 省略复杂的线程匹配逻辑
>    }
>}
>
>```
>
>适用场景与注意事项
>
>适用场景：任务执行时间短、需要低延迟的场景（如高频 RPC 调用、实时数据处理）；
>优点：无队列缓冲，任务直接移交线程，延迟最低；避免队列积压导致的内存占用；
>缺点：无空闲线程时任务会被阻塞或拒绝，需配合足够的最大线程数和合理的拒绝策略；
>典型应用：Executors.newCachedThreadPool()默认使用SynchronousQueue，核心线程数为 0，最大线程数为Integer.MAX_VALUE。

