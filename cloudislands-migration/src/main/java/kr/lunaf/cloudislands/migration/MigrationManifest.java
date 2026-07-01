package kr.lunaf.cloudislands.migration;

import java.util.List;
import java.util.UUID;

public record MigrationManifest(UUID islandId, UUID ownerUuid, List<UUID> members, List<MigrationMemberRole> memberRoles, List<UUID> bannedVisitors, List<MigrationHome> homes, List<MigrationWarp> warps, List<MigrationFlag> flags, List<MigrationPermission> permissions, List<MigrationUpgrade> upgrades, List<MigrationLimit> limits, List<MigrationMission> completedMissions, List<MigrationBlockValue> blockValues, List<MigrationBlockCount> blockCounts, List<MigrationWarehouseItem> warehouseItems, String biomeKey, String bankBalance, boolean publicAccess, boolean locked, int size, long level, String worth, MigrationLocation islandLocation, String sourceWorldPath) {
    public MigrationManifest(UUID islandId, UUID ownerUuid, List<UUID> members, List<MigrationMemberRole> memberRoles, List<UUID> bannedVisitors, List<MigrationHome> homes, List<MigrationWarp> warps, List<MigrationFlag> flags, List<MigrationPermission> permissions, List<MigrationUpgrade> upgrades, List<MigrationLimit> limits, List<MigrationMission> completedMissions, List<MigrationBlockValue> blockValues, List<MigrationBlockCount> blockCounts, String biomeKey, String bankBalance, boolean publicAccess, boolean locked, int size, long level, String worth) {
        this(islandId, ownerUuid, members, memberRoles, bannedVisitors, homes, warps, flags, permissions, upgrades, limits, completedMissions, blockValues, blockCounts, List.of(), biomeKey, bankBalance, publicAccess, locked, size, level, worth, MigrationLocation.unknown(), "");
    }

    public MigrationManifest(UUID islandId, UUID ownerUuid, List<UUID> members, List<MigrationMemberRole> memberRoles, List<UUID> bannedVisitors, List<MigrationHome> homes, List<MigrationWarp> warps, List<MigrationFlag> flags, List<MigrationPermission> permissions, List<MigrationUpgrade> upgrades, List<MigrationLimit> limits, List<MigrationMission> completedMissions, List<MigrationBlockValue> blockValues, List<MigrationBlockCount> blockCounts, String biomeKey, String bankBalance, boolean publicAccess, boolean locked, int size, long level, String worth, String sourceWorldPath) {
        this(islandId, ownerUuid, members, memberRoles, bannedVisitors, homes, warps, flags, permissions, upgrades, limits, completedMissions, blockValues, blockCounts, List.of(), biomeKey, bankBalance, publicAccess, locked, size, level, worth, MigrationLocation.unknown(), sourceWorldPath);
    }

    public MigrationManifest(UUID islandId, UUID ownerUuid, List<UUID> members, List<MigrationMemberRole> memberRoles, List<UUID> bannedVisitors, List<MigrationHome> homes, List<MigrationWarp> warps, List<MigrationFlag> flags, List<MigrationPermission> permissions, List<MigrationUpgrade> upgrades, List<MigrationLimit> limits, List<MigrationMission> completedMissions, List<MigrationBlockValue> blockValues, List<MigrationBlockCount> blockCounts, List<MigrationWarehouseItem> warehouseItems, String biomeKey, String bankBalance, boolean publicAccess, boolean locked, int size, long level, String worth, String sourceWorldPath) {
        this(islandId, ownerUuid, members, memberRoles, bannedVisitors, homes, warps, flags, permissions, upgrades, limits, completedMissions, blockValues, blockCounts, warehouseItems, biomeKey, bankBalance, publicAccess, locked, size, level, worth, MigrationLocation.unknown(), sourceWorldPath);
    }
}
