package com.diet.service.memory;

import com.diet.enums.MemoryType;
import com.diet.model.MemoryFact;
import com.diet.service.slot.SlotOptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryFactExtractorTest {

    private MemoryFactExtractor extractor;

    @BeforeEach
    void setUp() {
        SlotOptionService slotOptionService = mock(SlotOptionService.class);
        when(slotOptionService.findAllOptions()).thenReturn(Map.of(
                "cuisine", List.of("粤菜", "川菜"),
                "taste", List.of("清淡", "微辣"),
                "convenience", List.of("快速"),
                "healthGoal", List.of("控糖", "减脂")
        ));
        extractor = new MemoryFactExtractor(slotOptionService);
    }

    @Test
    void shouldExtractOnlyExplicitStableFacts() {
        List<MemoryFact> facts = extractor.extract("我对花生过敏，我平时喜欢清淡粤菜，我正在减脂");

        assertThat(facts).contains(
                new MemoryFact(MemoryType.ALLERGY, "ingredient", "花生"),
                new MemoryFact(MemoryType.PREFERENCE, "taste", "清淡"),
                new MemoryFact(MemoryType.PREFERENCE, "cuisine", "粤菜"),
                new MemoryFact(MemoryType.GOAL, "healthGoal", "减脂")
        );
    }

    @Test
    void shouldNotTreatCurrentRequestAsLongTermPreference() {
        assertThat(extractor.extract("今天晚餐想吃清淡粤菜，要快一点")).isEmpty();
    }
}
