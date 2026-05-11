package com.jianbo.localaiknowledge.service.skill;

import java.util.List;

/**
 * Skill 元数据描述（不可变值对象）。
 *
 * <p>由 {@link SkillRegistry} 在启动时从 {@link Skill} 注解中提取，
 * 供 {@link com.jianbo.localaiknowledge.controller.SkillController} 对外暴露。
 *
 * @param name         技能唯一标识
 * @param displayName  显示名称
 * @param description  功能描述
 * @param category     分类标签
 * @param version      版本号
 * @param inputParams  输入参数描述列表
 * @param outputFormat 输出格式
 * @param beanClass    实现类全限定名（供调试）
 */
public record SkillDescriptor(
        String name,
        String displayName,
        String description,
        String category,
        String version,
        List<String> inputParams,
        String outputFormat,
        String beanClass
) {
    /** 从注解 + Bean 类构造 */
    public static SkillDescriptor from(Skill annotation, Class<?> beanClass) {
        return new SkillDescriptor(
                annotation.name(),
                annotation.displayName(),
                annotation.description(),
                annotation.category(),
                annotation.version(),
                List.of(annotation.inputParams()),
                annotation.outputFormat(),
                beanClass.getName()
        );
    }
}
