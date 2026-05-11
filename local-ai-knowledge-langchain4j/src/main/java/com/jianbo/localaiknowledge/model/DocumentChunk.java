package com.jianbo.localaiknowledge.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DocumentChunk {
    private Long id;
    private String taskId;
    private Integer chunkIndex;
    private String content;
    private String source;
    private String userId;
    private String docScope;
    private LocalDateTime createdAt;
}
