package kr.lunaf.cloudislands.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public final class VelocityRoutingController {
    private final ProxyServer proxy;
    private final CoreApiClient coreApiClient;
    private final String fallbackServer;

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer) {
        this.proxy = proxy;
        this.coreApiClient = coreApiClient;
        this.fallbackServer = fallbackServer;
    }

    public void createIsland(Player player, String templateId) {
        coreApiClient.createIsland(player.getUniqueId(), templateId).thenAccept(result -> {
            if (result == null || !result.accepted()) {
                String code = result == null ? "FAILED" : result.code();
                player.sendMessage(Component.text(messageForCreateFailure(code)));
                return;
            }
            player.sendActionBar(Component.text("섬을 생성하고 있습니다."));
            if (result.ticket() != null) {
                route(player, result.ticket(), "섬으로 이동하지 못했습니다.");
            }
        }).exceptionally(error -> {
            player.sendMessage(Component.text("현재 섬 서비스 일부 기능이 점검 중입니다."));
            return null;
        });
    }

    public void deleteIsland(Player player, UUID islandId) {
        coreApiClient.deleteIsland(player.getUniqueId(), islandId).thenAccept(result -> {
            if (result != null && result.accepted()) {
                player.sendMessage(Component.text("섬을 삭제했습니다."));
                return;
            }
            player.sendMessage(Component.text("섬을 삭제할 수 없습니다."));
        }).exceptionally(error -> {
            player.sendMessage(Component.text("섬 삭제를 처리하지 못했습니다."));
            return null;
        });
    }

    public void resetIsland(Player player, UUID islandId, String reason) {
        coreApiClient.resetIsland(islandId, player.getUniqueId(), reason)
            .thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 리셋을 요청했습니다." : body)))
            .exceptionally(error -> {
                player.sendMessage(Component.text("섬 리셋을 요청하지 못했습니다."));
                return null;
            });
    }

    public void showMyIsland(Player player) {
        sendPlayerPayloadFuture(player, coreApiClient.islandInfoByOwner(player.getUniqueId()), "섬 정보를 불러오지 못했습니다.", "섬 정보를 불러왔습니다.");
    }

    public void showIslandSettings(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.islandInfo(islandId), "섬 설정을 불러오지 못했습니다.", "섬 설정을 불러왔습니다.");
    }

    public void showIslandLevel(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.islandInfo(islandId), "섬 레벨을 불러오지 못했습니다.", "섬 레벨 정보를 불러왔습니다.");
    }

    public void showIslandWorth(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.islandInfo(islandId), "섬 가치를 불러오지 못했습니다.", "섬 가치 정보를 불러왔습니다.");
    }

    public void showIslandSize(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.islandInfo(islandId), "섬 크기를 불러오지 못했습니다.", "섬 크기 정보를 불러왔습니다.");
    }

    public void showIslandBorder(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.islandInfo(islandId), "섬 경계를 불러오지 못했습니다.", "섬 경계 정보를 불러왔습니다.");
    }

    public void showBiome(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.islandBiome(islandId), "섬 바이옴을 불러오지 못했습니다.", "섬 바이옴 정보를 불러왔습니다.");
    }

    public void setBiome(Player player, UUID islandId, String biomeKey) {
        sendActionResult(player, coreApiClient.setIslandBiome(islandId, player.getUniqueId(), biomeKey), "섬 바이옴을 변경했습니다.", "섬 바이옴을 변경하지 못했습니다.");
    }

    public void routeHome(Player player) {
        routeHome(player, "default");
    }

    public void routeHome(Player player, String homeName) {
        routeFuture(player, coreApiClient.createHomeTicket(player.getUniqueId(), homeName), "현재 섬 서비스 일부 기능이 점검 중입니다.");
    }

    public void routeVisit(Player player, UUID targetIslandId) {
        routeFuture(player, coreApiClient.createVisitTicket(player.getUniqueId(), targetIslandId), "현재 섬 서버가 혼잡합니다. 잠시 후 다시 시도해주세요.");
    }

    public void routeVisitOwner(Player player, UUID ownerUuid) {
        if (ownerUuid.equals(new UUID(0L, 0L))) {
            player.sendMessage(Component.text("방문할 플레이어를 찾을 수 없습니다."));
            return;
        }
        coreApiClient.islandInfoByOwner(ownerUuid).thenAccept(body -> {
            UUID islandId = parseUuid(jsonValue(body, "islandId"));
            if (islandId.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("방문할 섬을 찾을 수 없습니다."));
                return;
            }
            routeVisit(player, islandId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("방문할 섬을 불러오지 못했습니다."));
            return null;
        });
    }

    public void routeVisitName(Player player, String islandName) {
        if (islandName == null || islandName.isBlank()) {
            player.sendMessage(Component.text("방문할 섬 이름을 입력해주세요."));
            return;
        }
        coreApiClient.islandInfoByName(islandName).thenAccept(body -> {
            UUID islandId = parseUuid(jsonValue(body, "islandId"));
            if (islandId.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("방문할 섬을 찾을 수 없습니다."));
                return;
            }
            routeVisit(player, islandId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("방문할 섬을 불러오지 못했습니다."));
            return null;
        });
    }

    public void routeVisitNamedTarget(Player player, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            player.sendMessage(Component.text("방문할 대상 이름을 입력해주세요."));
            return;
        }
        coreApiClient.playerInfoByName(targetName).thenAccept(body -> {
            UUID primaryIslandId = parseUuid(jsonValue(body, "primaryIslandId"));
            if (primaryIslandId.equals(new UUID(0L, 0L))) {
                routeVisitName(player, targetName);
                return;
            }
            routeVisit(player, primaryIslandId);
        }).exceptionally(error -> {
            routeVisitName(player, targetName);
            return null;
        });
    }

    public void recordPlayerProfile(Player player) {
        coreApiClient.touchPlayerProfile(player.getUniqueId(), player.getUsername())
            .exceptionally(error -> null);
    }

    public void listMyIslands(Player player) {
        coreApiClient.listPlayerIslands(player.getUniqueId())
            .thenAccept(body -> player.sendMessage(Component.text(playerIslandListMessage(body))))
            .exceptionally(error -> {
                player.sendMessage(Component.text("내 섬 목록을 불러오지 못했습니다."));
                return null;
            });
    }

    private String playerIslandListMessage(String body) {
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String islandId = islandText(object, "islandId");
            if (!islandId.isBlank()) {
                String name = islandText(object, "name");
                String role = islandText(object, "role");
                long level = islandNumber(object, "level");
                entries.add((name.isBlank() ? islandId : name) + " [" + (role.isBlank() ? "MEMBER" : role) + ", Lv." + level + "]");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "속한 섬이 없습니다." : "내 섬 목록: " + String.join(" / ", entries);
    }

    private String islandText(String body, String key) {
        String needle = "\"" + key + "\":\"";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = body.indexOf('"', start);
        return end < start ? "" : body.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private long islandNumber(String body, String key) {
        String needle = "\"" + key + "\":";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return 0L;
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        try {
            return Long.parseLong(body.substring(start, end));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    public void routeRandomVisit(Player player) {
        routeFuture(player, coreApiClient.createRandomVisitTicket(player.getUniqueId()), "방문 가능한 공개 섬을 찾지 못했습니다.");
    }

    public void routeWarp(Player player, UUID targetIslandId, String warpName) {
        routeFuture(player, coreApiClient.createWarpTicket(player.getUniqueId(), targetIslandId, warpName), "해당 워프로 이동할 수 없습니다.");
    }

    public void listWarps(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.listIslandWarps(islandId), "섬 워프를 불러오지 못했습니다.", "섬 워프 목록을 불러왔습니다.");
    }

    public void listPublicWarps(Player player) {
        sendPlayerPayloadFuture(player, coreApiClient.listPublicWarps(27), "공개 워프를 불러오지 못했습니다.", "공개 워프 목록을 불러왔습니다.");
    }

    public void setWarp(Player player, UUID islandId, String name, boolean publicAccess) {
        IslandLocation defaultLocation = new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F);
        sendActionResult(player, coreApiClient.setIslandWarp(islandId, player.getUniqueId(), name, defaultLocation, publicAccess), "섬 워프를 설정했습니다.", "섬 워프를 설정하지 못했습니다.");
    }

    public void deleteWarp(Player player, UUID islandId, String name) {
        sendActionResult(player, coreApiClient.deleteIslandWarp(islandId, player.getUniqueId(), name), "섬 워프를 삭제했습니다.", "섬 워프를 삭제하지 못했습니다.");
    }

    public void setWarpPublicAccess(Player player, UUID islandId, String name, boolean publicAccess) {
        sendActionResult(player, coreApiClient.setIslandWarpPublicAccess(islandId, player.getUniqueId(), name, publicAccess), publicAccess ? "섬 워프를 공개했습니다." : "섬 워프를 비공개로 변경했습니다.", "섬 워프 공개 상태를 변경하지 못했습니다.");
    }

    public void invite(Player player, UUID islandId, UUID targetUuid) {
        sendPlayerPayloadFuture(player, coreApiClient.createIslandInvite(islandId, player.getUniqueId(), targetUuid), "초대를 생성하지 못했습니다.", "섬 초대를 보냈습니다.");
    }

    public void inviteTarget(Player player, UUID islandId, String target) {
        resolvePlayerUuid(target).thenAccept(targetUuid -> {
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
        coreApiClient.listPendingInvites(player.getUniqueId()).thenAccept(body -> player.sendMessage(Component.text(inviteListMessage(body)))).exceptionally(error -> {
            player.sendMessage(Component.text("초대 목록을 불러오지 못했습니다."));
            return null;
        });
    }

    public void acceptInvite(Player player, UUID inviteId) {
        sendInviteActionResult(player, coreApiClient.acceptIslandInviteResult(inviteId, player.getUniqueId()), "섬 초대를 수락했습니다.", "섬 초대를 수락하지 못했습니다.");
    }

    public void acceptInviteTarget(Player player, String target) {
        resolveInviteTarget(player, target).thenAccept(inviteId -> {
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
        resolveInviteTarget(player, target).thenAccept(inviteId -> {
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
        sendPlayerPayloadFuture(player, coreApiClient.listIslandMembers(islandId), "멤버 목록을 불러오지 못했습니다.", "멤버 목록을 불러왔습니다.");
    }

    public void setRole(Player player, UUID islandId, UUID targetUuid, IslandRole role) {
        sendActionResult(player, coreApiClient.setIslandMember(islandId, player.getUniqueId(), targetUuid, role), "섬 멤버 역할을 변경했습니다.", "섬 멤버 역할을 변경하지 못했습니다.");
    }

    public void setRoleTarget(Player player, UUID islandId, String target, IslandRole role) {
        resolvePlayerUuid(target).thenAccept(targetUuid -> {
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

    public void transferOwnership(Player player, UUID islandId, UUID targetUuid) {
        sendActionResult(player, coreApiClient.transferIslandOwnership(islandId, player.getUniqueId(), targetUuid), "섬 소유권을 양도했습니다.", "섬 소유권을 양도하지 못했습니다.");
    }

    public void transferOwnershipTarget(Player player, UUID islandId, String target) {
        resolvePlayerUuid(target).thenAccept(targetUuid -> {
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
        resolvePlayerUuid(target).thenAccept(targetUuid -> {
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
        resolvePlayerUuid(target).thenAccept(targetUuid -> {
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
        sendPlayerPayloadFuture(player, coreApiClient.listIslandBans(islandId), "밴 목록을 불러오지 못했습니다.", "밴 목록을 불러왔습니다.");
    }

    public void pardonVisitor(Player player, UUID islandId, UUID targetUuid) {
        sendActionResult(player, coreApiClient.pardonIslandVisitor(islandId, player.getUniqueId(), targetUuid), "방문자 밴을 해제했습니다.", "방문자 밴을 해제하지 못했습니다.");
    }

    public void pardonVisitorTarget(Player player, UUID islandId, String target) {
        resolvePlayerUuid(target).thenAccept(targetUuid -> {
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
        proxy.getPlayer(targetUuid).ifPresentOrElse(target -> {
            target.sendMessage(Component.text("섬에서 추방되어 로비로 이동합니다."));
            fallback(target, "섬에서 추방되어 로비로 이동합니다.");
            player.sendMessage(Component.text("방문자를 섬에서 추방했습니다."));
        }, () -> player.sendMessage(Component.text("대상 플레이어가 온라인이 아닙니다.")));
    }

    public void kickVisitorTarget(Player player, UUID islandId, String target) {
        resolvePlayerUuid(target).thenAccept(targetUuid -> {
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

    public void setFlyFlag(Player player, UUID islandId, boolean enabled) {
        sendActionResult(player, coreApiClient.setIslandFlag(islandId, player.getUniqueId(), kr.lunaf.cloudislands.api.model.IslandFlag.FLY, Boolean.toString(enabled)), enabled ? "섬 비행을 허용했습니다." : "섬 비행을 비활성화했습니다.", "섬 비행 설정을 변경하지 못했습니다.");
    }

    public void setBooleanFlag(Player player, UUID islandId, kr.lunaf.cloudislands.api.model.IslandFlag flag, boolean enabled, String label) {
        sendActionResult(player, coreApiClient.setIslandFlag(islandId, player.getUniqueId(), flag, Boolean.toString(enabled)), "섬 " + label + " 설정을 " + (enabled ? "켰습니다." : "껐습니다."), "섬 " + label + " 설정을 변경하지 못했습니다.");
    }

    public void listFlags(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.listIslandFlags(islandId), "섬 플래그를 불러오지 못했습니다.", "섬 플래그 정보를 불러왔습니다.");
    }

    public void listHomes(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.listIslandHomes(islandId), "섬 홈을 불러오지 못했습니다.", "섬 홈 목록을 불러왔습니다.");
    }

    public void setHome(Player player, UUID islandId, String name) {
        IslandLocation defaultHome = new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F);
        sendActionResult(player, coreApiClient.setIslandHome(islandId, player.getUniqueId(), name, defaultHome), "섬 홈을 설정했습니다.", "섬 홈을 설정하지 못했습니다.");
    }

    public void setLocked(Player player, UUID islandId, boolean locked) {
        sendActionResult(player, coreApiClient.setIslandLocked(islandId, player.getUniqueId(), locked), locked ? "섬을 잠금 상태로 변경했습니다." : "섬 잠금을 해제했습니다.", "섬 잠금 상태를 변경하지 못했습니다.");
    }

    public void listPermissions(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.listIslandPermissions(islandId), "섬 권한을 불러오지 못했습니다.", "섬 권한 정보를 불러왔습니다.");
    }

    public void setPermission(Player player, UUID islandId, IslandRole role, IslandPermission permission, boolean allowed) {
        sendActionResult(player, coreApiClient.setIslandPermission(islandId, player.getUniqueId(), role, permission, allowed), "섬 권한을 변경했습니다.", "섬 권한을 변경하지 못했습니다.");
    }

    public void listIslandLogs(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.listIslandLogs(islandId, 30), "섬 로그를 불러오지 못했습니다.", "섬 로그를 불러왔습니다.");
    }

    public void showBank(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.islandBank(islandId), "섬 은행을 불러오지 못했습니다.", "섬 은행 정보를 불러왔습니다.");
    }

    public void depositBank(Player player, UUID islandId, String amount) {
        sendPlayerPayloadFuture(player, coreApiClient.depositIslandBank(islandId, player.getUniqueId(), amount), "입금에 실패했습니다.", "입금했습니다.");
    }

    public void withdrawBank(Player player, UUID islandId, String amount) {
        sendPlayerPayloadFuture(player, coreApiClient.withdrawIslandBank(islandId, player.getUniqueId(), amount), "출금에 실패했습니다.", "출금했습니다.");
    }

    public void showLevelRanking(Player player) {
        sendPlayerPayloadFuture(player, coreApiClient.topIslandsByLevel(10), "랭킹을 불러오지 못했습니다.", "랭킹을 불러왔습니다.");
    }

    public void showWorthRanking(Player player) {
        sendPlayerPayloadFuture(player, coreApiClient.topIslandsByWorth(10), "가치 랭킹을 불러오지 못했습니다.", "가치 랭킹을 불러왔습니다.");
    }

    public void recalculateLevel(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.recalculateIslandLevel(islandId, player.getUniqueId()), "레벨 계산을 시작하지 못했습니다.", "레벨 계산을 시작했습니다.");
    }

    public void listUpgradeRules(Player player) {
        sendPlayerPayloadFuture(player, coreApiClient.listUpgradeRules(), "업그레이드 목록을 불러오지 못했습니다.", "업그레이드 목록을 불러왔습니다.");
    }

    public void listUpgrades(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.listIslandUpgrades(islandId), "섬 업그레이드를 불러오지 못했습니다.", "섬 업그레이드 정보를 불러왔습니다.");
    }

    public void purchaseUpgrade(Player player, UUID islandId, String upgradeKey) {
        sendPlayerPayloadFuture(player, coreApiClient.purchaseIslandUpgrade(islandId, player.getUniqueId(), upgradeKey), "업그레이드에 실패했습니다.", "업그레이드를 처리했습니다.");
    }

    public void listMissions(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.listIslandMissions(islandId, "MISSION"), "미션 목록을 불러오지 못했습니다.", "미션 목록을 불러왔습니다.");
    }

    public void listChallenges(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.listIslandMissions(islandId, "CHALLENGE"), "챌린지 목록을 불러오지 못했습니다.", "챌린지 목록을 불러왔습니다.");
    }

    public void completeMission(Player player, UUID islandId, String missionKey) {
        sendPlayerPayloadFuture(player, coreApiClient.completeIslandMission(islandId, player.getUniqueId(), missionKey), "미션을 완료하지 못했습니다.", "미션을 완료했습니다.");
    }

    public void listLimits(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.listIslandLimits(islandId), "섬 제한을 불러오지 못했습니다.", "섬 제한을 불러왔습니다.");
    }

    public void setLimit(Player player, UUID islandId, String limitKey, long value) {
        sendPlayerPayloadFuture(player, coreApiClient.setIslandLimit(islandId, player.getUniqueId(), limitKey, value), "섬 제한을 변경하지 못했습니다.", "섬 제한을 변경했습니다.");
    }

    public void sendIslandChat(Player player, UUID islandId, String channel, String message) {
        sendPlayerPayloadFuture(player, coreApiClient.sendIslandChat(islandId, player.getUniqueId(), channel, message), "섬 채팅을 전송하지 못했습니다.", "섬 채팅을 전송했습니다.");
    }

    public void listSnapshots(Player player, UUID islandId) {
        sendPlayerPayloadFuture(player, coreApiClient.listIslandSnapshots(islandId, 20), "스냅샷 목록을 불러오지 못했습니다.", "스냅샷 목록을 불러왔습니다.");
    }

    public void snapshot(Player player, UUID islandId, String reason) {
        sendActionResult(player, coreApiClient.requestIslandSnapshot(islandId, reason), "섬 스냅샷을 요청했습니다.", "섬 스냅샷을 요청하지 못했습니다.");
    }

    public void restore(Player player, UUID islandId, long snapshotNo) {
        sendActionResult(player, coreApiClient.restoreIslandSnapshot(islandId, snapshotNo), "섬 복원을 요청했습니다.", "섬 복원을 요청하지 못했습니다.");
    }

    public void listJobs(Player player) {
        sendBodyResult(player, coreApiClient.listJobs(), "작업 목록을 불러오지 못했습니다.");
    }

    public void retryJob(Player player, UUID jobId) {
        sendBodyResult(player, coreApiClient.retryJob(jobId), "작업 재시도를 요청하지 못했습니다.");
    }

    public void cancelJob(Player player, UUID jobId) {
        sendBodyResult(player, coreApiClient.cancelJob(jobId), "작업 취소를 요청하지 못했습니다.");
    }

    public void recoverJobs(Player player, String nodeId, long minIdleMillis, int maxJobs) {
        sendBodyResult(player, coreApiClient.recoverJobs(nodeId, minIdleMillis, maxJobs), "작업 복구를 요청하지 못했습니다.");
    }

    public void listNodes(Player player) {
        sendBodyResult(player, coreApiClient.listNodes(), "노드 목록을 불러오지 못했습니다.");
    }

    public void nodeInfo(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.nodeInfo(nodeId), "노드 정보를 불러오지 못했습니다.");
    }

    public void drainNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.drainNode(nodeId), "노드 drain을 요청하지 못했습니다.");
    }

    public void undrainNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.undrainNode(nodeId), "노드 undrain을 요청하지 못했습니다.");
    }

    public void sweepNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.sweepNode(nodeId), "노드 장애 스윕을 요청하지 못했습니다.");
    }

    public void kickAllNode(Player player, String nodeId) {
        int moved = moveNodePlayersToFallback(nodeId);
        player.sendMessage(Component.text("노드 플레이어 " + moved + "명을 로비로 이동시켰습니다."));
    }

    public void shutdownSafeNode(Player player, String nodeId) {
        coreApiClient.drainNode(nodeId).thenAccept(body -> {
            int moved = moveNodePlayersToFallback(nodeId);
            player.sendMessage(Component.text("노드를 drain 처리하고 플레이어 " + moved + "명을 로비로 이동시켰습니다."));
        });
    }

    public void activateIsland(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.activateIsland(islandId), "섬 활성화를 요청하지 못했습니다.");
    }

    public void activateIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> activateIsland(player, islandId));
    }

    public void deactivateIsland(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.deactivateIsland(islandId), "섬 비활성화를 요청하지 못했습니다.");
    }

    public void deactivateIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> deactivateIsland(player, islandId));
    }

    public void migrateIsland(Player player, UUID islandId, String targetNode) {
        sendBodyResult(player, coreApiClient.migrateIsland(islandId, targetNode), "섬 마이그레이션을 요청하지 못했습니다.");
    }

    public void migrateIslandTarget(Player player, String target, String targetNode) {
        adminIslandTarget(player, target, islandId -> migrateIsland(player, islandId, targetNode));
    }

    public void quarantineIsland(Player player, UUID islandId, String reason) {
        sendBodyResult(player, coreApiClient.quarantineIsland(islandId, reason), "섬 격리를 요청하지 못했습니다.");
    }

    public void quarantineIslandTarget(Player player, String target, String reason) {
        adminIslandTarget(player, target, islandId -> quarantineIsland(player, islandId, reason));
    }

    public void adminIslandInfo(Player player, UUID lookupUuid) {
        coreApiClient.adminIslandInfo(lookupUuid).thenAccept(body -> sendPlayerPayload(player, body, "섬 정보를 불러오지 못했습니다.", "섬 정보를 불러왔습니다."));
    }

    public void adminIslandInfoTarget(Player player, String target) {
        UUID parsed = parseUuid(target);
        if (!parsed.equals(new UUID(0L, 0L))) {
            adminIslandInfo(player, parsed);
            return;
        }
        sendPlayerPayloadFuture(player, coreApiClient.islandInfoByName(target), "섬 정보를 불러오지 못했습니다.", "섬 정보를 불러왔습니다.");
    }

    public void adminIslandWhere(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.adminIslandWhere(islandId), "섬 위치 정보를 불러오지 못했습니다.");
    }

    public void adminIslandWhereTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> adminIslandWhere(player, islandId));
    }

    public void adminTeleportIsland(Player player, UUID islandId) {
        routeFuture(player, coreApiClient.adminIslandTeleport(player.getUniqueId(), islandId), "섬으로 이동하지 못했습니다.");
    }

    public void adminTeleportIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> adminTeleportIsland(player, islandId));
    }

    public void adminDeleteIsland(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.adminDeleteIsland(islandId), "섬 삭제를 요청하지 못했습니다.");
    }

    public void adminDeleteIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> adminDeleteIsland(player, islandId));
    }

    public void repairIsland(Player player, UUID islandId, String reason) {
        sendBodyResult(player, coreApiClient.repairIsland(islandId, reason), "섬 복구를 요청하지 못했습니다.");
    }

    public void repairIslandTarget(Player player, String target, String reason) {
        adminIslandTarget(player, target, islandId -> repairIsland(player, islandId, reason));
    }

    public void listSnapshotsTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> listSnapshots(player, islandId));
    }

    public void snapshotTarget(Player player, String target, String reason) {
        adminIslandTarget(player, target, islandId -> snapshot(player, islandId, reason));
    }

    public void restoreTarget(Player player, String target, long snapshotNo) {
        adminIslandTarget(player, target, islandId -> restore(player, islandId, snapshotNo));
    }

    public void debugRoutes(Player player, UUID playerUuid) {
        sendBodyResult(player, coreApiClient.debugRoutes(playerUuid), "라우트 정보를 불러오지 못했습니다.");
    }

    public void debugRoutesTarget(Player player, String target) {
        resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
                return;
            }
            debugRoutes(player, playerUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void routeTicket(Player player, UUID ticketId) {
        sendBodyResult(player, coreApiClient.routeTicket(ticketId), "티켓 정보를 불러오지 못했습니다.");
    }

    public void clearRoute(Player player, UUID playerUuid, UUID ticketId) {
        sendBodyResult(player, coreApiClient.clearRoute(playerUuid, ticketId), "라우트 정리를 요청하지 못했습니다.");
    }

    public void clearRouteTarget(Player player, String target, UUID ticketId) {
        resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
                return;
            }
            clearRoute(player, playerUuid, ticketId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void clearCache(Player player) {
        sendBodyResult(player, coreApiClient.clearCache(), "캐시 정리를 요청하지 못했습니다.");
    }

    public void listEvents(Player player) {
        sendBodyResult(player, coreApiClient.listEvents(), "이벤트 목록을 불러오지 못했습니다.");
    }

    public void listAuditLogs(Player player) {
        sendBodyResult(player, coreApiClient.listAuditLogs(), "감사 로그를 불러오지 못했습니다.");
    }

    public void listBlockValues(Player player) {
        sendBodyResult(player, coreApiClient.listBlockValues(), "블록 가치 목록을 불러오지 못했습니다.");
    }

    public void setBlockValue(Player player, String materialKey, String worth, long levelPoints, long limit) {
        sendBodyResult(player, coreApiClient.setBlockValueResult(player.getUniqueId(), materialKey, worth, levelPoints, limit), "블록 가치를 변경하지 못했습니다.");
    }

    public void reload(Player player) {
        sendBodyResult(player, coreApiClient.reload(), "reload를 요청하지 못했습니다.");
    }

    public void migrateSuperiorSkyblock2(Player player, String action, String path) {
        sendBodyResult(player, coreApiClient.migrateSuperiorSkyblock2(action, path), "마이그레이션 명령을 실행하지 못했습니다.");
    }

    public void playerInfo(Player player, UUID playerUuid) {
        sendBodyResult(player, coreApiClient.playerInfo(playerUuid), "플레이어 정보를 불러오지 못했습니다.");
    }

    public void playerInfoTarget(Player player, String target) {
        resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            playerInfo(player, playerUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void setPlayerIsland(Player player, UUID playerUuid, UUID islandId) {
        sendBodyResult(player, coreApiClient.setPlayerIsland(playerUuid, islandId), "플레이어 섬을 설정하지 못했습니다.");
    }

    public void setPlayerIslandTarget(Player player, String target, UUID islandId) {
        resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            setPlayerIsland(player, playerUuid, islandId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void clearPlayerIsland(Player player, UUID playerUuid) {
        sendBodyResult(player, coreApiClient.clearPlayerIsland(playerUuid), "플레이어 섬을 해제하지 못했습니다.");
    }

    public void clearPlayerIslandTarget(Player player, String target) {
        resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            clearPlayerIsland(player, playerUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void listTemplates(Player player) {
        sendBodyResult(player, coreApiClient.listTemplates(), "섬 템플릿 목록을 불러오지 못했습니다.");
    }

    public void upsertTemplate(Player player, String templateId, String displayName, boolean enabled, String minNodeVersion) {
        sendBodyResult(player, coreApiClient.upsertTemplate(templateId, displayName, enabled, minNodeVersion), "섬 템플릿을 저장하지 못했습니다.");
    }

    public void enableTemplate(Player player, String templateId) {
        sendBodyResult(player, coreApiClient.enableTemplate(templateId), "섬 템플릿을 활성화하지 못했습니다.");
    }

    public void disableTemplate(Player player, String templateId) {
        sendBodyResult(player, coreApiClient.disableTemplate(templateId), "섬 템플릿을 비활성화하지 못했습니다.");
    }

    private void sendPlayerPayload(Player player, String body, String emptyMessage, String successMessage) {
        player.sendMessage(Component.text(playerPayloadMessage(body, emptyMessage, successMessage)));
    }

    private void sendPlayerPayloadFuture(Player player, CompletableFuture<String> future, String emptyMessage, String successMessage) {
        future.thenAccept(body -> sendPlayerPayload(player, body, emptyMessage, successMessage)).exceptionally(error -> {
            player.sendMessage(Component.text(emptyMessage));
            return null;
        });
    }

    private void sendActionResult(Player player, CompletableFuture<Void> future, String successMessage, String failureMessage) {
        future.thenRun(() -> player.sendMessage(Component.text(successMessage))).exceptionally(error -> {
            player.sendMessage(Component.text(failureMessage));
            return null;
        });
    }

    private void sendBodyResult(Player player, CompletableFuture<String> future, String emptyMessage) {
        future.thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? emptyMessage : body))).exceptionally(error -> {
            player.sendMessage(Component.text(emptyMessage));
            return null;
        });
    }

    private void sendInviteActionResult(Player player, CompletableFuture<String> future, String successMessage, String failureMessage) {
        future.thenAccept(body -> {
            if (body == null || body.isBlank() || body.contains("\"error\"") || body.contains("\"accepted\":false")) {
                player.sendMessage(Component.text(failureMessage));
                return;
            }
            player.sendMessage(Component.text(successMessage));
        }).exceptionally(error -> {
            player.sendMessage(Component.text(failureMessage));
            return null;
        });
    }

    private CompletableFuture<UUID> resolveInviteTarget(Player player, String target) {
        if (target == null || target.isBlank()) {
            return CompletableFuture.completedFuture(new UUID(0L, 0L));
        }
        UUID parsed = parseUuid(target);
        if (!parsed.equals(new UUID(0L, 0L))) {
            return coreApiClient.listPendingInvites(player.getUniqueId()).thenApply(body -> {
                UUID inviteId = findInviteId(body, parsed);
                return inviteId.equals(new UUID(0L, 0L)) ? parsed : inviteId;
            });
        }
        Optional<Player> online = proxy.getPlayer(target);
        if (online.isPresent()) {
            return coreApiClient.listPendingInvites(player.getUniqueId()).thenApply(body -> findInviteId(body, online.get().getUniqueId()));
        }
        return coreApiClient.playerInfoByName(target)
            .thenCompose(body -> coreApiClient.listPendingInvites(player.getUniqueId()).thenApply(invites -> findInviteId(invites, parseUuid(jsonValue(body, "playerUuid")))));
    }

    private CompletableFuture<UUID> resolvePlayerUuid(String target) {
        if (target == null || target.isBlank()) {
            return CompletableFuture.completedFuture(new UUID(0L, 0L));
        }
        UUID parsed = parseUuid(target);
        if (!parsed.equals(new UUID(0L, 0L))) {
            return CompletableFuture.completedFuture(parsed);
        }
        Optional<Player> online = proxy.getPlayer(target);
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(online.get().getUniqueId());
        }
        return coreApiClient.playerInfoByName(target).thenApply(body -> parseUuid(jsonValue(body, "playerUuid")));
    }

    private void adminIslandTarget(Player player, String target, Consumer<UUID> action) {
        resolveIslandId(target).thenAccept(islandId -> {
            if (islandId.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("섬을 찾지 못했습니다."));
                return;
            }
            action.accept(islandId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("섬을 찾지 못했습니다."));
            return null;
        });
    }

    private CompletableFuture<UUID> resolveIslandId(String target) {
        if (target == null || target.isBlank()) {
            return CompletableFuture.completedFuture(new UUID(0L, 0L));
        }
        UUID parsed = parseUuid(target);
        if (!parsed.equals(new UUID(0L, 0L))) {
            return CompletableFuture.completedFuture(parsed);
        }
        return coreApiClient.islandInfoByName(target).thenApply(body -> parseUuid(jsonValue(body, "islandId")));
    }

    private UUID findInviteId(String body, UUID targetUuid) {
        if (body == null || targetUuid == null || targetUuid.equals(new UUID(0L, 0L))) {
            return new UUID(0L, 0L);
        }
        int index = 0;
        while (index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            UUID inviteId = parseUuid(jsonValue(object, "inviteId"));
            if (targetUuid.equals(inviteId) || targetUuid.equals(parseUuid(jsonValue(object, "islandId"))) || targetUuid.equals(parseUuid(jsonValue(object, "inviterUuid")))) {
                return inviteId;
            }
            index = objectEnd + 1;
        }
        return new UUID(0L, 0L);
    }

    private String inviteListMessage(String body) {
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String inviteId = jsonValue(object, "inviteId");
            if (!inviteId.isBlank()) {
                String islandId = jsonValue(object, "islandId");
                String inviterUuid = jsonValue(object, "inviterUuid");
                entries.add(inviteId + (islandId.isBlank() ? "" : " island=" + islandId) + (inviterUuid.isBlank() ? "" : " inviter=" + inviterUuid));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "대기 중인 섬 초대가 없습니다." : "섬 초대: " + String.join(", ", entries);
    }

    private String playerPayloadMessage(String body, String emptyMessage, String successMessage) {
        if (body == null || body.isBlank()) {
            return emptyMessage;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{\"error\"")) {
            String code = jsonValue(trimmed, "code");
            return playerErrorMessage(code, emptyMessage);
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return successMessage;
        }
        return trimmed;
    }

    private String playerErrorMessage(String code, String fallback) {
        if (code == null || code.isBlank()) {
            return fallback;
        }
        return switch (code) {
            case "ISLAND_NOT_FOUND" -> "섬을 찾을 수 없습니다.";
            case "ISLAND_PRIVATE" -> "해당 섬은 비공개 상태입니다.";
            case "VISITOR_BANNED" -> "해당 섬에 방문할 수 없습니다.";
            case "NODE_UNAVAILABLE" -> "현재 섬 서버가 혼잡합니다. 잠시 후 다시 시도해주세요.";
            case "ISLAND_PERMISSION_DENIED" -> "섬 권한이 없습니다.";
            case "UNAUTHORIZED", "ADMIN_PERMISSION_DENIED" -> "이 명령을 사용할 권한이 없습니다.";
            case "RATE_LIMITED" -> "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.";
            default -> fallback;
        };
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return new UUID(0L, 0L);
        }
    }

    private String jsonValue(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = json.indexOf('"', start);
        return end < 0 ? "" : json.substring(start, end);
    }

    private void route(Player player, RouteTicket ticket, String failureMessage) {
        if (ticket == null) {
            fallback(player, failureMessage);
            return;
        }
        if (ticket.state().name().equals("PREPARING")) {
            player.sendActionBar(Component.text("섬을 준비하는 중입니다."));
            BossBar bossBar = BossBar.bossBar(Component.text("섬 로딩 중"), 0.2F, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
            player.showBossBar(bossBar);
            waitForReadyTicket(player, ticket, failureMessage, bossBar, 0);
            return;
        }
        publishAndConnect(player, ticket);
    }

    private void routeFuture(Player player, CompletableFuture<RouteTicket> ticketFuture, String failureMessage) {
        ticketFuture.thenAccept(ticket -> route(player, ticket, failureMessage)).exceptionally(error -> {
            fallback(player, failureMessage);
            return null;
        });
    }

    private void waitForReadyTicket(Player player, RouteTicket ticket, String failureMessage, BossBar bossBar, int attempt) {
        int progress = Math.min(95, 20 + attempt);
        bossBar.progress(progress / 100.0F);
        bossBar.name(Component.text("섬 로딩 중 " + progress + "%"));
        player.sendActionBar(Component.text("섬을 준비하는 중입니다... " + progress + "%"));
        coreApiClient.routeTicketStatus(ticket.ticketId(), ticket.playerUuid(), ticket.nonce()).thenAccept(status -> {
            Optional<RouteTicket> ready = status.filter(value -> value.state().name().equals("READY"));
            if (ready.isPresent()) {
                bossBar.progress(1.0F);
                bossBar.name(Component.text("잠시 후 섬으로 이동합니다."));
                player.sendActionBar(Component.text("잠시 후 섬으로 이동합니다."));
                player.hideBossBar(bossBar);
                publishAndConnect(player, ready.get());
                return;
            }
            if (attempt >= 60) {
                player.hideBossBar(bossBar);
                fallback(player, failureMessage);
                return;
            }
            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> waitForReadyTicket(player, ticket, failureMessage, bossBar, attempt + 1));
        }).exceptionally(error -> {
            player.hideBossBar(bossBar);
            fallback(player, failureMessage);
            return null;
        });
    }

    private void publishAndConnect(Player player, RouteTicket ticket) {
        coreApiClient.publishRouteSession(ticket).thenRun(() -> {
            String targetServerName = ticket.payload().getOrDefault("targetServerName", ticket.targetNode());
            connectWithTicket(player, targetServerName);
        }).exceptionally(error -> {
            fallback(player, "섬 이동 정보를 준비하지 못했습니다. 로비로 이동합니다.");
            return null;
        });
    }

    private void connectWithTicket(Player player, String targetServerName) {
        RegisteredServer server = findServer(targetServerName);
        if (server == null) {
            fallback(player, "섬 이동 경로를 찾을 수 없습니다.");
            return;
        }
        connect(player, server);
    }

    private void connect(Player player, RegisteredServer server) {
        player.createConnectionRequest(server).connectWithIndication().thenAccept(success -> {
            if (!success) {
                fallback(player, "섬으로 이동하지 못했습니다. 로비로 이동합니다.");
            }
        }).exceptionally(error -> {
            fallback(player, "섬으로 이동하지 못했습니다. 로비로 이동합니다.");
            return null;
        });
    }

    private void fallback(Player player, String message) {
        player.sendMessage(Component.text(message));
        proxy.getServer(fallbackServer).ifPresent(server -> player.createConnectionRequest(server).connectWithIndication());
    }

    private int moveNodePlayersToFallback(String nodeId) {
        RegisteredServer target = findServer(nodeId);
        RegisteredServer fallback = findServer(fallbackServer);
        if (target == null || fallback == null) {
            return 0;
        }
        java.util.List<Player> players = java.util.List.copyOf(target.getPlayersConnected());
        for (Player connected : players) {
            connected.sendMessage(Component.text("섬 점검으로 로비로 이동합니다."));
            connected.createConnectionRequest(fallback).connectWithIndication();
        }
        return players.size();
    }

    private RegisteredServer findServer(String name) {
        return proxy.getServer(name).or(() -> proxy.getServer(nodeIdToServerName(name))).orElse(null);
    }

    private String nodeIdToServerName(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "";
        }
        String[] parts = nodeId.split("-");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('-');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String messageForCreateFailure(String code) {
        return switch (code) {
            case "ALREADY_HAS_ISLAND" -> "이미 섬을 보유하고 있습니다.";
            case "TEMPLATE_UNAVAILABLE" -> "사용할 수 없는 섬 템플릿입니다.";
            case "NODE_UNAVAILABLE" -> "현재 섬 서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.";
            default -> "섬 생성에 실패했습니다.";
        };
    }
}
