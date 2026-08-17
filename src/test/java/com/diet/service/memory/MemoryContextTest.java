package com.diet.service.memory;

import com.diet.model.ConversationTurn;
import com.diet.model.MemoryContext;
import com.diet.model.SlotBundle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryContextTest {

    @Test
    void shouldRemoveSensitiveHistoryBeforePromptInjection() {
        MemoryContext context = new MemoryContext(
                "用户：我喜欢粤菜\n用户：我对花生过敏",
                List.of(),
                SlotBundle.empty(),
                List.of(
                        new ConversationTurn("user", null, "上次想吃粤菜", 1L),
                        new ConversationTurn("user", null, "我对花生过敏", 2L)
                )
        );

        assertThat(context.promptSafeSessionSummary()).contains("粤菜").doesNotContain("花生");
        assertThat(context.promptSafeRecentHistory())
                .extracting(ConversationTurn::summary)
                .containsExactly("上次想吃粤菜");
    }
}
