package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandInviteActionResult;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.IslandQueryClient;
import kr.lunaf.cloudislands.coreclient.MemberActionView;
import kr.lunaf.cloudislands.coreclient.MemberCommandClient;
import kr.lunaf.cloudislands.coreclient.MemberCursor;
import kr.lunaf.cloudislands.coreclient.MemberPage;
import kr.lunaf.cloudislands.coreclient.MemberQueryClient;
import org.junit.jupiter.api.Test;

class MemberManagementUseCaseTest {
    @Test
    void resolvesDirectInviteTargetThroughPendingInviteIndex() {
        UUID playerUuid = uuid("00000000-0000-0000-0000-000000000001");
        UUID inviteId = uuid("00000000-0000-0000-0000-000000000010");
        UUID islandId = uuid("00000000-0000-0000-0000-000000000020");

        MemberManagementUseCase useCase = new MemberManagementUseCase(client(Map.of(
            "listPendingInvites", "{\"invites\":[{\"inviteId\":\"" + inviteId + "\",\"islandId\":\"" + islandId + "\",\"inviterUuid\":\"00000000-0000-0000-0000-000000000030\"}]}"
        )));

        assertEquals(inviteId, useCase.resolveInviteIdOrDirectId(playerUuid, islandId).join());
        assertEquals(inviteId, useCase.resolveInviteByPlayerUuid(playerUuid, islandId).join());
    }

    @Test
    void keepsUnknownDirectUuidAsInviteIdForBackwardCompatibleCommands() {
        UUID playerUuid = uuid("00000000-0000-0000-0000-000000000001");
        UUID requestedInviteId = uuid("00000000-0000-0000-0000-000000000099");

        MemberManagementUseCase useCase = new MemberManagementUseCase(client(Map.of(
            "listPendingInvites", "{\"invites\":[]}"
        )));

        assertEquals(requestedInviteId, useCase.resolveInviteIdOrDirectId(playerUuid, requestedInviteId).join());
    }

    @Test
    void resolvesTextTargetByPlayerThenFallsBackToIslandName() {
        UUID playerUuid = uuid("00000000-0000-0000-0000-000000000001");
        UUID inviteId = uuid("00000000-0000-0000-0000-000000000010");
        UUID islandId = uuid("00000000-0000-0000-0000-000000000020");

        MemberManagementUseCase useCase = new MemberManagementUseCase(client(Map.of(
            "listPendingInvites", "{\"invites\":[{\"inviteId\":\"" + inviteId + "\",\"islandId\":\"" + islandId + "\",\"inviterUuid\":\"00000000-0000-0000-0000-000000000030\"}]}",
            "islandInfoByName", "{\"islandId\":\"" + islandId + "\"}",
            "playerInfoByName", "ERROR"
        )));

        assertEquals(inviteId, useCase.resolveInviteByPlayerNameOrIslandName(playerUuid, "spawn").join());
    }

    @Test
    void typedMemberInviteAndBanViewsHideRawJsonFromCommandPresentation() {
        UUID playerUuid = uuid("00000000-0000-0000-0000-000000000001");
        UUID actorUuid = uuid("00000000-0000-0000-0000-000000000002");
        UUID targetUuid = uuid("00000000-0000-0000-0000-000000000003");
        UUID inviteId = uuid("00000000-0000-0000-0000-000000000010");
        UUID islandId = uuid("00000000-0000-0000-0000-000000000020");

        MemberManagementUseCase useCase = new MemberManagementUseCase(client(Map.of(
            "listIslandMembers", "{\"members\":[{\"playerUuid\":\"" + targetUuid + "\",\"role\":\"TRUSTED\",\"expiresAt\":\"2026-06-21T10:00:00Z\"}]}",
            "listPendingInvites", "{\"invites\":[{\"inviteId\":\"" + inviteId + "\",\"islandId\":\"" + islandId + "\",\"inviterUuid\":\"" + actorUuid + "\"}]}",
            "createIslandInvite", "{\"inviteId\":\"" + inviteId + "\",\"islandId\":\"" + islandId + "\",\"inviterUuid\":\"" + actorUuid + "\"}",
            "acceptIslandInviteResult", "{\"accepted\":true,\"code\":\"INVITE_ACCEPTED\"}",
            "declineIslandInviteResult", "{\"accepted\":false,\"code\":\"INVITE_EXPIRED\"}",
            "listIslandBans", "{\"bans\":[{\"bannedUuid\":\"" + targetUuid + "\",\"reason\":\"test\"}]}"
        )));

        assertEquals("TRUSTED", useCase.listMemberViews(islandId).join().get(0).role());
        assertEquals("2026-06-21T10:00:00Z", useCase.listMemberViews(islandId).join().get(0).expiresAt());
        assertEquals(inviteId.toString(), useCase.listPendingInviteViews(playerUuid).join().get(0).inviteId());
        assertEquals(inviteId.toString(), useCase.createInviteView(islandId, actorUuid, targetUuid).join().inviteId());
        assertEquals("ACCEPTED", useCase.acceptInviteAction(inviteId, playerUuid).join().code());
        assertEquals("INVITE_EXPIRED", useCase.declineInviteAction(inviteId, playerUuid).join().code());
        assertEquals("test", useCase.listBanViews(islandId).join().get(0).reason());
    }

    @Test
    void typedMemberActionsExposeStatusAndTemporaryTrustExpiry() {
        UUID actorUuid = uuid("00000000-0000-0000-0000-000000000002");
        UUID targetUuid = uuid("00000000-0000-0000-0000-000000000003");
        UUID islandId = uuid("00000000-0000-0000-0000-000000000020");

        MemberManagementUseCase useCase = new MemberManagementUseCase(client(Map.of(
            "removeIslandMemberResult", "{\"accepted\":true,\"code\":\"MEMBER_REMOVED\"}",
            "setIslandMemberResult", "{\"accepted\":true,\"code\":\"MEMBER_ROLE_SET\"}",
            "trustIslandMemberTemporary", "{\"accepted\":true,\"code\":\"TEMP_TRUST_SET\",\"expiresAt\":\"2026-06-21T10:00:00Z\"}",
            "transferIslandOwnershipResult", "{\"accepted\":true,\"code\":\"OWNERSHIP_TRANSFERRED\"}",
            "banIslandVisitorResult", "{\"accepted\":false,\"code\":\"VISITOR_BAN_DENIED\"}",
            "pardonIslandVisitorResult", "{\"accepted\":true,\"code\":\"VISITOR_PARDONED\"}",
            "kickIslandVisitorResult", "{\"accepted\":true,\"code\":\"VISITOR_KICKED\"}"
        )));

        assertEquals("MEMBER_REMOVED", useCase.removeMemberAction(islandId, actorUuid, targetUuid).join().code());
        assertEquals("MEMBER_ROLE_SET", useCase.setRoleAction(islandId, actorUuid, targetUuid, "trusted").join().code());
        assertEquals("2026-06-21T10:00:00Z", useCase.trustTemporarilyAction(islandId, actorUuid, targetUuid, 3600L).join().expiresAt());
        assertEquals("OWNERSHIP_TRANSFERRED", useCase.transferOwnershipAction(islandId, actorUuid, targetUuid).join().code());
        assertEquals(false, useCase.banVisitorAction(islandId, actorUuid, targetUuid, "reason").join().accepted());
        assertEquals("VISITOR_PARDONED", useCase.pardonVisitorAction(islandId, actorUuid, targetUuid).join().code());
        assertEquals("VISITOR_KICKED", useCase.kickVisitorAction(islandId, actorUuid, targetUuid).join().code());
    }

    private static CoreApiClient client(Map<String, String> bodies) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class, IslandQueryClient.class, MemberQueryClient.class, MemberCommandClient.class},
            (_proxy, method, _args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "test-core-client";
                        case "hashCode" -> 1;
                        case "equals" -> _proxy == _args[0];
                        default -> null;
                    };
                }
                if (method.getName().equals("islands")) {
                    return (IslandQueryClient) _proxy;
                }
                if (method.getName().equals("members")) {
                    return (MemberQueryClient) _proxy;
                }
                if (method.getName().equals("memberCommands")) {
                    return (MemberCommandClient) _proxy;
                }
                String value = bodies.get(method.getName());
                if (method.getName().equals("findIslandByName")) {
                    return completedView("islandInfoByName", bodies);
                }
                if (method.getName().equals("getIsland") || method.getName().equals("getIslandByOwner")) {
                    return completedView("islandInfoByName", bodies);
                }
                if (method.getName().equals("memberSnapshots")) {
                    return CompletableFuture.completedFuture(List.<IslandMemberSnapshot>of());
                }
                if (method.getName().equals("listMembers")) {
                    if (_args != null && _args.length == 2 && _args[1] instanceof MemberCursor) {
                        return CompletableFuture.completedFuture(new MemberPage(List.of(), (MemberCursor) _args[1], null, 0));
                    }
                    return CompletableFuture.completedFuture(memberViews(bodies.getOrDefault("listIslandMembers", "{\"members\":[]}")));
                }
                if (method.getName().equals("playerProfileByName")) {
                    String profile = bodies.get(method.getName());
                    if (profile == null) {
                        profile = bodies.get("playerInfoByName");
                    }
                    if ("ERROR".equals(profile)) {
                        return CompletableFuture.failedFuture(new IllegalStateException(method.getName() + " failed"));
                    }
                    return CompletableFuture.completedFuture(playerProfile(profile == null ? "{}" : profile));
                }
                if (method.getName().equals("pendingInvites")) {
                    return CompletableFuture.completedFuture(inviteViews(bodies.getOrDefault("listPendingInvites", "{\"invites\":[]}")));
                }
                if (method.getName().equals("bans")) {
                    return CompletableFuture.completedFuture(banViews(bodies.getOrDefault("listIslandBans", "{\"bans\":[]}")));
                }
                if (method.getName().equals("createInvite")) {
                    return CompletableFuture.completedFuture(inviteView(bodies.getOrDefault("createIslandInvite", "{}")));
                }
                if (method.getName().equals("acceptInvite")) {
                    return inviteAction(bodies.getOrDefault("acceptIslandInviteResult", "{}"), "ACCEPTED");
                }
                if (method.getName().equals("declineInvite")) {
                    return inviteAction(bodies.getOrDefault("declineIslandInviteResult", "{}"), "DECLINED");
                }
                if (method.getName().equals("removeMember")) {
                    return memberAction(bodies.getOrDefault("removeIslandMemberResult", "{}"));
                }
                if (method.getName().equals("setRole")) {
                    return memberAction(bodies.getOrDefault("setIslandMemberResult", "{}"));
                }
                if (method.getName().equals("trustTemporarily")) {
                    return memberAction(bodies.getOrDefault("trustIslandMemberTemporary", "{}"));
                }
                if (method.getName().equals("transferOwnership")) {
                    return memberAction(bodies.getOrDefault("transferIslandOwnershipResult", "{}"));
                }
                if (method.getName().equals("banVisitor")) {
                    return memberAction(bodies.getOrDefault("banIslandVisitorResult", "{}"));
                }
                if (method.getName().equals("pardonVisitor")) {
                    return memberAction(bodies.getOrDefault("pardonIslandVisitorResult", "{}"));
                }
                if (method.getName().equals("kickVisitor")) {
                    return memberAction(bodies.getOrDefault("kickIslandVisitorResult", "{}"));
                }
                if ("ERROR".equals(value)) {
                    return CompletableFuture.failedFuture(new IllegalStateException(method.getName() + " failed"));
                }
                return CompletableFuture.completedFuture(value == null ? "{}" : value);
            });
    }

    private static CompletableFuture<CoreGuiViews.IslandInfoView> completedView(String key, Map<String, String> bodies) {
        String value = bodies.get(key);
        if ("ERROR".equals(value)) {
            return CompletableFuture.failedFuture(new IllegalStateException(key + " failed"));
        }
        return CompletableFuture.completedFuture(islandInfoView(value == null ? "{}" : value));
    }

    private static List<CoreGuiViews.MemberView> memberViews(String value) {
        if (value == null || value.contains("\"members\":[]")) {
            return List.of();
        }
        return List.of(new CoreGuiViews.MemberView(
            text(value, "playerUuid"),
            firstText(value, "roleKey", "role"),
            text(value, "joinedAt"),
            text(value, "playerName"),
            text(value, "lastSeenAt"),
            text(value, "presenceState"),
            text(value, "presenceSource"),
            text(value, "expiresAt")
        ));
    }

    private static List<CoreGuiViews.InviteView> inviteViews(String value) {
        if (value == null || value.contains("\"invites\":[]")) {
            return List.of();
        }
        return List.of(inviteView(value));
    }

    private static CoreGuiViews.InviteView inviteView(String value) {
        return new CoreGuiViews.InviteView(
            text(value, "inviteId"),
            text(value, "islandId"),
            text(value, "inviterUuid"),
            text(value, "targetUuid"),
            text(value, "state"),
            text(value, "createdAt"),
            text(value, "expiresAt")
        );
    }

    private static List<CoreGuiViews.BanView> banViews(String value) {
        if (value == null || value.contains("\"bans\":[]")) {
            return List.of();
        }
        return List.of(new CoreGuiViews.BanView(
            text(value, "bannedUuid"),
            text(value, "actorUuid"),
            text(value, "reason"),
            text(value, "createdAt"),
            text(value, "expiresAt")
        ));
    }

    private static CoreGuiViews.PlayerProfileView playerProfile(String value) {
        return new CoreGuiViews.PlayerProfileView(text(value, "playerUuid"), text(value, "primaryIslandId"));
    }

    private static CoreGuiViews.IslandInfoView islandInfoView(String value) {
        return new CoreGuiViews.IslandInfoView(
            text(value, "name"),
            text(value, "state"),
            text(value, "islandId"),
            number(value, "level"),
            text(value, "worth"),
            bool(value, "publicAccess"),
            bool(value, "locked"),
            number(value, "size"),
            number(value, "border"),
            text(value, "ownerUuid"),
            text(value, "createdAt"),
            text(value, "updatedAt")
        );
    }

    private static CompletableFuture<IslandInviteActionResult> inviteAction(String value, String successCode) {
        boolean accepted = accepted(value);
        String code = text(value, "code");
        return CompletableFuture.completedFuture(new IslandInviteActionResult(accepted, accepted ? successCode : code));
    }

    private static CompletableFuture<MemberActionView> memberAction(String value) {
        return CompletableFuture.completedFuture(new MemberActionView(accepted(value), text(value, "code"), text(value, "expiresAt")));
    }

    private static boolean accepted(String value) {
        return value != null && value.contains("\"accepted\":true");
    }

    private static String text(String value, String key) {
        if (value == null) {
            return "";
        }
        String marker = "\"" + key + "\":\"";
        int start = value.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int valueStart = start + marker.length();
        int valueEnd = value.indexOf('"', valueStart);
        return valueEnd < 0 ? "" : value.substring(valueStart, valueEnd);
    }

    private static String firstText(String value, String... keys) {
        for (String key : keys) {
            String text = text(value, key);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private static long number(String value, String key) {
        String marker = "\"" + key + "\":";
        int start = value == null ? -1 : value.indexOf(marker);
        if (start < 0) {
            return 0L;
        }
        int valueStart = start + marker.length();
        int valueEnd = valueStart;
        while (valueEnd < value.length()) {
            char ch = value.charAt(valueEnd);
            if (!Character.isDigit(ch) && ch != '-') {
                break;
            }
            valueEnd++;
        }
        return valueEnd == valueStart ? 0L : Long.parseLong(value.substring(valueStart, valueEnd));
    }

    private static boolean bool(String value, String key) {
        String marker = "\"" + key + "\":";
        int start = value == null ? -1 : value.indexOf(marker);
        if (start < 0) {
            return false;
        }
        int valueStart = start + marker.length();
        return value.startsWith("true", valueStart);
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
