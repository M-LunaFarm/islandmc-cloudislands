package kr.lunaf.cloudislands.paper.command;

import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.gui.IslandInviteMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandMembershipCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final Runtime runtime;

    IslandMembershipCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("members") || subcommand.equals("member-menu") || subcommand.equals("멤버") || subcommand.equals("멤버관리")) {
            runtime.openIslandMemberMenu(player);
            return true;
        }
        if (subcommand.equals("member-list") || subcommand.equals("멤버목록")) {
            runtime.listIslandMembers(player);
            return true;
        }
        if (subcommand.equals("invite") || subcommand.equals("초대")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-invite-player-required", "초대할 플레이어를 입력해주세요."));
                return true;
            }
            runtime.inviteIslandMember(player, args[1]);
            return true;
        }
        if (subcommand.equals("invites") || subcommand.equals("invite-menu") || subcommand.equals("초대목록")) {
            IslandInviteMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
            return true;
        }
        if (subcommand.equals("invite-list")) {
            runtime.listPendingInvites(player);
            return true;
        }
        if (subcommand.equals("accept") || subcommand.equals("invite-accept") || subcommand.equals("초대수락")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-invite-accept-target-required", "수락할 초대 ID, 섬 ID/이름, 또는 초대한 플레이어를 입력해주세요."));
                return true;
            }
            runtime.acceptIslandInviteTarget(player, args[1]);
            return true;
        }
        if (subcommand.equals("decline") || subcommand.equals("invite-decline") || subcommand.equals("초대거절")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-invite-decline-target-required", "거절할 초대 ID, 섬 ID/이름, 또는 초대한 플레이어를 입력해주세요."));
                return true;
            }
            runtime.declineIslandInviteTarget(player, args[1]);
            return true;
        }
        if (subcommand.equals("kick") || subcommand.equals("remove-member") || subcommand.equals("추방")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-remove-player-required", "추방할 플레이어를 입력해주세요."));
                return true;
            }
            runtime.removeIslandMember(player, args[1]);
            return true;
        }
        if (subcommand.equals("trust") || subcommand.equals("신뢰")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-trust-player-required", "신뢰할 플레이어를 입력해주세요."));
                return true;
            }
            if (args.length > 2) {
                runtime.trustIslandMemberTemporary(player, args[1], args[2]);
            } else {
                runtime.setIslandMemberRole(player, args[1], IslandRole.TRUSTED, "섬 신뢰 멤버로 설정했습니다.");
            }
            return true;
        }
        if (subcommand.equals("untrust") || subcommand.equals("신뢰해제")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-untrust-player-required", "신뢰 해제할 플레이어를 입력해주세요."));
                return true;
            }
            runtime.setIslandMemberRole(player, args[1], IslandRole.MEMBER, "섬 신뢰를 해제했습니다.");
            return true;
        }
        if (subcommand.equals("promote") || subcommand.equals("승급")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-promote-player-required", "승급할 플레이어를 입력해주세요."));
                return true;
            }
            runtime.setIslandMemberRole(player, args[1], IslandRole.MODERATOR, "섬 멤버를 승급했습니다.");
            return true;
        }
        if (subcommand.equals("demote") || subcommand.equals("강등")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-demote-player-required", "강등할 플레이어를 입력해주세요."));
                return true;
            }
            runtime.setIslandMemberRole(player, args[1], IslandRole.MEMBER, "섬 멤버를 강등했습니다.");
            return true;
        }
        if (subcommand.equals("setrole") || subcommand.equals("role-set") || subcommand.equals("역할설정")) {
            if (args.length < 3) {
                runtime.message(player, runtime.routeMessage("input-member-role-required", "역할을 바꿀 플레이어와 역할을 입력해주세요."));
                return true;
            }
            String roleKey = runtime.roleKey(args[2]);
            if (!runtime.editableRoleKey(roleKey)) {
                runtime.message(player, runtime.routeMessage("input-member-role-invalid", "올바른 멤버 역할을 입력해주세요. 예: MEMBER, MODERATOR, BUILDER"));
                return true;
            }
            runtime.setIslandMemberRole(player, args[1], roleKey, "섬 멤버 역할을 " + roleKey + "(으)로 변경했습니다.");
            return true;
        }
        if (subcommand.equals("roles") || subcommand.equals("role-menu") || subcommand.equals("역할")) {
            runtime.openIslandRoleMenu(player);
            return true;
        }
        if (subcommand.equals("role-list") || subcommand.equals("역할목록")) {
            runtime.listIslandRoles(player);
            return true;
        }
        if (subcommand.equals("role-upsert") || subcommand.equals("role-edit") || subcommand.equals("역할편집")) {
            if (args.length < 4) {
                runtime.message(player, runtime.routeMessage("input-role-edit-required", "역할, 가중치, 표시 이름을 입력해주세요."));
                return true;
            }
            String roleKey = runtime.roleKey(args[1]);
            if (!runtime.editableRoleKey(roleKey)) {
                runtime.message(player, runtime.routeMessage("input-role-edit-invalid", "편집 가능한 멤버 역할을 입력해주세요. 예: BUILDER"));
                return true;
            }
            runtime.upsertIslandRole(player, roleKey, runtime.integer(args[2], runtime.defaultRoleWeight(roleKey)), runtime.joined(args, 3));
            return true;
        }
        if (subcommand.equals("role-reset") || subcommand.equals("역할초기화")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-role-reset-required", "초기화할 역할을 입력해주세요."));
                return true;
            }
            String roleKey = runtime.roleKey(args[1]);
            if (!runtime.editableRoleKey(roleKey)) {
                runtime.message(player, runtime.routeMessage("input-role-reset-invalid", "초기화 가능한 멤버 역할을 입력해주세요. 예: BUILDER"));
                return true;
            }
            runtime.resetIslandRole(player, roleKey);
            return true;
        }
        if (subcommand.equals("transfer") || subcommand.equals("양도")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-transfer-player-required", "양도할 플레이어를 입력해주세요."));
                return true;
            }
            runtime.transferIslandOwnership(player, args[1]);
            return true;
        }
        if (subcommand.equals("ban") || subcommand.equals("밴")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-ban-player-required", "밴할 플레이어를 입력해주세요."));
                return true;
            }
            runtime.banIslandVisitor(player, args[1], args.length > 2 ? runtime.joined(args, 2) : "");
            return true;
        }
        if (subcommand.equals("unban") || subcommand.equals("pardon") || subcommand.equals("밴해제")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-pardon-player-required", "밴 해제할 플레이어를 입력해주세요."));
                return true;
            }
            runtime.pardonIslandVisitor(player, args[1]);
            return true;
        }
        if (subcommand.equals("kickvisitor") || subcommand.equals("방문자추방")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-kick-visitor-required", "추방할 방문자를 입력해주세요."));
                return true;
            }
            runtime.kickIslandVisitor(player, args[1]);
            return true;
        }
        if (subcommand.equals("bans") || subcommand.equals("ban-menu") || subcommand.equals("banlist") || subcommand.equals("밴목록")) {
            runtime.openIslandBanMenu(player);
            return true;
        }
        if (subcommand.equals("ban-list")) {
            runtime.listIslandBans(player);
            return true;
        }
        if (subcommand.equals("permissions") || subcommand.equals("permission-menu") || subcommand.equals("permission") || subcommand.equals("perms") || subcommand.equals("권한")) {
            if (args.length > 3) {
                runtime.setIslandPermission(player, args[1], args[2], args[3]);
            } else {
                runtime.openIslandPermissionMenu(player);
            }
            return true;
        }
        if (subcommand.equals("permission-list") || subcommand.equals("권한목록")) {
            runtime.listIslandPermissions(player);
            return true;
        }
        if (subcommand.equals("permission-exception-list") || subcommand.equals("권한예외목록")) {
            runtime.listIslandPermissions(player);
            return true;
        }
        if (subcommand.equals("permission-exception") || subcommand.equals("권한예외")) {
            if (args.length < 4) {
                runtime.message(player, "플레이어, 권한, 허용 여부를 입력해주세요. 예: /섬 권한예외 Steve BUILD 허용");
                return true;
            }
            runtime.setIslandPermissionOverride(player, args[1], args[2], args[3]);
            return true;
        }
        if (subcommand.equals("setpermission") || subcommand.equals("permission-set") || subcommand.equals("권한설정")) {
            if (args.length < 4) {
                runtime.message(player, runtime.routeMessage("input-permission-set-required", "역할, 권한, 허용 여부를 입력해주세요."));
                return true;
            }
            runtime.setIslandPermission(player, args[1], args[2], args[3]);
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action, GuiClick click) {
        if (action instanceof GuiAction.InviteAction inviteAction) {
            if (inviteAction.accept()) {
                runtime.acceptIslandInviteTarget(player, inviteAction.inviteId().toString());
            } else {
                runtime.declineIslandInviteTarget(player, inviteAction.inviteId().toString());
            }
            return true;
        }
        if (action instanceof GuiAction.MemberRoleChange roleChange) {
            if (!roleChange.confirmation()) {
                boolean promote = roleChange.promote();
                runtime.openConfirmation(player,
                    runtime.routeMessage(promote ? "member-promote-confirm-title" : "member-demote-confirm-title", promote ? "멤버 승급 확인" : "멤버 강등 확인"),
                    runtime.routeMessage(promote ? "member-promote-confirm-description" : "member-demote-confirm-description", promote ? "선택한 플레이어를 MODERATOR 역할로 변경합니다." : "선택한 플레이어를 MEMBER 역할로 변경합니다."),
                    promote ? Material.EMERALD : Material.IRON_INGOT,
                    runtime.routeMessage(promote ? "member-promote-confirm-name" : "member-demote-confirm-name", promote ? "승급 확인" : "강등 확인"),
                    promote ? "island.member.promote" : "island.member.demote",
                    Map.of("playerUuid", roleChange.playerUuid().toString()),
                    runtime.routeMessage(promote ? "member-promote-confirm-lore" : "member-demote-confirm-lore", "클릭하면 Core에 역할 변경을 요청합니다."),
                    "island.members.open");
                return true;
            }
            if (runtime.confirmationAccepted(player, action, click)) {
                runtime.setIslandMemberRole(
                    player,
                    roleChange.playerUuid().toString(),
                    roleChange.promote() ? IslandRole.MODERATOR : IslandRole.MEMBER,
                    roleChange.promote() ? "섬 멤버를 승급했습니다." : "섬 멤버를 강등했습니다.");
            }
            return true;
        }
        if (action instanceof GuiAction.BanPardon banPardon) {
            if (!banPardon.confirmation()) {
                runtime.openConfirmation(player,
                    runtime.routeMessage("ban-pardon-confirm-title", "밴 해제 확인"),
                    runtime.routeMessage("ban-pardon-confirm-description", "선택한 방문자의 밴을 해제합니다."),
                    Material.MILK_BUCKET,
                    runtime.routeMessage("ban-pardon-confirm-name", "밴 해제"),
                    "island.ban.pardon.confirm",
                    Map.of("playerUuid", banPardon.playerUuid().toString()),
                    runtime.routeMessage("ban-pardon-confirm-lore", "클릭하면 Core에 밴 해제를 요청합니다."),
                    "island.bans.open");
                return true;
            }
            if (runtime.confirmationAccepted(player, action, click)) {
                runtime.pardonIslandVisitor(player, banPardon.playerUuid().toString());
            }
            return true;
        }
        String actionId = action.actionId();
        Map<String, String> data = action.data();
        return switch (actionId) {
            case "island.members.open" -> {
                runtime.openIslandMemberMenu(player);
                yield true;
            }
            case "island.member.detail" -> {
                runtime.message(player, runtime.routeMessage("member-detail-title", "멤버 상세"));
                runtime.message(player, "- " + runtime.routeMessage("member-detail-player", "플레이어: ") + data.getOrDefault("playerName", data.getOrDefault("playerUuid", "")));
                runtime.message(player, "- " + runtime.routeMessage("member-detail-role", "역할: ") + data.getOrDefault("role", "unknown"));
                runtime.message(player, "- " + runtime.routeMessage("member-detail-presence", "네트워크 상태: ") + data.getOrDefault("presenceState", "UNKNOWN"));
                runtime.message(player, "- " + runtime.routeMessage("member-detail-last-seen", "마지막 활동: ") + data.getOrDefault("lastSeenAt", runtime.routeMessage("member-detail-last-seen-empty", "기록 없음")));
                yield true;
            }
            case "island.member.role" -> {
                runtime.listIslandMembers(player);
                yield true;
            }
            case "island.members.page" -> {
                runtime.openIslandMemberMenu(player, (int) runtime.longValue(data.getOrDefault("page", "0"), 0L));
                yield true;
            }
            case "island.member.invite", "island.member.invite.help" -> {
                runtime.message(player, runtime.routeMessage("member-invite-help", "멤버 초대는 /섬 초대 <플레이어> 로 요청합니다."));
                yield true;
            }
            case "island.member.list" -> {
                runtime.listIslandMembers(player);
                yield true;
            }
            case "island.member.remove.prepare" -> {
                runtime.openConfirmation(player,
                    runtime.routeMessage("member-remove-confirm-title", "멤버 추방 확인"),
                    runtime.routeMessage("member-remove-confirm-description", "선택한 플레이어를 섬 멤버에서 제거합니다."),
                    Material.BARRIER,
                    runtime.routeMessage("member-remove-confirm-name", "멤버 추방"),
                    "island.member.remove.confirm",
                    Map.of("playerUuid", data.getOrDefault("playerUuid", "")),
                    runtime.routeMessage("member-remove-confirm-lore", "클릭하면 Core에 멤버 추방을 요청합니다."),
                    "island.members.open");
                yield true;
            }
            case "island.member.remove.confirm" -> {
                if (runtime.confirmationAccepted(player, action, click)) {
                    runtime.removeIslandMember(player, data.getOrDefault("playerUuid", ""));
                }
                yield true;
            }
            case "island.invites.open" -> {
                IslandInviteMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
                yield true;
            }
            case "island.bans.open" -> {
                runtime.openIslandBanMenu(player);
                yield true;
            }
            case "island.bans.list" -> {
                runtime.listIslandBans(player);
                yield true;
            }
            case "island.permissions.open" -> {
                runtime.openIslandPermissionMenu(player);
                yield true;
            }
            case "island.permissions.page" -> {
                runtime.openIslandPermissionMenu(player, (int) runtime.longValue(data.getOrDefault("page", "0"), 0L), (int) runtime.longValue(data.getOrDefault("rolePage", "0"), 0L));
                yield true;
            }
            case "island.permissions.list" -> {
                runtime.listIslandPermissions(player);
                yield true;
            }
            case "island.permissions.save" -> {
                runtime.saveStagedIslandPermissions(player);
                yield true;
            }
            case "island.permissions.reset" -> {
                runtime.resetStagedIslandPermissions(player);
                yield true;
            }
            case "island.permissions.set" -> {
                runtime.stageIslandPermission(player, data.getOrDefault("role", ""), data.getOrDefault("permission", ""), click.right() ? "false" : "true", data.getOrDefault("expectedVersion", ""));
                yield true;
            }
            case "island.roles.open" -> {
                runtime.openIslandRoleMenu(player);
                yield true;
            }
            case "island.role.weight.adjust" -> {
                runtime.adjustIslandRoleWeight(player, data.getOrDefault("role", ""), data.getOrDefault("weight", "0"), data.getOrDefault("displayName", ""), click);
                yield true;
            }
            case "island.roles.list" -> {
                runtime.listIslandRoles(player);
                yield true;
            }
            default -> false;
        };
    }

    interface Runtime {
        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        MessageRenderer messagesFor(Player player);

        String joined(String[] args, int start);

        int integer(String value, int fallback);

        long longValue(String value, long fallback);

        String roleKey(String value);

        boolean editableRoleKey(String roleKey);

        int defaultRoleWeight(String roleKey);

        void listIslandMembers(Player player);

        void openIslandMemberMenu(Player player);

        void openIslandMemberMenu(Player player, int page);

        void inviteIslandMember(Player player, String target);

        void listPendingInvites(Player player);

        void acceptIslandInviteTarget(Player player, String target);

        void declineIslandInviteTarget(Player player, String target);

        void removeIslandMember(Player player, String target);

        void setIslandMemberRole(Player player, String target, IslandRole role, String successMessage);

        void setIslandMemberRole(Player player, String target, String roleKey, String successMessage);

        void trustIslandMemberTemporary(Player player, String target, String duration);

        void transferIslandOwnership(Player player, String target);

        void banIslandVisitor(Player player, String target, String reason);

        void pardonIslandVisitor(Player player, String target);

        void kickIslandVisitor(Player player, String target);

        void openIslandBanMenu(Player player);

        void listIslandBans(Player player);

        void listIslandPermissions(Player player);

        void openIslandPermissionMenu(Player player);

        void openIslandPermissionMenu(Player player, int page, int rolePage);

        void stageIslandPermission(Player player, String roleName, String permissionName, String allowedValue);

        void stageIslandPermission(Player player, String roleName, String permissionName, String allowedValue, String expectedVersion);

        void resetStagedIslandPermissions(Player player);

        void saveStagedIslandPermissions(Player player);

        void setIslandPermission(Player player, String roleName, String permissionName, String allowedValue);

        void setIslandPermissionOverride(Player player, String target, String permissionName, String allowedValue);

        void openIslandRoleMenu(Player player);

        void listIslandRoles(Player player);

        void upsertIslandRole(Player player, String roleKey, int weight, String displayName);

        void resetIslandRole(Player player, String roleKey);

        void adjustIslandRoleWeight(Player player, String roleName, String weightValue, String displayName, GuiClick click);

        void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction);

        boolean confirmationAccepted(Player player, GuiAction action, GuiClick click);
    }
}
