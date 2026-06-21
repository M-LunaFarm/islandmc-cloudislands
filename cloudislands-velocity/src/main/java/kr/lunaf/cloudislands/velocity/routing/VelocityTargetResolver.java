package kr.lunaf.cloudislands.velocity.routing;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.function.Function;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;

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
            return coreApiClient.members().pendingInvites(playerUuid).thenApply(invites -> {
                UUID inviteId = findInviteId(invites, parsed);
                return inviteId.equals(EMPTY_UUID) ? parsed : inviteId;
            });
        }
        Optional<UUID> online = onlinePlayerLookup.apply(target);
        if (online.isPresent()) {
            return coreApiClient.members().pendingInvites(playerUuid).thenApply(invites -> findInviteId(invites, online.get()));
        }
        return coreApiClient.members().playerProfileByName(target)
            .handle((profile, error) -> error == null ? parseUuid(profile.playerUuid()) : EMPTY_UUID)
            .thenCompose(targetUuid -> {
                if (targetUuid.equals(EMPTY_UUID)) {
                    return resolveInviteIslandName(playerUuid, target);
                }
                return coreApiClient.members().pendingInvites(playerUuid).thenCompose(invites -> {
                    UUID inviteId = findInviteId(invites, targetUuid);
                    return inviteId.equals(EMPTY_UUID)
                        ? resolveInviteIslandName(playerUuid, target)
                        : CompletableFuture.completedFuture(inviteId);
                });
            });
    }

    public CompletableFuture<UUID> resolveInviteIslandName(UUID playerUuid, String islandName) {
        return coreApiClient.islands().findIslandByName(islandName)
            .thenCompose(island -> coreApiClient.members().pendingInvites(playerUuid).thenApply(invites -> findInviteId(invites, parseUuid(island.islandId()))));
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
        return coreApiClient.members().playerProfileByName(target).thenApply(profile -> parseUuid(profile.playerUuid()));
    }

    public CompletableFuture<UUID> resolveIslandId(String target) {
        if (target == null || target.isBlank()) {
            return CompletableFuture.completedFuture(EMPTY_UUID);
        }
        UUID parsed = parseUuid(target);
        if (!parsed.equals(EMPTY_UUID)) {
            return CompletableFuture.completedFuture(parsed);
        }
        return coreApiClient.islands().findIslandByName(target).thenApply(island -> parseUuid(island.islandId()));
    }

    public static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return EMPTY_UUID;
        }
    }

    static UUID findInviteId(List<CoreGuiViews.InviteView> invites, UUID targetUuid) {
        for (CoreGuiViews.InviteView invite : invites == null ? List.<CoreGuiViews.InviteView>of() : invites) {
            UUID inviteId = parseUuid(invite.inviteId());
            if (targetUuid.equals(inviteId)
                || targetUuid.equals(parseUuid(invite.islandId()))
                || targetUuid.equals(parseUuid(invite.inviterUuid()))
                || targetUuid.equals(parseUuid(invite.targetUuid()))) {
                return inviteId;
            }
        }
        return EMPTY_UUID;
    }
}
