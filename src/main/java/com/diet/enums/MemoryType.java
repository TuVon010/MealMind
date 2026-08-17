package com.diet.enums;

/**
 * 长期记忆类型。
 * 只保存用户明确表达且跨会话仍有价值的信息，不把普通单轮槽位全部永久化。
 */
public enum MemoryType {
    /** 食物过敏，属于安全约束。 */
    ALLERGY,
    /** 明确忌口或不能吃的内容。 */
    AVOID,
    /** 稳定口味、菜系或便捷性偏好。 */
    PREFERENCE,
    /** 明确的长期饮食目标。 */
    GOAL
}
