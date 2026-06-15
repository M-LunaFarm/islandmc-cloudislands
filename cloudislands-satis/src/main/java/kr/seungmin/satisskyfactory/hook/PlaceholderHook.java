package kr.seungmin.satisskyfactory.hook;

import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.FactoryContext;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import kr.seungmin.satisskyfactory.util.NumberFormatter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

public final class PlaceholderHook extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final FactoryIslandService islands;
    private final MachineService machines;
    private final StorageService storage;
    private final ResourceNodeService nodes;
    private final PowerNetworkService power;
    private final IslandBoostService boosts;
    private final ResearchService research;
    private final ContractService contracts;
    private final Predicate<String> featureEnabled;

    public PlaceholderHook(JavaPlugin plugin, FactoryIslandService islands, MachineService machines, StorageService storage,
                           ResourceNodeService nodes,
                           PowerNetworkService power, IslandBoostService boosts, ResearchService research,
                           ContractService contracts, Predicate<String> featureEnabled) {
        this.plugin = plugin;
        this.islands = islands;
        this.machines = machines;
        this.storage = storage;
        this.nodes = nodes;
        this.power = power;
        this.boosts = boosts;
        this.research = research;
        this.contracts = contracts;
        this.featureEnabled = featureEnabled;
    }

    @Override
    public String getIdentifier() {
        return "satisskyfactory";
    }

    @Override
    public String getAuthor() {
        return "LeeSeungmin";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        Player player = offlinePlayer == null ? null : offlinePlayer.getPlayer();
        if (player == null || params == null || params.isBlank()) {
            return "";
        }
        Optional<FactoryContext> context = islands.existingContext(player);
        if (context.isEmpty()) {
            return "";
        }
        FactoryIsland island = context.get().factoryIsland();
        String key = params.trim().toLowerCase(Locale.ROOT);
        if (!canResolve(key)) {
            return "";
        }
        if (key.equals("island_uuid")) {
            return island.islandUuid().toString();
        }
        if (key.equals("tier")) {
            return String.valueOf(island.tier());
        }
        if (key.equals("research")) {
            return String.valueOf(island.researchPoints());
        }
        if (key.equals("reputation")) {
            return String.valueOf(island.reputation());
        }
        if (key.equals("debt")) {
            return String.valueOf(island.maintenanceDebt());
        }
        if (key.equals("maintenance_status")) {
            return island.maintenanceStatus().name();
        }
        if (key.equals("factory_score")) {
            return String.valueOf(machines.factoryScore(island.islandUuid(), island.tier()));
        }
        if (key.equals("maintenance_score")) {
            return String.valueOf(machines.maintenanceScore(island.islandUuid()));
        }
        if (key.equals("machines")) {
            return String.valueOf(machines.byIsland(island.islandUuid()).size());
        }
        if (key.equals("resource_nodes") || key.equals("resource_node_count") || key.equals("nodes")) {
            return String.valueOf(nodes.nodes(island.islandUuid()).size());
        }
        if (key.equals("storage_used")) {
            return storage.findIslandStorage(island.islandUuid())
                    .map(VirtualInventory::used)
                    .map(String::valueOf)
                    .orElse("0");
        }
        if (key.equals("storage_capacity")) {
            return storage.findIslandStorage(island.islandUuid())
                    .map(VirtualInventory::capacity)
                    .map(String::valueOf)
                    .orElse("0");
        }
        if (key.equals("storage_free")) {
            return storage.findIslandStorage(island.islandUuid())
                    .map(VirtualInventory::remainingCapacity)
                    .map(String::valueOf)
                    .orElse("0");
        }
        if (key.equals("power_ratio")) {
            PowerNetworkService.NetworkState powerState = power.state(island.islandUuid());
            return NumberFormatter.ratio(powerState.ratio());
        }
        if (key.equals("power_generation")) {
            PowerNetworkService.NetworkState powerState = power.state(island.islandUuid());
            return NumberFormatter.decimal(powerState.generation(), 1);
        }
        if (key.equals("power_consumption")) {
            PowerNetworkService.NetworkState powerState = power.state(island.islandUuid());
            return NumberFormatter.decimal(powerState.consumption(), 1);
        }
        if (key.equals("battery_stored")) {
            PowerNetworkService.NetworkState powerState = power.state(island.islandUuid());
            return String.valueOf(powerState.batteryStored());
        }
        if (key.equals("battery_capacity")) {
            PowerNetworkService.NetworkState powerState = power.state(island.islandUuid());
            return NumberFormatter.whole(powerState.batteryCapacity());
        }
        if (key.equals("battery_percent")) {
            PowerNetworkService.NetworkState powerState = power.state(island.islandUuid());
            return powerState.batteryCapacity() <= 0
                    ? "0"
                    : NumberFormatter.whole(powerState.batteryStored() * 100.0 / powerState.batteryCapacity());
        }
        if (key.equals("agriculture_boost")) {
            return NumberFormatter.ratio(boosts.boosts(island.islandUuid()).agricultureBoost());
        }
        if (key.equals("machine_limit_bonus")) {
            return String.valueOf(boosts.boosts(island.islandUuid()).factorySlotBonus());
        }
        if (key.equals("contract_slot_bonus")) {
            return String.valueOf(boosts.boosts(island.islandUuid()).contractSlotBonus());
        }
        if (key.equals("contracts_active")) {
            return String.valueOf(contracts.activeContracts(island).size());
        }
        if (key.startsWith("unlocked_")) {
            return String.valueOf(research.unlocked(island).contains(key.substring("unlocked_".length())));
        }
        return null;
    }

    private boolean canResolve(String key) {
        if (key.equals("research") || key.startsWith("unlocked_")) {
            return enabled("research");
        }
        if (key.equals("debt") || key.equals("maintenance_status") || key.equals("maintenance_score")) {
            return enabled("maintenance");
        }
        if (key.equals("contracts_active") || key.equals("contract_slot_bonus")) {
            return enabled("contracts") && enabled("storage");
        }
        if (key.startsWith("storage_")) {
            return enabled("storage");
        }
        if (key.equals("resource_nodes") || key.equals("resource_node_count") || key.equals("nodes")) {
            return enabled("resource-nodes");
        }
        if (key.equals("factory_score") || key.equals("machines")
                || key.startsWith("power_") || key.startsWith("battery_") || key.equals("agriculture_boost")
                || key.equals("machine_limit_bonus")) {
            return enabled("machines");
        }
        return true;
    }

    private boolean enabled(String feature) {
        return featureEnabled == null || featureEnabled.test(feature);
    }
}
