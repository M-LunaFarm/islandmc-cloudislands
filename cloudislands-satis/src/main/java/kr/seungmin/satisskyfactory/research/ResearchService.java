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
import java.util.function.BooleanSupplier;

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
    private BooleanSupplier writesEnabled = () -> true;
    private final Map<String, UnlockDefinition> unlocks = new HashMap<>();
    private boolean blockTierUpgradesWhenLimited;
    private boolean active;

    public ResearchService(DatabaseService database, EconomyService economy) {
        this(database, economy, () -> true);
    }

    public ResearchService(DatabaseService database, EconomyService economy, BooleanSupplier maintenanceEnabled) {
        this.database = database;
        this.economy = economy;
        this.maintenanceEnabled = maintenanceEnabled == null ? () -> true : maintenanceEnabled;
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
        island.researchPoints(Math.max(0, island.researchPoints() + amount));
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
        if (unlock.moneyCost() > 0 && (owner == null || !economy.withdraw(owner, unlock.moneyCost()))) {
            return UnlockResult.NOT_ENOUGH_MONEY;
        }
        island.researchPoints(island.researchPoints() - unlock.cost());
        if (unlock.factoryTier() > island.tier()) {
            island.tier(unlock.factoryTier());
        }
        database.saveUnlock(island.islandUuid(), unlockId);
        unlock.grants().forEach(grant -> database.saveUnlock(island.islandUuid(), grant));
        database.saveIsland(island);
        return UnlockResult.UNLOCKED;
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
