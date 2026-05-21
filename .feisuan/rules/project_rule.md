
# 开发规范指南

为保证代码质量、可维护性、安全性与可扩展性，请严格遵循以下规范。

## 一、项目基本信息

- **工作区路径**：`D:\work\ai-backend-learning`
- **代码作者**：89695
- **系统环境**：Windows 11
- **开发语言**：Java 21
- **构建工具**：Maven
- **注释语言**：中文

## 二、技术栈要求

- **主框架**：Spring Boot 4.x
- **核心依赖**：
  - `spring-boot-starter-web`
  - `spring-boot-starter-data-redis`
  - `spring-boot-starter-data-jdbc`
  - `spring-boot-starter-data-redis` (Redisson)
  - `spring-boot-starter-webflux` (部分服务)
  - `spring-boot-starter-security` (JWT)
  - `lombok`
  - `mybatis-spring-boot-starter`
  - `spring-boot-starter-actuator` (可观测性)
  - `spring-cloud-starter-gateway` + `spring-cloud-starter-alibaba-sentinel`
  - `spring-boot-starter-kafka` / `rocketmq-spring-boot-starter` (消息队列)
  - `org.springframework.ai` (Spring AI 1.0.0-M5 或 2.0.0-M4)
  - `langchain4j` (部分服务)
  - `mybatis-spring-boot-starter` (MyBatis 4.0.1)
  - `postgresql` (JDBC 驱动)
  - `elasticsearch` (8.13.3 或 9.x)
  - `co.elastic.clients:elasticsearch-java`
  - `jsoup` / `playwright` (爬虫)
  - `xxl-job-core` (分布式任务调度)

## 三、目录结构

本项目采用多模块 Maven 项目结构，主要包含以下子模块：

```text
ai-backend-learning
├── demo-springai                    # Spring AI 演示模块 (Spring AI 1.0.0-M5)
│   ├── src/main/java/com/jianbo/springai/
│   │   ├── config/                  # 配置类
│   │   ├── controller/              # 控制器层
│   │   ├── entity/                  # 实体类
│   │   ├── service/                 # 服务层
│   │   └── utils/                   # 工具类
│   └── src/main/resources/
├── demo_redis                       # Redis 演示模块
│   └── src/main/java/com/jiabo/redis/
├── local-ai-crawler                 # AI 知识库爬虫服务
│   ├── src/main/java/com/jianbo/crawler/
│   │   ├── crawler/                 # 爬虫逻辑
│   │   ├── job/                     # 定时任务
│   │   ├── scheduler/               # 调度器
│   │   └── repository/              # 数据访问
│   └── src/main/resources/
│       ├── db/                      # 初始化脚本
│       └── logs/                    # 日志配置
├── local-ai-knowledge               # 知识库问答服务 (Spring AI 2.0.0-M4)
│   ├── src/main/java/com/jianbo/localaiknowledge/
│   │   ├── config/                  # 配置类
│   │   ├── controller/              # 控制器层
│   │   ├── mapper/                  # MyBatis Mapper
│   │   ├── model/dto/               # 数据传输对象
│   │   ├── service/                 # 服务层
│   │   └── utils/                   # 工具类
│   └── src/main/resources/
├── local-ai-knowledge-langchain4j   # 知识库问答服务 (LangChain4j 版)
├── demo-mq                          # 消息队列演示 (Kafka/RocketMQ)
├── demo-thread                      # 多线程演示
├── demo-gateway-sentinel            # 网关与限流
├── demo-algorithm                   # 算法练习
├── demo-springai-front              # Spring AI 前端 (Vue/Vite)
├── local-ai-knowledge-front         # 知识库前端 (Vue/Vite)
├── logs                             # 运行日志目录
└── uploads                          # 文件上传目录
```

## 四、代码分层架构规范

| 层级 | 职责说明 | 开发约束与注意事项 |
|------|----------|-------------------|
| **Controller** | 处理 HTTP 请求与响应，定义 API 接口 | 不得直接访问数据库，必须通过 Service 层调用。使用 `@Valid` 进行参数校验。 |
| **Service** | 实现业务逻辑、事务管理与数据校验 | 必须通过 Repository/MyBatis Mapper 层访问数据。返回 DTO 而非 Entity。 |
| **Repository / Mapper** | 数据库访问与持久化操作 | MyBatis Mapper 继承 `BaseMapper`；使用 `@EntityGraph` 避免 N+1 查询。 |
| **Entity / Model** | 映射数据库表结构或定义数据模型 | 不得直接返回给前端（需转换为 DTO）。包名统一。 |

### 接口与实现分离
- 所有业务逻辑通过接口定义（如 `UserService`），具体实现放在 `impl` 子包中。

## 五、安全与性能规范

### 输入校验
- 使用 `@Valid` 与 JSR-303 校验注解（如 `@NotBlank`, `@Size` 等）。
- **注意**：Spring Boot 4.x 中校验注解位于 `jakarta.validation.constraints.*`。

### 事务管理
- `@Transactional` 注解仅用于 **Service 层**方法。
- 避免在循环中频繁提交事务。

### 数据库连接池配置
- **PostgreSQL**: 使用 HikariCP，需设置 `max-lifetime` 小于数据库空闲超时（如 300000ms），避免死连接。
- **Redis**: 配置连接池参数（`max-active`, `max-idle` 等）。

### 向量存储规范
- **PGVector**: 索引类型推荐使用 HNSW，距离算法推荐 COSINE_DISTANCE。
- **Elasticsearch**: 确保客户端版本与 ES 服务端版本兼容（Spring AI BOM 管理依赖）。

## 六、代码风格规范

### 命名规范
| 类型 | 命名方式 | 示例 |
|------|----------|------|
| 类名 | UpperCamelCase | `UserServiceImpl` |
| 方法/变量 | lowerCamelCase | `saveUser()` |
| 常量 | UPPER_SNAKE_CASE | `MAX_LOGIN_ATTEMPTS` |

### 注释规范
- 所有类、方法、字段需添加 **Javadoc** 注释。
- **注释语言**：中文。

### 类型命名规范（阿里巴巴风格）
| 后缀 | 用途说明 | 示例 |
|------|----------|------|
| DTO | 数据传输对象 | `UserDTO` |
| DO | 数据库实体对象 | `UserDO` |
| BO | 业务逻辑封装对象 | `UserBO` |
| VO | 视图展示对象 | `UserVO` |
| Query | 查询参数封装对象 | `UserQuery` |

### 实体类简化工具
- 使用 Lombok 注解替代手动编写 getter/setter/构造方法：
  - `@Data`
  - `@NoArgsConstructor`
  - `@AllArgsConstructor`

## 七、扩展性与日志规范

### 接口优先原则
- 所有业务逻辑通过接口定义。

### 日志记录
- 使用 `@Slf4j` 注解代替 `System.out.println`。
- 日志配置文件路径：`classpath:logs/logback-spring.xml`。

### 可观测性
- 集成 `Spring Boot Actuator` 和 `Micrometer Prometheus`。
- 暴露指标端点：`/actuator/prometheus`，用于监控与 Grafana 集成。

### 消息队列使用
- Kafka/RocketMQ 消费者需正确处理异常并配置 `max-retries`。
- 生产者需设置合理的 `key-serializer` 和 `value-serializer`。

### 并发与异步
- **演示模块**：`demo-thread` 使用 JDK 21 虚拟线程。
- **爬虫模块**：使用 `@Scheduled` 定时任务，配置合理的 Cron 表达式。

## 八、编码原则总结

| 原则 | 说明 |
|------|------|
| **SOLID** | 高内聚、低耦合，增强可维护性与可扩展性 |
| **DRY** | 避免重复代码，提高复用性 |
| **KISS** | 保持代码简洁易懂 |
| **YAGNI** | 不实现当前不需要的功能 |
| **OWASP** | 防范常见安全漏洞，如 SQL 注入、XSS 等 |
