package kr.lunaf.cloudislands.coreservice.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.generator.InMemoryIslandGeneratorRepository;
import kr.lunaf.cloudislands.coreservice.islandlog.InMemoryIslandLogRepository;
import kr.lunaf.cloudislands.coreservice.limit.InMemoryIslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import org.junit.jupiter.api.Test;

class UpgradeEffectApplierTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000601");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000602");

    @Test
    void sizeUpgradeUpdatesLimitAndAuthoritativeIslandSize() {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandLimitRepository limits = new InMemoryIslandLimitRepository();
        InMemoryIslandLogRepository logs = new InMemoryIslandLogRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        islands.createOwnedIsland(ISLAND_ID, OWNER_ID, "default", "base");

        new UpgradeEffectApplier(limits, islands, new InMemoryIslandMetadataRepository(), logs, events)
            .apply(ISLAND_ID, OWNER_ID, new UpgradeRule("size", UpgradeType.ISLAND_SIZE, 3, BigDecimal.ZERO, BigDecimal.ONE, Map.of(2, 150L)), UpgradeType.ISLAND_SIZE, 2);

        assertEquals(150L, limits.list(ISLAND_ID).stream().filter(limit -> limit.limitKey().equals("SIZE")).findFirst().orElseThrow().value());
        assertEquals(150, islands.findById(ISLAND_ID).orElseThrow().size());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_LIMIT_CHANGED.name()));
        assertTrue(logs.list(ISLAND_ID, 10).stream().anyMatch(record -> record.action().equals("ISLAND_UPGRADE_EFFECT") && record.payload().get("effect").equals("ISLAND_SIZE")));
    }

    @Test
    void flyUpgradeAppliesFlagAndPublishesEvent() {
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        InMemoryIslandLogRepository logs = new InMemoryIslandLogRepository();

        UpgradeEffectApplier applier = new UpgradeEffectApplier(new InMemoryIslandLimitRepository(), new InMemoryIslandRepository(), metadata, logs, events);
        UpgradeRule rule = new UpgradeRule("fly", UpgradeType.FLY_ACCESS, 1, BigDecimal.ZERO, BigDecimal.ONE);
        applier.apply(ISLAND_ID, OWNER_ID, rule, UpgradeType.FLY_ACCESS, 1);
        applier.apply(ISLAND_ID, OWNER_ID, rule, UpgradeType.FLY_ACCESS, 1);

        assertEquals("true", metadata.flags(ISLAND_ID).values().get(IslandFlag.FLY));
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_FLAG_CHANGED.name()));
        assertTrue(logs.list(ISLAND_ID, 10).stream().anyMatch(record -> record.action().equals("ISLAND_UPGRADE_EFFECT") && record.payload().get("effect").equals("FLY_ACCESS")));
        assertTrue(logs.list(ISLAND_ID, 10).stream().anyMatch(record -> "UNCHANGED".equals(record.payload().get("result"))));
    }

    @Test
    void generatorUpgradeUpdatesAuthoritativeGeneratorProfile() {
        InMemoryIslandGeneratorRepository generators = new InMemoryIslandGeneratorRepository();
        InMemoryIslandLogRepository logs = new InMemoryIslandLogRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();

        new UpgradeEffectApplier(new InMemoryIslandLimitRepository(), new InMemoryIslandRepository(), new InMemoryIslandMetadataRepository(), generators, logs, events)
            .apply(ISLAND_ID, OWNER_ID, new UpgradeRule("generator", UpgradeType.GENERATOR_LEVEL, 5, BigDecimal.ZERO, BigDecimal.ONE, Map.of(3, 4L)), UpgradeType.GENERATOR_LEVEL, 3);

        assertEquals("default", generators.profile(ISLAND_ID).generatorKey());
        assertEquals(4, generators.profile(ISLAND_ID).level());
        assertTrue(logs.list(ISLAND_ID, 10).stream().anyMatch(record -> record.action().equals("ISLAND_UPGRADE_EFFECT") && record.payload().get("effect").equals("GENERATOR_LEVEL")));
    }

    @Test
    void ss2GeneratorRatesCreateIsolatedWeightedProfileForEachLevel() {
        InMemoryIslandGeneratorRepository generators = new InMemoryIslandGeneratorRepository();
        UpgradeRule rule = new UpgradeRule(
            "island-generators",
            UpgradeType.GENERATOR_LEVEL,
            2,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(
                1, Map.of(
                    "generator-rates.normal.stone", 85L,
                    "generator-rates.normal.coal-ore", 15L
                ),
                2, Map.of(
                    "generator-rates.normal.stone", 70L,
                    "generator-rates.normal.diamond-ore", 30L,
                    "generator-rates.nether.basalt", 100L
                )
            )
        );
        UpgradeEffectApplier applier = new UpgradeEffectApplier(
            new InMemoryIslandLimitRepository(),
            new InMemoryIslandRepository(),
            new InMemoryIslandMetadataRepository(),
            generators,
            new InMemoryIslandLogRepository(),
            new InMemoryGlobalEventPublisher()
        );

        applier.apply(ISLAND_ID, OWNER_ID, rule, UpgradeType.GENERATOR_LEVEL, 1);

        assertEquals("upgrade:island-generators:level:1", generators.profile(ISLAND_ID).generatorKey());
        assertEquals(1, generators.profile(ISLAND_ID).level());
        assertEquals(Map.of("minecraft:stone", 85.0D, "minecraft:coal_ore", 15.0D), generatorWeights(generators, "upgrade:island-generators:level:1"));

        applier.apply(ISLAND_ID, OWNER_ID, rule, UpgradeType.GENERATOR_LEVEL, 2);

        assertEquals("upgrade:island-generators:level:2", generators.profile(ISLAND_ID).generatorKey());
        assertEquals(2, generators.profile(ISLAND_ID).level());
        assertEquals(Map.of("minecraft:stone", 70.0D, "minecraft:diamond_ore", 30.0D), generatorWeights(generators, "upgrade:island-generators:level:2"));
        assertEquals(Map.of("minecraft:stone", 85.0D, "minecraft:coal_ore", 15.0D), generatorWeights(generators, "upgrade:island-generators:level:1"));
    }

    @Test
    void borderHomeAndBiomeUpgradesUpdateAuthoritativeLimits() {
        InMemoryIslandLimitRepository limits = new InMemoryIslandLimitRepository();

        UpgradeEffectApplier applier = new UpgradeEffectApplier(limits, new InMemoryIslandRepository(), new InMemoryIslandMetadataRepository(), new InMemoryIslandLogRepository(), new InMemoryGlobalEventPublisher());
        applier.apply(ISLAND_ID, OWNER_ID, new UpgradeRule("border", UpgradeType.BORDER_SIZE, 3, BigDecimal.ZERO, BigDecimal.ONE, Map.of(2, 150L)), UpgradeType.BORDER_SIZE, 2);
        applier.apply(ISLAND_ID, OWNER_ID, new UpgradeRule("homes", UpgradeType.HOME_LIMIT, 3, BigDecimal.ZERO, BigDecimal.ONE, Map.of(2, 2L)), UpgradeType.HOME_LIMIT, 2);
        applier.apply(ISLAND_ID, OWNER_ID, new UpgradeRule("biome", UpgradeType.BIOME_UNLOCK, 1, BigDecimal.ZERO, BigDecimal.ONE), UpgradeType.BIOME_UNLOCK, 1);

        assertEquals(150L, limitValue(limits, "BORDER"));
        assertEquals(2L, limitValue(limits, "HOMES"));
        assertEquals(1L, limitValue(limits, "BIOME_UNLOCK"));
    }

    @Test
    void keepInventoryAndBorderColorUpgradesApplyFlags() {
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();

        UpgradeEffectApplier applier = new UpgradeEffectApplier(new InMemoryIslandLimitRepository(), new InMemoryIslandRepository(), metadata, new InMemoryIslandLogRepository(), events);
        applier.apply(ISLAND_ID, OWNER_ID, new UpgradeRule("keep-inventory", UpgradeType.KEEP_INVENTORY_ENABLE, 1, BigDecimal.ZERO, BigDecimal.ONE), UpgradeType.KEEP_INVENTORY_ENABLE, 1);
        applier.apply(ISLAND_ID, OWNER_ID, new UpgradeRule("border-color", UpgradeType.BORDER_COLOR_UNLOCK, 1, BigDecimal.ZERO, BigDecimal.ONE), UpgradeType.BORDER_COLOR_UNLOCK, 1);

        assertEquals("true", metadata.flags(ISLAND_ID).values().get(IslandFlag.KEEP_INVENTORY));
        assertEquals("blue", metadata.flags(ISLAND_ID).values().get(IslandFlag.BORDER_COLOR));
        assertEquals(2L, events.countByType(CloudIslandEventType.ISLAND_FLAG_CHANGED.name()));
    }

    @Test
    void upgradeEffectsNeverReduceLimitsEarnedFromMissionsOrAdministration() {
        InMemoryIslandLimitRepository limits = new InMemoryIslandLimitRepository();
        limits.set(ISLAND_ID, "HOPPER", 75L, OWNER_ID);
        limits.set(ISLAND_ID, "BANK", 500_000L, OWNER_ID);
        UpgradeEffectApplier applier = new UpgradeEffectApplier(
            limits,
            new InMemoryIslandRepository(),
            new InMemoryIslandMetadataRepository(),
            new InMemoryIslandLogRepository(),
            new InMemoryGlobalEventPublisher()
        );

        applier.apply(ISLAND_ID, OWNER_ID, new UpgradeRule("hopper", UpgradeType.HOPPER_LIMIT, 5, BigDecimal.ZERO, BigDecimal.ONE), UpgradeType.HOPPER_LIMIT, 1);
        applier.apply(ISLAND_ID, OWNER_ID, new UpgradeRule("bank", UpgradeType.BANK_LIMIT, 5, BigDecimal.ZERO, BigDecimal.ONE), UpgradeType.BANK_LIMIT, 1);

        assertEquals(75L, limitValue(limits, "HOPPER"));
        assertEquals(500_000L, limitValue(limits, "BANK"));
    }

    @Test
    void oneUpgradeLevelAppliesEverySupportedConfiguredEffect() {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandLimitRepository limits = new InMemoryIslandLimitRepository();
        islands.createOwnedIsland(ISLAND_ID, OWNER_ID, "default", "base");
        UpgradeRule rule = new UpgradeRule(
            "utility",
            UpgradeType.ISLAND_SIZE,
            2,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            Map.of(),
            Map.of(1, 125L),
            Map.of(),
            Map.of(1, Map.ofEntries(
                Map.entry("size", 125L),
                Map.entry("team-limit", 4L),
                Map.entry("coop-limit", 5L),
                Map.entry("crops-growth", 140L),
                Map.entry("mob-drops", 175L),
                Map.entry("spawner-rates", 80L),
                Map.entry("island-effects.speed", 2L),
                Map.entry("island-effects.fast-digging", 1L),
                Map.entry("role-limits.member", 12L),
                Map.entry("block-limits.diamond-block", 64L),
                Map.entry("entity-limits.zombie", 12L),
                Map.entry("entity-limits.all", 250L)
            ))
        );

        new UpgradeEffectApplier(
            limits,
            islands,
            new InMemoryIslandMetadataRepository(),
            new InMemoryIslandLogRepository(),
            new InMemoryGlobalEventPublisher()
        ).apply(ISLAND_ID, OWNER_ID, rule, UpgradeType.ISLAND_SIZE, 1);

        assertEquals(125L, limitValue(limits, "SIZE"));
        assertEquals(4L, limitValue(limits, "MEMBERS"));
        assertEquals(5L, limitValue(limits, "ROLE_LIMIT:TRUSTED"));
        assertEquals(12L, limitValue(limits, "ROLE_LIMIT:MEMBER"));
        assertEquals(140L, limitValue(limits, "RATE:CROP_GROWTH"));
        assertEquals(175L, limitValue(limits, "RATE:MOB_DROPS"));
        assertEquals(80L, limitValue(limits, "RATE:SPAWNER_RATES"));
        assertEquals(2L, limitValue(limits, "EFFECT:SPEED"));
        assertEquals(1L, limitValue(limits, "EFFECT:HASTE"));
        assertEquals(64L, limitValue(limits, "BLOCK_AMOUNT:MINECRAFT:DIAMOND_BLOCK"));
        assertEquals(12L, limitValue(limits, "ENTITY_TYPE:MINECRAFT:ZOMBIE"));
        assertEquals(250L, limitValue(limits, "ENTITY"));
        assertEquals(125, islands.findById(ISLAND_ID).orElseThrow().size());
    }

    @Test
    void configuredEffectsDoNotReduceAdministrativeOverrides() {
        InMemoryIslandLimitRepository limits = new InMemoryIslandLimitRepository();
        limits.set(ISLAND_ID, "RATE:CROP_GROWTH", 250L, OWNER_ID);
        UpgradeRule rule = new UpgradeRule(
            "crop",
            UpgradeType.CROP_GROWTH,
            1,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            Map.of(),
            Map.of(1, 1L),
            Map.of(),
            Map.of(1, Map.of("crops-growth", 140L))
        );

        new UpgradeEffectApplier(
            limits,
            new InMemoryIslandRepository(),
            new InMemoryIslandMetadataRepository(),
            new InMemoryIslandLogRepository(),
            new InMemoryGlobalEventPublisher()
        ).apply(ISLAND_ID, OWNER_ID, rule, UpgradeType.CROP_GROWTH, 1);

        assertEquals(250L, limitValue(limits, "RATE:CROP_GROWTH"));
    }

    @Test
    void customCompositeUpgradeDoesNotApplyDefaultTypeFallback() {
        InMemoryIslandLimitRepository limits = new InMemoryIslandLimitRepository();
        UpgradeRule rule = new UpgradeRule(
            "custom-composite",
            UpgradeType.ISLAND_SIZE,
            2,
            BigDecimal.ZERO,
            BigDecimal.ONE,
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(2, Map.of("crops-growth", 150L))
        );

        new UpgradeEffectApplier(
            limits,
            new InMemoryIslandRepository(),
            new InMemoryIslandMetadataRepository(),
            new InMemoryIslandLogRepository(),
            new InMemoryGlobalEventPublisher()
        ).apply(ISLAND_ID, OWNER_ID, rule, UpgradeType.ISLAND_SIZE, 2);

        assertEquals(100L, limitValue(limits, "SIZE"));
        assertEquals(150L, limitValue(limits, "RATE:CROP_GROWTH"));
    }

    @Test
    void atomicMinimumWritesConvergeToTheHighestConcurrentLimit() throws Exception {
        InMemoryIslandLimitRepository limits = new InMemoryIslandLimitRepository();
        UUID lowWriter = UUID.fromString("00000000-0000-0000-0000-000000000603");
        UUID highWriter = UUID.fromString("00000000-0000-0000-0000-000000000604");
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int value = 1; value <= 1_000; value++) {
                long requested = value;
                UUID writer = value == 1_000 ? highWriter : lowWriter;
                executor.submit(() -> limits.setAtLeast(ISLAND_ID, "HOPPER", requested, writer));
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        var highest = limits.list(ISLAND_ID).stream()
            .filter(limit -> limit.limitKey().equals("HOPPER"))
            .findFirst()
            .orElseThrow();
        assertEquals(1_000L, highest.value());
        assertEquals(highWriter, highest.updatedBy());

        assertEquals(1_000L, limits.setAtLeast(ISLAND_ID, "HOPPER", 50L, lowWriter).value());
        assertEquals(highWriter, limits.list(ISLAND_ID).stream()
            .filter(limit -> limit.limitKey().equals("HOPPER"))
            .findFirst()
            .orElseThrow()
            .updatedBy());
    }

    private static long limitValue(InMemoryIslandLimitRepository limits, String limitKey) {
        return limits.list(ISLAND_ID).stream()
            .filter(limit -> limit.limitKey().equals(limitKey))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing " + limitKey + " from " + limits.list(ISLAND_ID)))
            .value();
    }

    private static Map<String, Double> generatorWeights(InMemoryIslandGeneratorRepository generators, String generatorKey) {
        return generators.rules(generatorKey).stream().collect(java.util.stream.Collectors.toMap(
            kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot::materialKey,
            kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot::chance
        ));
    }
}
