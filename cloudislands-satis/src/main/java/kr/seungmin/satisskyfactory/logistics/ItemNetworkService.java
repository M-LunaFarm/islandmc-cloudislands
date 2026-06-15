package kr.seungmin.satisskyfactory.logistics;

import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.model.ItemNetwork;
import kr.seungmin.satisskyfactory.model.MachineInstance;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class ItemNetworkService {
    private final DatabaseService database;
    private final MachineService machines;
    private final NetworkRebuildService networkRebuild;
    private BooleanSupplier writesEnabled = () -> true;
    private boolean active = true;

    public ItemNetworkService(DatabaseService database, MachineService machines, MachineDefinitionService definitions) {
        this.database = database;
        this.machines = machines;
        this.networkRebuild = new NetworkRebuildService(definitions);
    }

    public void writeGate(BooleanSupplier writesEnabled) {
        this.writesEnabled = writesEnabled == null ? () -> true : writesEnabled;
    }

    public void activate() {
        active = true;
    }

    public List<ItemNetwork> rebuildIsland(UUID islandUuid) {
        if (!active) {
            return List.of();
        }
        if (!writesEnabled()) {
            return load(islandUuid);
        }
        List<MachineInstance> islandMachines = machines.byIsland(islandUuid).stream().toList();
        NetworkRebuildService.RebuildResult result = networkRebuild.rebuild(
                islandUuid,
                islandMachines,
                machines::connectedTo,
                Instant.now().toEpochMilli()
        );
        Map<UUID, UUID> assignments = result.assignments();
        for (MachineInstance machine : islandMachines) {
            UUID networkId = assignments.get(machine.machineId());
            if (!Objects.equals(networkId, machine.itemNetworkId())) {
                machine.itemNetworkId(networkId);
                machines.saveLater(machine);
            }
        }
        database.replaceItemNetworks(islandUuid, result.networks());
        return result.networks();
    }

    public List<ItemNetwork> load(UUID islandUuid) {
        if (!active) {
            return List.of();
        }
        return database.loadItemNetworks(islandUuid);
    }

    public void clear() {
        active = false;
    }

    private boolean writesEnabled() {
        return writesEnabled.getAsBoolean();
    }
}
