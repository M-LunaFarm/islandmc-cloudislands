package kr.lunaf.cloudislands.migration;

import java.util.List;
import java.util.UUID;

public record MigrationManifest(UUID islandId, UUID ownerUuid, List<UUID> members, List<MigrationMemberRole> memberRoles, List<UUID> bannedVisitors, List<MigrationHome> homes, List<MigrationWarp> warps, List<MigrationFlag> flags, List<MigrationPermission> permissions, List<MigrationUpgrade> upgrades, String biomeKey, String bankBalance, boolean publicAccess, boolean locked, int size, long level, String worth) {}
