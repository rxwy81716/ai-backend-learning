package com.jianbo.springai.controller;

import com.jianbo.springai.service.MiniMaxAiService;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** AI对话层接口 对外提供Http调用入口 */
@RestController
@RequestMapping("/ai")
@AllArgsConstructor
public class MiniMaxAIController {
  private final MiniMaxAiService miniMaxAiService;

  /**
   * 小说润色接口
   *
   * @param message 用户输入的小说原文
   * @return 润色后的结果
   */
  @GetMapping("/novel-polish")
  public String novelPolish(@RequestParam String message) {
    return miniMaxAiService.novelPolish(message);
  }

  /** 聊天接口 */
  @GetMapping("/chat")
  public String chat(@RequestParam String message) {
    return miniMaxAiService.syncChat(message);
  }

  /**
   * 流式SSE聊天接口
   */
  @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE )
//  @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_PLAIN_VALUE )
  public Flux<String> chatStream(@RequestParam String message) {
    return miniMaxAiService.chatStream(message);
  }
}
