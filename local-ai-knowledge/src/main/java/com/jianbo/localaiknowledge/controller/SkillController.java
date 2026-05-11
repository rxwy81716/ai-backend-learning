package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.service.skill.SkillDescriptor;
import com.jianbo.localaiknowledge.service.skill.SkillRegistry;
import com.jianbo.localaiknowledge.service.skill.SkillResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

/**
 * Skill 管理 API：能力发现与调用。
 *
 * <h2>端点列表</h2>
 * <ul>
 *   <li>{@code GET /api/skills} — 列出所有已注册的 Skill</li>
 *   <li>{@code GET /api/skills/{name}} — 获取指定 Skill 的详细描述</li>
 *   <li>{@code POST /api/skills/{name}/execute} — 执行指定 Skill</li>
 * </ul>
 *
 * <h2>与其他 API 的关系</h2>
 * <ul>
 *   <li>{@code /api/rag/chat/stream}：面向终端用户的对话接口（走 Agent 路由）</li>
 *   <li>{@code /mcp/sse}：面向 AI 客户端的 MCP 协议接口</li>
 *   <li>{@code /api/skills}：面向开发者/编排器的能力发现接口（走 Skill 注册表）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillRegistry skillRegistry;

    /** 列出所有已注册的 Skill */
    @GetMapping
    public ResponseEntity<Collection<SkillDescriptor>> listSkills() {
        return ResponseEntity.ok(skillRegistry.listAll());
    }

    /** 获取指定 Skill 的详细描述 */
    @GetMapping("/{name}")
    public ResponseEntity<SkillDescriptor> getSkill(@PathVariable String name) {
        SkillDescriptor desc = skillRegistry.get(name);
        if (desc == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(desc);
    }

    /** 执行指定 Skill */
    @PostMapping("/{name}/execute")
    public ResponseEntity<SkillResult> executeSkill(
            @PathVariable String name,
            @RequestBody Map<String, Object> params) {
        if (!skillRegistry.has(name)) {
            return ResponseEntity.notFound().build();
        }
        SkillResult result = skillRegistry.execute(name, params);
        return ResponseEntity.ok(result);
    }
}
