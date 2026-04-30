package com.jianbo.localaiknowledge.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.jianbo.localaiknowledge.mapper.DocumentChunkMapper;
import com.jianbo.localaiknowledge.mapper.DocumentTaskLogMapper;
import com.jianbo.localaiknowledge.mapper.DocumentTaskMapper;
import com.jianbo.localaiknowledge.model.DocumentChunk;
import com.jianbo.localaiknowledge.model.DocumentTask;
import com.jianbo.localaiknowledge.model.DocumentTask.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档解析服务
 *
 * <p>流程：文件上传 → 本地保存 → 投递 Redisson 队列 → 消费者异步解析 → 入库
 * <ul>
 *   <li>ES：向量检索（主力，分批 + 429 自适应退避）
 *   <li>PG document_chunk：原文切片存档
 * </ul>
 * <p>任务状态持久化到 PostgreSQL（document_task 表）。
 * <p>ES 丢失时可通过“重新解析”从磁盘原文件重建。
 */
@Service
@Slf4j
public class DocumentParseService {

  public static final String QUEUE_NAME = "doc:parse:queue";

  private final EsVectorStoreService esVectorStoreService;
  private final DocumentTaskMapper taskMapper;
  private final DocumentTaskLogMapper taskLogMapper;
  private final DocumentChunkMapper chunkMapper;
  private final RedissonClient redissonClient;
  private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
  /** RAG 检索结果缓存（入库/删除后主动清除，避免 10min 内新文档检索不到） */
  private final Cache<String, java.util.List<Document>> ragSearchCache;

  public DocumentParseService(
      EsVectorStoreService esVectorStoreService,
      DocumentTaskMapper taskMapper,
      DocumentTaskLogMapper taskLogMapper,
      DocumentChunkMapper chunkMapper,
      RedissonClient redissonClient,
      org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
      Cache<String, java.util.List<Document>> ragSearchCache) {
    this.esVectorStoreService = esVectorStoreService;
    this.taskMapper = taskMapper;
    this.taskLogMapper = taskLogMapper;
    this.chunkMapper = chunkMapper;
    this.redissonClient = redissonClient;
    this.jdbcTemplate = jdbcTemplate;
    this.ragSearchCache = ragSearchCache;
  }

  /** 注册一个新任务（持久化到 DB） */
  public DocumentTask registerTask(String taskId, String fileName, String filePath, long fileSize) {
    return registerTask(taskId, fileName, filePath, fileSize, null, "PUBLIC");
  }

  /** 注册一个新任务（带用户归属） */
  public DocumentTask registerTask(
      String taskId,
      String fileName,
      String filePath,
      long fileSize,
      String userId,
      String docScope) {
    DocumentTask task = DocumentTask.create(taskId, fileName, filePath, fileSize, userId, docScope);
    taskMapper.insert(task);
    taskLogMapper.insert(taskId, "UPLOAD", "文件上传成功: " + fileName + " (" + fileSize + "字节)");
    log.info("[{}] 任务已注册并持久化: {}", taskId, fileName);
    return task;
  }

  /** 投递任务到 Redisson 队列（替代 @Async） */
  public void submitToQueue(String taskId) {
    RBlockingQueue<String> queue = redissonClient.getBlockingQueue(QUEUE_NAME);
    queue.offer(taskId);
    taskLogMapper.insert(taskId, "QUEUED", "任务已投递到解析队列");
    log.info("[{}] 任务已投递到 Redisson 队列", taskId);
  }

  /** 查询任务状态（从 DB 读取） */
  public DocumentTask getTask(String taskId) {
    return taskMapper.selectByTaskId(taskId);
  }

  /** 获取所有任务（从 DB 读取，按创建时间倒序） */
  public List<DocumentTask> getAllUserTasks(String userId) {
    return taskMapper.selectAllUserTasks(userId);
  }

  /** 获取当前用户可访问的任务（公开 + 当前用户私有） */
  public List<DocumentTask> getAccessibleTasks(String userId) {
    return taskMapper.selectAccessibleTasks(userId);
  }

  /** 获取所有任务（管理员用） */
  public List<DocumentTask> getAllTasks() {
    return taskMapper.selectAll();
  }

  /** 获取文档分段（从 PG document_chunk 读取） */
  public List<DocumentChunk> getDocumentChunks(String taskId) {
    return chunkMapper.selectByTaskId(taskId);
  }

  /**
   * 重新解析文档：清除旧的向量/切片数据，重置状态，重新投递队列。
   * 前提：本地文件仍存在；否则抛异常。
   */
  public void reparseTask(String taskId) {
    DocumentTask task = taskMapper.selectByTaskId(taskId);
    if (task == null) {
      throw new IllegalArgumentException("任务不存在");
    }
    // 校验任务不在处理中（防重复提交）
    if (task.getStatus() == TaskStatus.PARSING || task.getStatus() == TaskStatus.IMPORTING) {
      throw new IllegalStateException("文档正在处理中，请等待完成后再重新解析");
    }
    // 校验本地文件仍在
    if (!Files.exists(Paths.get(task.getFilePath()))) {
      throw new IllegalStateException("源文件已不存在，无法重新解析: " + task.getFilePath());
    }

    // 清除旧的 ES 向量数据
    try {
      esVectorStoreService.deleteBySource(task.getFileName(), task.getUserId());
    } catch (Exception e) {
      log.warn("[{}] 重新解析-ES向量清除失败（可能尚未入库）: {}", taskId, e.getMessage());
    }
    // 清除旧的 PG document_chunk
    try {
      int deleted = chunkMapper.deleteByTaskId(taskId);
      log.info("[{}] 重新解析-PG document_chunk 已清除 {} 条", taskId, deleted);
    } catch (Exception e) {
      log.warn("[{}] 重新解析-PG document_chunk 清除失败: {}", taskId, e.getMessage());
    }
    // 清除旧的 PG vector_store
    try {
      deletePgVectorBySource(task.getFileName());
    } catch (Exception e) {
      log.warn("[{}] 重新解析-PG vector_store 清除失败: {}", taskId, e.getMessage());
    }
    // 清空 RAG 缓存
    try {
      ragSearchCache.invalidateAll();
    } catch (Exception ignore) {
    }

    // 重置任务状态
    task.setStatus(TaskStatus.UPLOADED);
    task.setErrorMsg(null);
    task.setTotalChunks(0);
    task.setImportedChunks(0);
    task.setFinishedAt(null);
    taskMapper.update(task);

    taskLogMapper.insert(taskId, "REPARSE", "用户触发重新解析: " + task.getFileName());
    log.info("[{}] 重新解析已触发: {}", taskId, task.getFileName());

    // 重新投递队列
    submitToQueue(taskId);
  }

  /** 删除文档任务（DB记录 + 本地文件 + ES向量 + PG数据） */
  public void deleteTask(String taskId) {
    DocumentTask task = taskMapper.selectByTaskId(taskId);
    if (task == null) {
      throw new IllegalArgumentException("任务不存在");
    }

    // 删除ES向量数据（按source匹配删除）
    try {
      esVectorStoreService.deleteBySource(task.getFileName(), task.getUserId());
    } catch (Exception e) {
      log.warn("[{}] ES向量删除失败（可能尚未入库）: {}", taskId, e.getMessage());
    }

    // 删除PG document_chunk 分段数据
    try {
      int deleted = chunkMapper.deleteByTaskId(taskId);
      log.info("[{}] PG document_chunk 已删除 {} 条", taskId, deleted);
    } catch (Exception e) {
      log.warn("[{}] PG document_chunk 删除失败: {}", taskId, e.getMessage());
    }

    // 删除PG vector_store 向量数据（按 metadata.source 过滤）
    try {
      deletePgVectorBySource(task.getFileName());
    } catch (Exception e) {
      log.warn("[{}] PG vector_store 删除失败: {}", taskId, e.getMessage());
    }

    // 删除本地文件
    try {
      java.nio.file.Path filePath = Paths.get(task.getFilePath());
      Files.deleteIfExists(filePath);
    } catch (Exception e) {
      log.warn("[{}] 本地文件删除失败: {}", taskId, e.getMessage());
    }

    // 删除DB记录
    taskMapper.deleteByTaskId(taskId);
    taskLogMapper.insert(taskId, "DELETED", "文档已删除: " + task.getFileName());
    log.info("[{}] 文档任务已删除: {}", taskId, task.getFileName());

    // 删除也要清缓存，避免返回已不存在的文档片段
    try {
      ragSearchCache.invalidateAll();
    } catch (Exception ignore) {
      // 缓存不可用不阻断删除主流程
    }
  }

  /** 查询任务操作日志 */
  public List<Map<String, Object>> getTaskLogs(String taskId) {
    return taskLogMapper.selectByTaskId(taskId);
  }

  /**
   * 执行解析并入库（由队列消费者调用）。
   *
   * <p>入库策略：ES 向量（检索主力）+ PG document_chunk（原文存档）。
   *
   * <p>切片只做一次，结果在 ES 和 PG_chunk 两处复用，避免重复 CPU。
   * ES 分批入库（429 自适应退避），并通过进度回调实时更新 importedChunks。
   */
  public void parseAndImport(String taskId) {
    DocumentTask task = taskMapper.selectByTaskId(taskId);
    if (task == null) {
      log.error("任务不存在: {}", taskId);
      return;
    }

    try {
      // ===== 阶段1: Tika 解析文档 =====
      updateStatus(task, TaskStatus.PARSING);
      taskLogMapper.insert(taskId, "PARSE_START", "开始解析文档: " + task.getFileName());
      log.info("[{}] 开始解析文档: {}", taskId, task.getFileName());

      String rawText;
      String fileNameLower = task.getFileName().toLowerCase();
      if (fileNameLower.endsWith(".txt")) {
        // .txt 文件绕过 Tika，直接读取并自动检测编码（Tika 对 GBK/GB18030 中文 txt 编码检测不可靠）
        rawText = readPlainTextFile(Paths.get(task.getFilePath()));
        log.info("[{}] 纯文本直接读取完成, 编码自动检测, 提取 {} 字符", taskId, rawText.length());
      } else {
        // 非 txt 文件走 Tika（PDF / Word / HTML 等）
        FileSystemResource resource = new FileSystemResource(task.getFilePath());
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> tikaDocuments = reader.get();

        StringBuilder fullText = new StringBuilder();
        for (Document doc : tikaDocuments) {
          fullText.append(doc.getText()).append("\n");
        }
        rawText = fullText.toString().trim();
      }
      if (rawText.isEmpty()) {
        failTask(task, "文档解析结果为空，请检查文件内容");
        log.warn("[{}] 文档解析结果为空: {}", taskId, task.getFileName());
        return;
      }

      String parseMethod = fileNameLower.endsWith(".txt") ? "纯文本直读" : "Tika";
      taskLogMapper.insert(taskId, "PARSE_DONE", parseMethod + " 解析完成, 提取文本 " + rawText.length() + " 字符");
      log.info("[{}] {} 解析完成, 提取文本 {} 字符", taskId, parseMethod, rawText.length());

      // ===== 阶段2: 切片（仅 1 次）=====
      updateStatus(task, TaskStatus.IMPORTING);
      taskLogMapper.insert(taskId, "IMPORT_START", "开始切片+入库（ES向量 + PG原文）");

      String clean = com.jianbo.localaiknowledge.utils.TextCleanUtil.clean(rawText);
      List<String> chunks = com.jianbo.localaiknowledge.utils.TextSplitterUtil.splitText(clean);

      String source = task.getFileName();
      String userId = task.getUserId();
      String docScope = task.getDocScope();

      // 切片完成后立即写入 totalChunks，前端轮询可看到总数
      task.setTotalChunks(chunks.size());
      task.setImportedChunks(0);
      taskMapper.update(task);

      // ===== 阶段3: 入库（ES 向量 + PG 原文存档）=====
      // 3.1 ES 向量入库（检索主力），带进度回调实时更新 importedChunks
      //     每 500 chunks 更新一次 DB，避免长任务频繁写库
      final int totalChunks = chunks.size();
      int esCount = esVectorStoreService.importChunks(chunks, source, userId, docScope,
          imported -> {
            if (imported >= totalChunks || imported % 500 == 0) {
              task.setImportedChunks(imported);
              taskMapper.update(task);
            }
          });

      // 3.2 PG document_chunk 原文存档（不涉及 embedding，速度很快）
      saveChunksToPg(taskId, chunks, source, userId, docScope);

      task.setTotalChunks(esCount);
      task.setImportedChunks(esCount);
      task.setStatus(TaskStatus.DONE);
      task.setFinishedAt(LocalDateTime.now());
      taskMapper.update(task);

      taskLogMapper.insert(taskId, "IMPORT_DONE", "入库完成, 共 " + esCount + " 个切片（ES + PG原文）");
      log.info("[{}] 入库完成, 共 {} 个切片, 来源: {}", taskId, esCount, source);

      // 新文档入库后清空 RAG 检索缓存，保证下一次提问能够实时够检索到。
      // 主要代价：后续 10min 内原本能命中缓存的重复问题会重走 embedding（~2.7s），
      // 但避免了“刚传的文档检索不到”这种错误领域问题。
      try {
        ragSearchCache.invalidateAll();
        log.info("[{}] RAG 检索缓存已清空（新文档入库）", taskId);
      } catch (Exception cacheErr) {
        log.warn("[{}] RAG 检索缓存清理失败: {}", taskId, cacheErr.getMessage());
      }
    } catch (Exception e) {
      failTask(task, e.getMessage());
      log.error("[{}] 文档处理失败: {}", taskId, e.getMessage(), e);
    }
  }

  /** 保存分段原文到 PG document_chunk 表 */
  private void saveChunksToPg(
      String taskId, List<String> chunks, String source, String userId, String docScope) {
    try {
      List<DocumentChunk> rows = new ArrayList<>(chunks.size());
      for (int i = 0; i < chunks.size(); i++) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setTaskId(taskId);
        chunk.setChunkIndex(i);
        chunk.setContent(chunks.get(i));
        chunk.setSource(source);
        chunk.setUserId(userId);
        chunk.setDocScope(docScope != null ? docScope : "PUBLIC");
        rows.add(chunk);
      }
      chunkMapper.batchInsert(rows);
      log.info("[{}] PG document_chunk 入库 {} 条", taskId, rows.size());
    } catch (Exception e) {
      log.error("[{}] PG document_chunk 入库失败: {}", taskId, e.getMessage(), e);
      // 不中断主流程，ES 已成功入库
    }
  }

  /** 按 source 删除 PG vector_store 中的向量数据 PgVectorStore 的 metadata 是 JSONB 格式，通过 SQL 删除 */
  private void deletePgVectorBySource(String source) {
    try {
      // PgVectorStore M5 版本的 metadata 存储在 metadata 列中（JSONB）
      int deleted =
          jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'source' = ?", source);
      log.info("PG vector_store 按 source 删除 {} 条, source={}", deleted, source);
    } catch (Exception e) {
      log.warn("PG vector_store 按 source 删除失败: {}", e.getMessage());
    }
  }

  /** 更新任务状态并持久化 */
  private void updateStatus(DocumentTask task, TaskStatus status) {
    task.setStatus(status);
    taskMapper.update(task);
  }

  /** 标记任务失败 */
  private void failTask(DocumentTask task, String errorMsg) {
    task.setStatus(TaskStatus.FAILED);
    task.setErrorMsg(errorMsg);
    task.setFinishedAt(LocalDateTime.now());
    taskMapper.update(task);
    taskLogMapper.insert(task.getTaskId(), "FAILED", errorMsg);
  }

  /**
   * 读取纯文本文件（自动检测编码）。
   *
   * <p>尝试顺序：UTF-8（带 BOM 检测）→ GB18030（GBK/GB2312 超集）→ 系统默认。
   * 对中文网络小说 .txt 文件（通常 GBK/GB18030）比 Tika 可靠得多。
   */
  private String readPlainTextFile(Path filePath) throws java.io.IOException {
    byte[] bytes = Files.readAllBytes(filePath);

    // UTF-8 BOM (EF BB BF) → 直接 UTF-8
    if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
      log.info("检测到 UTF-8 BOM, 使用 UTF-8 解码");
      return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8).trim();
    }

    // 尝试严格 UTF-8 解码（REPORT 模式：遇到非法字节立即抛异常）
    try {
      CharsetDecoder utf8Decoder = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT);
      String text = utf8Decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString().trim();
      if (!text.isEmpty()) {
        log.info("UTF-8 严格解码成功, 文本长度 {} 字符", text.length());
        return text;
      }
    } catch (CharacterCodingException e) {
      log.info("UTF-8 严格解码失败, 尝试 GB18030");
    }

    // 回退到 GB18030（GBK / GB2312 超集，几乎覆盖所有中文 txt 编码）
    Charset gb18030 = Charset.forName("GB18030");
    try {
      CharsetDecoder gbDecoder = gb18030.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT);
      String text = gbDecoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString().trim();
      log.info("GB18030 解码成功, 文本长度 {} 字符", text.length());
      return text;
    } catch (CharacterCodingException e) {
      log.warn("GB18030 严格解码也失败, 使用 GB18030 宽松模式兜底");
    }

    // 最终兜底：GB18030 宽松模式（替换非法字符而非拒绝）
    return new String(bytes, gb18030).trim();
  }
}
