package kr.lunaf.cloudislands.coreservice.upgrade;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.feature.GameplayParityPolicy;
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
        applyConfiguredEffects(islandId, actorUuid, rule, type, level);
        applyGeneratorEffect(islandId, actorUuid, rule, type, level);
        applyFlagEffect(islandId, actorUuid, type);
    }

    private void applyConfiguredEffects(UUID islandId, UUID actorUuid, UpgradeRule rule, UpgradeType type, int level) {
        if (rule == null) {
            return;
        }
        String primaryLimitKey = primaryLimitKey(type);
        rule.effectsForLevel(level).forEach((effectKey, value) -> {
            String limitKey = configuredLimitKey(effectKey);
            if (limitKey == null || limitKey.equals(primaryLimitKey)) {
                return;
            }
            IslandLimitSnapshot snapshot = setMonotonicLimit(islandId, limitKey, value, actorUuid);
            if (limitKey.equals("SIZE")) {
                applyIslandSize(islandId, snapshot.value());
            }
            events.publish(CloudIslandEventType.ISLAND_LIMIT_CHANGED.name(), Map.of(
                "islandId", islandId.toString(),
                "limitKey", snapshot.limitKey(),
                "value", Long.toString(snapshot.value())
            ));
            islandLogs.append(islandId, actorUuid, "ISLAND_UPGRADE_EFFECT", Map.of(
                "effect", "CONFIGURED:" + effectKey,
                "limitKey", snapshot.limitKey(),
                "value", Long.toString(snapshot.value())
            ));
        });
    }

    private static String primaryLimitKey(UpgradeType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case ISLAND_SIZE -> "SIZE";
            case MAX_MEMBERS, MEMBER_LIMIT -> "MEMBERS";
            case MAX_WARPS, WARP_LIMIT -> "WARPS";
            case HOME_LIMIT -> "HOMES";
            case BORDER_SIZE -> "BORDER";
            case BIOME_UNLOCK -> "BIOME_UNLOCK";
            case HOPPER_LIMIT -> "HOPPER";
            case SPAWNER_LIMIT -> "SPAWNER";
            case MOB_LIMIT -> "ENTITY";
            case REDSTONE_LIMIT -> "REDSTONE";
            case BANK_LIMIT -> "BANK";
            case CROP_GROWTH -> "CROP_GROWTH";
            case GENERATOR_LEVEL, FLY_ACCESS, BORDER_COLOR_UNLOCK, KEEP_INVENTORY_ENABLE -> null;
        };
    }

    private static String configuredLimitKey(String effectKey) {
        return switch (effectKey) {
            case "size", "island-size" -> "SIZE";
            case "team-limit", "members", "member-limit", "max-members" -> "MEMBERS";
            case "warps-limit", "warps", "warp-limit" -> "WARPS";
            case "homes-limit", "homes", "home-limit", "max-homes" -> "HOMES";
            case "border-size" -> "BORDER";
            case "hopper-limit", "hoppers-limit", "hoppers", "max-hoppers" -> "HOPPER";
            case "spawner-limit", "spawners-limit", "spawners", "max-spawners" -> "SPAWNER";
            case "mob-limit", "entity-limit", "entities-limit" -> "ENTITY";
            case "redstone-limit", "redstone" -> "REDSTONE";
            case "bank-limit", "bank" -> "BANK";
            case "crops-growth", "crop-growth" -> "RATE:CROP_GROWTH";
            case "mob-drops" -> "RATE:MOB_DROPS";
            case "spawner-rates" -> "RATE:SPAWNER_RATES";
            case "coop-limit" -> GameplayParityPolicy.roleLimitKey("TRUSTED");
            default -> configuredNestedLimitKey(effectKey);
        };
    }

    private static String configuredNestedLimitKey(String effectKey) {
        int separator = effectKey.indexOf('.');
        if (separator <= 0 || separator == effectKey.length() - 1) {
            return null;
        }
        String group = effectKey.substring(0, separator);
        String nestedKey = effectKey.substring(separator + 1);
        return switch (group) {
            case "island-effects" -> "EFFECT:" + GameplayParityPolicy.normalizeGameplayKey(effectAlias(nestedKey), "UNKNOWN");
            case "role-limits" -> GameplayParityPolicy.roleLimitKey(nestedKey);
            case "block-limits" -> GameplayParityPolicy.blockAmountLimitKey(nestedKey);
            case "entity-limits" -> switch (GameplayParityPolicy.normalizeGameplayKey(nestedKey, "UNKNOWN")) {
                case "ALL", "GLOBAL", "*" -> "ENTITY";
                default -> GameplayParityPolicy.entityTypeLimitKey(nestedKey);
            };
            default -> null;
        };
    }

    private static String effectAlias(String effectKey) {
        return switch (effectKey) {
            case "fast-digging" -> "HASTE";
            case "jump" -> "JUMP_BOOST";
            case "regen" -> "REGENERATION";
            default -> effectKey;
        };
    }

    private void applyLimitEffect(UUID islandId, UUID actorUuid, UpgradeRule rule, UpgradeType type, int level) {
        java.util.OptionalLong configuredValue = rule == null ? java.util.OptionalLong.empty() : rule.limitValueForLevel(level);
        if (rule != null && !rule.levelEffects().isEmpty() && configuredValue.isEmpty()) {
            return;
        }
        IslandLimitSnapshot snapshot = switch (type) {
            case ISLAND_SIZE -> setMonotonicLimit(islandId, "SIZE", configuredValue.orElse(100L + Math.max(0L, level - 1L) * 50L), actorUuid);
            case MAX_MEMBERS, MEMBER_LIMIT -> setMonotonicLimit(islandId, "MEMBERS", configuredValue.orElse(3L + Math.max(0L, level - 1L) * 2L), actorUuid);
            case MAX_WARPS, WARP_LIMIT -> setMonotonicLimit(islandId, "WARPS", configuredValue.orElse(Math.max(1L, level)), actorUuid);
            case HOME_LIMIT -> setMonotonicLimit(islandId, "HOMES", configuredValue.orElse(Math.max(1L, level)), actorUuid);
            case BORDER_SIZE -> setMonotonicLimit(islandId, "BORDER", configuredValue.orElse(100L + Math.max(0L, level - 1L) * 50L), actorUuid);
            case BIOME_UNLOCK -> setMonotonicLimit(islandId, "BIOME_UNLOCK", configuredValue.orElse(Math.max(1L, level)), actorUuid);
            case HOPPER_LIMIT -> setMonotonicLimit(islandId, "HOPPER", configuredValue.orElse(Math.max(1L, level) * 50L), actorUuid);
            case SPAWNER_LIMIT -> setMonotonicLimit(islandId, "SPAWNER", configuredValue.orElse(Math.max(1L, level) * 25L), actorUuid);
            case MOB_LIMIT -> setMonotonicLimit(islandId, "ENTITY", configuredValue.orElse(Math.max(1L, level) * 200L), actorUuid);
            case REDSTONE_LIMIT -> setMonotonicLimit(islandId, "REDSTONE", configuredValue.orElse(Math.max(1L, level) * 512L), actorUuid);
            case BANK_LIMIT -> setMonotonicLimit(islandId, "BANK", configuredValue.orElse(Math.max(1L, level) * 100000L), actorUuid);
            case CROP_GROWTH -> setMonotonicLimit(islandId, "CROP_GROWTH", configuredValue.orElse(Math.max(1L, level)), actorUuid);
            case GENERATOR_LEVEL, FLY_ACCESS, BORDER_COLOR_UNLOCK, KEEP_INVENTORY_ENABLE -> null;
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

    private IslandLimitSnapshot setMonotonicLimit(UUID islandId, String limitKey, long requested, UUID actorUuid) {
        return limits.setAtLeast(islandId, limitKey, requested, actorUuid);
    }

    private void applyGeneratorEffect(UUID islandId, UUID actorUuid, UpgradeRule rule, UpgradeType type, int level) {
        if (type != UpgradeType.GENERATOR_LEVEL || generators == null) {
            return;
        }
        List<GeneratorRuleSnapshot> configuredRules = configuredGeneratorRules(rule, level);
        String generatorKey = configuredRules.isEmpty() ? generatorKey(rule) : configuredGeneratorKey(rule, level);
        int effectiveLevel = configuredRules.isEmpty()
            ? (int) Math.max(1L, Math.min(Integer.MAX_VALUE, rule == null ? level : rule.limitValueForLevel(level).orElse(level)))
            : Math.max(1, level);
        if (!configuredRules.isEmpty()) {
            configuredRules = generators.setRules(generatorKey, configuredRules);
        }
        var snapshot = generators.setProfileAtLeast(islandId, generatorKey, effectiveLevel);
        events.publish(CloudIslandEventType.ISLAND_UPGRADE.name(), Map.of(
            "islandId", islandId.toString(),
            "upgradeType", type.name(),
            "generatorKey", snapshot.generatorKey(),
            "level", Integer.toString(snapshot.level()),
            "ruleCount", Integer.toString(configuredRules.size())
        ));
        islandLogs.append(islandId, actorUuid, "ISLAND_UPGRADE_EFFECT", Map.of(
            "effect", type.name(),
            "generatorKey", snapshot.generatorKey(),
            "level", Integer.toString(snapshot.level()),
            "ruleCount", Integer.toString(configuredRules.size())
        ));
    }

    private static List<GeneratorRuleSnapshot> configuredGeneratorRules(UpgradeRule rule, int level) {
        if (rule == null) {
            return List.of();
        }
        String generatorKey = configuredGeneratorKey(rule, level);
        Map<String, Long> configuredLevel = rule.levelEffects().entrySet().stream()
            .filter(entry -> entry.getKey() <= level)
            .filter(entry -> entry.getValue().keySet().stream().anyMatch(key -> key.startsWith("generator-rates.")))
            .max(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .orElse(Map.of());
        return configuredLevel.entrySet().stream()
            .filter(entry -> entry.getValue() > 0L)
            .map(entry -> generatorRule(generatorKey, entry))
            .filter(java.util.Objects::nonNull)
            .sorted(java.util.Comparator.comparing(GeneratorRuleSnapshot::materialKey))
            .toList();
    }

    private static GeneratorRuleSnapshot generatorRule(String generatorKey, Map.Entry<String, Long> effect) {
        String[] parts = effect.getKey().split("\\.", 3);
        if (parts.length != 3 || !parts[0].equals("generator-rates") || !normalEnvironment(parts[1])) {
            return null;
        }
        String materialKey = generatorMaterialKey(parts[2]);
        if (materialKey.isBlank()) {
            return null;
        }
        return new GeneratorRuleSnapshot(generatorKey, materialKey, effect.getValue().doubleValue(), 0, 1, "*", true);
    }

    private static boolean normalEnvironment(String environmentKey) {
        return environmentKey.equals("normal") || environmentKey.equals("overworld") || environmentKey.equals("minecraft:overworld");
    }

    private static String generatorMaterialKey(String configuredKey) {
        String normalized = configuredKey == null ? "" : configuredKey.trim().toLowerCase().replace('-', '_');
        if (normalized.isBlank()) {
            return "";
        }
        return normalized.contains(":") ? normalized : "minecraft:" + normalized;
    }

    private static String configuredGeneratorKey(UpgradeRule rule, int level) {
        String upgradeKey = rule == null ? "generator" : rule.upgradeKey();
        String normalized = upgradeKey == null ? "generator" : upgradeKey.trim().toLowerCase().replaceAll("[^a-z0-9_.:-]+", "_");
        return "upgrade:" + (normalized.isBlank() ? "generator" : normalized) + ":level:" + Math.max(1, level);
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
        IslandFlag flag = switch (type) {
            case FLY_ACCESS -> IslandFlag.FLY;
            case KEEP_INVENTORY_ENABLE -> IslandFlag.KEEP_INVENTORY;
            case BORDER_COLOR_UNLOCK -> IslandFlag.BORDER_COLOR;
            default -> null;
        };
        if (flag == null) {
            return;
        }
        String value = type == UpgradeType.BORDER_COLOR_UNLOCK ? "blue" : "true";
        String result = metadata.setFlagResult(islandId, flag, value);
        if (result.equals("APPLIED")) {
            events.publish(CloudIslandEventType.ISLAND_FLAG_CHANGED.name(), Map.of("islandId", islandId.toString(), "flag", flag.name(), "value", value));
        }
        islandLogs.append(islandId, actorUuid, "ISLAND_UPGRADE_EFFECT", Map.of("effect", type.name(), "flag", flag.name(), "value", value, "result", result));
    }
}
