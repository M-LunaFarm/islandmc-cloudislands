package kr.lunaf.cloudislands.coreservice.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
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

        new UpgradeEffectApplier(new InMemoryIslandLimitRepository(), new InMemoryIslandRepository(), metadata, logs, events)
            .apply(ISLAND_ID, OWNER_ID, new UpgradeRule("fly", UpgradeType.FLY_ACCESS, 1, BigDecimal.ZERO, BigDecimal.ONE), UpgradeType.FLY_ACCESS, 1);

        assertEquals("true", metadata.flags(ISLAND_ID).values().get(IslandFlag.FLY));
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_FLAG_CHANGED.name()));
        assertTrue(logs.list(ISLAND_ID, 10).stream().anyMatch(record -> record.action().equals("ISLAND_UPGRADE_EFFECT") && record.payload().get("effect").equals("FLY_ACCESS")));
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

    private static long limitValue(InMemoryIslandLimitRepository limits, String limitKey) {
        return limits.list(ISLAND_ID).stream()
            .filter(limit -> limit.limitKey().equals(limitKey))
            .findFirst()
            .orElseThrow()
            .value();
    }
}
