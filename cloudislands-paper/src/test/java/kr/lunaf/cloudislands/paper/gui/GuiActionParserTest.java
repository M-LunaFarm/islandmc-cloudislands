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
        assertTrue(GuiActionParser.parse("island.log.detail", Map.of("actorUuid", "00000000-0000-0000-0000-000000000000")).isEmpty());
        assertTrue(GuiActionParser.parse("island.mission.complete", Map.of("missionKey", "")).isEmpty());
        assertTrue(GuiActionParser.parse("island.upgrade.purchase", Map.of("upgradeKey", "")).isEmpty());
        assertTrue(GuiActionParser.parse(DangerousGuiActionPolicy.RESET_CONFIRM_ACTION, Map.of(DangerousGuiActionPolicy.OPERATION_KEY, "reset", DangerousGuiActionPolicy.TOKEN_KEY, "NOPE")).isEmpty());
        assertTrue(GuiActionParser.parse(DangerousGuiActionPolicy.DELETE_CONFIRM_ACTION, Map.of(DangerousGuiActionPolicy.OPERATION_KEY, "delete")).isEmpty());
        assertTrue(GuiActionParser.parse("island.member.remove.prepare", Map.of("playerUuid", "not-a-uuid")).isEmpty());
    }

    @Test
    void parsesOverviewNavigationIntoTypedActions() {
        GuiAction main = GuiActionParser.parse("island.main.open", Map.of()).orElseThrow();
        GuiAction info = GuiActionParser.parse("island.info.open", Map.of()).orElseThrow();
        GuiAction list = GuiActionParser.parse("island.list.open", Map.of()).orElseThrow();

        assertTrue(main instanceof GuiAction.MainOpen);
        assertEquals(Map.of(), main.data());
        assertTrue(info instanceof GuiAction.InfoOpen);
        assertEquals(Map.of(), info.data());
        assertTrue(list instanceof GuiAction.IslandListOpen);
        assertEquals(Map.of(), list.data());
    }

    @Test
    void parsesChatLogNavigationIntoTypedActions() {
        GuiAction chat = GuiActionParser.parse("island.chat.open", Map.of()).orElseThrow();
        GuiAction logs = GuiActionParser.parse("island.logs.open", Map.of()).orElseThrow();
        GuiAction list = GuiActionParser.parse("island.logs.list", Map.of()).orElseThrow();

        assertTrue(chat instanceof GuiAction.ChatOpen);
        assertEquals(Map.of(), chat.data());
        assertTrue(logs instanceof GuiAction.LogsOpen);
        assertEquals(Map.of(), logs.data());
        assertTrue(list instanceof GuiAction.LogsList);
        assertEquals(Map.of(), list.data());
    }

    @Test
    void parsesEnvironmentAndSettingsNavigationIntoTypedActions() {
        assertNoPayloadType("island.biome.open", GuiAction.NoPayloadType.BIOME_OPEN);
        assertNoPayloadType("island.biome.show", GuiAction.NoPayloadType.BIOME_SHOW);
        assertNoPayloadType("island.limits.open", GuiAction.NoPayloadType.LIMITS_OPEN);
        assertNoPayloadType("island.limits.list", GuiAction.NoPayloadType.LIMITS_LIST);
        assertNoPayloadType("island.settings.open", GuiAction.NoPayloadType.SETTINGS_OPEN);
        assertNoPayloadType("island.public.toggle", GuiAction.NoPayloadType.PUBLIC_TOGGLE);
        assertNoPayloadType("island.lock.toggle", GuiAction.NoPayloadType.LOCK_TOGGLE);
        assertNoPayloadType("island.flags.open", GuiAction.NoPayloadType.FLAGS_OPEN);
        assertNoPayloadType("island.flags.list", GuiAction.NoPayloadType.FLAGS_LIST);
    }

    @Test
    void parsesBankSnapshotAndProgressionNavigationIntoTypedActions() {
        assertNoPayloadType("island.bank.open", GuiAction.NoPayloadType.BANK_OPEN);
        assertNoPayloadType("island.snapshots.open", GuiAction.NoPayloadType.SNAPSHOTS_OPEN);
        assertNoPayloadType("island.snapshots.list", GuiAction.NoPayloadType.SNAPSHOTS_LIST);
        assertNoPayloadType("island.ranking.open", GuiAction.NoPayloadType.RANKING_OPEN);
        assertNoPayloadType("island.level.recalculate", GuiAction.NoPayloadType.LEVEL_RECALCULATE);
        assertNoPayloadType("island.level.show", GuiAction.NoPayloadType.LEVEL_SHOW);
        assertNoPayloadType("island.worth.show", GuiAction.NoPayloadType.WORTH_SHOW);
        assertNoPayloadType("island.upgrades.open", GuiAction.NoPayloadType.UPGRADES_OPEN);
        assertNoPayloadType("island.upgrades.list", GuiAction.NoPayloadType.UPGRADES_LIST);
    }

    @Test
    void parsesHomeWarpAndVisitNavigationIntoTypedActions() {
        assertNoPayloadType("island.homes.open", GuiAction.NoPayloadType.HOMES_OPEN);
        assertNoPayloadType("island.warps.open", GuiAction.NoPayloadType.WARPS_OPEN);
        assertNoPayloadType("island.visit.open", GuiAction.NoPayloadType.VISIT_OPEN);
        assertNoPayloadType("island.visit.random", GuiAction.NoPayloadType.VISIT_RANDOM);
        assertNoPayloadType("island.visit.public.open", GuiAction.NoPayloadType.VISIT_PUBLIC_OPEN);
    }

    @Test
    void parsesLifecycleAndMembershipNavigationIntoTypedActions() {
        assertNoPayloadType("island.create.open", GuiAction.NoPayloadType.CREATE_OPEN);
        assertNoPayloadType("island.danger.open", GuiAction.NoPayloadType.DANGER_OPEN);
        assertNoPayloadType("island.danger.reset.prepare", GuiAction.NoPayloadType.DANGER_RESET_PREPARE);
        assertNoPayloadType("island.danger.delete.prepare", GuiAction.NoPayloadType.DANGER_DELETE_PREPARE);
        assertNoPayloadType("island.members.open", GuiAction.NoPayloadType.MEMBERS_OPEN);
        assertNoPayloadType("island.member.role", GuiAction.NoPayloadType.MEMBER_ROLE);
        assertNoPayloadType("island.member.invite", GuiAction.NoPayloadType.MEMBER_INVITE);
        assertNoPayloadType("island.member.invite.help", GuiAction.NoPayloadType.MEMBER_INVITE_HELP);
        assertNoPayloadType("island.member.list", GuiAction.NoPayloadType.MEMBER_LIST);
        assertNoPayloadType("island.invites.open", GuiAction.NoPayloadType.INVITES_OPEN);
        assertNoPayloadType("island.bans.open", GuiAction.NoPayloadType.BANS_OPEN);
        assertNoPayloadType("island.bans.list", GuiAction.NoPayloadType.BANS_LIST);
        assertNoPayloadType("island.permissions.open", GuiAction.NoPayloadType.PERMISSIONS_OPEN);
        assertNoPayloadType("island.permissions.list", GuiAction.NoPayloadType.PERMISSIONS_LIST);
        assertNoPayloadType("island.permissions.save", GuiAction.NoPayloadType.PERMISSIONS_SAVE);
        assertNoPayloadType("island.permissions.reset", GuiAction.NoPayloadType.PERMISSIONS_RESET);
        assertNoPayloadType("island.roles.open", GuiAction.NoPayloadType.ROLES_OPEN);
        assertNoPayloadType("island.roles.list", GuiAction.NoPayloadType.ROLES_LIST);
    }

    @Test
    void parsesCreateAndDangerConfirmActionsIntoTypedActions() {
        GuiAction create = GuiActionParser.parse("island.create", Map.of("templateId", " starter ")).orElseThrow();
        GuiAction reset = GuiActionParser.parse(DangerousGuiActionPolicy.RESET_CONFIRM_ACTION, DangerousGuiActionPolicy.resetConfirmationData()).orElseThrow();
        GuiAction delete = GuiActionParser.parse(DangerousGuiActionPolicy.DELETE_CONFIRM_ACTION, DangerousGuiActionPolicy.deleteConfirmationData()).orElseThrow();

        assertTrue(create instanceof GuiAction.IslandCreate);
        assertEquals(Map.of("templateId", "starter"), create.data());
        assertTrue(reset instanceof GuiAction.DangerResetConfirm);
        assertEquals(DangerousGuiActionPolicy.resetConfirmationData(), reset.data());
        assertTrue(delete instanceof GuiAction.DangerDeleteConfirm);
        assertEquals(DangerousGuiActionPolicy.deleteConfirmationData(), delete.data());
    }

    @Test
    void preservesRegisteredRawActions() {
        GuiAction action = GuiActionParser.parse("gui.close", Map.of()).orElseThrow();

        assertTrue(action instanceof GuiAction.Raw);
        assertEquals("gui.close", action.actionId());
        assertEquals(Map.of(), action.data());
    }

    @Test
    void parsesAdminNodeActionsIntoTypedActions() {
        GuiAction open = GuiActionParser.parse("admin.node.open", Map.of("nodeId", " island-2 ")).orElseThrow();
        GuiAction kickAll = GuiActionParser.parse("admin.node.kickall.confirm", Map.of(
            "nodeId", " island-2 ",
            "reason", " drain ",
            ConfirmationTokenPolicy.TOKEN_KEY, ConfirmationTokenPolicy.token("admin.node.kickall.confirm")
        )).orElseThrow();
        GuiAction prompt = GuiActionParser.parse("admin.island.migrate.prompt", Map.of("nodeId", " island-3 ")).orElseThrow();

        assertTrue(open instanceof GuiAction.AdminNodeAction);
        assertEquals(GuiAction.AdminNodeActionType.OPEN, ((GuiAction.AdminNodeAction) open).type());
        assertEquals(Map.of("nodeId", "island-2"), open.data());
        assertTrue(kickAll instanceof GuiAction.AdminNodeAction);
        assertEquals(GuiAction.AdminNodeActionType.KICKALL_CONFIRM, ((GuiAction.AdminNodeAction) kickAll).type());
        assertEquals(Map.of(
            "nodeId", "island-2",
            "reason", "drain",
            ConfirmationTokenPolicy.TOKEN_KEY, ConfirmationTokenPolicy.token("admin.node.kickall.confirm")
        ), kickAll.data());
        assertTrue(prompt instanceof GuiAction.AdminIslandPrompt);
        assertEquals(GuiAction.AdminIslandPromptType.MIGRATE, ((GuiAction.AdminIslandPrompt) prompt).type());
        assertEquals(Map.of("nodeId", "island-3"), prompt.data());
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
    void parsesLogDetailIntoTypedAction() {
        GuiAction action = GuiActionParser.parse("island.log.detail", Map.of(
            "action", " BANK_DEPOSIT ",
            "actorUuid", " 00000000-0000-0000-0000-000000000000 ",
            "createdAt", " now ",
            "payload", " amount=100 "
        )).orElseThrow();

        assertTrue(action instanceof GuiAction.LogDetail);
        assertEquals("island.log.detail", action.actionId());
        assertEquals(Map.of(
            "action", "BANK_DEPOSIT",
            "actorUuid", "00000000-0000-0000-0000-000000000000",
            "createdAt", "now",
            "payload", "amount=100"
        ), action.data());
    }

    @Test
    void parsesProgressionListsIntoTypedActions() {
        GuiAction ranking = GuiActionParser.parse("island.ranking.list", Map.of("kind", " worth ")).orElseThrow();
        GuiAction missions = GuiActionParser.parse("island.missions.open", Map.of("kind", " challenge ")).orElseThrow();

        assertTrue(ranking instanceof GuiAction.RankingList);
        assertEquals(Map.of("kind", "WORTH"), ranking.data());
        assertTrue(((GuiAction.RankingList) ranking).worth());
        assertTrue(missions instanceof GuiAction.MissionsOpen);
        assertEquals(Map.of("kind", "CHALLENGE"), missions.data());
    }

    @Test
    void parsesProgressionMutationsIntoTypedActions() {
        GuiAction mission = GuiActionParser.parse("island.mission.complete", Map.of("missionKey", " starter ", "kind", " challenge ", "label", " 섬 챌린지 ")).orElseThrow();
        GuiAction upgrade = GuiActionParser.parse("island.upgrade.purchase", Map.of("upgradeKey", " max-members ")).orElseThrow();

        assertTrue(mission instanceof GuiAction.MissionComplete);
        assertEquals("island.mission.complete", mission.actionId());
        assertEquals(Map.of("missionKey", "starter", "kind", "CHALLENGE", "label", "섬 챌린지"), mission.data());
        assertTrue(upgrade instanceof GuiAction.UpgradePurchase);
        assertEquals("island.upgrade.purchase", upgrade.actionId());
        assertEquals(Map.of("upgradeKey", "max-members"), upgrade.data());
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

    private static void assertNoPayloadType(String actionId, GuiAction.NoPayloadType expectedType) {
        GuiAction action = GuiActionParser.parse(actionId, Map.of()).orElseThrow();

        assertTrue(action instanceof GuiAction.NoPayload);
        assertEquals(expectedType, ((GuiAction.NoPayload) action).type());
        assertEquals(Map.of(), action.data());
    }
}
