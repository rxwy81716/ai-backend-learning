package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.model.DocumentTask;
import com.jianbo.localaiknowledge.service.DocumentParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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
 * GET    /api/doc/status/{taskId}     查询解析进度
 * GET    /api/doc/tasks               查询所有任务
 * GET    /api/doc/logs/{taskId}       查询任务操作日志
 */
@RestController
@RequestMapping("/api/doc")
@Slf4j
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentParseService documentParseService;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    /**
     * 上传文档
     *
     * 1. 先保存到本地磁盘
     * 2. 立即返回 taskId
     * 3. 后台异步解析 + 入库
     */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "userId", required = false) String userId) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件不能为空"));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件大小不能超过50MB"));
        }

        String originalName = file.getOriginalFilename();
        String taskId = UUID.randomUUID().toString().replace("-", "");

        try {
            // 1. 确保上传目录存在
            Path dirPath = Paths.get(uploadDir);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // 2. 保存文件（taskId_原始文件名，防重名）
            //    使用流式写入，避免大文件一次性加载到 JVM 堆内存
            String savedName = taskId + "_" + originalName;
            Path filePath = dirPath.resolve(savedName);
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("文件已保存: {} ({}字节)", filePath, file.getSize());

            // 3. 注册任务（有 userId 时为 PRIVATE 私有文档）
            String docScope = (userId != null && !userId.isBlank()) ? "PRIVATE" : "PUBLIC";
            DocumentTask task = documentParseService.registerTask(
                    taskId, originalName, filePath.toString(), file.getSize(), userId, docScope);

            // 4. 投递到 Redisson 队列（立即返回，不阻塞）
            documentParseService.submitToQueue(taskId);

            return ResponseEntity.ok(Map.of(
                    "taskId", taskId,
                    "fileName", originalName,
                    "fileSize", file.getSize(),
                    "status", task.getStatus()
            ));
        } catch (IOException e) {
            log.error("文件保存失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "文件保存失败: " + e.getMessage()));
        }
    }

    /**
     * 查询单个任务状态
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<?> status(@PathVariable String taskId) {
        DocumentTask task = documentParseService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }

    /**
     * 查询所有任务列表
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<DocumentTask>> tasks() {
        return ResponseEntity.ok(documentParseService.getAllTasks());
    }

    /**
     * 查询任务操作日志（每一步的详细记录）
     */
    @GetMapping("/logs/{taskId}")
    public ResponseEntity<?> logs(@PathVariable String taskId) {
        DocumentTask task = documentParseService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "task", task,
                "logs", documentParseService.getTaskLogs(taskId)
        ));
    }
}
