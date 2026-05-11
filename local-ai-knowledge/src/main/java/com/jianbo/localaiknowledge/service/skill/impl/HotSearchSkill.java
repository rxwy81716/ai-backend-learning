package com.jianbo.localaiknowledge.service.skill.impl;

import com.jianbo.localaiknowledge.service.HotSearchService;
import com.jianbo.localaiknowledge.service.skill.Skill;
import com.jianbo.localaiknowledge.service.skill.SkillExecutor;
import com.jianbo.localaiknowledge.service.skill.SkillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 热榜查询 Skill：封装各平台热搜数据查询能力。
 */
@Skill(
        name = "hot_search",
        displayName = "热榜查询",
        description = "查询各平台（微博/知乎/B站/GitHub/抖音/小红书）的实时热搜热榜数据",
        category = "external",
        inputParams = {"platform: 平台名称（可选，留空查全部）"},
        outputFormat = "markdown"
)
@Component
@Slf4j
@RequiredArgsConstructor
public class HotSearchSkill implements SkillExecutor {

    private final HotSearchService hotSearchService;

    @Override
    public SkillResult execute(Map<String, Object> params) {
        String platform = (String) params.getOrDefault("platform", "");
        String question = platform.isBlank() ? "热搜热榜" : platform + "热搜";

        log.info("[Skill] hot_search | platform={}", platform);
        String result = hotSearchService.queryAndFormat(question);
        return SkillResult.ok(result, "markdown");
    }
}
