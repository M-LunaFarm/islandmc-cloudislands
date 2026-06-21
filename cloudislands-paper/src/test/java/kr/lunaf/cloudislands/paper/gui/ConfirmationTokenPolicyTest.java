package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmationTokenPolicyTest {
    @Test
    void addsActionSpecificTokensToConfirmationData() {
        Map<String, String> data = ConfirmationTokenPolicy.withToken("island.member.remove.confirm", Map.of("playerUuid", "abc"));

        assertEquals("abc", data.get("playerUuid"));
        assertEquals("CONFIRM:island.member.remove.confirm", data.get(ConfirmationTokenPolicy.TOKEN_KEY));
        assertTrue(ConfirmationTokenPolicy.confirmed("island.member.remove.confirm", data, GuiClick.LEFT));
    }

    @Test
    void rejectsConfirmActionsWithoutMatchingLeftClickToken() {
        Map<String, String> data = ConfirmationTokenPolicy.withToken("island.snapshot.restore.confirm", Map.of("snapshotNo", "4"));

        assertFalse(ConfirmationTokenPolicy.confirmed("island.snapshot.restore.confirm", Map.of("snapshotNo", "4"), GuiClick.LEFT));
        assertFalse(ConfirmationTokenPolicy.confirmed("island.snapshot.restore.confirm", data, GuiClick.RIGHT));
        assertFalse(ConfirmationTokenPolicy.confirmed("island.snapshot.restore.confirm", data, GuiClick.SHIFT_LEFT));
        assertFalse(ConfirmationTokenPolicy.confirmed("island.member.remove.confirm", data, GuiClick.LEFT));
    }

    @Test
    void confirmsTypedGuiActionsWithoutReReadingPayloadMapsAtCallSites() {
        GuiAction action = new GuiAction.MemberRemoval(
            GuiAction.MemberRemovalType.CONFIRM,
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            ConfirmationTokenPolicy.token(ConfirmationTokenPolicy.MEMBER_REMOVE_CONFIRM_ACTION)
        );

        assertTrue(ConfirmationTokenPolicy.confirmed(action, GuiClick.LEFT));
        assertFalse(ConfirmationTokenPolicy.confirmed(action, GuiClick.RIGHT));
        assertFalse(ConfirmationTokenPolicy.confirmed(new GuiAction.MemberRemoval(
            GuiAction.MemberRemovalType.CONFIRM,
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            ConfirmationTokenPolicy.token(ConfirmationTokenPolicy.WARP_DELETE_CONFIRM_ACTION)
        ), GuiClick.LEFT));
    }

    @Test
    void nonConfirmedActionsRemainPassThrough() {
        assertTrue(ConfirmationTokenPolicy.confirmed("island.permissions.open", Map.of(), GuiClick.RIGHT));
        assertTrue(ConfirmationTokenPolicy.confirmed(new GuiAction.NoPayload(GuiAction.NoPayloadType.PERMISSIONS_OPEN), GuiClick.RIGHT));
        assertFalse(ConfirmationTokenPolicy.requiresToken("island.permissions.open"));
    }
}
