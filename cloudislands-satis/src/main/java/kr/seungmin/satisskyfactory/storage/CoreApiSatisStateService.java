package kr.seungmin.satisskyfactory.storage;

import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.task.DirtySaveService;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class CoreApiSatisStateService {
    private final Logger logger;
    private final CloudIslandsApi cloudIslandsApi;
    private final String addonId;

    public CoreApiSatisStateService(Logger logger, CloudIslandsApi cloudIslandsApi, String addonId) {
        this.logger = logger;
        this.cloudIslandsApi = cloudIslandsApi;
        this.addonId = addonId;
    }

    public void publishDirtyBatch(DirtySaveService.DirtySaveBatch batch) {
        if (cloudIslandsApi == null || batch == null) {
            return;
        }
        Map<UUID, Map<String, String>> stateByIsland = new LinkedHashMap<>();
        batch.islands().values().forEach(island -> state(stateByIsland, island.islandUuid())
                .put("table/factory_islands/" + island.islandUuid(), islandJson(island)));
        batch.machines().values().forEach(machine -> state(stateByIsland, machine.islandUuid())
                .put("table/machines/" + machine.machineId(), machineJson(machine)));
        batch.inventories().values().forEach(inventory -> state(stateByIsland, inventory.islandUuid())
                .put("table/virtual_inventories/" + inventory.inventoryId(), inventoryJson(inventory)));
        batch.nodes().values().forEach(node -> state(stateByIsland, node.islandUuid())
                .put("table/resource_nodes/" + node.nodeId(), nodeJson(node)));
        stateByIsland.forEach((islandId, state) -> {
            state.put("core-api-sync-schema", "1");
            state.put("core-api-sync-updated-at", Instant.now().toString());
            state.put("core-api-sync-keys", Integer.toString(state.size()));
            cloudIslandsApi.addons().putIslandState(addonId, islandId, state).exceptionally(error -> {
                logger.warning("Failed to publish Satis core-api table state for island " + islandId + ": " + error.getMessage());
                return Map.of();
            });
        });
    }

    private Map<String, String> state(Map<UUID, Map<String, String>> stateByIsland, UUID islandId) {
        return stateByIsland.computeIfAbsent(islandId, _ignored -> new LinkedHashMap<>());
    }

    private String islandJson(FactoryIsland island) {
        return "{"
                + field("islandUuid", island.islandUuid().toString()) + ","
                + field("ownerUuid", island.ownerUuid().toString()) + ","
                + number("tier", island.tier()) + ","
                + number("researchPoints", island.researchPoints()) + ","
                + number("reputation", island.reputation()) + ","
                + number("maintenanceDebt", island.maintenanceDebt()) + ","
                + field("maintenanceStatus", island.maintenanceStatus().name()) + ","
                + number("factoryScore", island.factoryScore()) + ","
                + number("lastMaintenanceAt", island.lastMaintenanceAt()) + ","
                + number("lastTickAt", island.lastTickAt()) + ","
                + number("emergencyContractsUsedToday", island.emergencyContractsUsedToday()) + ","
                + field("activeWorld", island.activeWorld()) + ","
                + number("activeCenterX", island.activeCenterX()) + ","
                + number("activeCenterY", island.activeCenterY()) + ","
                + number("activeCenterZ", island.activeCenterZ()) + ","
                + number("createdAt", island.createdAt()) + ","
                + number("updatedAt", island.updatedAt())
                + "}";
    }

    private String machineJson(MachineInstance machine) {
        return "{"
                + field("machineId", machine.machineId().toString()) + ","
                + field("islandUuid", machine.islandUuid().toString()) + ","
                + field("ownerUuid", machine.ownerUuid().toString()) + ","
                + field("typeId", machine.typeId()) + ","
                + number("tier", machine.tier()) + ","
                + field("world", machine.world()) + ","
                + number("x", machine.x()) + ","
                + number("y", machine.y()) + ","
                + number("z", machine.z()) + ","
                + field("direction", machine.direction().name()) + ","
                + field("status", machine.status().name()) + ","
                + field("inputInventoryId", string(machine.inputInventoryId())) + ","
                + field("outputInventoryId", string(machine.outputInventoryId())) + ","
                + field("powerNetworkId", string(machine.powerNetworkId())) + ","
                + field("itemNetworkId", string(machine.itemNetworkId())) + ","
                + field("linkedResourceNodeId", string(machine.linkedResourceNodeId())) + ","
                + field("selectedRecipeId", machine.selectedRecipeId()) + ","
                + field("configJson", machine.configJson()) + ","
                + number("lastProcessAt", machine.lastProcessAt()) + ","
                + number("wear", machine.wear()) + ","
                + number("createdAt", machine.createdAt()) + ","
                + number("updatedAt", machine.updatedAt())
                + "}";
    }

    private String inventoryJson(VirtualInventory inventory) {
        StringBuilder items = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> entry : inventory.items().entrySet()) {
            if (!first) {
                items.append(',');
            }
            first = false;
            items.append('"').append(escape(entry.getKey())).append("\":").append(entry.getValue());
        }
        items.append('}');
        return "{"
                + field("inventoryId", inventory.inventoryId().toString()) + ","
                + field("islandUuid", inventory.islandUuid().toString()) + ","
                + field("holderType", inventory.holderType()) + ","
                + field("holderId", inventory.holderId()) + ","
                + number("capacity", inventory.capacity()) + ","
                + "\"items\":" + items
                + "}";
    }

    private String nodeJson(ResourceNode node) {
        return "{"
                + field("nodeId", node.nodeId().toString()) + ","
                + field("islandUuid", node.islandUuid().toString()) + ","
                + field("nodeType", node.nodeType()) + ","
                + field("resourceId", node.resourceId()) + ","
                + number("purity", node.purity()) + ","
                + number("remaining", node.remaining()) + ","
                + number("maxRemaining", node.maxRemaining()) + ","
                + number("regenPerHour", node.regenPerHour()) + ","
                + number("requiredMachineTier", node.requiredMachineTier()) + ","
                + field("world", node.world()) + ","
                + number("x", node.x()) + ","
                + number("y", node.y()) + ","
                + number("z", node.z()) + ","
                + number("createdAt", node.createdAt()) + ","
                + number("updatedAt", node.updatedAt())
                + "}";
    }

    private String field(String key, String value) {
        return "\"" + key + "\":\"" + escape(value == null ? "" : value) + "\"";
    }

    private String number(String key, long value) {
        return "\"" + key + "\":" + value;
    }

    private String number(String key, double value) {
        return "\"" + key + "\":" + value;
    }

    private String string(UUID value) {
        return value == null ? "" : value.toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
