package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.model.DocumentTask;
import com.jianbo.localaiknowledge.service.DocumentParseService;
import com.jianbo.localaiknowledge.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档上传 & 解析状态查询
 *
 * POST   /api/doc/upload              上传文件（立即返回 taskId）
 * GET    /api/doc/status/{taskId}    查询解析进度
 * GET    /api/doc/tasks              查询所有任务
 * GET    /api/doc/logs/{taskId}      查询任务操作日志
 */
@RestController
@RequestMapping("/api/doc")
@Slf4j
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentParseService documentParseService;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    /**
     * 上传文档
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "userId", required = false) String userIdParam) {
        String userId = SecurityUtil.getCurrentUserIdStr();
        if (userId == null) {
            userId = userIdParam;
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过50MB");
        }

        String originalName = file.getOriginalFilename();
        String taskId = UUID.randomUUID().toString().replace("-", "");

        try {
            Path dirPath = Paths.get(uploadDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            String savedName = taskId + "_" + originalName;
            Path filePath = dirPath.resolve(savedName);
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("文件已保存: {} ({}字节)", filePath, file.getSize());

            String docScope = (userId != null && !userId.isBlank()) ? "PRIVATE" : "PUBLIC";
            DocumentTask task = documentParseService.registerTask(
                    taskId, originalName, filePath.toString(), file.getSize(), userId, docScope);

            documentParseService.submitToQueue(taskId);

            return Map.of(
                    "taskId", taskId,
                    "fileName", originalName,
                    "fileSize", file.getSize(),
                    "status", task.getStatus()
            );
        } catch (IOException e) {
            log.error("文件保存失败: {}", e.getMessage(), e);
            throw new IllegalStateException("文件保存失败: " + e.getMessage());
        }
    }

    /**
     * 查询单个任务状态
     */
    @GetMapping("/status/{taskId}")
    public DocumentTask status(@PathVariable String taskId) {
        DocumentTask task = documentParseService.getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        return task;
    }

    /**
     * 查询所有任务列表
     */
    @GetMapping("/tasks")
    public List<DocumentTask> tasks() {
        return documentParseService.getAllTasks();
    }

    /**
     * 查询任务操作日志
     */
    @GetMapping("/logs/{taskId}")
    public Map<String, Object> logs(@PathVariable String taskId) {
        DocumentTask task = documentParseService.getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        return Map.of(
                "task", task,
                "logs", documentParseService.getTaskLogs(taskId)
        );
    }
}
