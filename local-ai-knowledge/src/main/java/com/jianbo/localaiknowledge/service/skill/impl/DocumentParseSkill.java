package com.jianbo.localaiknowledge.service.skill.impl;

import com.jianbo.localaiknowledge.service.DocumentParseService;
import com.jianbo.localaiknowledge.service.skill.Skill;
import com.jianbo.localaiknowledge.service.skill.SkillExecutor;
import com.jianbo.localaiknowledge.service.skill.SkillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 文档解析 Skill：封装文档上传解析入库能力。
 *
 * <p>注意：本 Skill 只是查询任务状态的简化封装，
 * 实际的文件上传需要通过 DocumentController 的 Multipart 接口。
 */
@Skill(
        name = "document_status",
        displayName = "文档任务状态查询",
        description = "查询文档解析任务的当前状态（上传中/解析中/导入中/完成/失败）",
        category = "retrieval",
        inputParams = {"taskId: 任务ID（必填）"},
        outputFormat = "json"
)
@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentParseSkill implements SkillExecutor {

    private final DocumentParseService documentParseService;

    @Override
    public SkillResult execute(Map<String, Object> params) {
        String taskId = (String) params.get("taskId");
        if (taskId == null || taskId.isBlank()) {
            return SkillResult.fail("参数 taskId 不能为空");
        }

        log.info("[Skill] document_status | taskId={}", taskId);
        var task = documentParseService.getTask(taskId);
        if (task == null) {
            return SkillResult.fail("任务不存在: " + taskId);
        }

        String info = String.format(
                "{\"taskId\":\"%s\",\"fileName\":\"%s\",\"status\":\"%s\",\"totalChunks\":%d,\"importedChunks\":%d}",
                task.getTaskId(), task.getFileName(), task.getStatus(),
                task.getTotalChunks(), task.getImportedChunks());
        return SkillResult.ok(info, "json");
    }
}
