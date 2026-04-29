package com.jianbo.crawler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

/**
 * 知识库 API 调用服务
 *
 * 通过 HTTP 调用 local-ai-knowledge 服务爬虫专用接口，完成数据入库：
 *   - POST /api/doc/crawler-upload  → 上传文档（无需认证，固定 PUBLIC）
 *   - GET  /api/doc/status/{id}     → 查询解析状态
 *
 * 爬虫专用接口无需 JWT Token，docScope 固定为 PUBLIC。
 */
@Slf4j
@Service
public class KnowledgeApiService {

    private final RestClient knowledgeRestClient;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public KnowledgeApiService(
            @Qualifier("knowledgeRestClient") RestClient knowledgeRestClient) {
        this.knowledgeRestClient = knowledgeRestClient;
    }

    /**
     * 上传文档到 knowledge 服务（爬虫专用接口，无需认证）
     *
     * 将爬虫生成的结构化文本以 .txt 文件形式上传：
     *   POST /api/doc/crawler-upload
     *   Content-Type: multipart/form-data
     *   - file: 文本文件
     *
     * knowledge 服务自动以 PUBLIC 范围处理：解析 → 切片 → 向量化 → 存储
     *
     * @param content  文档内容（结构化文本）
     * @param fileName 文件名（如 github_trending_20250429_1000.txt）
     * @return 上传响应中的 taskId
     */
    public String uploadDocument(String content, String fileName) {
        try {
            byte[] fileBytes = content.getBytes(StandardCharsets.UTF_8);

            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };

            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.TEXT_PLAIN);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new HttpEntity<>(fileResource, fileHeaders));

            String response = knowledgeRestClient.post()
                    .uri("/api/doc/crawler-upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode node = MAPPER.readTree(response);
            String taskId = node.path("taskId").asText("");

            log.info("文档上传成功：fileName={}, taskId={}, size={}bytes",
                    fileName, taskId, fileBytes.length);
            return taskId;

        } catch (Exception e) {
            log.error("文档上传失败：fileName={}, error={}", fileName, e.getMessage(), e);
            throw new RuntimeException("文档上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询文档解析状态
     *
     * @param taskId 任务ID
     * @return 任务状态 JSON 字符串
     */
    public String queryTaskStatus(String taskId) {
        try {
            return knowledgeRestClient.get()
                    .uri("/api/doc/status/{taskId}", taskId)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("查询任务状态失败：taskId={}, error={}", taskId, e.getMessage());
            return null;
        }
    }
}
