package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.model.DocumentChunk;
import com.jianbo.localaiknowledge.model.DocumentTask;
import com.jianbo.localaiknowledge.service.DocumentParseService;
import com.jianbo.localaiknowledge.utils.SecurityUtil;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档上传 & 解析状态查询
 *
 * <p>POST /api/doc/upload 上传文件（立即返回 taskId） GET /api/doc/status/{taskId} 查询解析进度 GET /api/doc/tasks
 * 查询所有任务 GET /api/doc/logs/{taskId} 查询任务操作日志
 */
@RestController
@RequestMapping("/api/doc")
@Slf4j
@RequiredArgsConstructor
public class DocumentController {

  private final DocumentParseService documentParseService;

  @Value("${app.upload.dir:./uploads}")
  private String uploadDir;

  @Value("${app.crawler-api-key:}")
  private String crawlerApiKey;

  private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

  /**
   * 上传文档
   *
   * @param docScope 文档范围：PUBLIC=公开 / PRIVATE=私有（默认）
   */
  @PostMapping("/upload")
  public Map<String, Object> upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "docScope", required = false) String docScope) {
    String userId = SecurityUtil.getCurrentUserIdStr();
    if (file.isEmpty()) {
      throw new IllegalArgumentException("文件不能为空");
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new IllegalArgumentException("文件大小不能超过50MB");
    }

    // 默认私有
    if (docScope == null || docScope.isBlank()) {
      docScope = "PRIVATE";
    }

    return saveAndDispatch(file, userId, docScope, "");
  }

  /**
   * 爬虫专用上传接口（无需认证，固定 PUBLIC）
   *
   * <p>供 local-ai-crawler 模块直接调用，无需 JWT Token。 docScope 固定为 PUBLIC，userId 设为 "crawler-bot"。
   */
  @PostMapping("/crawler-upload")
  public Map<String, Object> crawlerUpload(
      @RequestParam("file") MultipartFile file,
      @RequestHeader(value = "X-Crawler-Key", required = false) String apiKey) {
    // API Key 认证，防止任意人调用该接口上传文档
    if (crawlerApiKey == null || crawlerApiKey.isBlank()) {
      throw new IllegalStateException("服务未配置 crawler-api-key，接口不可用");
    }
    // 常量时间比对，防止时序侧信道攻击（String.equals 遇不等字符会提前返回，可被量时推测 key）
    if (apiKey == null
        || !MessageDigest.isEqual(
            crawlerApiKey.getBytes(StandardCharsets.UTF_8),
            apiKey.getBytes(StandardCharsets.UTF_8))) {
      throw new IllegalArgumentException("无效的 API Key");
    }
    if (file.isEmpty()) {
      throw new IllegalArgumentException("文件不能为空");
    }
    if (file.getSize() > MAX_FILE_SIZE) {
      throw new IllegalArgumentException("文件大小不能超过50MB");
    }

    return saveAndDispatch(file, "crawler-bot", "PUBLIC", "[爬虫上传] ");
  }

  /**
   * 保存上传文件到 {@code uploadDir}、注册解析任务并入队。
   *
   * <p>抽取自 {@link #upload}、{@link #crawlerUpload} 中完全重复的公共逻辑。
   *
   * @param logPrefix 日志前缀（如 "[爬虫上传] "），便于区分上传渠道
   */
  private Map<String, Object> saveAndDispatch(
      MultipartFile file, String userId, String docScope, String logPrefix) {
    String originalName = sanitizeFilename(file.getOriginalFilename());
    String taskId = UUID.randomUUID().toString().replace("-", "");
    try {
      Path dirPath = Paths.get(uploadDir).normalize();
      if (!Files.exists(dirPath)) {
        Files.createDirectories(dirPath);
      }
      String savedName = taskId + "_" + originalName;
      Path filePath = dirPath.resolve(savedName).normalize();
      // 双保险：确保最终路径仍在 uploadDir 下，防止路径遍历
      if (!filePath.startsWith(dirPath)) {
        throw new IllegalArgumentException("非法的文件名");
      }
      try (var inputStream = file.getInputStream()) {
        Files.copy(inputStream, filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      log.info("{}文件已保存: {} ({}字节)", logPrefix, filePath, file.getSize());

      DocumentTask task =
          documentParseService.registerTask(
              taskId, originalName, filePath.toString(), file.getSize(), userId, docScope);
      documentParseService.submitToQueue(taskId);

      return Map.of(
          "taskId", taskId,
          "fileName", originalName,
          "fileSize", file.getSize(),
          "status", task.getStatus(),
          "docScope", docScope);
    } catch (IOException e) {
      log.error("{}文件保存失败: {}", logPrefix, e.getMessage(), e);
      throw new IllegalStateException("文件保存失败: " + e.getMessage());
    }
  }

  /**
   * 清洗上传文件名： 1）剩下 purely filename 部分（除掉客户端可能传的路径） 2）只保留安全字符：中文 / 字母数字 / 点 / 下划线 / 连字符
   * 3）其余字符（含路径分隔符、..、空格、中括号等）替换为 _
   */
  private static String sanitizeFilename(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("文件名不能为空");
    }
    String base = Paths.get(raw).getFileName().toString();
    String cleaned = base.replaceAll("[^a-zA-Z0-9._\\u4e00-\\u9fa5-]", "_");
    if (cleaned.isBlank() || ".".equals(cleaned) || "..".equals(cleaned)) {
      throw new IllegalArgumentException("非法的文件名");
    }
    return cleaned;
  }

  /** 查询单个任务状态 */
  @GetMapping("/status/{taskId}")
  public DocumentTask status(@PathVariable String taskId) {
    DocumentTask task = documentParseService.getTask(taskId);
    if (task == null) {
      throw new IllegalArgumentException("任务不存在");
    }
    return task;
  }

  /** 查询任务列表 - 管理员：返回公开文档 + 所有私有文档 - 普通用户：仅返回当前用户私有文档 */
  @GetMapping("/tasks")
  public List<DocumentTask> tasks() {
    if (SecurityUtil.isAdmin()) {
      return documentParseService.getAllTasks();
    }
    String userId = SecurityUtil.getCurrentUserIdStr();
    return documentParseService.getAllUserTasks(userId);
  }

  /** 查询任务操作日志 */
  @GetMapping("/logs/{taskId}")
  public Map<String, Object> logs(@PathVariable String taskId) {
    DocumentTask task = documentParseService.getTask(taskId);
    if (task == null) {
      throw new IllegalArgumentException("任务不存在");
    }
    return Map.of("task", task, "logs", documentParseService.getTaskLogs(taskId));
  }

  /** 查询文档分段详情（从 PG document_chunk 读取） */
  @GetMapping("/chunks/{taskId}")
  public Map<String, Object> chunks(@PathVariable String taskId) {
    DocumentTask task = documentParseService.getTask(taskId);
    if (task == null) {
      throw new IllegalArgumentException("任务不存在");
    }
    List<DocumentChunk> chunks = documentParseService.getDocumentChunks(taskId);
    return Map.of(
        "task", task,
        "chunks", chunks,
        "totalChunks", chunks.size());
  }

  /** 删除文档（仅允许删除自己的私有文档或公开文档） */
  @DeleteMapping("/{taskId}")
  public Map<String, Object> delete(@PathVariable String taskId) {
    DocumentTask task = documentParseService.getTask(taskId);
    if (task == null) {
      throw new IllegalArgumentException("任务不存在");
    }
    String userId = SecurityUtil.getCurrentUserIdStr();
    // 权限校验：只能删除自己的文档，管理员可删除所有
    if (!SecurityUtil.isAdmin()) {
      if (userId == null || !userId.equals(task.getUserId())) {
        throw new IllegalArgumentException("无权删除他人的文档");
      }
    }
    documentParseService.deleteTask(taskId);
    return Map.of("message", "删除成功");
  }

  /** 重新解析文档（清除旧数据，重新读取 + 切片 + 入库） */
  @PostMapping("/reparse/{taskId}")
  public Map<String, Object> reparse(@PathVariable String taskId) {
    DocumentTask task = documentParseService.getTask(taskId);
    if (task == null) {
      throw new IllegalArgumentException("任务不存在");
    }
    // 权限校验：只能重新解析自己的文档，管理员可操作所有
    String userId = SecurityUtil.getCurrentUserIdStr();
    if (!SecurityUtil.isAdmin()) {
      if (userId == null || !userId.equals(task.getUserId())) {
        throw new IllegalArgumentException("无权操作他人的文档");
      }
    }
    documentParseService.reparseTask(taskId);
    return Map.of("message", "重新解析已触发", "taskId", taskId);
  }

  /** 下载文档 */
  @GetMapping("/download/{taskId}")
  public ResponseEntity<Resource> download(@PathVariable String taskId) throws IOException {
    DocumentTask task = documentParseService.getTask(taskId);
    if (task == null) {
      throw new IllegalArgumentException("任务不存在");
    }

    Path filePath = Paths.get(task.getFilePath()).normalize();
    Path uploadDirPath = Paths.get(uploadDir).normalize();

    // 校验文件路径是否在允许的目录下，防止路径遍历攻击
    if (!filePath.startsWith(uploadDirPath)) {
      throw new IllegalArgumentException("非法的文件路径");
    }

    if (!Files.exists(filePath)) {
      throw new IllegalArgumentException("文件不存在");
    }

    Resource resource = new FileSystemResource(filePath);
    String encodedName =
        URLEncoder.encode(task.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");

    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
        .contentLength(resource.contentLength())
        .body(resource);
  }
}
