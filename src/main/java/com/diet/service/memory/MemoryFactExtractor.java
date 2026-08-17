package com.diet.service.memory;

import com.diet.enums.MemoryType;
import com.diet.model.MemoryFact;
import com.diet.service.slot.SlotOptionService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只提取用户明确、稳定表达的长期事实，避免把“今天想吃辣”误记成长期偏好。
 */
@Component
public class MemoryFactExtractor {

    private static final List<String> PREFERENCE_MARKERS = List.of(
            "我喜欢", "我爱吃", "我偏好", "平时喜欢", "一直喜欢");
    private static final List<String> GOAL_MARKERS = List.of(
            "我长期", "我一直在", "我正在", "长期保持", "长期想要");
    private static final List<String> AVOID_MARKERS = List.of(
            "我不能吃", "我不吃", "我忌口", "我从不吃");
    private static final List<String> PREFERENCE_SLOT_NAMES = List.of(
            "cuisine", "taste", "convenience");

    private final SlotOptionService slotOptionService;

    public MemoryFactExtractor(SlotOptionService slotOptionService) {
        this.slotOptionService = slotOptionService;
    }

    public List<MemoryFact> extract(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }

        Map<String, MemoryFact> facts = new LinkedHashMap<>();
        extractAllergies(userMessage, facts);
        extractAvoids(userMessage, facts);
        extractSlotFacts(userMessage, PREFERENCE_MARKERS, MemoryType.PREFERENCE,
                PREFERENCE_SLOT_NAMES, facts);
        extractSlotFacts(userMessage, GOAL_MARKERS, MemoryType.GOAL,
                List.of("healthGoal"), facts);
        return new ArrayList<>(facts.values());
    }

    private void extractAllergies(String message, Map<String, MemoryFact> facts) {
        int searchFrom = 0;
        while (true) {
            int allergyIndex = message.indexOf("过敏", searchFrom);
            if (allergyIndex < 0) {
                return;
            }
            int boundary = lastBoundary(message, allergyIndex);
            String raw = message.substring(boundary, allergyIndex)
                    .replace("我", "")
                    .replace("对", "")
                    .trim();
            addFreeTextFacts(MemoryType.ALLERGY, "ingredient", raw, facts);
            searchFrom = allergyIndex + 2;
        }
    }

    private void extractAvoids(String message, Map<String, MemoryFact> facts) {
        for (String marker : AVOID_MARKERS) {
            int index = message.indexOf(marker);
            if (index < 0) {
                continue;
            }
            String raw = untilBoundary(message.substring(index + marker.length()));
            addFreeTextFacts(MemoryType.AVOID, "ingredient", raw, facts);
        }
    }

    private void extractSlotFacts(String message,
                                  List<String> markers,
                                  MemoryType type,
                                  List<String> slotNames,
                                  Map<String, MemoryFact> facts) {
        Map<String, List<String>> allOptions = slotOptionService.findAllOptions();
        for (String marker : markers) {
            int searchFrom = 0;
            while (true) {
                int markerIndex = message.indexOf(marker, searchFrom);
                if (markerIndex < 0) {
                    break;
                }
                String explicitClause = untilBoundary(message.substring(markerIndex + marker.length()));
                for (String slotName : slotNames) {
                    for (String option : allOptions.getOrDefault(slotName, List.of())) {
                        if (explicitClause.contains(option)) {
                            addFact(new MemoryFact(type, slotName, option), facts);
                        }
                    }
                }
                searchFrom = markerIndex + marker.length();
            }
        }
    }

    private void addFreeTextFacts(MemoryType type,
                                  String key,
                                  String raw,
                                  Map<String, MemoryFact> facts) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String value : raw.split("[、,，/和及]")) {
            String normalized = value.trim();
            if (!normalized.isBlank() && normalized.length() <= 30) {
                addFact(new MemoryFact(type, key, normalized), facts);
            }
        }
    }

    private void addFact(MemoryFact fact, Map<String, MemoryFact> facts) {
        String deduplicationKey = fact.type() + "|" + fact.key() + "|" + fact.value();
        facts.putIfAbsent(deduplicationKey, fact);
    }

    private int lastBoundary(String text, int endExclusive) {
        int boundary = 0;
        for (char symbol : new char[]{'。', '！', '？', ',', '，', ';', '；', '\n'}) {
            boundary = Math.max(boundary, text.lastIndexOf(symbol, endExclusive - 1) + 1);
        }
        return boundary;
    }

    private String untilBoundary(String text) {
        int end = text.length();
        for (char symbol : new char[]{'。', '！', '？', ',', '，', ';', '；', '\n'}) {
            int index = text.indexOf(symbol);
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        return text.substring(0, end).trim();
    }
}
