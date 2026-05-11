package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.mapper.DocumentTaskMapper;
import com.jianbo.localaiknowledge.model.DocumentTask;
import com.jianbo.localaiknowledge.service.DocumentParseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档管理 Controller（与 Spring AI 版一致，无 AI 框架依赖）。
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentParseService parseService;
    private final DocumentTaskMapper taskMapper;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "PUBLIC") String docScope) {
        // TODO: 从 SecurityContext 获取 userId
        String userId = "demo-user";
        String taskId = UUID.randomUUID().toString().replace("-", "");

        DocumentTask task = new DocumentTask();
        task.setTaskId(taskId);
        task.setUserId(userId);
        task.setDocScope(docScope);
        task.setFileName(file.getOriginalFilename());
        task.setFileSize(file.getSize());
        task.setStatus("UPLOADED");
        task.setCreatedAt(java.time.LocalDateTime.now());
        // TODO: 保存文件到磁盘 + 设置 filePath

        taskMapper.insert(task);
        // TODO: 异步启动解析

        return ResponseEntity.ok(Map.of("taskId", taskId, "status", "UPLOADED"));
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<DocumentTask>> listTasks() {
        String userId = "demo-user"; // TODO: SecurityContext
        return ResponseEntity.ok(taskMapper.selectAccessibleTasks(userId));
    }

    @GetMapping("/status/{taskId}")
    public ResponseEntity<DocumentTask> getStatus(@PathVariable String taskId) {
        DocumentTask task = taskMapper.selectByTaskId(taskId);
        if (task == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(task);
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String taskId) {
        taskMapper.deleteByTaskId(taskId);
        return ResponseEntity.ok(Map.of("message", "已删除"));
    }
}
