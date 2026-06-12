package kr.lunaf.cloudislands.coreservice.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;

public final class InMemoryIslandMetadataRepository implements IslandMetadataRepository {
    private final Map<UUID, Map<UUID, IslandMemberSnapshot>> members = new ConcurrentHashMap<>();
    private final Map<UUID, IslandInviteSnapshot> invites = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, IslandBanSnapshot>> bans = new ConcurrentHashMap<>();
    private final Map<UUID, Map<IslandFlag, String>> flags = new ConcurrentHashMap<>();
    private final Map<UUID, IslandBiomeSnapshot> biomes = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, IslandHomeSnapshot>> homes = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, IslandWarpSnapshot>> warps = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> publicAccess = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> locked = new ConcurrentHashMap<>();

    @Override
    public List<IslandMemberSnapshot> members(UUID islandId) {
        return new ArrayList<>(members.getOrDefault(islandId, Map.of()).values());
    }

    @Override
    public List<IslandMemberSnapshot> islandsForMember(UUID playerUuid) {
        List<IslandMemberSnapshot> result = new ArrayList<>();
        for (Map<UUID, IslandMemberSnapshot> islandMembers : members.values()) {
            IslandMemberSnapshot member = islandMembers.get(playerUuid);
            if (member != null) {
                result.add(member);
            }
        }
        result.sort(java.util.Comparator.comparing(IslandMemberSnapshot::joinedAt));
        return result;
    }

    @Override
    public boolean isMember(UUID islandId, UUID playerUuid) {
        return members.getOrDefault(islandId, Map.of()).containsKey(playerUuid);
    }

    @Override
    public void upsertMember(UUID islandId, UUID playerUuid, IslandRole role) {
        members.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>())
            .compute(playerUuid, (ignored, current) -> new IslandMemberSnapshot(islandId, playerUuid, role, current == null ? Instant.now() : current.joinedAt()));
    }

    @Override
    public void removeMember(UUID islandId, UUID playerUuid) {
        Map<UUID, IslandMemberSnapshot> islandMembers = members.get(islandId);
        if (islandMembers != null) {
            islandMembers.remove(playerUuid);
        }
    }

    @Override
    public IslandInviteSnapshot createInvite(UUID islandId, UUID inviterUuid, UUID targetUuid) {
        IslandInviteSnapshot invite = new IslandInviteSnapshot(UUID.randomUUID(), islandId, inviterUuid, targetUuid, "PENDING", Instant.now(), Instant.now().plusSeconds(86400));
        invites.put(invite.inviteId(), invite);
        return invite;
    }

    @Override
    public List<IslandInviteSnapshot> pendingInvites(UUID targetUuid) {
        Instant now = Instant.now();
        List<IslandInviteSnapshot> result = new ArrayList<>();
        for (IslandInviteSnapshot invite : invites.values()) {
            if (invite.targetUuid().equals(targetUuid) && invite.state().equals("PENDING") && invite.expiresAt().isAfter(now)) {
                result.add(invite);
            }
        }
        return result;
    }

    @Override
    public boolean acceptInvite(UUID inviteId, UUID playerUuid) {
        IslandInviteSnapshot invite = invites.get(inviteId);
        if (invite == null || !invite.targetUuid().equals(playerUuid) || !invite.state().equals("PENDING") || !invite.expiresAt().isAfter(Instant.now())) {
            return false;
        }
        invites.put(inviteId, new IslandInviteSnapshot(invite.inviteId(), invite.islandId(), invite.inviterUuid(), invite.targetUuid(), "ACCEPTED", invite.createdAt(), invite.expiresAt()));
        upsertMember(invite.islandId(), playerUuid, IslandRole.MEMBER);
        return true;
    }

    @Override
    public boolean declineInvite(UUID inviteId, UUID playerUuid) {
        IslandInviteSnapshot invite = invites.get(inviteId);
        if (invite == null || !invite.targetUuid().equals(playerUuid) || !invite.state().equals("PENDING")) {
            return false;
        }
        invites.put(inviteId, new IslandInviteSnapshot(invite.inviteId(), invite.islandId(), invite.inviterUuid(), invite.targetUuid(), "DECLINED", invite.createdAt(), invite.expiresAt()));
        return true;
    }

    @Override
    public boolean isBanned(UUID islandId, UUID playerUuid) {
        return bans.getOrDefault(islandId, Map.of()).containsKey(playerUuid);
    }

    @Override
    public List<IslandBanSnapshot> bans(UUID islandId) {
        return new ArrayList<>(bans.getOrDefault(islandId, Map.of()).values());
    }

    @Override
    public void banVisitor(UUID islandId, UUID actorUuid, UUID playerUuid, String reason) {
        bans.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>())
            .put(playerUuid, new IslandBanSnapshot(islandId, playerUuid, actorUuid, reason == null ? "" : reason, Instant.now(), null));
    }

    @Override
    public void pardonVisitor(UUID islandId, UUID playerUuid) {
        Map<UUID, IslandBanSnapshot> islandBans = bans.get(islandId);
        if (islandBans != null) {
            islandBans.remove(playerUuid);
        }
    }

    @Override
    public boolean isLocked(UUID islandId) {
        return locked.getOrDefault(islandId, false);
    }

    @Override
    public void setLocked(UUID islandId, boolean locked) {
        this.locked.put(islandId, locked);
    }

    @Override
    public IslandFlagsSnapshot flags(UUID islandId) {
        Map<IslandFlag, String> islandFlags = flags.get(islandId);
        return new IslandFlagsSnapshot(islandId, islandFlags == null ? Map.of() : Map.copyOf(islandFlags));
    }

    @Override
    public void setFlag(UUID islandId, IslandFlag flag, String value) {
        flags.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).put(flag, value);
    }

    @Override
    public IslandBiomeSnapshot biome(UUID islandId) {
        return biomes.getOrDefault(islandId, new IslandBiomeSnapshot(islandId, "minecraft:plains", new UUID(0L, 0L), Instant.EPOCH));
    }

    @Override
    public void setBiome(UUID islandId, String biomeKey, UUID updatedBy) {
        biomes.put(islandId, new IslandBiomeSnapshot(islandId, biomeKey, updatedBy, Instant.now()));
    }

    @Override
    public List<IslandHomeSnapshot> homes(UUID islandId) {
        return new ArrayList<>(homes.getOrDefault(islandId, Map.of()).values());
    }

    @Override
    public java.util.Optional<IslandHomeSnapshot> home(UUID islandId, String name) {
        return java.util.Optional.ofNullable(homes.getOrDefault(islandId, Map.of()).get(name.toLowerCase()));
    }

    @Override
    public void upsertHome(UUID islandId, String name, IslandLocation location, UUID createdBy) {
        homes.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>())
            .put(name.toLowerCase(), new IslandHomeSnapshot(islandId, name.toLowerCase(), location, createdBy, Instant.now()));
    }

    @Override
    public List<IslandWarpSnapshot> warps(UUID islandId) {
        return new ArrayList<>(warps.getOrDefault(islandId, Map.of()).values());
    }

    @Override
    public List<IslandWarpSnapshot> publicWarps(int limit) {
        return warps.values().stream()
            .flatMap(islandWarps -> islandWarps.values().stream())
            .filter(IslandWarpSnapshot::publicAccess)
            .sorted(java.util.Comparator.comparing(IslandWarpSnapshot::createdAt).reversed())
            .limit(Math.max(1, limit))
            .toList();
    }

    @Override
    public Optional<IslandWarpSnapshot> warp(UUID islandId, String name) {
        return Optional.ofNullable(warps.getOrDefault(islandId, Map.of()).get(name.toLowerCase()));
    }

    @Override
    public void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy) {
        warps.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>())
            .put(name.toLowerCase(), new IslandWarpSnapshot(islandId, name.toLowerCase(), location, publicAccess, createdBy, Instant.now()));
    }

    @Override
    public void setWarpPublicAccess(UUID islandId, String name, boolean publicAccess) {
        Map<String, IslandWarpSnapshot> islandWarps = warps.get(islandId);
        if (islandWarps == null) {
            return;
        }
        islandWarps.computeIfPresent(name.toLowerCase(), (_key, warp) -> new IslandWarpSnapshot(
            warp.islandId(),
            warp.name(),
            warp.location(),
            publicAccess,
            warp.createdBy(),
            warp.createdAt()
        ));
    }

    @Override
    public void deleteWarp(UUID islandId, String name) {
        Map<String, IslandWarpSnapshot> islandWarps = warps.get(islandId);
        if (islandWarps != null) {
            islandWarps.remove(name.toLowerCase());
        }
    }

    @Override
    public boolean isPublicAccess(UUID islandId) {
        return publicAccess.getOrDefault(islandId, false);
    }

    @Override
    public void setPublicAccess(UUID islandId, boolean publicAccess) {
        this.publicAccess.put(islandId, publicAccess);
    }

    @Override
    public List<UUID> publicIslandIds(int limit) {
        return publicAccess.entrySet().stream()
            .filter(Map.Entry::getValue)
            .filter(entry -> !locked.getOrDefault(entry.getKey(), false))
            .map(Map.Entry::getKey)
            .limit(Math.max(0, limit))
            .toList();
    }
}
