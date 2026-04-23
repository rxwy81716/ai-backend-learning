## JAVA锁

### 什么是锁

> 锁是多线程编程中的一种同步机制,用于控制对共享资源的访问
>
> 据说最初锁的设计灵感来源于火车上的厕所，车上的乘客都可以使用这个厕所，但同一时刻只能有一个人使用。厕所从里面锁住，外面会显示“有人”，没锁会显示“无人”，火车上就用这种方式来控制乘客对厕所的使用。

### 为什么需要锁

> 概念：锁可以确保多个线程之间对共享资源的访问是互斥的，也就是同一时刻只有一个线程能够访问被保护的共享资源，从而避免并发访问带来的数据不一致性和竞态条件等问题，是解决线程安全问题常用手段之一。
>
> 接着说厕所，如果没有锁，也就是没有“有人”和“无人”的标识，假如你在里面上厕所，在你用完之前——生活场景：可能会有N个人打开厕所门看看厕所是否空闲。太抽象了，这下知道为什么需要锁了吧😅

### Java中锁的分类

> 1. 内置锁： 
>    1. 使用关键字synchronized实现。
>    2. 可以对方法或代码块进行同步，被同步的代码同一时刻只有一个线程可以执行其中的代码。
> 2. 显式锁：
>    1. 使用java.util.concurrent.locks包下锁机制实现，比如ReentrantLock。
>    2. 提供了更加灵活的控制，需要显式的用lock()方法加锁和unlock()方法释放锁。
> 3. 条件锁：
>    1. 使用java.util.concurrent.locks包下的Condition接口和ReentrantLock实现
>    2. 允许线程在某个特定条件满足时等待或唤醒
> 4. 读写锁：
>    1. 使用java.util.concurrent.locks包下的ReentrantReadWriteLock实现。
>    2. 允许多个线程同时读共享资源，但只允许一个线程进行写操作。
> 5. StampedLock：
>    1. 在Java8中引入的新型锁机制，也是在java.util.concurrent.locks包下。
>    2. 提供了三种模式：写锁、悲观读锁和乐观读锁。
> 6. 无锁
>    1. 也就是我们常说的乐观锁，基于原子操作实现的锁机制，比如CAS算法。
>    2. 避免了传统锁机制的线程阻塞和切换开销。

### synchronized关键字

> ###### 三个特点
>
> > - 互斥性：同一时间，只有一个线程可以获得锁，获得锁的线程才能执行被synchronized保护的代码片段。
> > - 阻塞性：未获得锁的线程只能阻塞，等待锁释放
> > - 可重入性：如果一个线程已经获得锁，在锁未释放前，再次请求锁的时候，是必然可以获取到的。
>
> - 修饰普通方法 锁的是当前实例
>
>   ```java
>   public synchronized void doSomething(){
>   }
>   ```
>
> - 修饰静态方法 锁的是当前类的Class对象
>
>   ```java
>   public static synchronized void doSomething(){
>   }
>   ```
>
> - 修饰代码块 锁的是括号里面的内容
>
>   ```java
>   synchronized (obj){}
>   ```
> 
>synchronized是怎么实现的
> 
>上面也提到synchronized可以修饰方法和代码块 ，JVM基于进入和退出Monitor来实现方法和代码块同步，但两者实现细节不一样，下面就介绍一下它俩的不同之处。
> 
>修饰方法：也就成了同步方法，它的常量池中会有一个ACC_SYNCHRONIZED标志。当某个线程要访问某个方法的时候，会检查是否有设置ACC_SYNCHRONIZED，如果有设置，则需要先获得监视器锁（Monitor），然后开始执行方法，方法执行之后再释放监视器锁（Monitor）。这时如果其它线程来请求执行方法，会因为无法获得监视器（Monitor）锁而被阻塞住。（ps：如果在方法执行过程中，发生了异常，并且方法内部并没有处理该异常，那么在异常被抛到方法外面之前监视器锁（Monitor）会被自动释放）

### 八种案例

> 详见day04/PhoneDemoSynchronized.java

## ReentrantLock

### ReentrantLock类实现及常用方法

> ReentrantLock内部是基于AbstractQueuedSynchronizer(简称AQS)实现的，其内部是一个双向队列，还有一个volatile修饰的int类型的state，state=0表示当前锁没有被占有，state>0表示当前已有线程持有锁，AQS暂时就先介绍这么多，后续会详解。
>
> ReentrantLock类的常用方法：
>
> void lock():获取锁，如果锁被占用，当前线程则进入等待状态。
> boolen tryLock()：尝试获取锁（成功返回true，失败返回false，不阻塞）
> void unlock()：释放锁
> void lockInterruptibly() throws Interrupt：可中断地获取锁，即在锁的获取中可以中断当前线程。
> boolean tryLock(long time,TimeUnit unit) throws InterruptedException：当前线程在以下3中情况下会返回：①当前线程在超时时间内获得了锁 ②当前线程在超时时间内被中断 ③超时时间结束，返回false。
> Condition newCondition()：获取等待通知组件，该组件和当前锁绑定。

#### 公平锁和非公平锁

> syncchronized关键字只有非公平锁，而ReentrantLock可实现非公平锁和公平锁。

####  什么是公平锁和非公平锁

> - 非公平锁：多个线程不按照申请锁的顺序去获得锁，而是同时直接去尝试获取锁，获取不到，再进入队列等待。
> - 公平锁：多个线程按照申请锁的顺序去获得锁，所有线程都在队列里排队，这样就保证队列中的第一个线程先获得锁。
>
> ```java
> //默认非公平锁
> ReentrantLock lock = new ReentrantLock();
> //公平锁
> ReentrantLock lock1 = new ReentrantLock(true);
> ```

#### 公平锁和非公平锁的优缺点

> 非公平锁：优点是减少了cpu唤醒线程的开销，整体的吞吐量会高一点。但它可能会导致队列中排队的线程一直获取不到锁或长时间获取不到，活活饿死。
> 公平锁：优点是所有的线程都能得到资源，不会饿死在队列中。但它的吞吐量相较非公平锁而言，就下降了很多，队列里面除了第一个线程，其它线程都会阻塞，cpu唤醒阻塞线程的开销是很大的缺点。

#### 与synchronized的对比

> 两个的相同点是，都是用于线程同步控制，且都是可重入锁，但也有很多不同点：
>
> synchronized是Java内置特性，而ReentrantLock是通过Java代码实现的。
> synchronized可以自动获取/释放锁，而ReentrantLock需要手动获取/释放锁。
> synchronized的锁状态无法判断，而ReentrantLock可以用tryLock()方法判断。
> synchronized同过notify()和notifyAll()唤醒一个和全部线程，而ReentrantLock可以结合Condition选择性的唤醒线程。
> 在3.1小节提到过ReentrantLock的常用方法，所以它还具有响应中断、超时等待、tryLock()非阻塞尝试获取锁等特性。
> ReentrantLock可以实现公平锁和非公平锁，而synchronized只是非公平锁。

### 什么是StampedLock（邮戳锁）

StampedLock是Java8提供的一种乐观读写锁。相比于ReentrantReadWriteLock，StampedLock引入了乐观读的概念，就是在已经有写线程加锁的同时，仍然允许读线程进行读操作，这相对于对读模式进行了优化，但是可能会导致数据不一致的问题，所以当使用乐观读时，必须对获取结果进行校验。

####  StampedLock的三种模式

读模式：在读模式下，多个线程可以同时获取读锁，不互相阻塞。但当写线程请求获取写锁时，读线程会被阻塞。与ReentrantReadWriteLock类似。
写模式：写模式时独占的，当一个写线程获取写锁时，其它线程无法同时持有写或读锁。写锁请求会阻塞其它线程的读锁。与ReentrantReadWriteLock类似。
乐观读模式：注意，上述两个模式均加了锁，所以它们之间读写互斥，乐观读模式是不加锁的读。这样就有两个好处，一是不加锁意味着性能会更高一点，二是写线程在写的同时，读线程仍然可以进行读操作。（如果对数据的一致性要求，那么在使用乐观读的时候需要进行validate()校验，可以看一下下面示例）