package kr.lunaf.cloudislands.velocity.command;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kr.lunaf.cloudislands.protocol.command.IslandPlayerCommandRegistry;
import kr.lunaf.cloudislands.protocol.command.CommandExecutionTarget;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IslandCommandCatalogTest {
    @Test
    void invalidPermissionAndFlagTokensNeverFallbackToDestructiveDefaults() throws Exception {
        String support = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityCommandSupport.java"));
        String membership = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerMembershipCommandDispatcher.java"));

        assertFalse(support.contains("return IslandPermission.BUILD;"), "unknown permission tokens must not mutate BUILD");
        assertFalse(support.contains("return kr.lunaf.cloudislands.api.model.IslandFlag.FLY;"), "unknown flag tokens must not mutate FLY");
        assertTrue(support.contains("Boolean parseExplicitToggle(String value)"), "permission mutations must distinguish invalid booleans from explicit false");
        assertTrue(membership.contains("if (flag == null || enabled == null)"), "invalid flag mutation input must stop before Core");
        assertTrue(membership.contains("if (roleKey.isBlank() || permission == null || allowed == null)"), "invalid role permission input must stop before Core");
        assertTrue(membership.contains("if (permission == null || allowed == null)"), "invalid player override input must stop before Core");
    }

    @Test
    void paperLocalStateCommandsBypassProxyExecution() throws Exception {
        String plugin = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/CloudIslandsVelocityPlugin.java"));
        String forwarder = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/PaperLocalCommandForwarder.java"));

        assertTrue(plugin.contains("onCommandExecute(CommandExecuteEvent event)"), "Velocity must intercept stateful commands before its registered global /is executor");
        assertTrue(plugin.contains("PaperLocalCommandForwarder.shouldForward(event.getCommand(), commandAliases)"), "forwarding must honor configured root aliases");
        assertTrue(plugin.contains("player.getCurrentServer().isEmpty()"), "forwarding must require an attached Paper backend");
        assertTrue(plugin.contains("CommandExecuteEvent.CommandResult.forwardToServer()"), "stateful commands must execute through the backend Paper command boundary");
        for (String command : List.of("deposit", "withdraw", "sethome", "setwarp", "warehouse-deposit", "warehouse-withdraw", "chest", "vault", "fly")) {
            assertTrue(forwarder.contains("\"" + command + "\""), command + " must be delegated to Paper because it depends on economy, inventory, location, GUI, or player state");
        }
    }

    @Test
    void currentIslandMutationsResolveNilBeforeCallingCore() throws Exception {
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerMembershipActions.java"));
        String routing = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerRoutingActions.java"));

        for (String unsafeCall : List.of(
                "createInvite(islandId,",
                "setRole(islandId,",
                "transferOwnership(islandId,",
                "removeMember(islandId,",
                "banVisitor(islandId,",
                "bans(islandId)",
                "pardonVisitor(islandId,",
                "kickVisitor(islandId,",
                "setPublicAccess(islandId,",
                "homes(islandId)",
                "setHome(islandId,",
                "setLocked(islandId,",
                "setPermission(islandId,",
                "upsertRole(islandId,",
                "resetRole(islandId,")) {
            assertFalse(actions.contains(unsafeCall), unsafeCall + " must resolve the current-island sentinel before Core mutation");
        }
        assertTrue(actions.contains("withResolvedIsland(player, islandId"), "Velocity membership actions must share the current-island resolution boundary");
        for (String unsafeCall : List.of(
                "deleteIsland(player.getUniqueId(), islandId)",
                "resetIsland(islandId,",
                "islandBiome(islandId)",
                "setBiome(islandId,",
                "createWarpTicket(player.getUniqueId(), targetIslandId,",
                "warps(islandId)",
                "setWarp(islandId,",
                "deleteWarp(islandId,",
                "setWarpPublicAccess(islandId,")) {
            assertFalse(routing.contains(unsafeCall), unsafeCall + " must resolve the current-island sentinel before Core routing or mutation");
        }
        assertTrue(routing.contains("showResolvedIsland(player, islandId"), "Velocity island views must share the current-island resolution boundary");
    }

    @Test
    void everySharedVelocityAliasHasAnExecutionBranch() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerCommandDispatcher.java"))
            + Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerMembershipCommandDispatcher.java"))
            + Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityCommandSupport.java"));

        IslandPlayerCommandRegistry.playerDescriptors().stream()
            .filter(descriptor -> descriptor.executionTarget() != CommandExecutionTarget.PAPER)
            .flatMap(descriptor -> descriptor.aliases().stream())
            .forEach(alias -> assertTrue(dispatcher.contains("\"" + alias + "\""), alias + " is advertised for Velocity but has no dispatcher branch"));
    }

    @Test
    void locationMutationsNeverPersistSyntheticProxyCoordinates() throws Exception {
        String plugin = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/CloudIslandsVelocityPlugin.java"));
        String memberships = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerMembershipActions.java"));
        String routing = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerRoutingActions.java"));

        assertTrue(plugin.contains("CommandExecuteEvent.CommandResult.denied()"), "Paper-local commands must be denied when no backend server exists");
        assertFalse(memberships.contains("new IslandLocation(\"ci_shard_001\""), "Velocity must not invent a home location");
        assertFalse(routing.contains("new IslandLocation(\"ci_shard_001\""), "Velocity must not invent a warp location");
        assertFalse(memberships.contains("homeWarpCommands().setHome("), "Velocity must not persist homes without a Paper location");
        assertFalse(routing.contains("homeWarpCommands().setWarp("), "Velocity must not persist warps without a Paper location");
    }

    @Test
    void inventoryMutationsNeverChangeWarehouseFromTheProxy() throws Exception {
        String progression = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerProgressionActions.java"));
        String forwarder = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/PaperLocalCommandForwarder.java"));

        assertTrue(forwarder.contains("\"warehouse-deposit\""), "warehouse deposits must be forwarded to Paper");
        assertTrue(forwarder.contains("\"warehouse-withdraw\""), "warehouse withdrawals must be forwarded to Paper");
        assertFalse(progression.contains("warehouseCommands().deposit("), "Velocity must not credit warehouse items without removing inventory");
        assertFalse(progression.contains("warehouseCommands().withdraw("), "Velocity must not debit warehouse items without delivering inventory");
        assertTrue(progression.contains("warehouse-deposit-paper-required"));
        assertTrue(progression.contains("warehouse-withdraw-paper-required"));
    }

    @Test
    void playerCommandCatalogIncludesGoalCommandsOnePerLine() {
        List<String> commands = IslandCommandCatalog.playerCommands();

        assertTrue(commands == IslandPlayerCommandRegistry.playerCommands() || commands.equals(IslandPlayerCommandRegistry.playerCommands()), "Velocity player help must consume the shared Paper/Velocity command registry");

        for (String command : List.of(
                "섬",
                "섬 도움말 [category] [page]",
                "섬 홈 [name]",
                "섬 셋홈 [name]",
                "섬 setteleport [name]",
                "섬 setspawnpoint [name]",
                "섬 랜덤방문",
                "섬 초대 <player>",
                "섬 초대수락 <플레이어|섬|inviteId>",
                "섬 초대거절 <플레이어|섬|inviteId>",
                "섬 탈퇴 confirm",
                "섬 멤버",
                "섬 추방 <player>",
                "섬 승급 <player>",
                "섬 강등 <player>",
                "섬 양도 <player>",
                "섬 신뢰 <player> [duration]",
                "섬 신뢰해제 <player>",
                "섬 밴 <player>",
                "섬 밴해제 <player>",
                "섬 밴목록",
                "섬 공개",
                "섬 비공개",
                "섬 잠금",
                "섬 잠금해제",
                "섬 설정",
                "섬 권한",
                "섬 플래그",
                "섬 워프 <name>",
                "섬 워프설정 <name> [category]",
                "섬 워프삭제 <name>",
                "섬 워프공개 <name>",
                "섬 워프비공개 <name>",
                "섬 레벨",
                "섬 가치",
                "섬 values [player|island] [limit]",
                "섬 counts [player|island] [limit]",
                "섬 블록상세 [limit]",
                "섬 랭킹 [limit]",
                "섬 top [limit]",
                "섬 leaderboard [limit]",
                "섬 ratings [limit]",
                "섬 레벨계산",
                "섬 업그레이드",
                "섬 rankup <upgradeKey>",
                "섬 크기",
                "섬 경계",
                "섬 toggle border",
                "섬 미션 [missionKey]",
                "섬 챌린지 [challengeKey]",
                "섬 채팅 <message>",
                "섬 팀채팅 <message>",
                "섬 teamchat toggle",
                "섬 로그",
                "섬 리셋 [reason]",
                "섬 삭제"
        )) {
            assertTrue(commands.contains(command), command);
        }
    }

    @Test
    void playerToggleAliasesUseTypedVelocityActions() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerCommandDispatcher.java"));
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerProgressionActions.java"));
        String suggestions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityCommandSuggestions.java"));

        assertTrue(dispatcher.contains("args[0].equalsIgnoreCase(\"toggle\")"), "Velocity must route SS2-style /is toggle commands");
        assertTrue(dispatcher.contains("playerProgression.toggleBorder"), "Velocity toggle border must use the typed progression action boundary");
        assertTrue(dispatcher.contains("playerProgression.showTeamChatMode"), "Velocity teamchat toggle must be handled before message dispatch");
        assertTrue(actions.contains("playerProfileCommands().setWorldBorderEnabled(player.getUniqueId(), enabled)"), "Velocity toggle border must write the personal player-profile preference");
        assertTrue(actions.contains("playerProfiles().profile(player.getUniqueId())"), "Velocity toggle border must read the current player preference when no explicit value is supplied");
        assertTrue(dispatcher.contains("playerProgression.setBorderColor(player, args[1])"), "Velocity must route /is border <color> and /is border-color through the personal profile action");
        assertTrue(suggestions.contains("List.of(\"border\", \"border-visible\", \"경계\", \"경계표시\")"), "Velocity toggle suggestions must expose border targets");
    }

    @Test
    void targetedCountsAndValuesPreservePaperVelocityParity() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerCommandDispatcher.java"));
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerProgressionActions.java"));
        String resolver = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/routing/VelocityTargetResolver.java"));

        assertTrue(dispatcher.contains("args[0].equalsIgnoreCase(\"counts\")"), "proxy command routing must accept canonical counts");
        assertTrue(dispatcher.contains("args.length > 1 && !isLong(args[1])"), "proxy must distinguish a target from the numeric result limit");
        assertTrue(dispatcher.contains("playerProgression.showBlockDetails(player, args[1]"), "proxy must preserve the target string for resolution");
        assertTrue(actions.contains("showBlockDetails(Player player, String target, int limit)"), "proxy actions must expose targeted block details");
        assertTrue(actions.contains("targetResolver.resolveIslandId(target)"), "proxy targeted block details must use the shared resolver");
        assertTrue(resolver.contains("playerProfileByName(target)"), "proxy target resolution must fall back from exact island name to player primary island");
    }

    @Test
    void targetedBalanceAliasesPreservePaperVelocityParity() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerCommandDispatcher.java"));
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerMembershipActions.java"));

        for (String command : List.of("섬 balance [player|island]", "섬 bal [player|island]", "섬 money [player|island]")) {
            assertTrue(IslandCommandCatalog.playerCommands().contains(command), command);
        }
        assertTrue(dispatcher.contains("args[0].equalsIgnoreCase(\"balance\")"));
        assertTrue(dispatcher.contains("playerMembership.showBank(player, args[1])"));
        assertTrue(actions.contains("showBank(Player player, String target)"));
        assertTrue(actions.contains("targetResolver.resolveIslandId(target)"));
    }

    @Test
    void targetedShowPreservesPaperVelocityParity() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerCommandDispatcher.java"));
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerRoutingActions.java"));

        assertTrue(IslandCommandCatalog.playerCommands().contains("섬 show [player|island]"));
        assertTrue(dispatcher.contains("args[0].equalsIgnoreCase(\"show\")"));
        assertTrue(dispatcher.contains("playerRouting.showIsland(player, args[1])"));
        assertTrue(actions.contains("showIsland(Player player, String target)"));
        assertTrue(actions.contains("targetResolver.resolveIslandId(target)"));
    }

    @Test
    void targetedTeamAliasesPreservePaperVelocityParity() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerMembershipCommandDispatcher.java"));
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerMembershipActions.java"));

        for (String command : List.of("섬 team [player|island]", "섬 showteam [player|island]", "섬 online [player|island]")) {
            assertTrue(IslandCommandCatalog.playerCommands().contains(command), command);
        }
        assertTrue(dispatcher.contains("args[0].equalsIgnoreCase(\"team\")"));
        assertTrue(dispatcher.contains("playerMembership.listMembers(player, args[1])"));
        assertTrue(actions.contains("listMembers(Player player, String target)"));
        assertTrue(actions.contains("withResolvedIsland(player, islandId"), "nil current-island lookup must be resolved before calling Core");
    }

    @Test
    void teleportAliasesPreservePaperVelocityParity() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerCommandDispatcher.java"));

        for (String command : List.of("섬 teleport [home]", "섬 tp [home]", "섬 go [home]")) {
            assertTrue(IslandCommandCatalog.playerCommands().contains(command), command);
        }
        assertTrue(dispatcher.contains("args[0].equalsIgnoreCase(\"teleport\")"));
        assertTrue(dispatcher.contains("playerRouting.routeHome(player, args.length > 1 ? args[1] : \"default\")"));
    }

    @Test
    void panelAndCoopsAliasesPreservePaperVelocityParity() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerCommandDispatcher.java"));
        String membership = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerMembershipCommandDispatcher.java"));

        assertTrue(dispatcher.contains("args[0].equalsIgnoreCase(\"panel\")"));
        assertTrue(membership.contains("args[0].equalsIgnoreCase(\"coops\")"));
        assertTrue(IslandCommandCatalog.playerCommands().contains("섬 coops"));
    }

    @Test
    void disbandPreservesDestructiveConfirmationOnVelocity() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerCommandDispatcher.java"));

        assertTrue(IslandCommandCatalog.playerCommands().contains("섬 disband confirm"));
        assertTrue(dispatcher.contains("args[0].equalsIgnoreCase(\"disband\")"));
        assertTrue(dispatcher.contains("if (!destructiveConfirmed(args))"));
        assertTrue(dispatcher.contains("sendDestructiveConfirmationRequired(player, \"섬 삭제 confirm\")"));
    }

    @Test
    void membershipAliasesPreservePaperVelocityParity() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerMembershipCommandDispatcher.java"));

        for (String alias : List.of("add", "remove", "expel", "leader", "leadership")) {
            assertTrue(dispatcher.contains("equalsIgnoreCase(\"" + alias + "\")"), alias);
        }
        for (String command : List.of("섬 add <player>", "섬 remove <player>", "섬 expel <player>", "섬 leader <player>", "섬 leadership <player>")) {
            assertTrue(IslandCommandCatalog.playerCommands().contains(command), command);
        }
    }

    @Test
    void langPersistsLocaleThroughCoreOnVelocity() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerCommandDispatcher.java"));
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerRoutingActions.java"));

        assertTrue(IslandCommandCatalog.playerCommands().contains("섬 lang <ko_kr|en_us>"));
        assertTrue(dispatcher.contains("args[0].equalsIgnoreCase(\"lang\")"));
        assertTrue(dispatcher.contains("playerRouting.setPlayerLocale(player, args[1])"));
        assertTrue(actions.contains("playerProfileCommands().setLocale(player.getUniqueId(), locale)"));
        assertTrue(actions.contains("profile == null || profile.locale() == null || profile.locale().isBlank()"), "mixed-version empty profile responses must not crash locale updates");
    }

    @Test
    void setTeleportAliasesPreservePaperVelocityParity() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerCommandDispatcher.java"));

        assertTrue(dispatcher.contains("args[0].equalsIgnoreCase(\"settp\")"));
        assertTrue(dispatcher.contains("args[0].equalsIgnoreCase(\"setgo\")"));
        assertTrue(IslandCommandCatalog.playerCommands().contains("섬 settp [name]"));
        assertTrue(IslandCommandCatalog.playerCommands().contains("섬 setgo [name]"));
    }

    @Test
    void setpermPreservesPaperVelocityParity() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerMembershipCommandDispatcher.java"));

        assertTrue(dispatcher.contains("args[0].equalsIgnoreCase(\"setperm\")"));
        assertTrue(IslandCommandCatalog.playerCommands().contains("섬 setperm"));
    }

    @Test
    void openClosePreservePaperVelocityParity() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerMembershipCommandDispatcher.java"));
        assertTrue(dispatcher.contains("equalsIgnoreCase(\"open\")"));
        assertTrue(dispatcher.contains("equalsIgnoreCase(\"close\")"));
        assertTrue(IslandCommandCatalog.playerCommands().containsAll(List.of("섬 open", "섬 close")));
    }

    @Test
    void remainingAliasesPreservePaperVelocityParity() throws Exception {
        String player = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerCommandDispatcher.java"));
        String membership = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerMembershipCommandDispatcher.java"));
        assertTrue(membership.contains("equalsIgnoreCase(\"join\")"));
        assertTrue(player.contains("equalsIgnoreCase(\"recalc\")"));
        assertTrue(player.contains("equalsIgnoreCase(\"tc\")"));
    }

    @Test
    void warehouseQueriesExecuteOnVelocityWhileInventoryMutationsStayOnPaper() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerCommandDispatcher.java"));
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityPlayerProgressionActions.java"));
        String forwarder = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/PaperLocalCommandForwarder.java"));
        assertTrue(dispatcher.contains("equalsIgnoreCase(\"vault\")"));
        assertTrue(dispatcher.contains("playerProgression.listWarehouse"));
        assertTrue(dispatcher.contains("playerProgression.depositWarehouse"));
        assertTrue(dispatcher.contains("playerProgression.withdrawWarehouse"));
        assertTrue(actions.contains("coreApiClient.warehouse().listItems"));
        assertFalse(actions.contains("coreApiClient.warehouseCommands().deposit"));
        assertFalse(actions.contains("coreApiClient.warehouseCommands().withdraw"));
        assertTrue(forwarder.contains("\"warehouse-deposit\""));
        assertTrue(forwarder.contains("\"warehouse-withdraw\""));
    }

    @Test
    void velocityCommandHelpUsesClickableAdventureComponents() throws Exception {
        String support = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityCommandSupport.java"));

        assertTrue(support.contains("ClickEvent.suggestCommand(\"/\" + oneLineCommand)"), "Velocity help entries must safely insert commands instead of plain-text-only output");
        assertTrue(support.contains("ClickEvent.runCommand(\"/\" + oneLineCommand)"), "Velocity help pagination must be clickable");
        assertTrue(support.contains("hoverEvent(Component.text(\"클릭: 명령어 입력"), "Velocity help entries must expose hover usage guidance");
        assertTrue(support.contains("CommandListPolicy.oneLine(command)"), "Velocity help entries must sanitize commands before rendering clickable components");
        assertTrue(support.contains("commandEntryComponent(command)"), "Velocity player/admin/destructive command lists must share the clickable renderer");
        assertTrue(support.contains("navigationEntryComponent(commandPage.nextCommand()"), "Velocity command lists must render next-page navigation as a clickable entry");
        assertTrue(support.contains("NamedTextColor.AQUA"), "Velocity command entries must be visually distinct from navigation");
    }

    @Test
    void adminCommandCatalogIncludesGoalCommandsAndKoreanAlias() {
        List<String> commands = IslandCommandCatalog.adminCommands(true);

        for (String command : List.of(
                "ciadmin",
                "섬관리",
                "ciadmin dashboard",
                "ciadmin doctor",
                "ciadmin island info <island|player>",
                "ciadmin island where <island>",
                "ciadmin island tp <island>",
                "ciadmin island activate <island>",
                "ciadmin island deactivate <island>",
                "ciadmin island migrate <island> <node>",
                "ciadmin island save <island>",
                "ciadmin island snapshot <island> [reason]",
                "ciadmin island rollback <island> <snapshot> --confirm",
                "ciadmin island quarantine <island> [reason]",
                "ciadmin island repair <island> [reason]",
                "ciadmin island delete <island> --confirm",
                "ciadmin island restore <island> <snapshot> --confirm",
                "ciadmin player info <player>",
                "ciadmin player setisland <player> <islandUuid>",
                "ciadmin player clearisland <player>",
                "ciadmin node list",
                "ciadmin node info <node>",
                "ciadmin node drain <node>",
                "ciadmin node undrain <node>",
                "ciadmin node kickall <node> [reason]",
                "ciadmin node shutdown-safe <node> [reason]",
                "ciadmin route debug [all|player]",
                "ciadmin route ticket <ticket|player>",
                "ciadmin route clear <player> [ticket]",
                "ciadmin jobs list",
                "ciadmin jobs retry <jobId>",
                "ciadmin jobs cancel <jobId>",
                "ciadmin integrations",
                "ciadmin support-bundle create",
                "ciadmin block-values reload",
                "ciadmin setblockamount <island> <materialKey> <amount>",
                "ciadmin seteffect <island> <effectKey> <amplifier>",
                "ciadmin setcropgrowth <island> <percent>",
                "ciadmin setmobdrops <island> <percent>",
                "ciadmin setspawnerrates <island> <percent>",
                "ciadmin template seticon <name> <material>",
                "ciadmin template setcost <name> <amount>",
                "ciadmin template setpermission <name> <permission>",
                "ciadmin cache clear",
                "ciadmin reload",
                "ciadmin migrate-superiorskyblock2 scan [path]",
                "ciadmin migrate-superiorskyblock2 report",
                "ciadmin migrate-superiorskyblock2 approve <approvalToken>",
                "ciadmin migrate-superiorskyblock2 compare <island>",
                "ciadmin migrate-superiorskyblock2 rollback-plan",
                "ciadmin migrate superiorskyblock2 scan",
                "ciadmin migrate superiorskyblock2 approve <dryRunId>",
                "ciadmin migrate superiorskyblock2 rollback-plan <batchId>",
                "ciadmin migrate superiorskyblock2 unlock --confirm <token>"
        )) {
            assertTrue(commands.contains(command), command);
        }
    }

    @Test
    void adminTemplateMutationCommandsUseTypedClients() throws Exception {
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityAdminActions.java"));
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityAdminCommandDispatcher.java"));
        String formatter = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/message/VelocityIslandMessageFormatter.java"));

        assertTrue(dispatcher.contains("args[1].equalsIgnoreCase(\"seticon\")"), "Velocity template icon command must route explicitly");
        assertTrue(dispatcher.contains("args[1].equalsIgnoreCase(\"setcost\")"), "Velocity template cost command must route explicitly");
        assertTrue(dispatcher.contains("args[1].equalsIgnoreCase(\"setpermission\")"), "Velocity template permission command must route explicitly");
        assertTrue(actions.contains("coreApiClient.templates().get(templateId).thenCompose(template ->"), "Velocity template mutations must fetch and preserve the existing typed template");
        assertTrue(actions.contains("templateWithCatalogFields(TemplateView template"), "Velocity template mutations must preserve non-catalog template fields");
        assertTrue(formatter.contains("view.requiredPermission()"), "Velocity template action output must include template permission state");
        assertTrue(formatter.contains("view.iconMaterial()"), "Velocity template action output must include template icon state");
        assertTrue(formatter.contains("view.creationCost()"), "Velocity template action output must include template cost state");
    }

    @Test
    void velocityAdminUxCommandsUseTypedCoreClients() throws Exception {
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/IslandCommandCatalog.java"));
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityAdminCommandDispatcher.java"));
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityAdminActions.java"));
        String actionContext = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityActionContext.java"));
        String runtimeFactory = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/bootstrap/VelocityRuntimeFactory.java"));
        String coreConfigFormatter = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/message/VelocityCoreConfigMessageFormatter.java"));
        String coreClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/AdminNodeQueryClient.java"));
        String jdkClient = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkAdminNodeQueryClient.java"));

        assertTrue(catalog.contains("ciadmin dashboard"), "Velocity admin help must expose dashboard");
        assertTrue(catalog.contains("ciadmin doctor"), "Velocity admin help must expose doctor");
        assertTrue(catalog.contains("ciadmin integrations"), "Velocity admin help must expose integration status");
        assertTrue(catalog.contains("ciadmin support-bundle create"), "Velocity admin help must expose support bundle creation");

        assertTrue(dispatcher.contains("adminActions.dashboard(player)"), "Velocity dashboard command must route explicitly");
        assertTrue(dispatcher.contains("adminActions.doctor(player)"), "Velocity doctor command must route explicitly");
        assertTrue(dispatcher.contains("adminActions.integrations(player)"), "Velocity integrations command must route explicitly");
        assertTrue(dispatcher.contains("adminActions.supportBundle(player)"), "Velocity support-bundle command must route explicitly");

        assertTrue(actions.contains("coreApiClient.adminMetrics().summary()"), "Dashboard/doctor must include typed metrics");
        assertTrue(actions.contains("coreApiClient.adminNodes().listNodesSummary()"), "Dashboard/doctor must include typed node summary");
        assertTrue(actions.contains("coreApiClient.jobs().list()"), "Dashboard/doctor must include typed job summary");
        assertTrue(actions.contains("coreApiClient.adminRoutes().debug(new UUID(0L, 0L))"), "Dashboard/doctor must include typed route context");
        assertTrue(actions.contains("coreApiClient.adminStorage().status()"), "Dashboard/doctor must include typed storage context");
        assertTrue(actions.contains("coreApiClient.adminAudit().list(5)"), "Doctor must include recent audit context");
        assertTrue(actions.contains("coreApiClient.adminSupportBundle().create()"), "Support bundle must use typed Core support-bundle client");
        assertTrue(dispatcher.contains("args[1].equalsIgnoreCase(\"reload\")"), "Velocity block-values reload must route explicitly");
        assertTrue(actions.contains("coreApiClient.adminMaintenance().reload()"), "Velocity block-values reload must use the typed Core maintenance reload boundary");
        assertTrue(dispatcher.contains("adminActions.setGameplayBlockAmount(player"), "Velocity setblockamount must route explicitly");
        assertTrue(dispatcher.contains("adminActions.setGameplayEffect(player"), "Velocity seteffect must route explicitly");
        assertTrue(dispatcher.contains("adminActions.setGameplayRate(player, args[1], \"RATE:CROP_GROWTH\""), "Velocity setcropgrowth must route explicitly");
        assertTrue(dispatcher.contains("adminActions.setGameplayRate(player, args[1], \"RATE:MOB_DROPS\""), "Velocity setmobdrops must route explicitly");
        assertTrue(dispatcher.contains("adminActions.setGameplayRate(player, args[1], \"RATE:SPAWNER_RATES\""), "Velocity setspawnerrates must route explicitly");
        assertTrue(actions.contains("coreApiClient.environmentCommands().setLimit"), "Velocity gameplay parity commands must write Core-visible runtime modifiers");
        assertTrue(actions.contains("\"BLOCK_AMOUNT:\" + normalizeGameplayKey"), "Velocity setblockamount must store a namespaced block amount key");
        assertTrue(actions.contains("\"EFFECT:\" + normalizeGameplayKey"), "Velocity seteffect must store a namespaced effect key");
        assertTrue(actions.contains("writeSupportBundle"), "Velocity support bundle must write a local operator artifact");
        assertTrue(actions.contains("cloudislands-velocity-support-bundle-") && actions.contains(".zip"), "Velocity support bundle must be packaged as a zip bundle");
        assertTrue(actions.contains("core-support-bundle.json"), "Velocity support bundle must include the redacted Core bundle");
        assertTrue(actions.contains("velocity-runtime.txt"), "Velocity support bundle must include local Velocity runtime context");
        assertTrue(actions.contains("VelocityDiagnosticRedactor.redact(coreBundleJson"), "Velocity support bundle must redact Core payload secrets before writing");
        assertTrue(actions.contains("dataDirectory.resolve(\"support-bundles\")"), "Velocity support bundle must write under the plugin data directory");
        assertTrue(actionContext.contains("Path dataDirectory"), "Velocity admin actions must receive the plugin data directory");
        assertTrue(runtimeFactory.contains("dataDirectory"), "Velocity runtime factory must pass the real plugin data directory into actions");
        assertTrue(actions.contains("doctorSeverity(String body)") && actions.contains("\"CRITICAL\"") && actions.contains("\"WARN\"") && actions.contains("\"INFO\""), "Doctor must classify sections with CRITICAL/WARN/INFO");
        assertTrue(actions.contains("doctorRecommendation(String label, String severity, String body)"), "Doctor must recommend operator remediation commands");
        assertTrue(actions.contains("coreConfigMessages::format"), "Velocity doctor must render Core config-doctor details");
        assertTrue(coreConfigFormatter.contains("configDoctorChecks"), "Velocity Core config output must include the P8 config-doctor risk checklist");
        assertTrue(coreConfigFormatter.contains("gameplayParityContract"), "Velocity Core config output must include the P5 gameplay parity contract");

        assertTrue(coreClient.contains("integrationSummary()"), "Core node client must expose typed integration metadata");
        assertTrue(jdkClient.contains("AdminNodeIntegrationSummaryView integrationSummary(String body)"), "JDK node client must parse integration metadata from Core node JSON");
        assertTrue(jdkClient.contains("CoreJson.objectValue(node, \"integrations\")"), "Integration parser must use typed Core JSON object helpers");
    }

    @Test
    void velocitySetupWizardIsFirstClassAdminCommand() throws Exception {
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/IslandCommandCatalog.java"));
        String dispatcher = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityAdminCommandDispatcher.java"));
        String actions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/VelocityAdminActions.java"));
        String suggestions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityCommandSuggestions.java"));

        for (String command : List.of(
                "ciadmin setup start",
                "ciadmin setup core",
                "ciadmin setup redis",
                "ciadmin setup database",
                "ciadmin setup storage",
                "ciadmin setup velocity",
                "ciadmin setup paper-node",
                "ciadmin setup verify"
        )) {
            assertTrue(catalog.contains(command), command);
        }
        assertTrue(dispatcher.contains("adminActions.setup(player"), "Velocity setup command must route explicitly");
        assertTrue(actions.contains("Setup verify delegates to /ciadmin doctor"), "Velocity setup verify must explain doctor delegation");
        assertTrue(actions.contains("doctor(player)"), "Velocity setup verify must reuse doctor checks");
        assertTrue(suggestions.contains("args[0].equalsIgnoreCase(\"setup\")"), "Velocity setup command must tab-complete subcommands");
        assertTrue(suggestions.contains("\"dashboard\", \"doctor\", \"setup\""), "Velocity admin permission switch must include setup and health roots");
        assertTrue(suggestions.contains("\"integrations\""), "Velocity integrations must be a first-class permission root");
        assertTrue(suggestions.contains("\"support-bundle\""), "Velocity support-bundle must be a first-class permission root");
    }

    @Test
    void destructiveVelocityCommandsRequireConfirmationBeforeCoreMutation() throws Exception {
        String support = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityCommandSupport.java"));
        String player = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityPlayerCommandDispatcher.java"));
        String admin = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityAdminCommandDispatcher.java"));
        String suggestions = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/velocity/command/VelocityCommandSuggestions.java"));

        assertTrue(support.contains("destructiveConfirmed(String[] args)"), "Velocity command support must define a shared destructive confirmation boundary");
        assertTrue(support.contains("--confirm") && support.contains("confirm") && support.contains("확인"), "Velocity destructive confirmation must support flag, English, and Korean confirmation tokens");
        assertTrue(indexOf(player, "sendDestructiveConfirmationRequired(player, \"섬 리셋 [reason] confirm\")") < indexOf(player, "playerRouting.resetIsland("), "player reset must require confirmation before Core mutation");
        assertTrue(indexOf(player, "sendDestructiveConfirmationRequired(player, \"섬 삭제 confirm\")") < indexOf(player, "playerRouting.deleteIsland("), "player delete must require confirmation before Core mutation");
        assertTrue(indexOf(player, "sendDestructiveConfirmationRequired(player, \"섬 탈퇴 confirm\")") < indexOf(player, "playerMembership.leaveIsland("), "player leave must require confirmation before Core mutation");
        assertTrue(indexOf(admin, "sendDestructiveConfirmationRequired(player, \"ciadmin island rollback <island> <snapshot> --confirm\")") < indexOf(admin, "adminActions.restoreTarget("), "admin rollback must require confirmation before Core mutation");
        assertTrue(indexOf(admin, "sendDestructiveConfirmationRequired(player, \"ciadmin island restore <island> <snapshot> --confirm\")") < admin.lastIndexOf("adminActions.restoreTarget("), "admin restore must require confirmation before Core mutation");
        assertTrue(indexOf(admin, "sendDestructiveConfirmationRequired(player, \"ciadmin island delete <island> --confirm\")") < indexOf(admin, "adminActions.adminDeleteIslandTarget("), "admin delete must require confirmation before Core mutation");
        assertTrue(suggestions.contains("List.of(\"--confirm\", \"confirm\", \"확인\")"), "Velocity suggestions must expose destructive confirmation tokens");
    }

    private int indexOf(String source, String needle) {
        int index = source.indexOf(needle);
        assertTrue(index >= 0, needle);
        return index;
    }
}
