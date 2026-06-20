package kr.lunaf.cloudislands.coreservice.repository;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import kr.lunaf.cloudislands.common.cache.RedisTtls;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
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
        Optional<List<IslandMemberSnapshot>> cached = cachedMembers(islandId);
        if (cached.isPresent()) {
            return cached.get();
        }
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
        Optional<List<IslandBanSnapshot>> cached = cachedBans(islandId);
        if (cached.isPresent()) {
            return cached.get().stream().anyMatch(ban -> ban.bannedUuid().equals(playerUuid));
        }
        boolean banned = delegate.isBanned(islandId, playerUuid);
        cacheBans(islandId, delegate.bans(islandId));
        return banned;
    }

    @Override
    public List<IslandBanSnapshot> bans(UUID islandId) {
        Optional<List<IslandBanSnapshot>> cached = cachedBans(islandId);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<IslandBanSnapshot> bans = delegate.bans(islandId);
        cacheBans(islandId, bans);
        return bans;
    }

    @Override
    public void banVisitor(UUID islandId, UUID actorUuid, UUID playerUuid, String reason) {
        delegate.banVisitor(islandId, actorUuid, playerUuid, reason);
        cacheBans(islandId, delegate.bans(islandId));
    }

    @Override
    public void pardonVisitor(UUID islandId, UUID playerUuid) {
        delegate.pardonVisitor(islandId, playerUuid);
        cacheBans(islandId, delegate.bans(islandId));
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
        Optional<IslandFlagsSnapshot> cached = cachedFlags(islandId);
        if (cached.isPresent()) {
            return cached.get();
        }
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
        Optional<List<IslandHomeSnapshot>> cached = cachedHomes(islandId);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<IslandHomeSnapshot> homes = delegate.homes(islandId);
        cacheHomes(islandId, homes);
        return homes;
    }

    @Override
    public Optional<IslandHomeSnapshot> home(UUID islandId, String name) {
        Optional<List<IslandHomeSnapshot>> cached = cachedHomes(islandId);
        if (cached.isPresent()) {
            return cached.get().stream().filter(home -> home.name().equalsIgnoreCase(name)).findFirst();
        }
        Optional<IslandHomeSnapshot> home = delegate.home(islandId, name);
        cacheHomes(islandId, delegate.homes(islandId));
        return home;
    }

    @Override
    public void upsertHome(UUID islandId, String name, IslandLocation location, UUID createdBy) {
        delegate.upsertHome(islandId, name, location, createdBy);
        cacheHomes(islandId, delegate.homes(islandId));
    }

    @Override
    public List<IslandWarpSnapshot> warps(UUID islandId) {
        Optional<List<IslandWarpSnapshot>> cached = cachedWarps(islandId);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<IslandWarpSnapshot> warps = delegate.warps(islandId);
        cacheWarps(islandId, warps);
        return warps;
    }

    @Override
    public List<IslandWarpSnapshot> publicWarps(int limit) {
        return delegate.publicWarps(limit);
    }

    @Override
    public List<IslandWarpSnapshot> publicWarps(int limit, String category, String query) {
        return delegate.publicWarps(limit, category, query);
    }

    @Override
    public Optional<IslandWarpSnapshot> warp(UUID islandId, String name) {
        Optional<List<IslandWarpSnapshot>> cached = cachedWarps(islandId);
        if (cached.isPresent()) {
            return cached.get().stream().filter(warp -> warp.name().equalsIgnoreCase(name)).findFirst();
        }
        Optional<IslandWarpSnapshot> warp = delegate.warp(islandId, name);
        cacheWarps(islandId, delegate.warps(islandId));
        return warp;
    }

    @Override
    public void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy) {
        delegate.upsertWarp(islandId, name, location, publicAccess, createdBy);
        cacheWarps(islandId, delegate.warps(islandId));
    }

    @Override
    public void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy, String category) {
        delegate.upsertWarp(islandId, name, location, publicAccess, createdBy, category);
        cacheWarps(islandId, delegate.warps(islandId));
    }

    @Override
    public void setWarpPublicAccess(UUID islandId, String name, boolean publicAccess) {
        delegate.setWarpPublicAccess(islandId, name, publicAccess);
        cacheWarps(islandId, delegate.warps(islandId));
    }

    @Override
    public void deleteWarp(UUID islandId, String name) {
        delegate.deleteWarp(islandId, name);
        cacheWarps(islandId, delegate.warps(islandId));
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
            redis.command("SET", RedisKeys.islandMembers(islandId), membersJson(members), "PX", Long.toString(RedisTtls.ISLAND_METADATA_MILLIS));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private Optional<List<IslandMemberSnapshot>> cachedMembers(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.islandMembers(islandId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            List<IslandMemberSnapshot> members = new ArrayList<>();
            for (String object : objects(json)) {
                members.add(new IslandMemberSnapshot(
                    JsonFields.uuid(object, "islandId", islandId),
                    JsonFields.uuid(object, "playerUuid", new UUID(0L, 0L)),
                    JsonFields.enumValue(IslandRole.class, object, "role", IslandRole.VISITOR),
                    instant(JsonFields.text(object, "joinedAt", ""))
                ));
            }
            return Optional.of(List.copyOf(members));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private void cacheBans(UUID islandId, List<IslandBanSnapshot> bans) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandBans(islandId), bansJson(bans), "PX", Long.toString(RedisTtls.ISLAND_METADATA_MILLIS));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private Optional<List<IslandBanSnapshot>> cachedBans(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.islandBans(islandId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            List<IslandBanSnapshot> bans = new ArrayList<>();
            for (String object : objects(json)) {
                bans.add(new IslandBanSnapshot(
                    JsonFields.uuid(object, "islandId", islandId),
                    JsonFields.uuid(object, "bannedUuid", new UUID(0L, 0L)),
                    JsonFields.uuid(object, "actorUuid", new UUID(0L, 0L)),
                    JsonFields.text(object, "reason", ""),
                    instant(JsonFields.text(object, "createdAt", "")),
                    nullableInstant(JsonFields.text(object, "expiresAt", ""))
                ));
            }
            return Optional.of(List.copyOf(bans));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private Optional<IslandFlagsSnapshot> cachedFlags(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.islandFlags(islandId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            Map<IslandFlag, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : JsonFields.object(json, "values").entrySet()) {
                values.put(IslandFlag.valueOf(entry.getKey()), entry.getValue());
            }
            return Optional.of(new IslandFlagsSnapshot(JsonFields.uuid(json, "islandId", islandId), Map.copyOf(values)));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private void cacheFlags(IslandFlagsSnapshot flags) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandFlags(flags.islandId()), flagsJson(flags), "PX", Long.toString(RedisTtls.ISLAND_PERMISSIONS_MILLIS));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private void cacheHomes(UUID islandId, List<IslandHomeSnapshot> homes) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandHomes(islandId), homesJson(homes), "PX", Long.toString(RedisTtls.ISLAND_METADATA_MILLIS));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private Optional<List<IslandHomeSnapshot>> cachedHomes(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.islandHomes(islandId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            List<IslandHomeSnapshot> homes = new ArrayList<>();
            for (String object : objects(json)) {
                IslandLocation location = new IslandLocation(
                    JsonFields.text(object, "worldName", ""),
                    JsonFields.decimal(object, "localX", 0.0D),
                    JsonFields.decimal(object, "localY", 0.0D),
                    JsonFields.decimal(object, "localZ", 0.0D),
                    (float) JsonFields.decimal(object, "yaw", 0.0D),
                    (float) JsonFields.decimal(object, "pitch", 0.0D)
                );
                homes.add(new IslandHomeSnapshot(
                    JsonFields.uuid(object, "islandId", islandId),
                    JsonFields.text(object, "name", "default"),
                    location,
                    JsonFields.uuid(object, "createdBy", new UUID(0L, 0L)),
                    instant(JsonFields.text(object, "createdAt", ""))
                ));
            }
            return Optional.of(List.copyOf(homes));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private void cacheWarps(UUID islandId, List<IslandWarpSnapshot> warps) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandWarps(islandId), warpsJson(warps), "PX", Long.toString(RedisTtls.ISLAND_METADATA_MILLIS));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private Optional<List<IslandWarpSnapshot>> cachedWarps(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.islandWarps(islandId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            List<IslandWarpSnapshot> warps = new ArrayList<>();
            for (String object : objects(json)) {
                IslandLocation location = new IslandLocation(
                    JsonFields.text(object, "worldName", ""),
                    JsonFields.decimal(object, "localX", 0.0D),
                    JsonFields.decimal(object, "localY", 0.0D),
                    JsonFields.decimal(object, "localZ", 0.0D),
                    (float) JsonFields.decimal(object, "yaw", 0.0D),
                    (float) JsonFields.decimal(object, "pitch", 0.0D)
                );
                warps.add(new IslandWarpSnapshot(
                    JsonFields.uuid(object, "islandId", islandId),
                    JsonFields.text(object, "name", "default"),
                    location,
                    JsonFields.bool(object, "publicAccess", false),
                    JsonFields.uuid(object, "createdBy", new UUID(0L, 0L)),
                    instant(JsonFields.text(object, "createdAt", "")),
                    JsonFields.text(object, "category", "default")
                ));
            }
            return Optional.of(List.copyOf(warps));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
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

    private static String bansJson(List<IslandBanSnapshot> bans) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (IslandBanSnapshot ban : bans) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(ban.islandId()).append("\",")
                .append("\"bannedUuid\":\"").append(ban.bannedUuid()).append("\",")
                .append("\"actorUuid\":\"").append(ban.actorUuid()).append("\",")
                .append("\"reason\":\"").append(escape(ban.reason())).append("\",")
                .append("\"createdAt\":\"").append(ban.createdAt()).append("\",")
                .append("\"expiresAt\":\"").append(ban.expiresAt() == null ? "" : ban.expiresAt()).append("\"")
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

    private static String homesJson(List<IslandHomeSnapshot> homes) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (IslandHomeSnapshot home : homes) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(home.islandId()).append("\",")
                .append("\"name\":\"").append(escape(home.name())).append("\",")
                .append("\"worldName\":\"").append(escape(home.location().worldName())).append("\",")
                .append("\"localX\":").append(home.location().localX()).append(',')
                .append("\"localY\":").append(home.location().localY()).append(',')
                .append("\"localZ\":").append(home.location().localZ()).append(',')
                .append("\"yaw\":").append(home.location().yaw()).append(',')
                .append("\"pitch\":").append(home.location().pitch()).append(',')
                .append("\"createdBy\":\"").append(home.createdBy()).append("\",")
                .append("\"createdAt\":\"").append(home.createdAt()).append("\"")
                .append('}');
        }
        return builder.append(']').toString();
    }

    private static String warpsJson(List<IslandWarpSnapshot> warps) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (IslandWarpSnapshot warp : warps) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(warp.islandId()).append("\",")
                .append("\"name\":\"").append(escape(warp.name())).append("\",")
                .append("\"worldName\":\"").append(escape(warp.location().worldName())).append("\",")
                .append("\"localX\":").append(warp.location().localX()).append(',')
                .append("\"localY\":").append(warp.location().localY()).append(',')
                .append("\"localZ\":").append(warp.location().localZ()).append(',')
                .append("\"yaw\":").append(warp.location().yaw()).append(',')
                .append("\"pitch\":").append(warp.location().pitch()).append(',')
                .append("\"publicAccess\":").append(warp.publicAccess()).append(',')
                .append("\"category\":\"").append(escape(warp.category())).append("\",")
                .append("\"createdBy\":\"").append(warp.createdBy()).append("\",")
                .append("\"createdAt\":\"").append(warp.createdAt()).append("\"")
                .append('}');
        }
        return builder.append(']').toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static List<String> objects(String json) {
        List<String> result = new ArrayList<>();
        int index = 0;
        while (index < json.length()) {
            int start = json.indexOf('{', index);
            if (start < 0) {
                return result;
            }
            int end = matchingObjectEnd(json, start);
            if (end < 0) {
                return result;
            }
            result.add(json.substring(start, end + 1));
            index = end + 1;
        }
        return result;
    }

    private static int matchingObjectEnd(String json, int objectStart) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = objectStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    private static Instant nullableInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
