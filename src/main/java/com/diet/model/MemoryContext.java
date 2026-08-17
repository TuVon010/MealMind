package com.diet.model;

import java.util.List;

/**
 * 一轮 Agent 调用使用的分层记忆上下文。
 * recentHistory 是短期工作窗口，sessionSummary 是已压缩历史，longTermMemories 是跨会话稳定事实。
 */
public record MemoryContext(
        String sessionSummary,
        List<UserMemoryRow> longTermMemories,
        SlotBundle preferenceSlots,
        List<ConversationTurn> recentHistory
) {
    public MemoryContext {
        sessionSummary = sessionSummary == null ? "" : sessionSummary;
        longTermMemories = longTermMemories == null ? List.of() : List.copyOf(longTermMemories);
        preferenceSlots = preferenceSlots == null ? SlotBundle.empty() : preferenceSlots;
        recentHistory = recentHistory == null ? List.of() : List.copyOf(recentHistory);
    }

    public static MemoryContext empty() {
        return new MemoryContext("", List.of(), SlotBundle.empty(), List.of());
    }

    /** 会话摘要进入外部模型前删除含健康敏感词的整行。 */
    public String promptSafeSessionSummary() {
        if (sessionSummary.isBlank()) {
            return "无";
        }
        String safeSummary = sessionSummary.lines()
                .filter(line -> !containsSensitiveKeyword(line))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return safeSummary.isBlank() ? "无" : safeSummary;
    }

    /** 最近对话同样过滤历史敏感内容；本轮用户原文仍由正常聊天流程处理。 */
    public List<ConversationTurn> promptSafeRecentHistory() {
        return recentHistory.stream()
                .filter(turn -> !containsSensitiveKeyword(turn.summary()))
                .toList();
    }

    private boolean containsSensitiveKeyword(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return List.of(
                        "过敏", "不能吃", "忌口", "疾病", "治疗", "胃疼", "胃痛",
                        "糖尿病", "高血压", "孕妇", "怀孕", "用药", "药物")
                .stream()
                .anyMatch(text::contains);
    }

}
