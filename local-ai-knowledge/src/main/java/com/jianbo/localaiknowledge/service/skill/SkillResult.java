package com.jianbo.localaiknowledge.service.skill;

/**
 * Skill 执行结果（不可变值对象）。
 *
 * @param success 是否成功
 * @param data    结果数据（成功时为实际内容，失败时为错误信息）
 * @param format  数据格式（text / json / markdown）
 */
public record SkillResult(
        boolean success,
        String data,
        String format
) {
    public static SkillResult ok(String data) {
        return new SkillResult(true, data, "text");
    }

    public static SkillResult ok(String data, String format) {
        return new SkillResult(true, data, format);
    }

    public static SkillResult fail(String error) {
        return new SkillResult(false, error, "text");
    }
}
