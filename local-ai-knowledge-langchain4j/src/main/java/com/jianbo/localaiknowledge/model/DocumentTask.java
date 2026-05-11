package com.jianbo.localaiknowledge.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DocumentTask {
    private String taskId;
    private String userId;
    private String docScope;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String status; // UPLOADED, PARSING, IMPORTING, DONE, FAILED
    private Integer totalChunks;
    private Integer importedChunks;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime finishedAt;
}
