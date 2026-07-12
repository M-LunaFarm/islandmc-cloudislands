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

    private static String normalizeResourceName(String name) {
        return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public List<IslandMemberSnapshot> members(UUID islandId) {
        return activeMembers(members.getOrDefault(islandId, Map.of()));
    }

    @Override
    public List<IslandMemberSnapshot> islandsForMember(UUID playerUuid) {
        List<IslandMemberSnapshot> result = new ArrayList<>();
        for (Map<UUID, IslandMemberSnapshot> islandMembers : members.values()) {
            IslandMemberSnapshot member = islandMembers.get(playerUuid);
            if (member != null && !expired(member)) {
                result.add(member);
            }
        }
        result.sort(java.util.Comparator.comparing(IslandMemberSnapshot::joinedAt));
        return result;
    }

    @Override
    public boolean isMember(UUID islandId, UUID playerUuid) {
        IslandMemberSnapshot member = members.getOrDefault(islandId, Map.of()).get(playerUuid);
        return member != null && !expired(member);
    }

    @Override
    @Deprecated(forRemoval = false)
    @SuppressWarnings("deprecation")
    public void upsertMember(UUID islandId, UUID playerUuid, IslandRole role) {
        upsertMember(islandId, playerUuid, role, null);
    }

    @Override
    public void upsertMemberKey(UUID islandId, UUID playerUuid, String roleKey) {
        upsertMemberKey(islandId, playerUuid, roleKey, null);
    }

    @Override
    @Deprecated(forRemoval = false)
    @SuppressWarnings("deprecation")
    public void upsertMember(UUID islandId, UUID playerUuid, IslandRole role, Instant expiresAt) {
        upsertMemberKey(islandId, playerUuid, role.name(), expiresAt);
    }

    @Override
    public void upsertMemberKey(UUID islandId, UUID playerUuid, String roleKey, Instant expiresAt) {
        String normalizedRoleKey = kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository.normalizeRoleKey(roleKey);
        members.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>())
            .compute(playerUuid, (ignored, current) -> new IslandMemberSnapshot(islandId, playerUuid, normalizedRoleKey, current == null ? Instant.now() : current.joinedAt(), expiresAt));
    }

    @Override
    public void removeMember(UUID islandId, UUID playerUuid) {
        Map<UUID, IslandMemberSnapshot> islandMembers = members.get(islandId);
        if (islandMembers != null) {
            islandMembers.remove(playerUuid);
        }
    }

    @Override
    public synchronized IslandInviteSnapshot createInvite(UUID islandId, UUID inviterUuid, UUID targetUuid) {
        Instant now = Instant.now();
        for (Map.Entry<UUID, IslandInviteSnapshot> entry : invites.entrySet()) {
            IslandInviteSnapshot current = entry.getValue();
            if (current.islandId().equals(islandId) && current.targetUuid().equals(targetUuid) && current.state().equals("PENDING")) {
                invites.put(entry.getKey(), new IslandInviteSnapshot(current.inviteId(), current.islandId(), current.inviterUuid(), current.targetUuid(), "EXPIRED", current.createdAt(), current.expiresAt()));
            }
        }
        IslandInviteSnapshot invite = new IslandInviteSnapshot(UUID.randomUUID(), islandId, inviterUuid, targetUuid, "PENDING", now, now.plusSeconds(86400));
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
    public synchronized boolean acceptInvite(UUID inviteId, UUID playerUuid) {
        return acceptInvite(inviteId, playerUuid, Long.MAX_VALUE);
    }

    @Override
    public synchronized boolean acceptInvite(UUID inviteId, UUID playerUuid, long maxMembers) {
        IslandInviteSnapshot invite = invites.get(inviteId);
        if (invite == null || !invite.targetUuid().equals(playerUuid) || !invite.state().equals("PENDING") || !invite.expiresAt().isAfter(Instant.now())) {
            return false;
        }
        boolean existingTeamMember = members(invite.islandId()).stream()
            .anyMatch(member -> member.playerUuid().equals(playerUuid) && kr.lunaf.cloudislands.coreservice.role.CoreRoleKeys.teamMemberRole(member.effectiveRoleKey()));
        long teamMemberCount = members(invite.islandId()).stream()
            .filter(member -> kr.lunaf.cloudislands.coreservice.role.CoreRoleKeys.teamMemberRole(member.effectiveRoleKey()))
            .count();
        if (!existingTeamMember && teamMemberCount >= Math.max(0L, maxMembers)) {
            return false;
        }
        invites.put(inviteId, new IslandInviteSnapshot(invite.inviteId(), invite.islandId(), invite.inviterUuid(), invite.targetUuid(), "ACCEPTED", invite.createdAt(), invite.expiresAt()));
        upsertMemberKey(invite.islandId(), playerUuid, kr.lunaf.cloudislands.coreservice.role.CoreRoleKeys.MEMBER);
        return true;
    }

    @Override
    public synchronized boolean declineInvite(UUID inviteId, UUID playerUuid) {
        IslandInviteSnapshot invite = invites.get(inviteId);
        if (invite == null || !invite.targetUuid().equals(playerUuid) || !invite.state().equals("PENDING")) {
            return false;
        }
        if (!invite.expiresAt().isAfter(Instant.now())) {
            invites.put(inviteId, new IslandInviteSnapshot(invite.inviteId(), invite.islandId(), invite.inviterUuid(), invite.targetUuid(), "EXPIRED", invite.createdAt(), invite.expiresAt()));
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
    public synchronized void banVisitor(UUID islandId, UUID actorUuid, UUID playerUuid, String reason) {
        bans.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>())
            .put(playerUuid, new IslandBanSnapshot(islandId, playerUuid, actorUuid, reason == null ? "" : reason, Instant.now(), null));
    }

    @Override
    public synchronized String banVisitorResult(UUID islandId, UUID actorUuid, UUID playerUuid, String reason) {
        String currentRole = members(islandId).stream()
            .filter(member -> member.playerUuid().equals(playerUuid))
            .map(IslandMemberSnapshot::effectiveRoleKey)
            .findFirst()
            .orElse("");
        if (kr.lunaf.cloudislands.coreservice.role.CoreRoleKeys.memberRole(currentRole)) {
            return "VISITOR_BAN_DENIED";
        }
        banVisitor(islandId, actorUuid, playerUuid, reason);
        return "APPLIED";
    }

    @Override
    public synchronized void pardonVisitor(UUID islandId, UUID playerUuid) {
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
    public boolean setLockedResult(UUID islandId, boolean locked) {
        setLocked(islandId, locked);
        return true;
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
    public boolean resetFlags(UUID islandId) {
        return flags.remove(islandId) != null;
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
        return java.util.Optional.ofNullable(homes.getOrDefault(islandId, Map.of()).get(normalizeResourceName(name)));
    }

    @Override
    public void upsertHome(UUID islandId, String name, IslandLocation location, UUID createdBy) {
        homes.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>())
            .put(normalizeResourceName(name), new IslandHomeSnapshot(islandId, normalizeResourceName(name), location, createdBy, Instant.now()));
    }

    @Override
    public synchronized String upsertHomeWithLimit(UUID islandId, String name, IslandLocation location, UUID createdBy, long maxHomes) {
        Map<String, IslandHomeSnapshot> islandHomes = homes.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>());
        String normalizedName = normalizeResourceName(name);
        boolean existingHome = islandHomes.containsKey(normalizedName);
        if (!existingHome && islandHomes.size() >= Math.max(0L, maxHomes)) {
            return "HOME_LIMIT";
        }
        upsertHome(islandId, normalizedName, location, createdBy);
        return existingHome ? "UPDATED" : "CREATED";
    }

    @Override
    public List<IslandWarpSnapshot> warps(UUID islandId) {
        return new ArrayList<>(warps.getOrDefault(islandId, Map.of()).values());
    }

    @Override
    public List<IslandWarpSnapshot> publicWarps(int limit) {
        return publicWarps(limit, "", "");
    }

    @Override
    public List<IslandWarpSnapshot> publicWarps(int limit, String category, String query) {
        String normalizedCategory = IslandWarpSnapshot.normalizeCategory(category);
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        return warps.values().stream()
            .flatMap(islandWarps -> islandWarps.values().stream())
            .filter(IslandWarpSnapshot::publicAccess)
            .filter(warp -> category == null || category.isBlank() || warp.category().equalsIgnoreCase(normalizedCategory))
            .filter(warp -> normalizedQuery.isBlank() || warp.name().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery) || warp.category().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery))
            .sorted(java.util.Comparator.comparing(IslandWarpSnapshot::createdAt).reversed())
            .limit(Math.max(1, limit))
            .toList();
    }

    @Override
    public Optional<IslandWarpSnapshot> warp(UUID islandId, String name) {
        return Optional.ofNullable(warps.getOrDefault(islandId, Map.of()).get(normalizeResourceName(name)));
    }

    @Override
    public void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy) {
        upsertWarp(islandId, name, location, publicAccess, createdBy, "default");
    }

    @Override
    public void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy, String category) {
        warps.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>())
            .put(normalizeResourceName(name), new IslandWarpSnapshot(islandId, normalizeResourceName(name), location, publicAccess, createdBy, Instant.now(), category));
    }

    @Override
    public synchronized String upsertWarpWithLimit(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy, String category, long maxWarps) {
        Map<String, IslandWarpSnapshot> islandWarps = warps.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>());
        String normalizedName = normalizeResourceName(name);
        boolean existingWarp = islandWarps.containsKey(normalizedName);
        if (!existingWarp && islandWarps.size() >= Math.max(0L, maxWarps)) {
            return "WARP_LIMIT";
        }
        upsertWarp(islandId, normalizedName, location, publicAccess, createdBy, category);
        return existingWarp ? "UPDATED" : "CREATED";
    }

    @Override
    public void setWarpPublicAccess(UUID islandId, String name, boolean publicAccess) {
        setWarpPublicAccessResult(islandId, name, publicAccess);
    }

    @Override
    public boolean setWarpPublicAccessResult(UUID islandId, String name, boolean publicAccess) {
        Map<String, IslandWarpSnapshot> islandWarps = warps.get(islandId);
        if (islandWarps == null) {
            return false;
        }
        return islandWarps.computeIfPresent(normalizeResourceName(name), (_key, warp) -> new IslandWarpSnapshot(
            warp.islandId(),
            warp.name(),
            warp.location(),
            publicAccess,
            warp.createdBy(),
            warp.createdAt(),
            warp.category()
        )) != null;
    }

    @Override
    public void deleteWarp(UUID islandId, String name) {
        deleteWarpResult(islandId, name);
    }

    @Override
    public boolean deleteWarpResult(UUID islandId, String name) {
        Map<String, IslandWarpSnapshot> islandWarps = warps.get(islandId);
        return islandWarps != null && islandWarps.remove(normalizeResourceName(name)) != null;
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

    @Override
    public List<UUID> publicIslandIdsPage(int offset, int limit) {
        return publicAccess.entrySet().stream()
            .filter(Map.Entry::getValue)
            .filter(entry -> !locked.getOrDefault(entry.getKey(), false))
            .map(Map.Entry::getKey)
            .sorted()
            .skip(Math.max(0, offset))
            .limit(Math.max(0, limit))
            .toList();
    }

    private List<IslandMemberSnapshot> activeMembers(Map<UUID, IslandMemberSnapshot> islandMembers) {
        List<IslandMemberSnapshot> result = new ArrayList<>();
        for (IslandMemberSnapshot member : islandMembers.values()) {
            if (!expired(member)) {
                result.add(member);
            }
        }
        return result;
    }

    private boolean expired(IslandMemberSnapshot member) {
        return member.expiresAt() != null && !member.expiresAt().isAfter(Instant.now());
    }
}
