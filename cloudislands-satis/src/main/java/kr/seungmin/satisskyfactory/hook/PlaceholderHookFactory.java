package kr.seungmin.satisskyfactory.hook;

import java.util.function.Predicate;
import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.runtime.SatisPlaceholderRuntime;
import kr.seungmin.satisskyfactory.storage.StorageService;
import org.bukkit.plugin.java.JavaPlugin;

/** Loaded only after PlaceholderAPI presence has been confirmed. */
public final class PlaceholderHookFactory {
    private PlaceholderHookFactory() {
    }

    public static SatisPlaceholderRuntime.PlaceholderExpansionHandle create(
            JavaPlugin plugin,
            FactoryIslandService islands,
            MachineService machines,
            StorageService storage,
            ResourceNodeService nodes,
            PowerNetworkService power,
            IslandBoostService boosts,
            ResearchService research,
            ContractService contracts,
            Predicate<String> featureEnabled) {
        return new PlaceholderHook(
                plugin, islands, machines, storage, nodes, power, boosts, research, contracts, featureEnabled);
    }
}
