package kr.lunaf.cloudislands.coreservice.upgrade;

import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.generator.IslandGeneratorRepository;
import kr.lunaf.cloudislands.coreservice.islandlog.IslandLogRepository;
import kr.lunaf.cloudislands.coreservice.limit.IslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;

public final class UpgradeEffectApplier {
    private final IslandLimitRepository limits;
    private final IslandRepository islands;
    private final IslandMetadataRepository metadata;
    private final IslandGeneratorRepository generators;
    private final IslandLogRepository islandLogs;
    private final GlobalEventPublisher events;

    public UpgradeEffectApplier(
            IslandLimitRepository limits,
            IslandRepository islands,
            IslandMetadataRepository metadata,
            IslandLogRepository islandLogs,
            GlobalEventPublisher events) {
        this(limits, islands, metadata, null, islandLogs, events);
    }

    public UpgradeEffectApplier(
            IslandLimitRepository limits,
            IslandRepository islands,
            IslandMetadataRepository metadata,
            IslandGeneratorRepository generators,
            IslandLogRepository islandLogs,
            GlobalEventPublisher events) {
        this.limits = limits;
        this.islands = islands;
        this.metadata = metadata;
        this.generators = generators;
        this.islandLogs = islandLogs;
        this.events = events;
    }

    public void apply(UUID islandId, UUID actorUuid, UpgradeRule rule, UpgradeType type, int level) {
        applyLimitEffect(islandId, actorUuid, rule, type, level);
        applyGeneratorEffect(islandId, actorUuid, rule, type, level);
        applyFlagEffect(islandId, actorUuid, type);
    }

    private void applyLimitEffect(UUID islandId, UUID actorUuid, UpgradeRule rule, UpgradeType type, int level) {
        java.util.OptionalLong configuredValue = rule == null ? java.util.OptionalLong.empty() : rule.limitValueForLevel(level);
        IslandLimitSnapshot snapshot = switch (type) {
            case ISLAND_SIZE -> limits.set(islandId, "SIZE", configuredValue.orElse(100L + Math.max(0L, level - 1L) * 50L), actorUuid);
            case MAX_MEMBERS -> limits.set(islandId, "MEMBERS", configuredValue.orElse(3L + Math.max(0L, level - 1L) * 2L), actorUuid);
            case MAX_WARPS -> limits.set(islandId, "WARPS", configuredValue.orElse(Math.max(1L, level)), actorUuid);
            case HOPPER_LIMIT -> limits.set(islandId, "HOPPER", configuredValue.orElse(Math.max(1L, level) * 50L), actorUuid);
            case SPAWNER_LIMIT -> limits.set(islandId, "SPAWNER", configuredValue.orElse(Math.max(1L, level) * 25L), actorUuid);
            case MOB_LIMIT -> limits.set(islandId, "ENTITY", configuredValue.orElse(Math.max(1L, level) * 200L), actorUuid);
            case REDSTONE_LIMIT -> limits.set(islandId, "REDSTONE", configuredValue.orElse(Math.max(1L, level) * 512L), actorUuid);
            case BANK_LIMIT -> limits.set(islandId, "BANK", configuredValue.orElse(Math.max(1L, level) * 100000L), actorUuid);
            case GENERATOR_LEVEL, CROP_GROWTH, FLY_ACCESS -> null;
        };
        if (snapshot == null) {
            return;
        }
        if (type == UpgradeType.ISLAND_SIZE) {
            applyIslandSize(islandId, snapshot.value());
        }
        events.publish(CloudIslandEventType.ISLAND_LIMIT_CHANGED.name(), Map.of("islandId", islandId.toString(), "limitKey", snapshot.limitKey(), "value", Long.toString(snapshot.value())));
        islandLogs.append(islandId, actorUuid, "ISLAND_UPGRADE_EFFECT", Map.of("effect", type.name(), "limitKey", snapshot.limitKey(), "value", Long.toString(snapshot.value())));
    }

    private void applyGeneratorEffect(UUID islandId, UUID actorUuid, UpgradeRule rule, UpgradeType type, int level) {
        if (type != UpgradeType.GENERATOR_LEVEL || generators == null) {
            return;
        }
        String generatorKey = generatorKey(rule);
        int effectiveLevel = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, rule == null ? level : rule.limitValueForLevel(level).orElse(level)));
        var snapshot = generators.setProfile(islandId, generatorKey, effectiveLevel);
        events.publish(CloudIslandEventType.ISLAND_UPGRADE.name(), Map.of(
            "islandId", islandId.toString(),
            "upgradeType", type.name(),
            "generatorKey", snapshot.generatorKey(),
            "level", Integer.toString(snapshot.level())
        ));
        islandLogs.append(islandId, actorUuid, "ISLAND_UPGRADE_EFFECT", Map.of(
            "effect", type.name(),
            "generatorKey", snapshot.generatorKey(),
            "level", Integer.toString(snapshot.level())
        ));
    }

    private static String generatorKey(UpgradeRule rule) {
        String key = rule == null ? "" : rule.upgradeKey();
        if (key == null || key.isBlank() || key.equals("generator")) {
            return "default";
        }
        if (key.startsWith("generator:")) {
            return key.substring("generator:".length()).trim().toLowerCase();
        }
        return key.trim().toLowerCase();
    }

    private void applyIslandSize(UUID islandId, long size) {
        IslandSnapshot island = islands.findById(islandId).orElse(null);
        if (island == null) {
            return;
        }
        int safeSize = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, size));
        islands.updateStats(islandId, safeSize, island.level(), island.worth());
    }

    private void applyFlagEffect(UUID islandId, UUID actorUuid, UpgradeType type) {
        if (type != UpgradeType.FLY_ACCESS) {
            return;
        }
        metadata.setFlag(islandId, IslandFlag.FLY, "true");
        events.publish(CloudIslandEventType.ISLAND_FLAG_CHANGED.name(), Map.of("islandId", islandId.toString(), "flag", IslandFlag.FLY.name(), "value", "true"));
        islandLogs.append(islandId, actorUuid, "ISLAND_UPGRADE_EFFECT", Map.of("effect", type.name(), "flag", IslandFlag.FLY.name(), "value", "true"));
    }
}
