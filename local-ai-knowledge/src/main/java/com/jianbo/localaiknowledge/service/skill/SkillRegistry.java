package com.jianbo.localaiknowledge.service.skill;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skill 注册表：启动时自动扫描所有 {@link Skill} 注解的 Bean，收集元数据。
 *
 * <h2>工作原理</h2>
 * <pre>
 * Spring 容器启动
 *   │
 *   ▼
 * @PostConstruct init()
 *   │
 *   ├→ 扫描所有 Bean
 *   ├→ 过滤带 @Skill 注解的
 *   ├→ 提取 SkillDescriptor 元数据
 *   └→ 注册到 registry Map
 *
 * 对外接口：
 *   ├→ listAll()       → 列出所有 Skill 描述
 *   ├→ get(name)       → 按名称查找
 *   └→ execute(name, params) → 执行指定 Skill
 * </pre>
 *
 * <h2>与 Agent 注册表的对比</h2>
 * <ul>
 *   <li>{@code MultiAgentOrchestrator.agentRegistry}：按 AgentType 注册，面向对话路由</li>
 *   <li>{@code SkillRegistry}：按 Skill name 注册，面向能力发现和外部调用</li>
 * </ul>
 */
@Component
@Slf4j
public class SkillRegistry {

    private final ApplicationContext applicationContext;
    private final Map<String, SkillDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, SkillExecutor> executors = new LinkedHashMap<>();

    public SkillRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    void init() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Skill.class);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            Skill annotation = bean.getClass().getAnnotation(Skill.class);
            if (annotation == null) continue;

            SkillDescriptor desc = SkillDescriptor.from(annotation, bean.getClass());
            descriptors.put(annotation.name(), desc);

            if (bean instanceof SkillExecutor executor) {
                executors.put(annotation.name(), executor);
            }
            log.info("✅ 注册 Skill: {} → {} [{}]",
                    annotation.name(), annotation.displayName(), bean.getClass().getSimpleName());
        }
        log.info("✅ Skill 注册完成，共 {} 个技能: {}", descriptors.size(), descriptors.keySet());
    }

    /** 列出所有已注册的 Skill 描述 */
    public Collection<SkillDescriptor> listAll() {
        return Collections.unmodifiableCollection(descriptors.values());
    }

    /** 按名称查找 Skill 描述 */
    public SkillDescriptor get(String name) {
        return descriptors.get(name);
    }

    /** 执行指定 Skill */
    public SkillResult execute(String name, Map<String, Object> params) {
        SkillExecutor executor = executors.get(name);
        if (executor == null) {
            return SkillResult.fail("Skill not found: " + name);
        }
        return executor.execute(params);
    }

    /** 检查 Skill 是否已注册 */
    public boolean has(String name) {
        return descriptors.containsKey(name);
    }
}
