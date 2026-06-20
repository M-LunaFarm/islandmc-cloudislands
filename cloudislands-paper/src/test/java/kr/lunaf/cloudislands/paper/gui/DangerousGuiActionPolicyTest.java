package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DangerousGuiActionPolicyTest {
    @Test
    void resetAndDeleteConfirmationsCarryOperationSpecificTokens() {
        assertTrue(DangerousGuiActionPolicy.confirmed(
            DangerousGuiActionPolicy.resetConfirmationData(),
            GuiClick.LEFT,
            DangerousGuiActionPolicy.RESET_OPERATION,
            DangerousGuiActionPolicy.RESET_TOKEN
        ));
        assertTrue(DangerousGuiActionPolicy.confirmed(
            DangerousGuiActionPolicy.deleteConfirmationData(),
            GuiClick.LEFT,
            DangerousGuiActionPolicy.DELETE_OPERATION,
            DangerousGuiActionPolicy.DELETE_TOKEN
        ));
    }

    @Test
    void rejectsMissingTokensWrongOperationAndNonConfirmClicks() {
        assertFalse(DangerousGuiActionPolicy.confirmed(Map.of(), GuiClick.LEFT, DangerousGuiActionPolicy.RESET_OPERATION, DangerousGuiActionPolicy.RESET_TOKEN));
        assertFalse(DangerousGuiActionPolicy.confirmed(DangerousGuiActionPolicy.resetConfirmationData(), GuiClick.RIGHT, DangerousGuiActionPolicy.RESET_OPERATION, DangerousGuiActionPolicy.RESET_TOKEN));
        assertFalse(DangerousGuiActionPolicy.confirmed(DangerousGuiActionPolicy.resetConfirmationData(), GuiClick.SHIFT_LEFT, DangerousGuiActionPolicy.RESET_OPERATION, DangerousGuiActionPolicy.RESET_TOKEN));
        assertFalse(DangerousGuiActionPolicy.confirmed(DangerousGuiActionPolicy.deleteConfirmationData(), GuiClick.LEFT, DangerousGuiActionPolicy.RESET_OPERATION, DangerousGuiActionPolicy.RESET_TOKEN));
    }
}
