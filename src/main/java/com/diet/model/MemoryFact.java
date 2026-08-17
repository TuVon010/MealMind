package com.diet.model;

import com.diet.enums.MemoryType;

/**
 * 从用户明确表达中提取出的长期记忆事实。
 */
public record MemoryFact(MemoryType type, String key, String value) {
}
