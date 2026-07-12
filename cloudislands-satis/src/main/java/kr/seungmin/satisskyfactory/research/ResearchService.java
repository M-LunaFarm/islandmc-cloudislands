package kr.seungmin.satisskyfactory.research;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public final class ResearchService {
    public enum UnlockResult {
        UNLOCKED,
        UNKNOWN,
        ALREADY_UNLOCKED,
        MISSING_REQUIREMENT,
        NOT_ENOUGH_POINTS,
        NOT_ENOUGH_MONEY,
        NOT_ENOUGH_REPUTATION,
        MAINTENANCE_LIMITED
    }

    private final DatabaseService database;
    private final EconomyService economy;
    private final BooleanSupplier maintenanceEnabled;
    private final Predicate<FactoryIsland> islandSaver;
    private BooleanSupplier writesEnabled = () -> true;
    private final Map<String, UnlockDefinition> unlocks = new HashMap<>();
    private boolean blockTierUpgradesWhenLimited;
    private boolean active;

    public ResearchService(DatabaseService database, EconomyService economy) {
        this(database, economy, () -> true);
    }

    public ResearchService(DatabaseService database, EconomyService economy, BooleanSupplier maintenanceEnabled) {
        this(database, economy, maintenanceEnabled, island -> {
            database.saveIsland(island);
            return true;
        });
    }

    public ResearchService(DatabaseService database, EconomyService economy, BooleanSupplier maintenanceEnabled,
                           Predicate<FactoryIsland> islandSaver) {
        this.database = database;
        this.economy = economy;
        this.maintenanceEnabled = maintenanceEnabled == null ? () -> true : maintenanceEnabled;
        this.islandSaver = islandSaver == null ? island -> {
            database.saveIsland(island);
            return true;
        } : islandSaver;
    }

    public void writeGate(BooleanSupplier writesEnabled) {
        this.writesEnabled = writesEnabled == null ? () -> true : writesEnabled;
    }

    public void load(FileConfiguration config) {
        load(config, null);
    }

    public void load(FileConfiguration config, FileConfiguration maintenanceConfig) {
        unlocks.clear();
        active = true;
        blockTierUpgradesWhenLimited = maintenanceConfig != null
                && maintenanceConfig.getBoolean("maintenance.limited.block-upgrades", true);
        ConfigurationSection section = config.getConfigurationSection("research.unlocks");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            unlocks.put(id, new UnlockDefinition(
                    id,
                    section.getString(id + ".display-name", section.getString(id + ".display", id)),
                    section.getLong(id + ".cost-research-points", section.getLong(id + ".cost", 0)),
                    section.getLong(id + ".cost-money", 0),
                    section.getLong(id + ".required-reputation", 0),
                    stringList(section, id + ".required-unlocks", id + ".requires"),
                    section.getStringList(id + ".unlocks"),
                    section.getInt(id + ".factory-tier", 0)
            ));
        }
    }

    public void clear() {
        unlocks.clear();
        active = false;
        blockTierUpgradesWhenLimited = false;
    }

    public boolean addResearch(FactoryIsland island, long amount) {
        if (!active) {
            return false;
        }
        if (!writesEnabled()) {
            return false;
        }
        if (amount <= 0) {
            return false;
        }
        island.addResearchPoints(amount);
        return true;
    }

    public UnlockResult unlock(FactoryIsland island, String unlockId) {
        return unlock(island, null, unlockId);
    }

    public UnlockResult unlock(FactoryIsland island, OfflinePlayer owner, String unlockId) {
        if (!active) {
            return UnlockResult.UNKNOWN;
        }
        if (!writesEnabled()) {
            return UnlockResult.UNKNOWN;
        }
        UnlockDefinition unlock = unlocks.get(unlockId);
        if (unlock == null) {
            return UnlockResult.UNKNOWN;
        }
        Set<String> current = database.loadUnlocks(island.islandUuid());
        if (current.contains(unlockId)) {
            return UnlockResult.ALREADY_UNLOCKED;
        }
        if (!current.containsAll(unlock.requires())) {
            return UnlockResult.MISSING_REQUIREMENT;
        }
        if (island.researchPoints() < unlock.cost()) {
            return UnlockResult.NOT_ENOUGH_POINTS;
        }
        if (island.reputation() < unlock.requiredReputation()) {
            return UnlockResult.NOT_ENOUGH_REPUTATION;
        }
        if (maintenanceEnabled()
                && blockTierUpgradesWhenLimited
                && island.maintenanceStatus() == MaintenanceStatus.LIMITED
                && unlock.factoryTier() > island.tier()) {
            return UnlockResult.MAINTENANCE_LIMITED;
        }
        String idempotencyKey = researchIdempotencyKey(island, owner, unlockId, unlock);
        DatabaseService.EconomyLedgerClaim moneyClaim = DatabaseService.EconomyLedgerClaim.COMPLETED;
        if (unlock.moneyCost() > 0) {
            if (owner == null) {
                return UnlockResult.NOT_ENOUGH_MONEY;
            }
            moneyClaim = database.beginEconomyLedger(
                    island.islandUuid(), playerUuid(owner), "RESEARCH_UNLOCK", -unlock.moneyCost(), unlockId, idempotencyKey);
            if (moneyClaim == DatabaseService.EconomyLedgerClaim.STARTED) {
                if (!economy.withdraw(owner, unlock.moneyCost())) {
                    database.failEconomyLedger(idempotencyKey);
                    return UnlockResult.NOT_ENOUGH_MONEY;
                }
            } else if (moneyClaim != DatabaseService.EconomyLedgerClaim.COMPLETED) {
                return UnlockResult.UNKNOWN;
            }
        }
        long previousResearch = island.researchPoints();
        int previousTier = island.tier();
        island.researchPoints(island.researchPoints() - unlock.cost());
        if (unlock.factoryTier() > island.tier()) {
            island.tier(unlock.factoryTier());
        }
        if (!islandSaver.test(island)) {
            island.researchPoints(previousResearch);
            island.tier(previousTier);
            if (unlock.moneyCost() > 0 && moneyClaim == DatabaseService.EconomyLedgerClaim.STARTED) {
                database.compensateEconomyLedger(idempotencyKey);
            }
            return UnlockResult.UNKNOWN;
        }
        try {
            database.saveUnlock(island.islandUuid(), unlockId);
            unlock.grants().forEach(grant -> database.saveUnlock(island.islandUuid(), grant));
            if (unlock.moneyCost() > 0 && moneyClaim == DatabaseService.EconomyLedgerClaim.STARTED) {
                database.completeEconomyLedger(idempotencyKey);
            }
        } catch (RuntimeException exception) {
            if (unlock.moneyCost() > 0 && moneyClaim == DatabaseService.EconomyLedgerClaim.STARTED) {
                database.compensateEconomyLedger(idempotencyKey);
            }
            throw exception;
        }
        return UnlockResult.UNLOCKED;
    }

    private String researchIdempotencyKey(FactoryIsland island, OfflinePlayer owner, String unlockId, UnlockDefinition unlock) {
        return String.join(":",
                "RESEARCH_UNLOCK",
                island.islandUuid().toString(),
                playerUuid(owner) == null ? "system" : playerUuid(owner).toString(),
                unlockId,
                Integer.toString(island.tier()),
                Long.toString(unlock.moneyCost()));
    }

    private UUID playerUuid(OfflinePlayer owner) {
        return owner == null ? null : owner.getUniqueId();
    }

    public Set<String> unlocked(FactoryIsland island) {
        if (!active) {
            return Set.of();
        }
        return database.loadUnlocks(island.islandUuid());
    }

    public Map<String, UnlockDefinition> all() {
        if (!active) {
            return Map.of();
        }
        return Map.copyOf(unlocks);
    }

    private boolean writesEnabled() {
        try {
            return writesEnabled.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean maintenanceEnabled() {
        try {
            return maintenanceEnabled.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private List<String> stringList(ConfigurationSection section, String firstPath, String secondPath) {
        List<String> values = new ArrayList<>(section.getStringList(firstPath));
        if (!values.isEmpty()) {
            return values;
        }
        values.addAll(section.getStringList(secondPath));
        return values;
    }
}
