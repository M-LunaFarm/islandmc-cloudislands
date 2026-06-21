package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GuiActionParserTest {
    @Test
    void parsesPermissionPageIntoTypedAction() {
        GuiAction action = GuiActionParser.parse("island.permissions.page", Map.of("page", "2", "rolePage", "1")).orElseThrow();

        assertTrue(action instanceof GuiAction.PermissionPage);
        assertEquals("island.permissions.page", action.actionId());
        assertEquals(Map.of("page", "2", "rolePage", "1"), action.data());
    }

    @Test
    void parsesPermissionChangeIntoCanonicalRoleAndPermission() {
        GuiAction action = GuiActionParser.parse("island.permissions.set", Map.of("role", "builder", "permission", "open-container")).orElseThrow();

        assertTrue(action instanceof GuiAction.ChangePermission);
        assertEquals("island.permissions.set", action.actionId());
        assertEquals(Map.of("role", "BUILDER", "permission", "OPEN_CONTAINER"), action.data());
    }

    @Test
    void rejectsMalformedKnownActionsInsteadOfExecutingRawMaps() {
        assertTrue(GuiActionParser.parse("island.permissions.set", Map.of("role", "", "permission", "BUILD")).isEmpty());
        assertTrue(GuiActionParser.parse("island.permissions.set", Map.of("role", "MEMBER", "permission", "NOPE")).isEmpty());
        assertTrue(GuiActionParser.parse("island.member.remove.prepare", Map.of("playerUuid", "not-a-uuid")).isEmpty());
    }

    @Test
    void preservesRegisteredRawActions() {
        GuiAction action = GuiActionParser.parse("island.bank.open", Map.of()).orElseThrow();

        assertTrue(action instanceof GuiAction.Raw);
        assertEquals("island.bank.open", action.actionId());
        assertEquals(Map.of(), action.data());
    }

    @Test
    void parsesBankAmountsIntoTypedActions() {
        GuiAction action = GuiActionParser.parse("island.bank.deposit", Map.of("amount", "1000.00")).orElseThrow();

        assertTrue(action instanceof GuiAction.BankAmount);
        assertEquals("island.bank.deposit", action.actionId());
        assertEquals(Map.of("amount", "1000"), action.data());
        assertTrue(((GuiAction.BankAmount) action).deposit());
    }

    @Test
    void rejectsUnregisteredActionIdsInsteadOfExecutingRawMaps() {
        assertTrue(GuiActionParser.parse("island.member.remvoe", Map.of("playerUuid", "00000000-0000-0000-0000-000000000000")).isEmpty());
        assertTrue(GuiActionParser.parse("island.unknown.open", Map.of()).isEmpty());
        assertTrue(GuiActionParser.parse("island.bank.withdraw", Map.of("amount", "0")).isEmpty());
        assertTrue(GuiActionParser.parse("island.bank.deposit", Map.of("amount", "abc")).isEmpty());
    }
}
