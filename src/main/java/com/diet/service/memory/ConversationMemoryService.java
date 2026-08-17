package com.diet.service.memory;

import com.diet.enums.MemoryType;
import com.diet.mapper.MemoryMapper;
import com.diet.model.ConversationTurn;
import com.diet.model.MemoryContext;
import com.diet.model.MemoryFact;
import com.diet.model.SessionMemoryRow;
import com.diet.model.SessionMessageRow;
import com.diet.model.SlotBundle;
import com.diet.model.UserMemoryRow;
import com.diet.service.session.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 分层会话记忆：最近消息作为短期记忆，旧消息压缩为会话摘要，稳定事实作为长期记忆。
 */
@Slf4j
@Service
public class ConversationMemoryService {

    private final MemoryMapper memoryMapper;
    private final SessionService sessionService;
    private final MemoryFactExtractor factExtractor;
    private final boolean enabled;
    private final int shortTermMessages;
    private final int summaryBatchSize;
    private final int summaryMaxChars;
    private final int summaryMessageMaxChars;
    private final int longTermLimit;
    private final int longTermMaxChars;

    public ConversationMemoryService(MemoryMapper memoryMapper,
                                     SessionService sessionService,
                                     MemoryFactExtractor factExtractor,
                                     @Value("${diet.memory.enabled:true}") boolean enabled,
                                     @Value("${diet.memory.short-term-messages:6}") int shortTermMessages,
                                     @Value("${diet.memory.summary-batch-size:50}") int summaryBatchSize,
                                     @Value("${diet.memory.summary-max-chars:800}") int summaryMaxChars,
                                     @Value("${diet.memory.summary-message-max-chars:120}") int summaryMessageMaxChars,
                                     @Value("${diet.memory.long-term-limit:20}") int longTermLimit,
                                     @Value("${diet.memory.long-term-max-chars:600}") int longTermMaxChars) {
        this.memoryMapper = memoryMapper;
        this.sessionService = sessionService;
        this.factExtractor = factExtractor;
        this.enabled = enabled;
        this.shortTermMessages = Math.max(1, shortTermMessages);
        this.summaryBatchSize = Math.max(1, summaryBatchSize);
        this.summaryMaxChars = Math.max(100, summaryMaxChars);
        this.summaryMessageMaxChars = Math.max(20, summaryMessageMaxChars);
        this.longTermLimit = Math.max(1, longTermLimit);
        this.longTermMaxChars = Math.max(100, longTermMaxChars);
    }

    public MemoryContext prepareContext(String sessionId, Long userId) {
        List<ConversationTurn> recent = sessionService.recentConversationTurns(
                sessionId, userId, shortTermMessages);
        if (!enabled) {
            return new MemoryContext("", List.of(), SlotBundle.empty(), recent);
        }
        try {
            compactOldMessages(sessionId, userId);
            SessionMemoryRow sessionMemory = memoryMapper.findSessionMemory(sessionId, userId);
            List<UserMemoryRow> longTerm = withinLongTermBudget(
                    memoryMapper.listActiveUserMemories(userId, longTermLimit));
            return new MemoryContext(
                    sessionMemory == null ? "" : sessionMemory.getSummary(),
                    longTerm,
                    toPreferenceSlots(longTerm),
                    recent);
        } catch (RuntimeException ex) {
            log.warn("memory context load failed, degrade to recent conversation: sessionId={}",
                    sessionId, ex);
            return new MemoryContext("", List.of(), SlotBundle.empty(), recent);
        }
    }

    public List<MemoryFact> rememberExplicitFacts(Long userId, String sessionId, String userMessage) {
        if (!enabled) {
            return List.of();
        }
        List<MemoryFact> facts = factExtractor.extract(userMessage);
        for (MemoryFact fact : facts) {
            try {
                UserMemoryRow row = new UserMemoryRow();
                row.setUserId(userId);
                row.setMemoryType(fact.type().name());
                row.setMemoryKey(fact.key());
                row.setMemoryValue(fact.value());
                row.setSourceSessionId(sessionId);
                row.setActive(true);
                memoryMapper.upsertUserMemory(row);
            } catch (RuntimeException ex) {
                log.warn("long-term memory save failed: userId={}, type={}, key={}",
                        userId, fact.type(), fact.key(), ex);
            }
        }
        return facts;
    }

    public List<UserMemoryRow> listLongTermMemories(Long userId) {
        return memoryMapper.listActiveUserMemories(userId, longTermLimit);
    }

    public boolean forget(Long userId, Long memoryId) {
        return memoryMapper.deactivateUserMemory(memoryId, userId) > 0;
    }

    private void compactOldMessages(String sessionId, Long userId) {
        Long cutoffMessageId = memoryMapper.findRecentCutoffMessageId(
                sessionId, userId, shortTermMessages);
        if (cutoffMessageId == null) {
            return;
        }

        SessionMemoryRow stored = memoryMapper.findSessionMemory(sessionId, userId);
        long summarizedMessageId = stored == null || stored.getSummarizedMessageId() == null
                ? 0L : stored.getSummarizedMessageId();
        String summary = stored == null ? "" : stored.getSummary();

        for (int batch = 0; batch < 20; batch++) {
            List<SessionMessageRow> messages = memoryMapper.listMessagesForSummary(
                    sessionId, userId, summarizedMessageId, cutoffMessageId, summaryBatchSize);
            if (messages.isEmpty()) {
                return;
            }
            for (SessionMessageRow message : messages) {
                summary = appendSummary(summary, message);
                summarizedMessageId = message.getId();
            }
            SessionMemoryRow updated = new SessionMemoryRow();
            updated.setSessionId(sessionId);
            updated.setSummary(summary);
            updated.setSummarizedMessageId(summarizedMessageId);
            memoryMapper.upsertSessionMemory(updated);
            if (messages.size() < summaryBatchSize) {
                return;
            }
        }
    }

    private String appendSummary(String summary, SessionMessageRow message) {
        String content = message.getContent() == null ? "" : message.getContent().trim();
        if (content.length() > summaryMessageMaxChars) {
            content = content.substring(0, summaryMessageMaxChars) + "…";
        }
        String role = "USER".equalsIgnoreCase(message.getRole()) ? "用户" : "助手";
        String combined = (summary == null || summary.isBlank() ? "" : summary + "\n")
                + role + "：" + content;
        if (combined.length() <= summaryMaxChars) {
            return combined;
        }
        return "…" + combined.substring(combined.length() - summaryMaxChars + 1);
    }

    private List<UserMemoryRow> withinLongTermBudget(List<UserMemoryRow> memories) {
        List<UserMemoryRow> selected = new ArrayList<>();
        int chars = 0;
        for (UserMemoryRow memory : memories) {
            int next = safeLength(memory.getMemoryType())
                    + safeLength(memory.getMemoryKey())
                    + safeLength(memory.getMemoryValue()) + 4;
            if (!selected.isEmpty() && chars + next > longTermMaxChars) {
                break;
            }
            selected.add(memory);
            chars += next;
        }
        return selected;
    }

    private SlotBundle toPreferenceSlots(List<UserMemoryRow> memories) {
        List<String> mealTime = new ArrayList<>();
        List<String> mood = new ArrayList<>();
        List<String> scene = new ArrayList<>();
        List<String> healthGoal = new ArrayList<>();
        List<String> cuisine = new ArrayList<>();
        List<String> taste = new ArrayList<>();
        List<String> convenience = new ArrayList<>();
        for (UserMemoryRow memory : memories) {
            if (!MemoryType.PREFERENCE.name().equals(memory.getMemoryType())
                    && !MemoryType.GOAL.name().equals(memory.getMemoryType())) {
                continue;
            }
            switch (memory.getMemoryKey()) {
                case "mealTime" -> mealTime.add(memory.getMemoryValue());
                case "mood" -> mood.add(memory.getMemoryValue());
                case "scene" -> scene.add(memory.getMemoryValue());
                case "healthGoal" -> healthGoal.add(memory.getMemoryValue());
                case "cuisine" -> cuisine.add(memory.getMemoryValue());
                case "taste" -> taste.add(memory.getMemoryValue());
                case "convenience" -> convenience.add(memory.getMemoryValue());
                default -> log.debug("ignore unsupported memory slot: {}", memory.getMemoryKey());
            }
        }
        return new SlotBundle(mealTime, mood, scene, healthGoal, cuisine, taste, convenience);
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }
}
