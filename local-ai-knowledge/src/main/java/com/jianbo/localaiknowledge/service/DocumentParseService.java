package com.jianbo.localaiknowledge.service;

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
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档解析服务
 *
 * 流程：文件上传 → 本地保存 → 投递 Redisson 队列 → 消费者异步解析 → 双写入库（ES + PG）
 * - ES：向量检索（优先查询）
 * - PG：document_chunk 存分段原文 + vector_store 存向量（备份）
 * 任务状态持久化到 PostgreSQL（document_task 表）
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
    private final VectorStore pgVectorStore;

    public DocumentParseService(EsVectorStoreService esVectorStoreService,
                                 DocumentTaskMapper taskMapper,
                                 DocumentTaskLogMapper taskLogMapper,
                                 DocumentChunkMapper chunkMapper,
                                 RedissonClient redissonClient,
                                 org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                                 @Qualifier("vectorStore") VectorStore pgVectorStore) {
        this.esVectorStoreService = esVectorStoreService;
        this.taskMapper = taskMapper;
        this.taskLogMapper = taskLogMapper;
        this.chunkMapper = chunkMapper;
        this.redissonClient = redissonClient;
        this.jdbcTemplate = jdbcTemplate;
        this.pgVectorStore = pgVectorStore;
    }

    /** 注册一个新任务（持久化到 DB） */
    public DocumentTask registerTask(String taskId, String fileName, String filePath, long fileSize) {
        return registerTask(taskId, fileName, filePath, fileSize, null, "PUBLIC");
    }

    /** 注册一个新任务（带用户归属） */
    public DocumentTask registerTask(String taskId, String fileName, String filePath,
                                      long fileSize, String userId, String docScope) {
        DocumentTask task = DocumentTask.create(taskId, fileName, filePath, fileSize, userId, docScope);
        taskMapper.insert(task);
        taskLogMapper.insert(taskId, "UPLOAD", "文件上传成功: " + fileName + " (" + fileSize + "字节)");
        log.info("[{}] 任务已注册并持久化: {}", taskId, fileName);
        return task;
    }

    /**
     * 投递任务到 Redisson 队列（替代 @Async）
     */
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
     * 删除文档任务（DB记录 + 本地文件 + ES向量 + PG数据）
     */
    public void deleteTask(String taskId) {
        DocumentTask task = taskMapper.selectByTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }

        // 删除ES向量数据（按source匹配删除）
        try {
            esVectorStoreService.deleteBySource(task.getFileName(),task.getUserId());
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
    }

    /** 查询任务操作日志 */
    public List<Map<String, Object>> getTaskLogs(String taskId) {
        return taskLogMapper.selectByTaskId(taskId);
    }

    /**
     * 执行解析并入库（由队列消费者调用）
     *
     * 双写策略：ES 向量 + PG（document_chunk 原文 + vector_store 向量）
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

            FileSystemResource resource = new FileSystemResource(task.getFilePath());
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> tikaDocuments = reader.get();

            // 将 Tika 解析出的多段内容合并成完整文本
            StringBuilder fullText = new StringBuilder();
            for (Document doc : tikaDocuments) {
                fullText.append(doc.getText()).append("\n");
            }

            String rawText = fullText.toString().trim();
            if (rawText.isEmpty()) {
                failTask(task, "文档解析结果为空，请检查文件内容");
                log.warn("[{}] 文档解析结果为空: {}", taskId, task.getFileName());
                return;
            }

            taskLogMapper.insert(taskId, "PARSE_DONE",
                    "Tika 解析完成, 提取文本 " + rawText.length() + " 字符");
            log.info("[{}] Tika 解析完成, 提取文本 {} 字符", taskId, rawText.length());

            // ===== 阶段2: 切片 + 双写入库 =====
            updateStatus(task, TaskStatus.IMPORTING);
            taskLogMapper.insert(taskId, "IMPORT_START", "开始切片+双写入库（ES + PG）");

            // 2.1 ES 向量入库（优先检索）
            int chunks = esVectorStoreService.importDocuments(
                    rawText, task.getFileName(), task.getUserId(), task.getDocScope());

            // 2.2 PG document_chunk 存分段原文
            saveChunksToPg(taskId, rawText, task.getFileName(), task.getUserId(), task.getDocScope());

            // 2.3 PG vector_store 向量双写
            saveVectorToPg(rawText, task.getFileName(), task.getUserId(), task.getDocScope());

            task.setTotalChunks(chunks);
            task.setImportedChunks(chunks);
            task.setStatus(TaskStatus.DONE);
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.update(task);

            taskLogMapper.insert(taskId, "IMPORT_DONE",
                    "双写入库完成, 共 " + chunks + " 个切片（ES+PG）");
            log.info("[{}] 双写入库完成, 共 {} 个切片, 来源: {}", taskId, chunks, task.getFileName());

        } catch (Exception e) {
            failTask(task, e.getMessage());
            log.error("[{}] 文档处理失败: {}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 保存分段原文到 PG document_chunk 表
     */
    private void saveChunksToPg(String taskId, String rawText, String source, String userId, String docScope) {
        try {
            String clean = com.jianbo.localaiknowledge.utils.TextCleanUtil.clean(rawText);
            List<String> splitText = com.jianbo.localaiknowledge.utils.TextSplitterUtil.splitText(clean);

            List<DocumentChunk> chunks = new ArrayList<>();
            for (int i = 0; i < splitText.size(); i++) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setTaskId(taskId);
                chunk.setChunkIndex(i);
                chunk.setContent(splitText.get(i));
                chunk.setSource(source);
                chunk.setUserId(userId);
                chunk.setDocScope(docScope != null ? docScope : "PUBLIC");
                chunks.add(chunk);
            }
            chunkMapper.batchInsert(chunks);
            log.info("[{}] PG document_chunk 入库 {} 条", taskId, chunks.size());
        } catch (Exception e) {
            log.error("[{}] PG document_chunk 入库失败: {}", taskId, e.getMessage(), e);
            // 不中断主流程，ES 已成功入库
        }
    }

    /**
     * 向量双写到 PG vector_store
     */
    private void saveVectorToPg(String rawText, String source, String userId, String docScope) {
        try {
            String clean = com.jianbo.localaiknowledge.utils.TextCleanUtil.clean(rawText);
            List<String> splitText = com.jianbo.localaiknowledge.utils.TextSplitterUtil.splitText(clean);

            List<Document> documents = new ArrayList<>();
            for (int i = 0; i < splitText.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", source);
                metadata.put("chunk_index", String.valueOf(i));
                metadata.put("total_chunks", String.valueOf(splitText.size()));
                metadata.put("doc_scope", docScope != null ? docScope : "PUBLIC");
                if (userId != null) {
                    metadata.put("user_id", userId);
                }
                documents.add(new Document(splitText.get(i), metadata));
            }

            pgVectorStore.add(documents);
            log.info("PG vector_store 入库 {} 条, 来源: {}", documents.size(), source);
        } catch (Exception e) {
            log.error("PG vector_store 入库失败, 来源: {}, error: {}", source, e.getMessage(), e);
            // 不中断主流程，ES 已成功入库
        }
    }

    /**
     * 按 source 删除 PG vector_store 中的向量数据
     * PgVectorStore 的 metadata 是 JSONB 格式，通过 SQL 删除
     */
    private void deletePgVectorBySource(String source) {
        try {
            // PgVectorStore M5 版本的 metadata 存储在 metadata 列中（JSONB）
            int deleted = jdbcTemplate.update(
                    "DELETE FROM vector_store WHERE metadata->>'source' = ?", source);
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
}
