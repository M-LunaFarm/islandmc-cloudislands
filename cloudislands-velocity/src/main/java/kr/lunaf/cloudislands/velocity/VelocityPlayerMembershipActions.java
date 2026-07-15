package kr.lunaf.cloudislands.velocity;

import com.velocitypowered.api.proxy.Player;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import net.kyori.adventure.text.Component;

public final class VelocityPlayerMembershipActions extends VelocityActionSupport {
    VelocityPlayerMembershipActions(VelocityActionContext context) {
        super(context);
    }

    public void invite(Player player, UUID islandId, UUID targetUuid) {
        withResolvedIsland(player, islandId, "초대를 보낼 섬을 찾지 못했습니다.", "초대를 생성하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.memberCommands().createInvite(resolved, player.getUniqueId(), targetUuid).thenApply(islandMessages::inviteCreate), "초대를 생성하지 못했습니다."));
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
        coreApiClient.members().pendingInvites(player.getUniqueId()).thenAccept(invites -> player.sendMessage(Component.text(islandMessages.invites(invites)))).exceptionally(error -> {
            player.sendMessage(Component.text("초대 목록을 불러오지 못했습니다."));
            return null;
        });
    }

    public void acceptInvite(Player player, UUID inviteId) {
        coreApiClient.memberCommands().acceptInvite(inviteId, player.getUniqueId()).thenAccept(result ->
            player.sendMessage(Component.text(result.applied() ? "섬 초대를 수락했습니다." : "섬 초대를 수락하지 못했습니다."))
        ).exceptionally(error -> {
            player.sendMessage(Component.text("섬 초대를 수락하지 못했습니다."));
            return null;
        });
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
        coreApiClient.memberCommands().declineInvite(inviteId, player.getUniqueId()).thenAccept(result ->
            player.sendMessage(Component.text(result.applied() ? "섬 초대를 거절했습니다." : "섬 초대를 거절하지 못했습니다."))
        ).exceptionally(error -> {
            player.sendMessage(Component.text("섬 초대를 거절하지 못했습니다."));
            return null;
        });
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
        withResolvedIsland(player, islandId, "멤버를 확인할 섬을 찾지 못했습니다.", "멤버 목록을 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.islands().listMembers(resolved).thenApply(islandMessages::memberList), "멤버 목록을 불러오지 못했습니다."));
    }

    public void listMembers(Player player, String target) {
        targetResolver.resolveIslandId(target)
            .thenAccept(islandId -> {
                if (islandId.equals(new UUID(0L, 0L))) {
                    player.sendMessage(Component.text("멤버를 확인할 섬 또는 플레이어를 찾지 못했습니다."));
                    return;
                }
                listMembers(player, islandId);
            })
            .exceptionally(error -> {
                player.sendMessage(Component.text("멤버를 확인할 섬 또는 플레이어를 찾지 못했습니다."));
                return null;
            });
    }

    public void setRole(Player player, UUID islandId, UUID targetUuid, String roleKey) {
        withResolvedIsland(player, islandId, "역할을 변경할 섬을 찾지 못했습니다.", "섬 멤버 역할을 변경하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.memberCommands().setRole(resolved, player.getUniqueId(), targetUuid, roleKey).thenApply(result -> islandMessages.memberAction("섬 멤버 역할 변경", result)), "섬 멤버 역할을 변경하지 못했습니다."));
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
        withResolvedIsland(player, islandId, "소유권을 양도할 섬을 찾지 못했습니다.", "섬 소유권을 양도하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.memberCommands().transferOwnership(resolved, player.getUniqueId(), targetUuid).thenApply(result -> islandMessages.memberAction("섬 소유권 양도", result)), "섬 소유권을 양도하지 못했습니다."));
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
        withResolvedIsland(player, islandId, "멤버를 추방할 섬을 찾지 못했습니다.", "섬 멤버를 추방하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.memberCommands().removeMember(resolved, player.getUniqueId(), targetUuid).thenApply(result -> islandMessages.memberAction("섬 멤버 추방", result)), "섬 멤버를 추방하지 못했습니다."));
    }

    public void leaveIsland(Player player, UUID islandId) {
        withResolvedIsland(player, islandId, "탈퇴할 섬을 찾지 못했습니다.", "섬 탈퇴를 처리하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.memberCommands().removeMember(resolved, player.getUniqueId(), player.getUniqueId()).thenApply(result -> islandMessages.memberAction("섬 탈퇴", result)), "섬 탈퇴를 처리하지 못했습니다."));
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
        withResolvedIsland(player, islandId, "방문자를 밴할 섬을 찾지 못했습니다.", "방문자를 밴하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.memberCommands().banVisitor(resolved, player.getUniqueId(), targetUuid, reason).thenApply(result -> islandMessages.memberAction("방문자 밴", result)), "방문자를 밴하지 못했습니다."));
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
        withResolvedIsland(player, islandId, "밴 목록을 확인할 섬을 찾지 못했습니다.", "밴 목록을 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.members().bans(resolved).thenApply(islandMessages::banList), "밴 목록을 불러오지 못했습니다."));
    }

    public void pardonVisitor(Player player, UUID islandId, UUID targetUuid) {
        withResolvedIsland(player, islandId, "방문자 밴을 해제할 섬을 찾지 못했습니다.", "방문자 밴을 해제하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.memberCommands().pardonVisitor(resolved, player.getUniqueId(), targetUuid).thenApply(result -> islandMessages.memberAction("방문자 밴 해제", result)), "방문자 밴을 해제하지 못했습니다."));
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
        withResolvedIsland(player, islandId, "방문자를 추방할 섬을 찾지 못했습니다.", "방문자를 추방하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.memberCommands().kickVisitor(resolved, player.getUniqueId(), targetUuid).thenApply(result -> islandMessages.memberAction("방문자 추방", result)), "방문자를 추방할 권한이 없거나 처리하지 못했습니다."));
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
        withResolvedIsland(player, islandId, "공개 상태를 변경할 섬을 찾지 못했습니다.", "섬 공개 상태를 변경하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.settingsCommands().setPublicAccess(resolved, player.getUniqueId(), publicAccess).thenApply(result -> islandMessages.settingsAction(publicAccess ? "섬 공개 변경" : "섬 비공개 변경", result)), "섬 공개 상태를 변경하지 못했습니다."));
    }

    public void setIslandName(Player player, UUID islandId, String name) {
        if (name == null || name.isBlank()) {
            player.sendMessage(Component.text("새 섬 이름을 입력해주세요."));
            return;
        }
        withResolvedIsland(player, islandId, "이름을 변경할 섬을 찾지 못했습니다.", "섬 이름을 변경하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.settingsCommands().setName(resolved, player.getUniqueId(), name).thenApply(result -> islandMessages.settingsAction("섬 이름 변경", result)), "섬 이름을 변경하지 못했습니다."));
    }

    public void setFlyFlag(Player player, UUID islandId, boolean enabled) {
        setBooleanFlag(player, islandId, kr.lunaf.cloudislands.api.model.IslandFlag.FLY, enabled, "비행");
    }

    public void setBooleanFlag(Player player, UUID islandId, kr.lunaf.cloudislands.api.model.IslandFlag flag, boolean enabled, String label) {
        withResolvedIsland(player, islandId, "설정할 섬을 찾지 못했습니다.", "섬 " + label + " 설정을 변경하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.settingsCommands().setFlag(resolved, player.getUniqueId(), flag, Boolean.toString(enabled)).thenApply(result -> islandMessages.settingsAction("섬 " + label + " 설정", result)), "섬 " + label + " 설정을 변경하지 못했습니다."));
    }

    public void setTextFlag(Player player, UUID islandId, kr.lunaf.cloudislands.api.model.IslandFlag flag, String value, String label) {
        String normalized = normalizeProfileValue(value);
        int maxLength = flag == kr.lunaf.cloudislands.api.model.IslandFlag.PROFILE_DESCRIPTION ? 256 : 128;
        if (normalized == null || normalized.length() > maxLength) {
            player.sendMessage(Component.text("섬 " + label + " 값은 " + maxLength + "자 이하의 일반 텍스트여야 합니다."));
            return;
        }
        withResolvedIsland(player, islandId, "설정할 섬을 찾지 못했습니다.", "섬 " + label + " 설정을 변경하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.settingsCommands().setFlag(resolved, player.getUniqueId(), flag, normalized).thenApply(result -> islandMessages.settingsAction("섬 " + label + " 설정", result)), "섬 " + label + " 설정을 변경하지 못했습니다."));
    }

    private static String normalizeProfileValue(String value) {
        String normalized = value == null ? "" : value.trim();
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        if (java.util.Set.of("clear", "reset", "remove", "none", "삭제", "초기화").contains(lower)) {
            return "";
        }
        return normalized.chars().anyMatch(Character::isISOControl) ? null : normalized;
    }

    public void listFlags(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "플래그를 확인할 섬을 찾지 못했습니다.", "섬 플래그를 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.environment().flagValues(resolved).thenApply(islandMessages::flagList), "섬 플래그를 불러오지 못했습니다."));
    }

    public void listHomes(Player player, UUID islandId) {
        withResolvedIsland(player, islandId, "홈을 확인할 섬을 찾지 못했습니다.", "섬 홈을 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.homeWarps().homes(resolved).thenApply(islandMessages::homeList), "섬 홈을 불러오지 못했습니다."));
    }

    public void setHome(Player player, UUID islandId, String name) {
        player.sendMessage(messages.component("home-set-paper-required"));
    }

    public void deleteHome(Player player, UUID islandId, String name) {
        withResolvedIsland(player, islandId, "삭제할 홈의 섬을 찾지 못했습니다.", "섬 홈을 삭제하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.homeWarpCommands().deleteHome(resolved, player.getUniqueId(), name).thenApply(result -> islandMessages.homeWarpAction("섬 홈 삭제", result)), "섬 홈을 삭제하지 못했습니다."));
    }

    public void setLocked(Player player, UUID islandId, boolean locked) {
        withResolvedIsland(player, islandId, "잠금 상태를 변경할 섬을 찾지 못했습니다.", "섬 잠금 상태를 변경하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.settingsCommands().setLocked(resolved, player.getUniqueId(), locked).thenApply(result -> islandMessages.settingsAction(locked ? "섬 잠금" : "섬 잠금 해제", result)), "섬 잠금 상태를 변경하지 못했습니다."));
    }

    public void listPermissions(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "권한을 확인할 섬을 찾지 못했습니다.", "섬 권한을 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.permissionQueries().permissions(resolved).thenApply(islandMessages::permissionList), "섬 권한을 불러오지 못했습니다."));
    }

    public void setPermission(Player player, UUID islandId, String roleKey, IslandPermission permission, boolean allowed) {
        withResolvedIsland(player, islandId, "권한을 변경할 섬을 찾지 못했습니다.", "섬 권한을 변경하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.permissions().setPermission(resolved, player.getUniqueId(), roleKey, permission, allowed).thenApply(result -> islandMessages.permissionAction("섬 권한 변경", result)), "섬 권한을 변경하지 못했습니다."));
    }

    public void setPermissionOverrideTarget(Player player, UUID islandId, String target, IslandPermission permission, boolean allowed) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("권한 예외를 설정할 플레이어를 찾지 못했습니다."));
                return;
            }
            withResolvedIsland(player, islandId, "권한 예외를 설정할 섬을 찾지 못했습니다.", "섬 권한 예외를 변경하지 못했습니다.",
                resolved -> sendTextResult(player, coreApiClient.permissions().setPermissionOverride(resolved, player.getUniqueId(), targetUuid, permission, allowed).thenApply(result -> islandMessages.permissionAction("섬 권한 예외 변경", result)), "섬 권한 예외를 변경하지 못했습니다."));
        }).exceptionally(error -> {
            player.sendMessage(Component.text("권한 예외를 설정할 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void listRoles(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "역할을 확인할 섬을 찾지 못했습니다.", "섬 역할을 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.permissionQueries().roles(resolved).thenApply(islandMessages::roleList), "섬 역할을 불러오지 못했습니다."));
    }

    public void upsertRole(Player player, UUID islandId, String roleKey, int weight, String displayName) {
        withResolvedIsland(player, islandId, "역할을 저장할 섬을 찾지 못했습니다.", "섬 역할을 저장하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.permissions().upsertRole(resolved, player.getUniqueId(), roleKey, weight, displayName).thenApply(result -> islandMessages.roleMutation("섬 역할 저장 완료", result)), "섬 역할을 저장하지 못했습니다."));
    }

    public void resetRole(Player player, UUID islandId, String roleKey) {
        withResolvedIsland(player, islandId, "역할을 초기화할 섬을 찾지 못했습니다.", "섬 역할을 초기화하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.permissions().resetRole(resolved, player.getUniqueId(), roleKey).thenApply(result -> islandMessages.roleMutation("섬 역할 초기화 완료", result)), "섬 역할을 초기화하지 못했습니다."));
    }

    public void listIslandLogs(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "로그를 확인할 섬을 찾지 못했습니다.", "섬 로그를 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.communication().listLogs(resolved, 30).thenApply(islandMessages::islandLogList), "섬 로그를 불러오지 못했습니다."));
    }

    public void showBank(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "은행을 확인할 섬을 찾지 못했습니다.", "섬 은행을 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.bank().islandBank(resolved).thenApply(view -> islandMessages.bankInfo(resolved, view)), "섬 은행을 불러오지 못했습니다."));
    }

    public void showBank(Player player, String target) {
        targetResolver.resolveIslandId(target)
            .thenAccept(islandId -> {
                if (islandId.equals(new UUID(0L, 0L))) {
                    player.sendMessage(Component.text("은행을 확인할 섬 또는 플레이어를 찾지 못했습니다."));
                    return;
                }
                showBank(player, islandId);
            })
            .exceptionally(error -> {
                player.sendMessage(Component.text("은행을 확인할 섬 또는 플레이어를 찾지 못했습니다."));
                return null;
            });
    }

    public void depositBank(Player player, UUID islandId, String amount) {
        player.sendMessage(messages.component("bank-deposit-paper-required"));
    }

    public void withdrawBank(Player player, UUID islandId, String amount) {
        player.sendMessage(messages.component("bank-withdraw-paper-required"));
    }
}
