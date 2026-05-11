package com.jianbo.localaiknowledge.service.skill;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Skill 声明注解：标记一个 Bean 为标准化能力单元。
 *
 * <h2>什么是 Skill？</h2>
 * <p>Skill 是对 AI 能力的标准化封装。类比：
 * <ul>
 *   <li><b>Agent</b> 是"员工"——有自主决策能力，能思考和行动</li>
 *   <li><b>Tool / Function</b> 是"工具"——Agent 可以调用的具体函数</li>
 *   <li><b>Skill</b> 是"技能证书"——对能力的标准化描述，包含：
 *       做什么、怎么调、输入什么、输出什么</li>
 * </ul>
 *
 * <h2>为什么需要 Skill？</h2>
 * <p>项目中已有多个 Agent，但它们的能力描述分散在各自类中。
 * Skill 层提供统一的元数据注册和发现机制：
 * <ul>
 *   <li>外部系统可以通过 API 查询"这个系统有哪些能力"</li>
 *   <li>编排器可以根据 Skill 元数据动态选择执行策略</li>
 *   <li>便于能力复用——同一个 Skill 可以被不同 Agent 组合使用</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 * <pre>
 * {@code @Skill(
 *     name = "knowledge_search",
 *     displayName = "知识库检索",
 *     description = "从企业私域文档中检索相关信息",
 *     category = "retrieval",
 *     version = "1.0"
 * )}
 * {@code @Component}
 * public class KnowledgeSearchSkill implements SkillExecutor { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Skill {

    /** 技能唯一标识（英文，snake_case） */
    String name();

    /** 显示名称（中文友好） */
    String displayName();

    /** 技能描述：做什么、适用什么场景 */
    String description();

    /** 分类标签（retrieval / generation / analysis / external） */
    String category() default "general";

    /** 版本号 */
    String version() default "1.0";

    /** 输入参数描述（JSON Schema 简化版） */
    String[] inputParams() default {};

    /** 输出格式描述 */
    String outputFormat() default "text";
}
