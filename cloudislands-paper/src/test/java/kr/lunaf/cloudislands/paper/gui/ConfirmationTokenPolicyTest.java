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

        GuiAction reviewReport = new GuiAction.ReviewReport(
            GuiAction.ReviewReportType.CONFIRM,
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            "ReviewPlayer",
            ConfirmationTokenPolicy.token(ConfirmationTokenPolicy.REVIEW_REPORT_CONFIRM_ACTION)
        );
        assertTrue(ConfirmationTokenPolicy.confirmed(reviewReport, GuiClick.LEFT));
        assertFalse(ConfirmationTokenPolicy.confirmed(reviewReport, GuiClick.SHIFT_LEFT));

        GuiAction jobCancel = new GuiAction.AdminJobCancel(
            GuiAction.AdminJobCancelType.CONFIRM,
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
            0,
            ConfirmationTokenPolicy.token(ConfirmationTokenPolicy.ADMIN_JOB_CANCEL_CONFIRM_ACTION)
        );
        assertTrue(ConfirmationTokenPolicy.confirmed(jobCancel, GuiClick.LEFT));
        assertFalse(ConfirmationTokenPolicy.confirmed(jobCancel, GuiClick.RIGHT));

        GuiAction routeClear = new GuiAction.AdminRouteClear(
            GuiAction.AdminRouteClearType.CONFIRM,
            UUID.fromString("00000000-0000-0000-0000-000000000005"),
            UUID.fromString("00000000-0000-0000-0000-000000000006"),
            0,
            ConfirmationTokenPolicy.token(ConfirmationTokenPolicy.ADMIN_ROUTE_CLEAR_CONFIRM_ACTION)
        );
        assertTrue(ConfirmationTokenPolicy.confirmed(routeClear, GuiClick.LEFT));
        assertFalse(ConfirmationTokenPolicy.confirmed(routeClear, GuiClick.SHIFT_RIGHT));

        GuiAction migrationRollback = new GuiAction.AdminMigrationRollback(
            ConfirmationTokenPolicy.token(ConfirmationTokenPolicy.ADMIN_MIGRATION_ROLLBACK_CONFIRM_ACTION)
        );
        assertTrue(ConfirmationTokenPolicy.confirmed(migrationRollback, GuiClick.LEFT));
        assertFalse(ConfirmationTokenPolicy.confirmed(migrationRollback, GuiClick.RIGHT));

        GuiAction templateToggle = new GuiAction.AdminTemplateToggle(
            GuiAction.AdminTemplateToggleType.CONFIRM,
            "starter",
            false,
            0,
            2,
            ConfirmationTokenPolicy.token(ConfirmationTokenPolicy.ADMIN_TEMPLATE_TOGGLE_CONFIRM_ACTION)
        );
        assertTrue(ConfirmationTokenPolicy.confirmed(templateToggle, GuiClick.LEFT));
        assertFalse(ConfirmationTokenPolicy.confirmed(templateToggle, GuiClick.RIGHT));

        GuiAction addonToggle = new GuiAction.AdminAddonToggle(
            GuiAction.AdminAddonToggleType.CONFIRM,
            "machines",
            false,
            0,
            ConfirmationTokenPolicy.token(ConfirmationTokenPolicy.ADMIN_ADDON_TOGGLE_CONFIRM_ACTION)
        );
        assertTrue(ConfirmationTokenPolicy.confirmed(addonToggle, GuiClick.LEFT));
        assertFalse(ConfirmationTokenPolicy.confirmed(addonToggle, GuiClick.RIGHT));

        GuiAction addonFeatureToggle = new GuiAction.AdminAddonFeatureToggle(
            GuiAction.AdminAddonFeatureToggleType.CONFIRM,
            "machines",
            "commands",
            false,
            0,
            0,
            ConfirmationTokenPolicy.token(ConfirmationTokenPolicy.ADMIN_ADDON_FEATURE_TOGGLE_CONFIRM_ACTION)
        );
        assertTrue(ConfirmationTokenPolicy.confirmed(addonFeatureToggle, GuiClick.LEFT));
        assertFalse(ConfirmationTokenPolicy.confirmed(addonFeatureToggle, GuiClick.RIGHT));
    }

    @Test
    void nonConfirmedActionsRemainPassThrough() {
        assertTrue(ConfirmationTokenPolicy.confirmed("island.permissions.open", Map.of(), GuiClick.RIGHT));
        assertTrue(ConfirmationTokenPolicy.confirmed(new GuiAction.NoPayload(GuiAction.NoPayloadType.PERMISSIONS_OPEN), GuiClick.RIGHT));
        assertFalse(ConfirmationTokenPolicy.requiresToken("island.permissions.open"));
    }
}
