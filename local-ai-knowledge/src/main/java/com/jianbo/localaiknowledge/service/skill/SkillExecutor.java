package com.jianbo.localaiknowledge.service.skill;

import java.util.Map;

/**
 * Skill 执行接口：所有 Skill 实现类必须实现此接口。
 *
 * <p>与 {@link com.jianbo.localaiknowledge.service.agent.SpecializedAgent} 的区别：
 * <ul>
 *   <li><b>Agent</b>：面向对话场景，输入是消息列表，输出是流式 token</li>
 *   <li><b>Skill</b>：面向能力调用，输入是参数 Map，输出是结构化结果</li>
 * </ul>
 *
 * <p>一个 Agent 可以内部使用多个 Skill；一个 Skill 也可以被多个 Agent 复用。
 */
public interface SkillExecutor {

    /**
     * 执行技能。
     *
     * @param params 输入参数（key-value）
     * @return 执行结果
     */
    SkillResult execute(Map<String, Object> params);
}
