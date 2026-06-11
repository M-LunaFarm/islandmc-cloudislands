package kr.lunaf.cloudislands.coreservice.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;

public final class InMemoryIslandMetadataRepository implements IslandMetadataRepository {
    private final Map<UUID, Map<UUID, IslandMemberSnapshot>> members = new ConcurrentHashMap<>();
    private final Map<UUID, Map<IslandFlag, String>> flags = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, IslandWarpSnapshot>> warps = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> publicAccess = new ConcurrentHashMap<>();

    @Override
    public List<IslandMemberSnapshot> members(UUID islandId) {
        return new ArrayList<>(members.getOrDefault(islandId, Map.of()).values());
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
    public IslandFlagsSnapshot flags(UUID islandId) {
        Map<IslandFlag, String> islandFlags = flags.get(islandId);
        return new IslandFlagsSnapshot(islandId, islandFlags == null ? Map.of() : Map.copyOf(islandFlags));
    }

    @Override
    public void setFlag(UUID islandId, IslandFlag flag, String value) {
        flags.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).put(flag, value);
    }

    @Override
    public List<IslandWarpSnapshot> warps(UUID islandId) {
        return new ArrayList<>(warps.getOrDefault(islandId, Map.of()).values());
    }

    @Override
    public void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy) {
        warps.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>())
            .put(name.toLowerCase(), new IslandWarpSnapshot(islandId, name.toLowerCase(), location, publicAccess, createdBy, Instant.now()));
    }

    @Override
    public void deleteWarp(UUID islandId, String name) {
        Map<String, IslandWarpSnapshot> islandWarps = warps.get(islandId);
        if (islandWarps != null) {
            islandWarps.remove(name.toLowerCase());
        }
    }

    @Override
    public void setPublicAccess(UUID islandId, boolean publicAccess) {
        this.publicAccess.put(islandId, publicAccess);
    }
}
