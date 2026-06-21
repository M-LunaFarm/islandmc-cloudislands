package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuiActionDedupePolicyTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void rejectsSamePlayerSameActionInsideWindow() {
        GuiActionDedupePolicy policy = new GuiActionDedupePolicy(500L);
        GuiAction action = new GuiAction.BankAmount("island.bank.withdraw", new BigDecimal("1000"));

        assertTrue(policy.accept(PLAYER, action, GuiClick.LEFT, 1_000L));
        assertFalse(policy.accept(PLAYER, action, GuiClick.LEFT, 1_100L));
        assertTrue(policy.accept(PLAYER, action, GuiClick.LEFT, 1_600L));
    }

    @Test
    void allowsDifferentActionDataClickOrPlayerInsideWindow() {
        GuiActionDedupePolicy policy = new GuiActionDedupePolicy(500L);
        GuiAction first = new GuiAction.BankAmount("island.bank.withdraw", new BigDecimal("1000"));

        assertTrue(policy.accept(PLAYER, first, GuiClick.LEFT, 1_000L));
        assertTrue(policy.accept(PLAYER, new GuiAction.BankAmount("island.bank.withdraw", new BigDecimal("10000")), GuiClick.LEFT, 1_100L));
        assertTrue(policy.accept(PLAYER, first, GuiClick.RIGHT, 1_200L));
        assertTrue(policy.accept(UUID.fromString("00000000-0000-0000-0000-000000000002"), first, GuiClick.LEFT, 1_300L));
    }
}
