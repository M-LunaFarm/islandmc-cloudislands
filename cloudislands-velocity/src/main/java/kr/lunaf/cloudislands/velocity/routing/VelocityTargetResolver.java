package kr.lunaf.cloudislands.velocity.routing;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.matchingObjectEnd;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.function.Function;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class VelocityTargetResolver {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final CoreApiClient coreApiClient;
    private final Function<String, Optional<UUID>> onlinePlayerLookup;

    public VelocityTargetResolver(CoreApiClient coreApiClient) {
        this(coreApiClient, ignored -> Optional.empty());
    }

    public VelocityTargetResolver(CoreApiClient coreApiClient, Function<String, Optional<UUID>> onlinePlayerLookup) {
        this.coreApiClient = coreApiClient;
        this.onlinePlayerLookup = onlinePlayerLookup == null ? ignored -> Optional.empty() : onlinePlayerLookup;
    }

    public CompletableFuture<UUID> resolveInviteTarget(UUID playerUuid, String target) {
        if (target == null || target.isBlank()) {
            return CompletableFuture.completedFuture(EMPTY_UUID);
        }
        UUID parsed = parseUuid(target);
        if (!parsed.equals(EMPTY_UUID)) {
            return coreApiClient.listPendingInvites(playerUuid).thenApply(body -> {
                UUID inviteId = findInviteId(body, parsed);
                return inviteId.equals(EMPTY_UUID) ? parsed : inviteId;
            });
        }
        Optional<UUID> online = onlinePlayerLookup.apply(target);
        if (online.isPresent()) {
            return coreApiClient.listPendingInvites(playerUuid).thenApply(body -> findInviteId(body, online.get()));
        }
        return coreApiClient.playerInfoByName(target)
            .handle((body, error) -> error == null ? parseUuid(jsonValue(body, "playerUuid")) : EMPTY_UUID)
            .thenCompose(targetUuid -> {
                if (targetUuid.equals(EMPTY_UUID)) {
                    return resolveInviteIslandName(playerUuid, target);
                }
                return coreApiClient.listPendingInvites(playerUuid).thenCompose(invites -> {
                    UUID inviteId = findInviteId(invites, targetUuid);
                    return inviteId.equals(EMPTY_UUID)
                        ? resolveInviteIslandName(playerUuid, target)
                        : CompletableFuture.completedFuture(inviteId);
                });
            });
    }

    public CompletableFuture<UUID> resolveInviteIslandName(UUID playerUuid, String islandName) {
        return coreApiClient.islandInfoByName(islandName)
            .thenCompose(body -> coreApiClient.listPendingInvites(playerUuid).thenApply(invites -> findInviteId(invites, parseUuid(jsonValue(body, "islandId")))));
    }

    public CompletableFuture<UUID> resolvePlayerUuid(String target) {
        if (target == null || target.isBlank()) {
            return CompletableFuture.completedFuture(EMPTY_UUID);
        }
        UUID parsed = parseUuid(target);
        if (!parsed.equals(EMPTY_UUID)) {
            return CompletableFuture.completedFuture(parsed);
        }
        Optional<UUID> online = onlinePlayerLookup.apply(target);
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(online.get());
        }
        return coreApiClient.playerInfoByName(target).thenApply(body -> parseUuid(jsonValue(body, "playerUuid")));
    }

    public CompletableFuture<UUID> resolveIslandId(String target) {
        if (target == null || target.isBlank()) {
            return CompletableFuture.completedFuture(EMPTY_UUID);
        }
        UUID parsed = parseUuid(target);
        if (!parsed.equals(EMPTY_UUID)) {
            return CompletableFuture.completedFuture(parsed);
        }
        return coreApiClient.islandInfoByName(target).thenApply(body -> parseUuid(jsonValue(body, "islandId")));
    }

    public static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return EMPTY_UUID;
        }
    }

    static UUID findInviteId(String body, UUID targetUuid) {
        String invites = kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.arrayValue(body, "invites");
        if (invites.isBlank()) {
            invites = body == null ? "" : body;
        }
        int index = 0;
        while (index < invites.length()) {
            int objectStart = invites.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(invites, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = invites.substring(objectStart, objectEnd + 1);
            UUID inviteId = parseUuid(jsonValue(object, "inviteId"));
            if (targetUuid.equals(inviteId) || targetUuid.equals(parseUuid(jsonValue(object, "islandId"))) || targetUuid.equals(parseUuid(jsonValue(object, "inviterUuid")))) {
                return inviteId;
            }
            index = objectEnd + 1;
        }
        return EMPTY_UUID;
    }
}
