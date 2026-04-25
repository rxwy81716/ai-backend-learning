搭建环境注意:\

POM要加在自定义仓库

注意版本适配

```xml
 <repositories>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
    </repositories>
```

配置文件

```yml
spring:
  application:
    name: demo-springai
  ai:
    minimax:
      api-key: ${MINIMAX_API_KEY:}
      chat:
        options:
          model: MiniMax-M2.7
          temperature: 0.3
          max-tokens: 2048
  servlet:
    encoding:
      charset: utf-8
      enabled: true
      force: true
server:
  port: 12115

```

