封装minimax的chatModel

```java
  @Bean("MiniMaxChatClient")
  public ChatClient miniMaxChatClient(MiniMaxChatModel miniMaxChatModel) {
    return ChatClient.create(miniMaxChatModel);
  }
```

聊天

```java
/**
   * 通过对话方法(带System prompt 角色设定)
   *
   * @param userInput 用户输入
   * @return AI润色后的回复
   */
  public String novelPolish(String userInput) {
    // ===================== 核心：System Prompt 角色设定 =====================
    // client调用
    String content =
        miniMaxChatClient
            .prompt()
            .system(
                """
            你是一位专业的小说润色专家，擅长：
            1. 优化小说语句通顺度，提升文笔质感
            2. 保留原文核心剧情和人物设定
            3. 修正语法错误、标点错误、逻辑不通顺问题
            4. 语言风格简洁优美，不添加无关内容
            请严格按照要求润色用户输入的小说内容
            """)
            .user(userInput)
            .call()
            .content();
    System.out.println(content);
    return content;
  }
```

流式响应 

注意  produces = MediaType.TEXT_EVENT_STREAM_VALUE 

字符集要在yml中配置  见day17

```java
 public Flux<String> chatStream(String message) {
    return miniMaxChatClient
        .prompt()
        .system(
            """
                        你是一位专业的助手，擅长：
                        1. 回答用户问题
                        2. 与用户进行对话
                        3. 提供帮助和建议
                        请根据用户输入进行回复
                        """)
        .user(message)
        .stream()
        .content();
//        .map(s -> ServerSentEvent.builder(s).build());
  }

 /**
   * 流式SSE聊天接口
   */
  @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE )
//  @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_PLAIN_VALUE )
  public Flux<String> chatStream(@RequestParam String message) {
    return miniMaxAiService.chatStream(message);
  }
```

