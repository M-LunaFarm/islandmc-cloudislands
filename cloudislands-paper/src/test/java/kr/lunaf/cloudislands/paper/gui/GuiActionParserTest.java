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
        assertTrue(GuiActionParser.parse("island.role.weight.adjust", Map.of("role", "BUILDER", "weight", "-1")).isEmpty());
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
    void parsesSnapshotActionsIntoTypedActions() {
        GuiAction create = GuiActionParser.parse("island.snapshot.create", Map.of("reason", " manual save ")).orElseThrow();
        GuiAction restore = GuiActionParser.parse("island.snapshot.restore.prepare", Map.of("snapshotNo", "7")).orElseThrow();

        assertTrue(create instanceof GuiAction.SnapshotCreate);
        assertEquals("island.snapshot.create", create.actionId());
        assertEquals(Map.of("reason", "manual save"), create.data());
        assertTrue(restore instanceof GuiAction.SnapshotRestore);
        assertEquals("island.snapshot.restore.prepare", restore.actionId());
        assertEquals(Map.of("snapshotNo", "7"), restore.data());
    }

    @Test
    void parsesLimitSetIntoTypedAction() {
        GuiAction action = GuiActionParser.parse("island.limit.set", Map.of("limitKey", " redstone-blocks ", "value", "12")).orElseThrow();

        assertTrue(action instanceof GuiAction.LimitSet);
        assertEquals("island.limit.set", action.actionId());
        assertEquals(Map.of("limitKey", "REDSTONE_BLOCKS", "value", "12"), action.data());
    }

    @Test
    void parsesRoleWeightAdjustIntoTypedAction() {
        GuiAction action = GuiActionParser.parse("island.role.weight.adjust", Map.of("role", "builder", "weight", "42", "displayName", " Builder ")).orElseThrow();

        assertTrue(action instanceof GuiAction.RoleWeightAdjust);
        assertEquals("island.role.weight.adjust", action.actionId());
        assertEquals(Map.of("role", "BUILDER", "weight", "42", "displayName", "Builder"), action.data());
    }

    @Test
    void parsesBiomeSetIntoTypedAction() {
        GuiAction action = GuiActionParser.parse("island.biome.set", Map.of("biomeKey", " minecraft:plains ")).orElseThrow();

        assertTrue(action instanceof GuiAction.BiomeSet);
        assertEquals("island.biome.set", action.actionId());
        assertEquals(Map.of("biomeKey", "minecraft:plains"), action.data());
    }

    @Test
    void parsesFlagSetIntoTypedAction() {
        GuiAction action = GuiActionParser.parse("island.flag.set", Map.of("flag", " public-warps ")).orElseThrow();

        assertTrue(action instanceof GuiAction.FlagSet);
        assertEquals("island.flag.set", action.actionId());
        assertEquals(Map.of("flag", "PUBLIC_WARPS"), action.data());
    }

    @Test
    void parsesVisitTargetIntoTypedAction() {
        GuiAction action = GuiActionParser.parse("island.visit.target", Map.of("target", " 00000000-0000-0000-0000-000000000000 ")).orElseThrow();

        assertTrue(action instanceof GuiAction.VisitTarget);
        assertEquals("island.visit.target", action.actionId());
        assertEquals(Map.of("target", "00000000-0000-0000-0000-000000000000"), action.data());
    }

    @Test
    void parsesHomeAndWarpActionsIntoTypedActions() {
        GuiAction home = GuiActionParser.parse("island.home", Map.of("homeName", " default ")).orElseThrow();
        GuiAction homeSet = GuiActionParser.parse("island.home.set", Map.of("homeName", " farm ")).orElseThrow();
        GuiAction warp = GuiActionParser.parse("island.warp.teleport", Map.of("warpName", " shop ", "islandId", "00000000-0000-0000-0000-000000000000")).orElseThrow();
        GuiAction toggle = GuiActionParser.parse("island.warp.public.toggle", Map.of("warpName", " shop ", "publicAccess", "false")).orElseThrow();
        GuiAction delete = GuiActionParser.parse("island.warp.delete.prepare", Map.of("warpName", " shop ")).orElseThrow();
        GuiAction confirm = GuiActionParser.parse("island.warp.delete.confirm", Map.of("warpName", " shop ", ConfirmationTokenPolicy.TOKEN_KEY, ConfirmationTokenPolicy.token("island.warp.delete.confirm"))).orElseThrow();

        assertTrue(home instanceof GuiAction.HomeTeleport);
        assertEquals(Map.of("homeName", "default"), home.data());
        assertTrue(homeSet instanceof GuiAction.HomeSet);
        assertEquals(Map.of("homeName", "farm"), homeSet.data());
        assertTrue(warp instanceof GuiAction.WarpTeleport);
        assertEquals(Map.of("warpName", "shop", "islandId", "00000000-0000-0000-0000-000000000000"), warp.data());
        assertTrue(toggle instanceof GuiAction.WarpAccess);
        assertEquals(Map.of("warpName", "shop", "publicAccess", "false"), toggle.data());
        assertTrue(((GuiAction.WarpAccess) toggle).targetPublicAccess());
        assertTrue(delete instanceof GuiAction.WarpDelete);
        assertEquals(Map.of("warpName", "shop"), delete.data());
        assertTrue(confirm instanceof GuiAction.WarpDelete);
        assertEquals(Map.of("warpName", "shop", ConfirmationTokenPolicy.TOKEN_KEY, ConfirmationTokenPolicy.token("island.warp.delete.confirm")), confirm.data());
    }

    @Test
    void parsesInviteActionsIntoTypedActions() {
        GuiAction accept = GuiActionParser.parse("island.invite.accept", Map.of("inviteId", "00000000-0000-0000-0000-000000000000")).orElseThrow();
        GuiAction decline = GuiActionParser.parse("island.invite.decline", Map.of("inviteId", "00000000-0000-0000-0000-000000000001")).orElseThrow();

        assertTrue(accept instanceof GuiAction.InviteAction);
        assertEquals("island.invite.accept", accept.actionId());
        assertEquals(Map.of("inviteId", "00000000-0000-0000-0000-000000000000"), accept.data());
        assertTrue(((GuiAction.InviteAction) accept).accept());
        assertTrue(decline instanceof GuiAction.InviteAction);
        assertEquals("island.invite.decline", decline.actionId());
        assertEquals(Map.of("inviteId", "00000000-0000-0000-0000-000000000001"), decline.data());
    }

    @Test
    void parsesMemberPageAndDetailIntoTypedActions() {
        GuiAction page = GuiActionParser.parse("island.members.page", Map.of("page", "2")).orElseThrow();
        GuiAction detail = GuiActionParser.parse("island.member.detail", Map.of(
            "playerUuid", "00000000-0000-0000-0000-000000000000",
            "playerName", " Builder ",
            "role", " MEMBER ",
            "presenceState", " RECENT_ACTIVITY ",
            "lastSeenAt", " now "
        )).orElseThrow();

        assertTrue(page instanceof GuiAction.MemberPage);
        assertEquals(Map.of("page", "2"), page.data());
        assertTrue(detail instanceof GuiAction.MemberDetail);
        assertEquals(Map.of(
            "playerUuid", "00000000-0000-0000-0000-000000000000",
            "playerName", "Builder",
            "role", "MEMBER",
            "presenceState", "RECENT_ACTIVITY",
            "lastSeenAt", "now"
        ), detail.data());
    }

    @Test
    void parsesMemberRoleAndBanPardonConfirmationsIntoTypedActions() {
        GuiAction promote = GuiActionParser.parse("island.member.promote.prepare", Map.of("playerUuid", "00000000-0000-0000-0000-000000000000")).orElseThrow();
        GuiAction demote = GuiActionParser.parse("island.member.demote", Map.of("playerUuid", "00000000-0000-0000-0000-000000000001", ConfirmationTokenPolicy.TOKEN_KEY, ConfirmationTokenPolicy.token("island.member.demote"))).orElseThrow();
        GuiAction removal = GuiActionParser.parse("island.member.remove.confirm", Map.of("playerUuid", "00000000-0000-0000-0000-000000000003", ConfirmationTokenPolicy.TOKEN_KEY, ConfirmationTokenPolicy.token("island.member.remove.confirm"))).orElseThrow();
        GuiAction pardon = GuiActionParser.parse("island.ban.pardon.confirm", Map.of("playerUuid", "00000000-0000-0000-0000-000000000002", ConfirmationTokenPolicy.TOKEN_KEY, ConfirmationTokenPolicy.token("island.ban.pardon.confirm"))).orElseThrow();

        assertTrue(promote instanceof GuiAction.MemberRoleChange);
        assertEquals(Map.of("playerUuid", "00000000-0000-0000-0000-000000000000"), promote.data());
        assertTrue(((GuiAction.MemberRoleChange) promote).promote());
        assertTrue(demote instanceof GuiAction.MemberRoleChange);
        assertEquals(Map.of("playerUuid", "00000000-0000-0000-0000-000000000001", ConfirmationTokenPolicy.TOKEN_KEY, ConfirmationTokenPolicy.token("island.member.demote")), demote.data());
        assertTrue(((GuiAction.MemberRoleChange) demote).confirmation());
        assertTrue(removal instanceof GuiAction.MemberRemoval);
        assertEquals(Map.of("playerUuid", "00000000-0000-0000-0000-000000000003", ConfirmationTokenPolicy.TOKEN_KEY, ConfirmationTokenPolicy.token("island.member.remove.confirm")), removal.data());
        assertTrue(pardon instanceof GuiAction.BanPardon);
        assertEquals(Map.of("playerUuid", "00000000-0000-0000-0000-000000000002", ConfirmationTokenPolicy.TOKEN_KEY, ConfirmationTokenPolicy.token("island.ban.pardon.confirm")), pardon.data());
    }

    @Test
    void rejectsUnregisteredActionIdsInsteadOfExecutingRawMaps() {
        assertTrue(GuiActionParser.parse("island.member.remvoe", Map.of("playerUuid", "00000000-0000-0000-0000-000000000000")).isEmpty());
        assertTrue(GuiActionParser.parse("island.unknown.open", Map.of()).isEmpty());
        assertTrue(GuiActionParser.parse("island.bank.withdraw", Map.of("amount", "0")).isEmpty());
        assertTrue(GuiActionParser.parse("island.bank.deposit", Map.of("amount", "abc")).isEmpty());
        assertTrue(GuiActionParser.parse("island.snapshot.restore.prepare", Map.of("snapshotNo", "0")).isEmpty());
        assertTrue(GuiActionParser.parse("island.snapshot.restore.confirm", Map.of("snapshotNo", "abc")).isEmpty());
        assertTrue(GuiActionParser.parse("island.limit.set", Map.of("limitKey", "", "value", "1")).isEmpty());
        assertTrue(GuiActionParser.parse("island.limit.set", Map.of("limitKey", "ENTITY", "value", "-1")).isEmpty());
        assertTrue(GuiActionParser.parse("island.limit.set", Map.of("limitKey", "ENTITY", "value", "abc")).isEmpty());
        assertTrue(GuiActionParser.parse("island.biome.set", Map.of("biomeKey", "")).isEmpty());
        assertTrue(GuiActionParser.parse("island.flag.set", Map.of("flag", "")).isEmpty());
        assertTrue(GuiActionParser.parse("island.flag.set", Map.of("flag", "NOPE")).isEmpty());
        assertTrue(GuiActionParser.parse("island.visit.target", Map.of("target", "")).isEmpty());
        assertTrue(GuiActionParser.parse("island.home", Map.of("homeName", "")).isEmpty());
        assertTrue(GuiActionParser.parse("island.home.set", Map.of()).isEmpty());
        assertTrue(GuiActionParser.parse("island.warp.teleport", Map.of("warpName", "")).isEmpty());
        assertTrue(GuiActionParser.parse("island.warp.teleport", Map.of("warpName", "shop", "islandId", "nope")).isEmpty());
        assertTrue(GuiActionParser.parse("island.warp.public.toggle", Map.of("warpName", "shop", "publicAccess", "maybe")).isEmpty());
        assertTrue(GuiActionParser.parse("island.warp.delete.prepare", Map.of("warpName", "")).isEmpty());
        assertTrue(GuiActionParser.parse("island.invite.accept", Map.of("inviteId", "")).isEmpty());
        assertTrue(GuiActionParser.parse("island.invite.decline", Map.of("inviteId", "nope")).isEmpty());
        assertTrue(GuiActionParser.parse("island.members.page", Map.of("page", "abc")).isEmpty());
        assertTrue(GuiActionParser.parse("island.member.detail", Map.of("playerUuid", "")).isEmpty());
        assertTrue(GuiActionParser.parse("island.member.detail", Map.of("playerUuid", "nope")).isEmpty());
        assertTrue(GuiActionParser.parse("island.member.promote.prepare", Map.of("playerUuid", "")).isEmpty());
        assertTrue(GuiActionParser.parse("island.member.demote", Map.of("playerUuid", "nope")).isEmpty());
        assertTrue(GuiActionParser.parse("island.ban.pardon.prepare", Map.of("playerUuid", "")).isEmpty());
        assertTrue(GuiActionParser.parse("island.ban.pardon.confirm", Map.of("playerUuid", "nope")).isEmpty());
    }
}
