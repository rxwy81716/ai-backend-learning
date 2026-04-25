

## 一、PostgreSQL 为什么能做向量库？

因为有一个官方插件：

## **pgvector**

作用：让 PG 支持 **向量存储 + 余弦相似度检索**

你现在只要有 PostgreSQL，**装个插件就能当向量数据库用**，不用学 Milvus、Chroma、ES！

## 二、pgvector 核心优势（必记）

1. 不用新装数据库

   你本地本来就有 PostgreSQL → 直接开启向量功能

2. SQL 直接查询向量

   不用学新语法，会写 SQL 就会做语义检索

3. 支持相似度查询

   余弦相似度、内积、欧几里得距离

4. 企业级稳定

   可持久化、可索引、可事务、可备份

5. SpringAI 直接兼容

   完美对接 RAG 流程

------

## 三、pgvector 安装（本地 1 分钟搞定）

### Windows /macOS 通用

1. 去 pgvector 官网下载对应 PostgreSQL 版本的插件
2. 把 DLL/so 文件放进 PostgreSQL 的 `lib` 和 `share/extension`
3. 进入数据库执行：

```
CREATE EXTENSION vector;
```

✅ 安装完成！你的 PG 现在变成向量数据库了

## 四、向量表结构设计（RAG 标准表）

```
CREATE TABLE rag_docs (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500),        -- 文档标题
    content TEXT,             -- 文本片段（切片后的内容）
    embedding vector(1536),   -- 向量字段 1536维（OpenAI/通义/豆包通用）
    create_time TIMESTAMP DEFAULT NOW()
);

-- 创建向量索引（加速检索）
CREATE INDEX idx_embedding ON rag_docs USING ivfflat (embedding vector_cosine_ops);
```

重点：

- `vector(1536)`：大部分嵌入模型都是 **1536 维**
- `vector_cosine_ops`：使用**余弦相似度**做语义检索

## 五、RAG 语义检索 SQL（最核心！）

用户提问 → 转向量 → SQL 查询最相似的文本片段

```
SELECT 
    content,
    1 - (embedding <=> '[查询向量]') AS similarity  -- 余弦相似度
FROM rag_docs
ORDER BY embedding <=> '[查询向量]'
LIMIT 3;  -- 取TOP3最相关的片段
```

符号说明：

- `<=>` ：余弦相似度
- 数值越接近 **1** → 语义越相似

这就是 **RAG 召回逻辑**！

## 六、PostgreSQL 做 RAG 的完整流程（你本地可直接跑）

```
【本地文档】
   ↓
文本切片（分段）
   ↓
Embedding 模型 → 生成向量
   ↓
存入 PostgreSQL（带 vector 字段）
   ↓
用户提问
   ↓
提问转向量
   ↓
PG SQL 余弦相似度检索 → 召回相关片段
   ↓
拼接资料 + Prompt → 给大模型
   ↓
AI 基于资料回答（无幻觉）
```

------

## 七、为什么强烈推荐你用 PostgreSQL + pgvector？

1. **不用装任何新软件**（Milvus、Chroma 都可以不用）
2. 你本来就会 PG，**零学习成本**
3. SQL 直接做语义检索
4. SpringAI 官方支持 pgvector
5. 本地开发、上线生产都能用
6. 是目前 **最简单、最稳定** 的 RAG 向量库方案

------

## 八、必背总结（面试 + 笔记）

1. **PostgreSQL + pgvector = 企业级向量数据库**

2. 支持：**向量存储、余弦相似度、语义检索**

3. 不用新装数据库，会 SQL 就能做 RAG

4. 是本地开发最简单方案，没有之一

5. RAG 流程不变：

   ```
   切片 → 嵌入 → PG存储 → 相似度查询 → 提示词增强 → AI生成
   ```

## 一、先记住：PG 和你熟悉的数据库 90% 是一样的

**通用 SQL 完全通用**，你原来会的：

- `SELECT / INSERT / UPDATE / DELETE`

- `JOIN / WHERE / GROUP BY / HAVING / ORDER BY`

- 子查询、CTE、事务、索引

  在 PG 里写法几乎一模一样。

------

## 二、PG 独有的、必须马上学会的 6 个关键点（最核心）

### 1. 大小写敏感（和 MySQL/Oracle 完全不同）

PG 默认：

- **不加引号 = 自动转小写**
- **加双引号 = 严格区分大小写**

```
-- PG 会自动变成 select * from user;
SELECT * FROM User;

-- 必须这样才叫大写表名
SELECT * FROM "User";
```

✅ **最佳实践**：全部用小写，不要用大写、不要用驼峰。

------

### 2. 字符串用单引号，双引号只给字段 / 表名

MySQL 可以双引号包字符串，PG **绝对不行**：

```
-- 错误
SELECT * FROM t WHERE name = "张三";

-- 正确
SELECT * FROM t WHERE name = '张三';
```

------

### 3. 分页语法（和 MySQL 不同，和 SQL Server 类似）

MySQL：`LIMIT ... OFFSET ...`

PG：**完全一样**，放心用！

```
SELECT * FROM t LIMIT 10 OFFSET 20;
```

------

### 4. 自增 ID（PG 没有 AUTO_INCREMENT）

MySQL：`id INT AUTO_INCREMENT`

PG 用 **SERIAL / BIGSERIAL / IDENTITY**

```
-- 最简单，和自增一样用
CREATE TABLE t (
  id BIGSERIAL PRIMARY KEY,
  name TEXT
);
```

插入时不用管 id，PG 自动生成。

------

### 5. 字符串类型（PG 更简单）

PG 不区分 `VARCHAR` 长度性能，**直接用 TEXT 最爽**：

```
name TEXT,        -- 不限长度，性能最好
content VARCHAR,  -- 也能用，和 TEXT 几乎无区别
title VARCHAR(50) -- 要限制长度才用
```

比 MySQL、SQL Server 省心。

------

### 6. 时间函数（常用的记这几个）

```
now()                  -- 当前时间
CURRENT_DATE           -- 当前日期
CURRENT_TIMESTAMP      -- 当前时间戳
age(结束时间, 开始时间) -- 计算时间差
```

------

## 三、从 Oracle 转 PG：你最关心的区别

|    功能    |     Oracle      |            PostgreSQL            |             |      |      |                         |
| :--------: | :-------------: | :------------------------------: | :---------: | :--: | :--: | :---------------------: |
| 字符串连接 |        `        |                                  | ` 或 CONCAT |  `   |      | ` 或 CONCAT（完全一样） |
|    分页    |     ROWNUM      |           LIMIT/OFFSET           |             |      |      |                         |
|    序列    | CREATE SEQUENCE |        一样，语法几乎相同        |             |      |      |                         |
|  空字符串  |      NULL       | 空字符串 ≠ NULL（和 MySQL 一样） |             |      |      |                         |
| 子查询别名 |   必须加别名    |            必须加别名            |             |      |      |                         |
|    伪列    |  ROWNUM, ROWID  |        无，用 LIMIT 替代         |             |      |      |                         |

------

## 四、从 SQL Server 转 PG：核心区别

- PG 用 `LIMIT` 代替 `TOP N`
- PG 用 `$1,$2` 参数，不用 `@para`
- PG 用 `SERIAL` 代替 `IDENTITY(1,1)`
- 注释用 `--` 和 `/* */`，完全一样

------

## 五、10 分钟就能跑的 PG 入门示例（直接复制运行）

```
-- 1. 创建表（自增ID + 文本 + 时间）
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username TEXT NOT NULL,
    age INT,
    create_time TIMESTAMP DEFAULT now()
);

-- 2. 插入数据
INSERT INTO users (username, age)
VALUES ('zhangsan', 20), ('lisi', 25);

-- 3. 查询
SELECT * FROM users WHERE age > 20;

-- 4. 分页
SELECT * FROM users LIMIT 10 OFFSET 0;

-- 5. 更新
UPDATE users SET age = 26 WHERE username = 'lisi';

-- 6. 删除
DELETE FROM users WHERE age < 18;
```

**你会发现：和 MySQL 几乎没区别！**

------

## 六、最快学习路线（按这个顺序，1 天学会）

1. **安装 PostgreSQL + pgAdmin（图形化工具）**
2. 学会建库、建表、增删改查（和 MySQL 一样）
3. 学会索引、外键、事务
4. 学会常用函数（字符串、时间、数值）
5. 学会备份恢复（`pg_dump`）
6. 学习一点点高级：JSON 字段、数组（PG 超强特性）

------

## 七、我可以直接帮你做的事

你不用从头啃书，我可以直接：

- 把你的 **MySQL 表结构转 PG 语句**
- 把你的 **Oracle SQL 改成 PG 可运行版本**
- 给你 **PG 常用语句速查表**
- 帮你排查 PG 语法错误

# 一、索引（PG 版）

## 1. 普通索引（和 MySQL 几乎一致）



```sql
-- 单列索引
CREATE INDEX idx_user_name ON users(username);

-- 联合索引
CREATE INDEX idx_user_age_time ON users(age, create_time);

-- 唯一索引
CREATE UNIQUE INDEX idx_user_phone ON users(phone);
```

## 2. PG 特有高频索引



```
-- 1. 局部索引（条件索引，省空间、效率高）
CREATE INDEX idx_adult_user ON users(age) WHERE age >= 18;

-- 2. 前缀索引（字符串优化）
CREATE INDEX idx_name_prefix ON users(substring(username,1,10));
```

## 3. 删除 / 查看索引

```
-- 删除索引
DROP INDEX IF EXISTS idx_user_name;

-- 查看表所有索引
\d 表名;
```

------

# 二、外键约束

语法和 MySQL 基本一致，PG 严格校验，不能随意忽略外键。



```
-- 主表
CREATE TABLE dept (
    id BIGSERIAL PRIMARY KEY,
    dept_name TEXT NOT NULL
);

-- 子表 + 外键
CREATE TABLE emp (
    id BIGSERIAL PRIMARY KEY,
    emp_name TEXT,
    dept_id BIGINT,
    -- 关联部门表id
    CONSTRAINT fk_emp_dept 
    FOREIGN KEY (dept_id) REFERENCES dept(id)
    ON DELETE SET NULL   -- 主表删除，子表设为null
    -- ON DELETE CASCADE  级联删除
);
```

------

# 三、事务

PG 默认**自动提交开启**，事务用法和标准 SQL、Oracle 完全一致。



```
-- 手动开启事务
BEGIN;

-- 执行DML
INSERT INTO dept(dept_name) VALUES ('研发部');
UPDATE emp SET emp_name = '测试' WHERE id = 1;

-- 提交
COMMIT;

-- 回滚
ROLLBACK;
```

关键特性：

1. PG 事务隔离级别完整（读已提交、可重复读、串行化）
2. 支持保存点 `SAVEPOINT`，适合复杂业务回滚局部操作

------

# 四、常用函数（字符串 / 时间 / 数值）

## 1. 字符串函数



```
-- 拼接（推荐 ||，Oracle同款）
SELECT '姓名：' || username FROM users;

-- 长度
SELECT length('pg学习');

-- 截取
SELECT substring('hello', 1, 2);

-- 大小写
SELECT upper('abc'), lower('ABC');

-- 去除空格
SELECT trim('  测试  ');

-- 替换
SELECT replace('abc123','123','456');
```

## 2. 时间函数（重点，和 MySQL 差异点）



```
-- 当前时间
SELECT now();           -- 带时区完整时间
SELECT current_date;    -- 仅日期
SELECT current_time;    -- 仅时间

-- 时间加减
SELECT now() + INTERVAL '1 day';    -- 加1天
SELECT now() - INTERVAL '3 hour';  -- 减3小时

-- 时间差
SELECT age(now(), '2026-01-01'::date);

-- 格式化日期
SELECT to_char(now(), 'yyyy-MM-dd HH24:mi:ss');
```

## 3. 数值函数





```
-- 四舍五入
SELECT round(3.1415,2);

-- 向上/向下取整
SELECT ceil(3.2), floor(3.8);

-- 取绝对值
SELECT abs(-10);

-- 随机数
SELECT random();
```

------

# 五、备份与恢复 pg_dump /pg_restore

## 1. 整库备份（命令行执行）



```
# 完整库备份
pg_dump -U 用户名 -d 库名 -F c -f 备份文件.dump

# 仅导出表结构，不要数据
pg_dump -U 用户名 -d 库名 -s -f 结构备份.sql
```

## 2. 单表备份

```
pg_dump -U 用户名 -d 库名 -t 表名 -f table_backup.sql
```

## 3. 数据恢复



```
# 恢复dump文件
pg_restore -U 用户名 -d 目标库 备份文件.dump

# 恢复sql文件
psql -U 用户名 -d 目标库 -f table_backup.sql
```

> 小提示：Windows、Docker、Linux 环境命令通用，只需要找到 pg_dump 执行目录。

------

# 六、PG 高级特性：JSON + 数组（核心强项）

## 1. JSON 字段开发常用

两种类型：

- `json`：纯文本存储
- `jsonb`：**推荐**，索引支持、查询更快



```
-- 建表带jsonb字段
CREATE TABLE goods (
    id BIGSERIAL PRIMARY KEY,
    name TEXT,
    info jsonb   -- 商品扩展json信息
);

-- 插入json数据
INSERT INTO goods(name,info)
VALUES ('手机', '{"price":1999,"color":["黑","白"]}');

-- json取值 -> 取json对象，->> 取文本值
SELECT 
  name,
  info ->> 'price' AS price
FROM goods;

-- json条件查询
SELECT * FROM goods WHERE info @> '{"price":1999}';
```

### 1. 核心操作符表

在 PostgreSQL 中，最常用的 JSON 查询符号有三个：

| **操作符** | **含义**   | **返回类型**    | **常用场景**                                 |
| ---------- | ---------- | --------------- | -------------------------------------------- |
| **`->`**   | 按键名获取 | **jsonb/json**  | 链式访问（如：`data->'author'->'name'`）     |
| **`->>`**  | 按键名获取 | **text (文本)** | **最常用**，用于条件过滤和直接展示结果       |
| **`@>`**   | 包含       | **boolean**     | 用于判断 JSON 是否包含某个结构，**可走索引** |

### 2. 针对 MyBatis 的整合建议

在 Spring Boot 4 + MyBatis 环境中，你需要在 XML 中对特殊字符进行处理。

**示例：根据 JSON 中的热度值排序**

XML

```
<select id="findByMinScore" resultType="Hotspot">
    SELECT * FROM hotspot_data
    WHERE (info->>'hot_score')::int >= #{minScore}
    ORDER BY (info->>'hot_score')::int DESC
</select>
```

------

### 3. 性能杀手锏：GIN 索引

如果你在 IDEA 的 Console 中发现 JSON 查询很慢，那是因为你没有为 JSON 字段建立 **GIN (Generalized Inverted Index)** 索引。

SQL

```
-- 为整个 JSONB 字段创建 GIN 索引（支持 @> 操纵符）
CREATE INDEX idx_hotspot_info ON hotspot_data USING gin (info);

-- 只为 JSON 中的某个路径创建索引（更节省空间）
CREATE INDEX idx_hotspot_source ON hotspot_data USING gin ((info->'source'));
```

------

### 4. 2026 年的新姿势：JSON 路径查询 (SQL/JSON)

PostgreSQL 12+ 引入了类似 JSONPath 的语法，功能更强大：

SQL

```
-- 使用 jsonb_path_query_array 进行复杂过滤
SELECT jsonb_path_query(info, '$.items[*] ? (@.price > 100)') 
FROM products;
```

**总结建议：**

- 如果只是简单的条件筛选，用 **`->>`**。
- 如果是高频的数组搜索或结构匹配，用 **`@>`** 并配合 **GIN 索引**。
- 在 MyBatis 中，由于 `->>` 包含特殊字符，建议在 XML 中使用 `<![CDATA[ ... ]]>` 包裹 SQL。

## 2. 数组类型（MySQL 没有，PG 神器）

```
-- 建表带数组
CREATE TABLE tag (
    id BIGSERIAL PRIMARY KEY,
    tag_list text[]  -- 字符串数组
);

-- 插入数组
INSERT INTO tag(tag_list) 
VALUES (ARRAY['技术','Java','PG']);

-- 查询数组包含元素
SELECT * FROM tag WHERE 'Java' = ANY(tag_list);

-- 取数组第N个元素（下标从1开始）
SELECT tag_list[1] FROM tag;
```

------

### 1. 核心操作符与函数

处理 `text[]` 时，你最常用的“武器”是以下几个：

| **操作符/函数** | **含义**         | **示例**                                                     |
| --------------- | ---------------- | ------------------------------------------------------------ |
| **`&&`**        | **是否有交集**   | `tags && ARRAY['Java', 'Spring']` (只要包含其中之一就返回 true) |
| **`@>`**        | **是否包含**     | `tags @> ARRAY['AI']` (必须包含 AI 标签)                     |
| **`ANY`**       | **匹配其中之一** | `'Java' = ANY(tags)` (类似 `IN` 的数组版)                    |
| **`             |                  | `**                                                          |
| **`unnest()`**  | **展开数组**     | 将数组转为多行数据（常用于统计词频）                         |

### 2. MyBatis 整合 `text[]`

在 Spring Boot 中处理数组类型，MyBatis 需要一点特殊的转换。

#### **MyBatis XML 写法**

PostgreSQL 需要 `ARRAY[...]` 语法或者 `{'a','b'}` 这种字符串格式。

XML

```
<select id="findByTags" resultType="Hotspot">
    SELECT * FROM hotspot_items 
    WHERE tags @> #{tagArray, jdbcType=ARRAY}::text[]
</select>
```

#### **Java 端处理**

虽然你可以写自定义 `TypeHandler`，但在 Spring Boot 4 中，最简单的方式是将 `List<String>` 或 `String[]` 转化为符合 Postgres 要求的字符串，或者在 SQL 中使用 `ANY`：

Java

```
// Mapper 接口
List<Hotspot> findByTags(@Param("tagArray") String[] tags);
```

------

### 3. 性能优化：GIN 索引

和 `jsonb` 一样，如果你的 `text[]` 数据量很大，**必须**加索引，否则 `&&` 或 `@>` 会全表扫描。

SQL

```
-- 为 text[] 列创建 GIN 索引
CREATE INDEX idx_hotspot_tags ON hotspot_items USING gin (tags);
```

------

### 4. 什么时候用 `text[]` 而不是 `jsonb`？

- **选 `text[]` 的场景：** 数据结构非常固定（就是一串字符串列表），需要极高的检索性能，或者需要利用 `&&` 这种数组特有操作符。
- **选 `jsonb` 的场景：** 数据结构不固定，或者除了标签还有其他复杂的嵌套信息（如 `{"tags": ["a"], "meta": {"id": 1}}`）。