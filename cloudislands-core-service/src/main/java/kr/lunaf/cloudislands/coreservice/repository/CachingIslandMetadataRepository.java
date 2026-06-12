package kr.lunaf.cloudislands.coreservice.repository;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingIslandMetadataRepository implements IslandMetadataRepository {
    private final IslandMetadataRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandMetadataRepository(IslandMetadataRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public List<IslandMemberSnapshot> members(UUID islandId) {
        List<IslandMemberSnapshot> members = delegate.members(islandId);
        cacheMembers(islandId, members);
        return members;
    }

    @Override
    public List<IslandMemberSnapshot> islandsForMember(UUID playerUuid) {
        return delegate.islandsForMember(playerUuid);
    }

    @Override
    public boolean isMember(UUID islandId, UUID playerUuid) {
        return delegate.isMember(islandId, playerUuid);
    }

    @Override
    public void upsertMember(UUID islandId, UUID playerUuid, IslandRole role) {
        delegate.upsertMember(islandId, playerUuid, role);
        cacheMembers(islandId, delegate.members(islandId));
    }

    @Override
    public void removeMember(UUID islandId, UUID playerUuid) {
        delegate.removeMember(islandId, playerUuid);
        cacheMembers(islandId, delegate.members(islandId));
    }

    @Override
    public IslandInviteSnapshot createInvite(UUID islandId, UUID inviterUuid, UUID targetUuid) {
        return delegate.createInvite(islandId, inviterUuid, targetUuid);
    }

    @Override
    public List<IslandInviteSnapshot> pendingInvites(UUID targetUuid) {
        return delegate.pendingInvites(targetUuid);
    }

    @Override
    public boolean acceptInvite(UUID inviteId, UUID playerUuid) {
        boolean accepted = delegate.acceptInvite(inviteId, playerUuid);
        if (accepted) {
            for (IslandMemberSnapshot member : delegate.islandsForMember(playerUuid)) {
                cacheMembers(member.islandId(), delegate.members(member.islandId()));
            }
        }
        return accepted;
    }

    @Override
    public boolean declineInvite(UUID inviteId, UUID playerUuid) {
        return delegate.declineInvite(inviteId, playerUuid);
    }

    @Override
    public boolean isBanned(UUID islandId, UUID playerUuid) {
        return delegate.isBanned(islandId, playerUuid);
    }

    @Override
    public List<IslandBanSnapshot> bans(UUID islandId) {
        return delegate.bans(islandId);
    }

    @Override
    public void banVisitor(UUID islandId, UUID actorUuid, UUID playerUuid, String reason) {
        delegate.banVisitor(islandId, actorUuid, playerUuid, reason);
    }

    @Override
    public void pardonVisitor(UUID islandId, UUID playerUuid) {
        delegate.pardonVisitor(islandId, playerUuid);
    }

    @Override
    public boolean isLocked(UUID islandId) {
        return delegate.isLocked(islandId);
    }

    @Override
    public void setLocked(UUID islandId, boolean locked) {
        delegate.setLocked(islandId, locked);
    }

    @Override
    public IslandFlagsSnapshot flags(UUID islandId) {
        IslandFlagsSnapshot flags = delegate.flags(islandId);
        cacheFlags(flags);
        return flags;
    }

    @Override
    public void setFlag(UUID islandId, IslandFlag flag, String value) {
        delegate.setFlag(islandId, flag, value);
        cacheFlags(delegate.flags(islandId));
    }

    @Override
    public IslandBiomeSnapshot biome(UUID islandId) {
        return delegate.biome(islandId);
    }

    @Override
    public void setBiome(UUID islandId, String biomeKey, UUID updatedBy) {
        delegate.setBiome(islandId, biomeKey, updatedBy);
    }

    @Override
    public List<IslandHomeSnapshot> homes(UUID islandId) {
        return delegate.homes(islandId);
    }

    @Override
    public Optional<IslandHomeSnapshot> home(UUID islandId, String name) {
        return delegate.home(islandId, name);
    }

    @Override
    public void upsertHome(UUID islandId, String name, IslandLocation location, UUID createdBy) {
        delegate.upsertHome(islandId, name, location, createdBy);
    }

    @Override
    public List<IslandWarpSnapshot> warps(UUID islandId) {
        return delegate.warps(islandId);
    }

    @Override
    public List<IslandWarpSnapshot> publicWarps(int limit) {
        return delegate.publicWarps(limit);
    }

    @Override
    public Optional<IslandWarpSnapshot> warp(UUID islandId, String name) {
        return delegate.warp(islandId, name);
    }

    @Override
    public void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy) {
        delegate.upsertWarp(islandId, name, location, publicAccess, createdBy);
    }

    @Override
    public void setWarpPublicAccess(UUID islandId, String name, boolean publicAccess) {
        delegate.setWarpPublicAccess(islandId, name, publicAccess);
    }

    @Override
    public void deleteWarp(UUID islandId, String name) {
        delegate.deleteWarp(islandId, name);
    }

    @Override
    public boolean isPublicAccess(UUID islandId) {
        return delegate.isPublicAccess(islandId);
    }

    @Override
    public void setPublicAccess(UUID islandId, boolean publicAccess) {
        delegate.setPublicAccess(islandId, publicAccess);
    }

    @Override
    public List<UUID> publicIslandIds(int limit) {
        return delegate.publicIslandIds(limit);
    }

    public long failuresTotal() {
        return failures.get();
    }

    private void cacheMembers(UUID islandId, List<IslandMemberSnapshot> members) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandMembers(islandId), membersJson(members));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private void cacheFlags(IslandFlagsSnapshot flags) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandFlags(flags.islandId()), flagsJson(flags));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private static String membersJson(List<IslandMemberSnapshot> members) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (IslandMemberSnapshot member : members) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(member.islandId()).append("\",")
                .append("\"playerUuid\":\"").append(member.playerUuid()).append("\",")
                .append("\"role\":\"").append(member.role().name()).append("\",")
                .append("\"joinedAt\":\"").append(member.joinedAt()).append("\"")
                .append('}');
        }
        return builder.append(']').toString();
    }

    private static String flagsJson(IslandFlagsSnapshot flags) {
        StringBuilder builder = new StringBuilder("{\"islandId\":\"")
            .append(flags.islandId())
            .append("\",\"values\":{");
        boolean first = true;
        for (java.util.Map.Entry<IslandFlag, String> entry : flags.values().entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(entry.getKey().name()).append("\":\"").append(escape(entry.getValue())).append('"');
        }
        return builder.append("}}").toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
