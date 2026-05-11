package com.jianbo.localaiknowledge.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SystemPrompt {
    private Long id;
    private String name;
    private String content;
    private String description;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
