package kr.lunaf.cloudislands.velocity;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.longValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.parseLong;
import static kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver.parseUuid;

import com.velocitypowered.api.proxy.Player;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import net.kyori.adventure.text.Component;

public final class VelocityPlayerMembershipActions extends VelocityActionSupport {
    VelocityPlayerMembershipActions(VelocityActionContext context) {
        super(context);
    }

    public void invite(Player player, UUID islandId, UUID targetUuid) {
        sendBodyResult(player, coreApiClient.createIslandInvite(islandId, player.getUniqueId(), targetUuid).thenApply(islandMessages::inviteCreate), "초대를 생성하지 못했습니다.");
    }

    public void inviteTarget(Player player, UUID islandId, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("초대할 플레이어를 찾지 못했습니다."));
                return;
            }
            invite(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("초대할 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void listInvites(Player player) {
        coreApiClient.listPendingInvites(player.getUniqueId()).thenAccept(body -> player.sendMessage(Component.text(islandMessages.invites(body)))).exceptionally(error -> {
            player.sendMessage(Component.text("초대 목록을 불러오지 못했습니다."));
            return null;
        });
    }

    public void acceptInvite(Player player, UUID inviteId) {
        sendInviteActionResult(player, coreApiClient.acceptIslandInviteResult(inviteId, player.getUniqueId()), "섬 초대를 수락했습니다.", "섬 초대를 수락하지 못했습니다.");
    }

    public void acceptInviteTarget(Player player, String target) {
        targetResolver.resolveInviteTarget(player.getUniqueId(), target).thenAccept(inviteId -> {
            if (inviteId.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 초대를 찾지 못했습니다."));
                return;
            }
            acceptInvite(player, inviteId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 초대를 찾지 못했습니다."));
            return null;
        });
    }

    public void declineInvite(Player player, UUID inviteId) {
        sendInviteActionResult(player, coreApiClient.declineIslandInviteResult(inviteId, player.getUniqueId()), "섬 초대를 거절했습니다.", "섬 초대를 거절하지 못했습니다.");
    }

    public void declineInviteTarget(Player player, String target) {
        targetResolver.resolveInviteTarget(player.getUniqueId(), target).thenAccept(inviteId -> {
            if (inviteId.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 초대를 찾지 못했습니다."));
                return;
            }
            declineInvite(player, inviteId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 초대를 찾지 못했습니다."));
            return null;
        });
    }

    public void listMembers(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.listIslandMembers(islandId).thenApply(islandMessages::memberList), "멤버 목록을 불러오지 못했습니다.");
    }

    public void setRole(Player player, UUID islandId, UUID targetUuid, IslandRole role) {
        sendActionResult(player, coreApiClient.setIslandMember(islandId, player.getUniqueId(), targetUuid, role), "섬 멤버 역할을 변경했습니다.", "섬 멤버 역할을 변경하지 못했습니다.");
    }

    public void setRole(Player player, UUID islandId, UUID targetUuid, String roleKey) {
        sendBodyResult(player, coreApiClient.setIslandMemberResult(islandId, player.getUniqueId(), targetUuid, roleKey).thenApply(body -> "섬 멤버 역할을 변경했습니다: " + jsonValue(body, "roleKey")), "섬 멤버 역할을 변경하지 못했습니다.");
    }

    public void setRoleTarget(Player player, UUID islandId, String target, IslandRole role) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            setRole(player, islandId, targetUuid, role);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void setRoleTarget(Player player, UUID islandId, String target, String roleKey) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            setRole(player, islandId, targetUuid, roleKey);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void transferOwnership(Player player, UUID islandId, UUID targetUuid) {
        sendActionResult(player, coreApiClient.transferIslandOwnership(islandId, player.getUniqueId(), targetUuid), "섬 소유권을 양도했습니다.", "섬 소유권을 양도하지 못했습니다.");
    }

    public void transferOwnershipTarget(Player player, UUID islandId, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            transferOwnership(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void kickMember(Player player, UUID islandId, UUID targetUuid) {
        sendActionResult(player, coreApiClient.removeIslandMember(islandId, player.getUniqueId(), targetUuid), "섬 멤버를 추방했습니다.", "섬 멤버를 추방하지 못했습니다.");
    }

    public void kickMemberTarget(Player player, UUID islandId, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            kickMember(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void banVisitor(Player player, UUID islandId, UUID targetUuid, String reason) {
        sendActionResult(player, coreApiClient.banIslandVisitor(islandId, player.getUniqueId(), targetUuid, reason), "방문자를 밴했습니다.", "방문자를 밴하지 못했습니다.");
    }

    public void banVisitorTarget(Player player, UUID islandId, String target, String reason) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            banVisitor(player, islandId, targetUuid, reason);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void listBans(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.listIslandBans(islandId).thenApply(islandMessages::banList), "밴 목록을 불러오지 못했습니다.");
    }

    public void pardonVisitor(Player player, UUID islandId, UUID targetUuid) {
        sendActionResult(player, coreApiClient.pardonIslandVisitor(islandId, player.getUniqueId(), targetUuid), "방문자 밴을 해제했습니다.", "방문자 밴을 해제하지 못했습니다.");
    }

    public void pardonVisitorTarget(Player player, UUID islandId, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            pardonVisitor(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void kickVisitor(Player player, UUID islandId, UUID targetUuid) {
        coreApiClient.kickIslandVisitor(islandId, player.getUniqueId(), targetUuid).thenRun(() ->
            player.sendMessage(Component.text("방문자 추방을 요청했습니다."))
        ).exceptionally(error -> {
            player.sendMessage(Component.text("방문자를 추방할 권한이 없거나 처리하지 못했습니다."));
            return null;
        });
    }

    public void kickVisitorTarget(Player player, UUID islandId, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            kickVisitor(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void setPublicAccess(Player player, UUID islandId, boolean publicAccess) {
        sendActionResult(player, coreApiClient.setIslandPublicAccess(islandId, player.getUniqueId(), publicAccess), publicAccess ? "섬을 공개로 변경했습니다." : "섬을 비공개로 변경했습니다.", "섬 공개 상태를 변경하지 못했습니다.");
    }

    public void setIslandName(Player player, UUID islandId, String name) {
        if (name == null || name.isBlank()) {
            player.sendMessage(Component.text("새 섬 이름을 입력해주세요."));
            return;
        }
        withResolvedIsland(player, islandId, "이름을 변경할 섬을 찾지 못했습니다.", "섬 이름을 변경하지 못했습니다.",
            resolved -> sendActionResult(player, coreApiClient.setIslandName(resolved, player.getUniqueId(), name), "섬 이름을 변경했습니다.", "섬 이름을 변경하지 못했습니다."));
    }

    public void setFlyFlag(Player player, UUID islandId, boolean enabled) {
        sendActionResult(player, coreApiClient.setIslandFlag(islandId, player.getUniqueId(), kr.lunaf.cloudislands.api.model.IslandFlag.FLY, Boolean.toString(enabled)), enabled ? "섬 비행을 허용했습니다." : "섬 비행을 비활성화했습니다.", "섬 비행 설정을 변경하지 못했습니다.");
    }

    public void setBooleanFlag(Player player, UUID islandId, kr.lunaf.cloudislands.api.model.IslandFlag flag, boolean enabled, String label) {
        sendActionResult(player, coreApiClient.setIslandFlag(islandId, player.getUniqueId(), flag, Boolean.toString(enabled)), "섬 " + label + " 설정을 " + (enabled ? "켰습니다." : "껐습니다."), "섬 " + label + " 설정을 변경하지 못했습니다.");
    }

    public void listFlags(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "플래그를 확인할 섬을 찾지 못했습니다.", "섬 플래그를 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandFlags(resolved).thenApply(islandMessages::flagList), "섬 플래그를 불러오지 못했습니다."));
    }

    public void listHomes(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.listIslandHomes(islandId).thenApply(islandMessages::homeList), "섬 홈을 불러오지 못했습니다.");
    }

    public void setHome(Player player, UUID islandId, String name) {
        IslandLocation defaultHome = new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F);
        sendActionResult(player, coreApiClient.setIslandHome(islandId, player.getUniqueId(), name, defaultHome), "섬 홈을 설정했습니다.", "섬 홈을 설정하지 못했습니다.");
    }

    public void setLocked(Player player, UUID islandId, boolean locked) {
        sendActionResult(player, coreApiClient.setIslandLocked(islandId, player.getUniqueId(), locked), locked ? "섬을 잠금 상태로 변경했습니다." : "섬 잠금을 해제했습니다.", "섬 잠금 상태를 변경하지 못했습니다.");
    }

    public void listPermissions(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "권한을 확인할 섬을 찾지 못했습니다.", "섬 권한을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandPermissions(resolved).thenApply(islandMessages::permissionList), "섬 권한을 불러오지 못했습니다."));
    }

    public void setPermission(Player player, UUID islandId, IslandRole role, IslandPermission permission, boolean allowed) {
        sendActionResult(player, coreApiClient.setIslandPermission(islandId, player.getUniqueId(), role, permission, allowed), "섬 권한을 변경했습니다.", "섬 권한을 변경하지 못했습니다.");
    }

    public void setPermission(Player player, UUID islandId, String roleKey, IslandPermission permission, boolean allowed) {
        sendBodyResult(player, coreApiClient.setIslandPermissionResult(islandId, player.getUniqueId(), roleKey, permission, allowed).thenApply(body -> "섬 권한을 변경했습니다: " + jsonValue(body, "roleKey")), "섬 권한을 변경하지 못했습니다.");
    }

    public void listRoles(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "역할을 확인할 섬을 찾지 못했습니다.", "섬 역할을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandRoles(resolved).thenApply(islandMessages::roleList), "섬 역할을 불러오지 못했습니다."));
    }

    public void upsertRole(Player player, UUID islandId, IslandRole role, int weight, String displayName) {
        sendBodyResult(player, coreApiClient.upsertIslandRole(islandId, player.getUniqueId(), role, weight, displayName).thenApply(body -> "섬 역할 저장 완료: " + jsonValue(body, "role") + " weight=" + longValue(body, "weight") + " name=" + jsonValue(body, "displayName")), "섬 역할을 저장하지 못했습니다.");
    }

    public void upsertRole(Player player, UUID islandId, String roleKey, int weight, String displayName) {
        sendBodyResult(player, coreApiClient.upsertIslandRole(islandId, player.getUniqueId(), roleKey, weight, displayName).thenApply(body -> "섬 역할 저장 완료: " + jsonValue(body, "roleKey") + " weight=" + longValue(body, "weight") + " name=" + jsonValue(body, "displayName")), "섬 역할을 저장하지 못했습니다.");
    }

    public void resetRole(Player player, UUID islandId, IslandRole role) {
        sendBodyResult(player, coreApiClient.resetIslandRole(islandId, player.getUniqueId(), role).thenApply(body -> "섬 역할 초기화 완료: " + jsonValue(body, "role")), "섬 역할을 초기화하지 못했습니다.");
    }

    public void resetRole(Player player, UUID islandId, String roleKey) {
        sendBodyResult(player, coreApiClient.resetIslandRole(islandId, player.getUniqueId(), roleKey).thenApply(body -> "섬 역할 초기화 완료: " + jsonValue(body, "roleKey")), "섬 역할을 초기화하지 못했습니다.");
    }

    public void listIslandLogs(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "로그를 확인할 섬을 찾지 못했습니다.", "섬 로그를 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandLogs(resolved, 30).thenApply(islandMessages::islandLogList), "섬 로그를 불러오지 못했습니다."));
    }

    public void showBank(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "은행을 확인할 섬을 찾지 못했습니다.", "섬 은행을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.islandBank(resolved).thenApply(islandMessages::bankInfo), "섬 은행을 불러오지 못했습니다."));
    }

    public void depositBank(Player player, UUID islandId, String amount) {
        player.sendMessage(Component.text("섬 은행 입금은 경제 플러그인 연동이 필요한 작업이라 Paper Agent에서만 처리합니다."));
    }

    public void withdrawBank(Player player, UUID islandId, String amount) {
        player.sendMessage(Component.text("섬 은행 출금은 경제 플러그인 연동이 필요한 작업이라 Paper Agent에서만 처리합니다."));
    }
}
