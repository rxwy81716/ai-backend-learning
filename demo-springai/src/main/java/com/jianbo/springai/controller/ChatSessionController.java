package com.jianbo.springai.controller;

import com.jianbo.springai.entity.ChatSessionDTO;
import com.jianbo.springai.service.ChatSessionService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/chat-session")
@AllArgsConstructor
public class ChatSessionController {
  private final ChatSessionService chatSessionService;

  @GetMapping("/chat")
  public String getChatHistory(@RequestParam String question, @RequestParam String sessionId) {
    return chatSessionService.chat(new ChatSessionDTO(sessionId, question, false));
  }

  @PostMapping("/chat")
  public String sessionChat(@RequestBody ChatSessionDTO dto) {
    return chatSessionService.chat(dto);
  }
}
