package kr.lunaf.cloudislands.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandInviteActionResult;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.BanView;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.InviteView;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.MemberView;
import kr.lunaf.cloudislands.paper.application.MemberManagementUseCase;
import kr.lunaf.cloudislands.paper.application.MemberManagementUseCase.MemberActionResult;
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
    private final MemberManagementUseCase memberManagement;
    private final Runtime runtime;

    IslandMembershipCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.memberManagement = new MemberManagementUseCase(coreApiClient);
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("members") || subcommand.equals("member-menu") || subcommand.equals("멤버") || subcommand.equals("멤버관리")) {
            runtime.openIslandMemberMenu(player);
            return true;
        }
        if (subcommand.equals("member-list") || subcommand.equals("멤버목록")) {
            listIslandMembers(player);
            return true;
        }
        if (subcommand.equals("invite") || subcommand.equals("초대")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-invite-player-required", "초대할 플레이어를 입력해주세요."));
                return true;
            }
            inviteIslandMember(player, args[1]);
            return true;
        }
        if (subcommand.equals("invites") || subcommand.equals("invite-menu") || subcommand.equals("초대목록")) {
            IslandInviteMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
            return true;
        }
        if (subcommand.equals("invite-list")) {
            listPendingInvites(player);
            return true;
        }
        if (subcommand.equals("accept") || subcommand.equals("invite-accept") || subcommand.equals("초대수락")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-invite-accept-target-required", "수락할 초대 ID, 섬 ID/이름, 또는 초대한 플레이어를 입력해주세요."));
                return true;
            }
            acceptIslandInviteTarget(player, args[1]);
            return true;
        }
        if (subcommand.equals("decline") || subcommand.equals("invite-decline") || subcommand.equals("초대거절")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-invite-decline-target-required", "거절할 초대 ID, 섬 ID/이름, 또는 초대한 플레이어를 입력해주세요."));
                return true;
            }
            declineIslandInviteTarget(player, args[1]);
            return true;
        }
        if (subcommand.equals("kick") || subcommand.equals("remove-member") || subcommand.equals("추방")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-remove-player-required", "추방할 플레이어를 입력해주세요."));
                return true;
            }
            removeIslandMember(player, args[1]);
            return true;
        }
        if (subcommand.equals("trust") || subcommand.equals("신뢰")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-trust-player-required", "신뢰할 플레이어를 입력해주세요."));
                return true;
            }
            if (args.length > 2) {
                trustIslandMemberTemporary(player, args[1], args[2]);
            } else {
                setIslandMemberRole(player, args[1], "TRUSTED", "섬 신뢰 멤버로 설정했습니다.");
            }
            return true;
        }
        if (subcommand.equals("untrust") || subcommand.equals("신뢰해제")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-untrust-player-required", "신뢰 해제할 플레이어를 입력해주세요."));
                return true;
            }
            setIslandMemberRole(player, args[1], "MEMBER", "섬 신뢰를 해제했습니다.");
            return true;
        }
        if (subcommand.equals("promote") || subcommand.equals("승급")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-promote-player-required", "승급할 플레이어를 입력해주세요."));
                return true;
            }
            setIslandMemberRole(player, args[1], "MODERATOR", "섬 멤버를 승급했습니다.");
            return true;
        }
        if (subcommand.equals("demote") || subcommand.equals("강등")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("input-demote-player-required", "강등할 플레이어를 입력해주세요."));
                return true;
            }
            setIslandMemberRole(player, args[1], "MEMBER", "섬 멤버를 강등했습니다.");
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
            setIslandMemberRole(player, args[1], roleKey, "섬 멤버 역할을 " + roleKey + "(으)로 변경했습니다.");
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
            transferIslandOwnership(player, args[1]);
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
            listIslandBans(player);
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
                acceptIslandInviteTarget(player, inviteAction.inviteId().toString());
            } else {
                declineIslandInviteTarget(player, inviteAction.inviteId().toString());
            }
            return true;
        }
        if (action instanceof GuiAction.MemberPage memberPage) {
            runtime.openIslandMemberMenu(player, memberPage.page());
            return true;
        }
        if (action instanceof GuiAction.MemberDetail memberDetail) {
            runtime.message(player, runtime.routeMessage("member-detail-title", "멤버 상세"));
            runtime.message(player, "- " + runtime.routeMessage("member-detail-player", "플레이어: ") + memberDetail.displayName());
            runtime.message(player, "- " + runtime.routeMessage("member-detail-role", "역할: ") + memberDetail.role());
            runtime.message(player, "- " + runtime.routeMessage("member-detail-presence", "네트워크 상태: ") + memberDetail.presenceState());
            runtime.message(player, "- " + runtime.routeMessage("member-detail-last-seen", "마지막 활동: ") + (memberDetail.lastSeenAt().isBlank() ? runtime.routeMessage("member-detail-last-seen-empty", "기록 없음") : memberDetail.lastSeenAt()));
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
                setIslandMemberRole(
                    player,
                    roleChange.playerUuid().toString(),
                    roleChange.promote() ? "MODERATOR" : "MEMBER",
                    roleChange.promote() ? "섬 멤버를 승급했습니다." : "섬 멤버를 강등했습니다.");
            }
            return true;
        }
        if (action instanceof GuiAction.MemberRemoval memberRemoval) {
            if (!memberRemoval.confirmation()) {
                runtime.openConfirmation(player,
                    runtime.routeMessage("member-remove-confirm-title", "멤버 추방 확인"),
                    runtime.routeMessage("member-remove-confirm-description", "선택한 플레이어를 섬 멤버에서 제거합니다."),
                    Material.BARRIER,
                    runtime.routeMessage("member-remove-confirm-name", "멤버 추방"),
                    "island.member.remove.confirm",
                    Map.of("playerUuid", memberRemoval.playerUuid().toString()),
                    runtime.routeMessage("member-remove-confirm-lore", "클릭하면 Core에 멤버 추방을 요청합니다."),
                    "island.members.open");
                return true;
            }
            if (runtime.confirmationAccepted(player, action, click)) {
                removeIslandMember(player, memberRemoval.playerUuid().toString());
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
        if (action instanceof GuiAction.PermissionPage permissionPage) {
            runtime.openIslandPermissionMenu(player, permissionPage.page(), permissionPage.rolePage());
            return true;
        }
        if (action instanceof GuiAction.ChangePermission changePermission) {
            runtime.stageIslandPermission(
                player,
                changePermission.roleId().value(),
                changePermission.permission().name(),
                click.right() ? "false" : "true",
                changePermission.expectedVersion());
            return true;
        }
        if (action instanceof GuiAction.RoleWeightAdjust roleWeight) {
            runtime.adjustIslandRoleWeight(
                player,
                roleWeight.roleId().value(),
                Integer.toString(roleWeight.weight()),
                roleWeight.displayName(),
                click);
            return true;
        }
        if (action instanceof GuiAction.NoPayload noPayload) {
            return switch (noPayload.type()) {
                case MEMBERS_OPEN -> {
                    runtime.openIslandMemberMenu(player);
                    yield true;
                }
                case MEMBER_ROLE, MEMBER_LIST -> {
                    listIslandMembers(player);
                    yield true;
                }
                case MEMBER_INVITE, MEMBER_INVITE_HELP -> {
                    runtime.message(player, runtime.routeMessage("member-invite-help", "멤버 초대는 /섬 초대 <플레이어> 로 요청합니다."));
                    yield true;
                }
                case INVITES_OPEN -> {
                    IslandInviteMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
                    yield true;
                }
                case BANS_OPEN -> {
                    runtime.openIslandBanMenu(player);
                    yield true;
                }
                case BANS_LIST -> {
                    listIslandBans(player);
                    yield true;
                }
                case PERMISSIONS_OPEN -> {
                    runtime.openIslandPermissionMenu(player);
                    yield true;
                }
                case PERMISSIONS_LIST -> {
                    runtime.listIslandPermissions(player);
                    yield true;
                }
                case PERMISSIONS_SAVE -> {
                    runtime.saveStagedIslandPermissions(player);
                    yield true;
                }
                case PERMISSIONS_RESET -> {
                    runtime.resetStagedIslandPermissions(player);
                    yield true;
                }
                case ROLES_OPEN -> {
                    runtime.openIslandRoleMenu(player);
                    yield true;
                }
                case ROLES_LIST -> {
                    runtime.listIslandRoles(player);
                    yield true;
                }
                default -> false;
            };
        }
        return false;
    }

    private void listIslandMembers(Player player) {
        runtime.currentIsland(player, "섬 안에서만 멤버를 확인할 수 있습니다.").ifPresent(islandId -> {
            memberManagement.listMemberViews(islandId)
                .thenAccept(members -> runtime.message(player, memberListMessage(members)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 멤버를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandBans(Player player) {
        runtime.currentIsland(player, "섬 안에서만 밴 목록을 볼 수 있습니다.").ifPresent(islandId -> {
            memberManagement.listBanViews(islandId)
                .thenAccept(bans -> runtime.message(player, banListMessage(bans)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 밴 목록을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void inviteIslandMember(Player player, String target) {
        runtime.currentIsland(player, "섬 안에서만 플레이어를 초대할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_MEMBERS)) {
                runtime.message(player, runtime.routeMessage("member-invite-denied", "섬 멤버를 초대할 권한이 없습니다."));
                return;
            }
            Player online = plugin.getServer().getPlayerExact(target);
            UUID parsed = uuid(target);
            if (online != null || parsed != null) {
                sendIslandInvite(player, islandId, online == null ? parsed : online.getUniqueId());
                return;
            }
            memberManagement.playerUuidByName(target).thenAccept(profileUuid -> {
                sendIslandInvite(player, islandId, profileUuid == null ? plugin.getServer().getOfflinePlayer(target).getUniqueId() : profileUuid);
            }).exceptionally(error -> {
                sendIslandInvite(player, islandId, plugin.getServer().getOfflinePlayer(target).getUniqueId());
                return null;
            });
        });
    }

    private void sendIslandInvite(Player player, UUID islandId, UUID targetUuid) {
        runtime.mutate("island.invite.create", () -> memberManagement.createInviteView(islandId, player.getUniqueId(), targetUuid))
            .thenAccept(invite -> runtime.message(player, inviteCreatedMessage(invite)))
            .exceptionally(error -> {
                runtime.message(player, "섬 초대를 보내지 못했습니다.");
                return null;
            });
    }

    private void removeIslandMember(Player player, String target) {
        runtime.currentIsland(player, "섬 안에서만 멤버를 추방할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_MEMBERS)) {
                runtime.message(player, runtime.routeMessage("member-remove-denied", "섬 멤버를 추방할 권한이 없습니다."));
                return;
            }
            runtime.resolvePlayerUuid(target).thenAccept(targetUuid -> {
                runtime.mutateIdempotent("island.member.remove", () -> memberManagement.removeMemberAction(islandId, player.getUniqueId(), targetUuid))
                    .thenAccept(result -> runtime.message(player, memberActionMessage("섬 멤버 제거", targetUuid, result)))
                    .exceptionally(error -> {
                        runtime.message(player, "섬 멤버를 제거하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void setIslandMemberRole(Player player, String target, String roleKey, String successMessage) {
        runtime.currentIsland(player, "섬 안에서만 멤버 역할을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_ROLES)) {
                runtime.message(player, runtime.routeMessage("member-role-denied", "섬 멤버 역할을 변경할 권한이 없습니다."));
                return;
            }
            runtime.resolvePlayerUuid(target).thenAccept(targetUuid -> {
                runtime.mutate("island.member.role.set", () -> memberManagement.setRoleAction(islandId, player.getUniqueId(), targetUuid, roleKey))
                    .thenAccept(result -> runtime.message(player, memberActionMessage(successMessage, targetUuid, result)))
                    .exceptionally(error -> {
                        runtime.message(player, "섬 멤버 역할을 변경하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void trustIslandMemberTemporary(Player player, String target, String duration) {
        long seconds = parseDurationSeconds(duration, 3600L);
        if (seconds <= 0L) {
            runtime.message(player, "신뢰 기간을 올바르게 입력해주세요. 예: 30m, 2h, 1d");
            return;
        }
        runtime.currentIsland(player, "섬 안에서만 임시 신뢰를 설정할 수 있습니다.").ifPresent(islandId -> {
            if (!runtime.allowed(player, IslandPermission.MANAGE_ROLES)) {
                runtime.message(player, runtime.routeMessage("member-role-denied", "섬 멤버 역할을 변경할 권한이 없습니다."));
                return;
            }
            runtime.resolvePlayerUuid(target).thenAccept(targetUuid -> {
                runtime.mutate("island.member.temp-trust", () -> memberManagement.trustTemporarilyAction(islandId, player.getUniqueId(), targetUuid, seconds))
                    .thenAccept(result -> runtime.message(player, memberActionMessage("섬 임시 신뢰 설정 " + formatDuration(seconds), targetUuid, result) + (result.expiresAt().isBlank() ? "" : " 만료=" + result.expiresAt())))
                    .exceptionally(error -> {
                        runtime.message(player, "섬 임시 신뢰를 설정하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void transferIslandOwnership(Player player, String target) {
        runtime.currentIsland(player, "섬 안에서만 소유권을 양도할 수 있습니다.").ifPresent(islandId -> {
            runtime.resolvePlayerUuid(target).thenAccept(targetUuid -> {
                runtime.mutateIdempotent("island.ownership.transfer", () -> memberManagement.transferOwnershipAction(islandId, player.getUniqueId(), targetUuid))
                    .thenAccept(result -> runtime.message(player, memberActionMessage("섬 소유권 양도", targetUuid, result)))
                    .exceptionally(error -> {
                        runtime.message(player, "섬 소유권을 양도하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void listPendingInvites(Player player) {
        memberManagement.listPendingInviteViews(player.getUniqueId())
            .thenAccept(invites -> runtime.message(player, inviteListMessage(invites)))
            .exceptionally(error -> {
                runtime.message(player, "섬 초대 목록을 불러오지 못했습니다.");
                return null;
            });
    }

    private void acceptIslandInviteTarget(Player player, String target) {
        resolveInviteTarget(player, target).thenAccept(inviteId -> {
            if (inviteId == null) {
                runtime.message(player, "대상 초대를 찾지 못했습니다.");
                return;
            }
            acceptIslandInvite(player, inviteId);
        }).exceptionally(error -> {
            runtime.message(player, "대상 초대를 찾지 못했습니다.");
            return null;
        });
    }

    private void acceptIslandInvite(Player player, UUID inviteId) {
        if (inviteId == null) {
            runtime.message(player, runtime.routeMessage("input-invite-id-invalid", "올바른 초대 ID를 입력해주세요."));
            return;
        }
        runtime.mutate("island.invite.accept", () -> memberManagement.acceptInviteAction(inviteId, player.getUniqueId()))
            .thenAccept(result -> runtime.message(player, inviteActionMessage("섬 초대 수락", inviteId, result)))
            .exceptionally(error -> {
                runtime.message(player, "섬 초대를 수락하지 못했습니다.");
                return null;
            });
    }

    private void declineIslandInviteTarget(Player player, String target) {
        resolveInviteTarget(player, target).thenAccept(inviteId -> {
            if (inviteId == null) {
                runtime.message(player, "대상 초대를 찾지 못했습니다.");
                return;
            }
            declineIslandInvite(player, inviteId);
        }).exceptionally(error -> {
            runtime.message(player, "대상 초대를 찾지 못했습니다.");
            return null;
        });
    }

    private void declineIslandInvite(Player player, UUID inviteId) {
        if (inviteId == null) {
            runtime.message(player, runtime.routeMessage("input-invite-id-invalid", "올바른 초대 ID를 입력해주세요."));
            return;
        }
        runtime.mutate("island.invite.decline", () -> memberManagement.declineInviteAction(inviteId, player.getUniqueId()))
            .thenAccept(result -> runtime.message(player, inviteActionMessage("섬 초대 거절", inviteId, result)))
            .exceptionally(error -> {
                runtime.message(player, "섬 초대를 거절하지 못했습니다.");
                return null;
            });
    }

    private CompletableFuture<UUID> resolveInviteTarget(Player player, String target) {
        UUID parsed = uuid(target);
        if (parsed != null) {
            return memberManagement.resolveInviteIdOrDirectId(player.getUniqueId(), parsed);
        }
        Player online = plugin.getServer().getPlayerExact(target);
        if (online != null) {
            return memberManagement.resolveInviteByPlayerUuid(player.getUniqueId(), online.getUniqueId());
        }
        return memberManagement.resolveInviteByPlayerNameOrIslandName(player.getUniqueId(), target);
    }

    private static String memberListMessage(List<MemberView> members) {
        List<String> entries = new ArrayList<>();
        for (MemberView member : members == null ? List.<MemberView>of() : members) {
            String playerUuid = member.playerUuid();
            String role = member.role();
            String expiresAt = member.expiresAt();
            if (!playerUuid.isBlank()) {
                entries.add(compactId(playerUuid) + (role.isBlank() ? "" : " 역할=" + role) + (expiresAt.isBlank() ? "" : " 만료=" + expiresAt));
            }
        }
        return entries.isEmpty() ? "섬 멤버가 없습니다." : "섬 멤버: " + String.join(", ", entries);
    }

    private static String banListMessage(List<BanView> bans) {
        List<String> entries = new ArrayList<>();
        for (BanView ban : bans == null ? List.<BanView>of() : bans) {
            String bannedUuid = ban.bannedUuid();
            String reason = ban.reason();
            if (!bannedUuid.isBlank()) {
                entries.add(bannedUuid + (reason.isBlank() ? "" : " " + reason));
            }
        }
        return entries.isEmpty() ? "섬 밴 목록이 비어 있습니다." : "섬 밴 목록: " + String.join(", ", entries);
    }

    private static String inviteListMessage(List<InviteView> invites) {
        List<String> entries = new ArrayList<>();
        for (InviteView invite : invites == null ? List.<InviteView>of() : invites) {
            String inviteId = invite.inviteId();
            String islandId = invite.islandId();
            String inviterUuid = invite.inviterUuid();
            if (!inviteId.isBlank()) {
                entries.add(compactId(inviteId) + (islandId.isBlank() ? "" : " 섬=" + compactId(islandId)) + (inviterUuid.isBlank() ? "" : " 초대한사람=" + compactId(inviterUuid)));
            }
        }
        return entries.isEmpty() ? "대기 중인 섬 초대가 없습니다." : "섬 초대: " + String.join(", ", entries);
    }

    private static String inviteActionMessage(String label, UUID inviteId, IslandInviteActionResult result) {
        return actionStatusMessage(label, inviteId == null ? "" : inviteId.toString(), result != null && result.applied(), result == null ? "" : result.code());
    }

    private static String inviteCreatedMessage(InviteView invite) {
        String inviteId = invite == null ? "" : invite.inviteId();
        return actionStatusMessage("섬 초대", inviteId, true, "");
    }

    private static String memberActionMessage(String label, UUID targetId, MemberActionResult result) {
        return actionStatusMessage(label, targetId == null ? "" : targetId.toString(), result != null && result.accepted(), result == null ? "" : result.code());
    }

    private static String actionStatusMessage(String label, String targetId, boolean accepted, String code) {
        StringBuilder builder = new StringBuilder(label)
            .append(accepted ? " 완료" : " 실패");
        if (targetId != null && !targetId.isBlank()) {
            builder.append(": 대상=").append(compactId(targetId));
        }
        if (code != null && !code.isBlank()) {
            builder.append(" code=").append(code);
        }
        return builder.toString();
    }

    private static String compactId(String value) {
        if (value == null || value.length() != 36 || !value.contains("-")) {
            return value == null ? "" : value;
        }
        return value.substring(0, 8);
    }

    private long parseDurationSeconds(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        long multiplier = 1L;
        if (normalized.endsWith("m")) {
            multiplier = 60L;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("h")) {
            multiplier = 3600L;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("d")) {
            multiplier = 86400L;
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        long amount = runtime.longValue(normalized, -1L);
        if (amount <= 0L) {
            return -1L;
        }
        return Math.max(60L, Math.min(amount * multiplier, 2_592_000L));
    }

    private static String formatDuration(long seconds) {
        if (seconds % 86400L == 0L) {
            return (seconds / 86400L) + "d";
        }
        if (seconds % 3600L == 0L) {
            return (seconds / 3600L) + "h";
        }
        if (seconds % 60L == 0L) {
            return (seconds / 60L) + "m";
        }
        return seconds + "s";
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
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

        Optional<UUID> currentIsland(Player player, String missingMessage);

        boolean allowed(Player player, IslandPermission permission);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);

        CompletableFuture<UUID> resolvePlayerUuid(String value);

        void openIslandMemberMenu(Player player);

        void openIslandMemberMenu(Player player, int page);

        void banIslandVisitor(Player player, String target, String reason);

        void pardonIslandVisitor(Player player, String target);

        void kickIslandVisitor(Player player, String target);

        void openIslandBanMenu(Player player);

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
