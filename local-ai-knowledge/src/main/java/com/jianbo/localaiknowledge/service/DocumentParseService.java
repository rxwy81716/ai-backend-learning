package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.mapper.DocumentTaskLogMapper;
import com.jianbo.localaiknowledge.mapper.DocumentTaskMapper;
import com.jianbo.localaiknowledge.model.DocumentTask;
import com.jianbo.localaiknowledge.model.DocumentTask.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文档解析服务
 *
 * 流程：文件上传 → 本地保存 → 投递 Redisson 队列 → 消费者异步解析 → 向量入库 ES
 * 任务状态持久化到 PostgreSQL（document_task 表）
 * 每步操作记录到 document_task_log 表
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentParseService {

    public static final String QUEUE_NAME = "doc:parse:queue";

    private final EsVectorStoreService esVectorStoreService;
    private final DocumentTaskMapper taskMapper;
    private final DocumentTaskLogMapper taskLogMapper;
    private final RedissonClient redissonClient;

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
     *
     * 优势：
     *   - 任务持久化在 Redis，JVM 重启不丢失
     *   - 天然限流（消费者按自身能力拉取）
     *   - 未来可扩展多实例消费
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
    public List<DocumentTask> getAllTasks() {
        return taskMapper.selectAll();
    }

    /** 查询任务操作日志 */
    public List<Map<String, Object>> getTaskLogs(String taskId) {
        return taskLogMapper.selectByTaskId(taskId);
    }

    /**
     * 执行解析并入库（由队列消费者调用）
     *
     * 每个阶段变更都会持久化到 DB + 写操作日志
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

            // ===== 阶段2: 切片 + 向量入库 =====
            updateStatus(task, TaskStatus.IMPORTING);
            taskLogMapper.insert(taskId, "IMPORT_START", "开始切片+向量入库");

            int chunks = esVectorStoreService.importDocuments(
                    rawText, task.getFileName(), task.getUserId(), task.getDocScope());

            task.setTotalChunks(chunks);
            task.setImportedChunks(chunks);
            task.setStatus(TaskStatus.DONE);
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.update(task);

            taskLogMapper.insert(taskId, "IMPORT_DONE",
                    "入库完成, 共 " + chunks + " 个切片");
            log.info("[{}] 入库完成, 共 {} 个切片, 来源: {}", taskId, chunks, task.getFileName());

        } catch (Exception e) {
            failTask(task, e.getMessage());
            log.error("[{}] 文档处理失败: {}", taskId, e.getMessage(), e);
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
