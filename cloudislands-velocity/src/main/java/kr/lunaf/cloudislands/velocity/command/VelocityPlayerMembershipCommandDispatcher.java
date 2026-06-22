package kr.lunaf.cloudislands.velocity.command;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RoleId;
import kr.lunaf.cloudislands.api.model.SystemRole;
import kr.lunaf.cloudislands.velocity.VelocityRoutingController;
import kr.lunaf.cloudislands.velocity.config.VelocityConfig;
import net.kyori.adventure.text.Component;

final class VelocityPlayerMembershipCommandDispatcher extends VelocityCommandSupport {
    private static final String ROLE_MODERATOR = "MODERATOR";
    private static final String ROLE_MEMBER = "MEMBER";
    private static final String ROLE_TRUSTED = "TRUSTED";

    VelocityPlayerMembershipCommandDispatcher(ProxyServer proxy, VelocityRoutingController routingController, VelocityConfig config) {
        super(proxy, routingController, config);
    }

    boolean dispatch(Player player, String[] args) {
        if (args[0].equalsIgnoreCase("invite") || args[0].equals("초대")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            playerMembership.inviteTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""));
            return true;
        }
        if (args[0].equalsIgnoreCase("invites") || args[0].equalsIgnoreCase("invite-list") || args[0].equalsIgnoreCase("invite-menu") || args[0].equals("초대목록")) {
            playerMembership.listInvites(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("invite-accept") || args[0].equals("수락") || args[0].equals("초대수락")) {
            playerMembership.acceptInviteTarget(player, args.length > 1 ? args[1] : "");
            return true;
        }
        if (args[0].equalsIgnoreCase("decline") || args[0].equalsIgnoreCase("invite-decline") || args[0].equals("거절") || args[0].equals("초대거절")) {
            playerMembership.declineInviteTarget(player, args.length > 1 ? args[1] : "");
            return true;
        }
        if (args[0].equalsIgnoreCase("members") || args[0].equalsIgnoreCase("member-list") || args[0].equalsIgnoreCase("member-menu") || args[0].equals("멤버") || args[0].equals("멤버목록") || args[0].equals("멤버관리")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerMembership.listMembers(player, islandId);
            return true;
        }
        if (args[0].equalsIgnoreCase("kick") || args[0].equalsIgnoreCase("remove-member") || args[0].equals("추방")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            playerMembership.kickMemberTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""));
            return true;
        }
        if (args[0].equalsIgnoreCase("promote") || args[0].equals("승급")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            playerMembership.setRoleTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""), ROLE_MODERATOR);
            return true;
        }
        if (args[0].equalsIgnoreCase("demote") || args[0].equals("강등")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            playerMembership.setRoleTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""), ROLE_MEMBER);
            return true;
        }
        if (args[0].equalsIgnoreCase("setrole") || args[0].equalsIgnoreCase("role-set") || args[0].equals("역할설정")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            String target = argumentAfterOptionalIsland(args, 1, "");
            int roleIndex = indexAfterOptionalIslandValue(args, 1);
            String roleKey = args.length > roleIndex ? roleKeyOrBlank(args[roleIndex]) : "";
            if (!memberAssignableRoleKey(roleKey)) {
                player.sendMessage(Component.text("올바른 멤버 역할을 입력해주세요. 예: MEMBER, MODERATOR, BUILDER"));
                return true;
            }
            playerMembership.setRoleTarget(player, islandId, target, roleKey);
            return true;
        }
        if (args[0].equalsIgnoreCase("roles") || args[0].equalsIgnoreCase("role-list") || args[0].equals("역할목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerMembership.listRoles(player, islandId);
            return true;
        }
        if (args[0].equalsIgnoreCase("role-upsert") || args[0].equalsIgnoreCase("role-edit") || args[0].equals("역할편집")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            int roleIndex = hasOptionalIslandIdArgument(args, 1) ? 2 : 1;
            String roleKey = args.length > roleIndex ? roleKeyOrBlank(args[roleIndex]) : "";
            if (!editableRoleKey(roleKey)) {
                player.sendMessage(Component.text("편집 가능한 멤버 역할을 입력해주세요. 예: MEMBER, MODERATOR, BUILDER"));
                return true;
            }
            int weightIndex = roleIndex + 1;
            int displayIndex = roleIndex + 2;
            int weight = args.length > weightIndex ? (int) parseLongOrZero(args[weightIndex]) : defaultRoleWeight(roleKey);
            String displayName = args.length > displayIndex ? joinArgs(args, displayIndex) : roleKey;
            playerMembership.upsertRole(player, islandId, roleKey, weight, displayName.isBlank() ? roleKey : displayName);
            return true;
        }
        if (args[0].equalsIgnoreCase("role-reset") || args[0].equals("역할초기화")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            int roleIndex = hasOptionalIslandIdArgument(args, 1) ? 2 : 1;
            String roleKey = args.length > roleIndex ? roleKeyOrBlank(args[roleIndex]) : "";
            if (!editableRoleKey(roleKey)) {
                player.sendMessage(Component.text("초기화 가능한 멤버 역할을 입력해주세요. 예: MEMBER, MODERATOR, BUILDER"));
                return true;
            }
            playerMembership.resetRole(player, islandId, roleKey);
            return true;
        }
        if (args[0].equalsIgnoreCase("transfer") || args[0].equals("양도")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            playerMembership.transferOwnershipTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""));
            return true;
        }
        if (args[0].equalsIgnoreCase("trust") || args[0].equals("신뢰")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            playerMembership.setRoleTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""), ROLE_TRUSTED);
            return true;
        }
        if (args[0].equalsIgnoreCase("untrust") || args[0].equals("신뢰해제")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            playerMembership.setRoleTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""), ROLE_MEMBER);
            return true;
        }
        if (args[0].equalsIgnoreCase("ban") || args[0].equals("밴")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            String target = argumentAfterOptionalIsland(args, 1, "");
            int reasonIndex = indexAfterOptionalIslandValue(args, 1);
            String reason = args.length > reasonIndex ? joinArgs(args, reasonIndex) : "island ban";
            playerMembership.banVisitorTarget(player, islandId, target, reason);
            return true;
        }
        if (args[0].equalsIgnoreCase("unban") || args[0].equalsIgnoreCase("pardon") || args[0].equals("밴해제")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            playerMembership.pardonVisitorTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""));
            return true;
        }
        if (args[0].equalsIgnoreCase("kickvisitor") || args[0].equals("방문자추방")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            playerMembership.kickVisitorTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""));
            return true;
        }
        if (args[0].equalsIgnoreCase("banlist") || args[0].equalsIgnoreCase("bans") || args[0].equalsIgnoreCase("ban-list") || args[0].equalsIgnoreCase("ban-menu") || args[0].equals("밴목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerMembership.listBans(player, islandId);
            return true;
        }
        if (args[0].equalsIgnoreCase("public") || args[0].equals("공개")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerMembership.setPublicAccess(player, islandId, true);
            return true;
        }
        if (args[0].equalsIgnoreCase("private") || args[0].equals("비공개")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerMembership.setPublicAccess(player, islandId, false);
            return true;
        }
        if (args[0].equalsIgnoreCase("lock") || args[0].equals("잠금")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerMembership.setLocked(player, islandId, true);
            return true;
        }
        if (args[0].equalsIgnoreCase("unlock") || args[0].equals("잠금해제")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerMembership.setLocked(player, islandId, false);
            return true;
        }
        if (args[0].equalsIgnoreCase("fly") || args[0].equals("비행")) {
            UUID islandId = islandIdArgument(args, 1);
            boolean enabled = parseToggle(args, hasIslandIdArgument(args, 1) ? 2 : 1, true);
            playerMembership.setFlyFlag(player, islandId, enabled);
            return true;
        }
        if (args[0].equalsIgnoreCase("keepinventory") || args[0].equalsIgnoreCase("keepinv") || args[0].equals("인벤보존")) {
            UUID islandId = islandIdArgument(args, 1);
            playerMembership.setBooleanFlag(player, islandId, kr.lunaf.cloudislands.api.model.IslandFlag.KEEP_INVENTORY, parseToggle(args, hasIslandIdArgument(args, 1) ? 2 : 1, true), "인벤토리 보존");
            return true;
        }
        if (args[0].equalsIgnoreCase("pvp") || args[0].equals("피빕")) {
            UUID islandId = islandIdArgument(args, 1);
            playerMembership.setBooleanFlag(player, islandId, kr.lunaf.cloudislands.api.model.IslandFlag.PVP, parseToggle(args, hasIslandIdArgument(args, 1) ? 2 : 1, true), "PVP");
            return true;
        }
        if (args[0].equalsIgnoreCase("publicwarps") || args[0].equalsIgnoreCase("public-warps") || args[0].equals("공개워프")) {
            UUID islandId = islandIdArgument(args, 1);
            playerMembership.setBooleanFlag(player, islandId, kr.lunaf.cloudislands.api.model.IslandFlag.PUBLIC_WARPS, parseToggle(args, hasIslandIdArgument(args, 1) ? 2 : 1, true), "공개 워프");
            return true;
        }
        if (args[0].equalsIgnoreCase("setflag") || args[0].equalsIgnoreCase("flag-set") || args[0].equals("플래그설정")) {
            UUID islandId = islandIdArgument(args, 1);
            int flagIndex = hasIslandIdArgument(args, 1) ? 2 : 1;
            kr.lunaf.cloudislands.api.model.IslandFlag flag = args.length > flagIndex ? parseFlag(args[flagIndex]) : kr.lunaf.cloudislands.api.model.IslandFlag.FLY;
            boolean enabled = parseToggle(args, flagIndex + 1, true);
            playerMembership.setBooleanFlag(player, islandId, flag, enabled, flag.name());
            return true;
        }
        if (args[0].equalsIgnoreCase("flags") || args[0].equalsIgnoreCase("flag-list") || args[0].equalsIgnoreCase("flag-menu") || args[0].equals("플래그") || args[0].equals("플래그목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerMembership.listFlags(player, islandId);
            return true;
        }
        if (args[0].equalsIgnoreCase("permissions") || args[0].equalsIgnoreCase("permission-list") || args[0].equalsIgnoreCase("permission-menu") || args[0].equalsIgnoreCase("permission") || args[0].equalsIgnoreCase("perms") || args[0].equals("권한") || args[0].equals("권한목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerMembership.listPermissions(player, islandId);
            return true;
        }
        if (args[0].equalsIgnoreCase("setpermission") || args[0].equalsIgnoreCase("permission-set") || args[0].equals("권한설정")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            int roleIndex = hasOptionalIslandIdArgument(args, 1) ? 2 : 1;
            String roleKey = args.length > roleIndex ? roleKeyOrBlank(args[roleIndex]) : ROLE_MEMBER;
            IslandPermission permission = args.length > roleIndex + 1 ? parsePermission(args[roleIndex + 1]) : IslandPermission.BUILD;
            boolean allowed = parseToggle(args, roleIndex + 2, false);
            if (roleKey.isBlank()) {
                player.sendMessage(Component.text("권한을 설정할 역할을 입력해주세요. 예: MEMBER, VISITOR, BUILDER"));
                return true;
            }
            playerMembership.setPermission(player, islandId, roleKey, permission, allowed);
            return true;
        }
        if (args[0].equalsIgnoreCase("logs") || args[0].equalsIgnoreCase("log") || args[0].equalsIgnoreCase("log-list") || args[0].equalsIgnoreCase("log-menu") || args[0].equals("로그") || args[0].equals("로그목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerMembership.listIslandLogs(player, islandId);
            return true;
        }
        return false;
    }

    private static String roleKeyOrBlank(String value) {
        try {
            return RoleId.of(value).value();
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static boolean memberAssignableRoleKey(String roleKey) {
        return editableRoleKey(roleKey);
    }

    private static boolean editableRoleKey(String roleKey) {
        SystemRole systemRole = SystemRole.from(roleKey);
        return roleKey != null
            && !roleKey.isBlank()
            && systemRole != SystemRole.OWNER
            && systemRole != SystemRole.VISITOR
            && systemRole != SystemRole.BANNED;
    }

    private static int defaultRoleWeight(String roleKey) {
        return switch (RoleId.normalize(roleKey, "CUSTOM")) {
            case "CO_OWNER" -> 1;
            case "MODERATOR" -> 2;
            case "MEMBER" -> 3;
            case "TRUSTED" -> 4;
            default -> 100;
        };
    }
}
