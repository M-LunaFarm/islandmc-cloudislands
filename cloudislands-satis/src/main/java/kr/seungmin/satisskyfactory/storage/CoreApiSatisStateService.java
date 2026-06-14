package kr.seungmin.satisskyfactory.storage;

import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.task.DirtySaveService;
import org.bukkit.block.BlockFace;

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

    public boolean hydrateIsland(UUID islandId, DatabaseService database) {
        if (cloudIslandsApi == null || islandId == null || database == null) {
            return false;
        }
        Map<String, String> state;
        try {
            state = cloudIslandsApi.addons().islandState(addonId, islandId).join();
        } catch (RuntimeException exception) {
            logger.warning("Failed to read Satis core-api table state for island " + islandId + ": " + exception.getMessage());
            return false;
        }
        if (state == null || state.isEmpty()) {
            return false;
        }
        boolean restored = false;
        for (Map.Entry<String, String> entry : state.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }
            try {
                if (key.startsWith("table/factory_islands/")) {
                    database.saveIsland(island(value));
                    restored = true;
                } else if (key.startsWith("table/virtual_inventories/")) {
                    database.saveInventory(inventory(value));
                    restored = true;
                } else if (key.startsWith("table/machines/")) {
                    database.saveMachine(machine(value));
                    restored = true;
                } else if (key.startsWith("table/resource_nodes/")) {
                    database.saveNode(node(value));
                    restored = true;
                }
            } catch (RuntimeException exception) {
                logger.warning("Failed to hydrate Satis core-api row " + key + " for island " + islandId + ": " + exception.getMessage());
            }
        }
        return restored;
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

    private FactoryIsland island(String json) {
        FactoryIsland island = new FactoryIsland(uuid(text(json, "islandUuid", "")), uuid(text(json, "ownerUuid", "")));
        island.tier(integer(json, "tier", 1));
        island.researchPoints(longValue(json, "researchPoints", 0L));
        island.reputation(longValue(json, "reputation", 0L));
        island.maintenanceDebt(longValue(json, "maintenanceDebt", 0L));
        island.maintenanceStatus(enumValue(MaintenanceStatus.class, text(json, "maintenanceStatus", "NORMAL"), MaintenanceStatus.NORMAL));
        island.factoryScore(longValue(json, "factoryScore", 0L));
        island.lastMaintenanceAt(longValue(json, "lastMaintenanceAt", 0L));
        island.lastTickAt(longValue(json, "lastTickAt", 0L));
        island.emergencyContractsUsedToday(integer(json, "emergencyContractsUsedToday", 0));
        island.activeWorld(text(json, "activeWorld", ""));
        island.activeCenterX(integer(json, "activeCenterX", 0));
        island.activeCenterY(integer(json, "activeCenterY", 0));
        island.activeCenterZ(integer(json, "activeCenterZ", 0));
        island.createdAt(longValue(json, "createdAt", System.currentTimeMillis()));
        island.updatedAt(longValue(json, "updatedAt", 0L));
        return island;
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

    private MachineInstance machine(String json) {
        MachineInstance machine = new MachineInstance(
                uuid(text(json, "machineId", "")),
                uuid(text(json, "islandUuid", "")),
                uuid(text(json, "ownerUuid", "")),
                text(json, "typeId", ""),
                integer(json, "tier", 1),
                new BlockKey(text(json, "world", ""), integer(json, "x", 0), integer(json, "y", 0), integer(json, "z", 0))
        );
        machine.direction(enumValue(BlockFace.class, text(json, "direction", "NORTH"), BlockFace.NORTH));
        machine.status(MachineStatus.fromStoredValue(text(json, "status", "SLEEPING")));
        machine.inputInventoryId(uuidOrNull(text(json, "inputInventoryId", "")));
        machine.outputInventoryId(uuidOrNull(text(json, "outputInventoryId", "")));
        machine.powerNetworkId(uuidOrNull(text(json, "powerNetworkId", "")));
        machine.itemNetworkId(uuidOrNull(text(json, "itemNetworkId", "")));
        machine.linkedResourceNodeId(uuidOrNull(text(json, "linkedResourceNodeId", "")));
        machine.selectedRecipeId(blankToNull(text(json, "selectedRecipeId", "")));
        machine.configJson(text(json, "configJson", "{}"));
        machine.lastProcessAt(longValue(json, "lastProcessAt", 0L));
        machine.wear(decimal(json, "wear", 0.0D));
        machine.createdAt(longValue(json, "createdAt", System.currentTimeMillis()));
        machine.updatedAt(longValue(json, "updatedAt", 0L));
        return machine;
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

    private VirtualInventory inventory(String json) {
        VirtualInventory inventory = new VirtualInventory(
                uuid(text(json, "inventoryId", "")),
                uuid(text(json, "islandUuid", "")),
                text(json, "holderType", ""),
                text(json, "holderId", ""),
                longValue(json, "capacity", 0L)
        );
        itemMap(objectBody(json, "items")).forEach(inventory::set);
        return inventory;
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

    private ResourceNode node(String json) {
        return new ResourceNode(
                uuid(text(json, "nodeId", "")),
                uuid(text(json, "islandUuid", "")),
                text(json, "nodeType", ""),
                text(json, "resourceId", ""),
                decimal(json, "purity", 1.0D),
                longValue(json, "remaining", 0L),
                longValue(json, "maxRemaining", 0L),
                longValue(json, "regenPerHour", 0L),
                integer(json, "requiredMachineTier", 1),
                new BlockKey(text(json, "world", ""), integer(json, "x", 0), integer(json, "y", 0), integer(json, "z", 0)),
                longValue(json, "createdAt", System.currentTimeMillis()),
                longValue(json, "updatedAt", 0L)
        );
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

    private String text(String json, String field, String fallback) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return fallback;
        }
        int index = start + needle.length();
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        while (index < json.length()) {
            char current = json.charAt(index++);
            if (escaped) {
                value.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return value.toString();
            }
            value.append(current);
        }
        return fallback;
    }

    private long longValue(String json, String field, long fallback) {
        String value = scalar(json, field);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int integer(String json, String field, int fallback) {
        return (int) longValue(json, field, fallback);
    }

    private double decimal(String json, String field, double fallback) {
        String value = scalar(json, field);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String scalar(String json, String field) {
        String needle = "\"" + field + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        int index = start + needle.length();
        int end = index;
        while (end < json.length() && "-0123456789.Ee".indexOf(json.charAt(end)) >= 0) {
            end++;
        }
        return end == index ? null : json.substring(index, end);
    }

    private String objectBody(String json, String field) {
        String needle = "\"" + field + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int objectStart = json.indexOf('{', start + needle.length());
        if (objectStart < 0) {
            return "";
        }
        int depth = 0;
        for (int index = objectStart; index < json.length(); index++) {
            char current = json.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(objectStart + 1, index);
                }
            }
        }
        return "";
    }

    private Map<String, Long> itemMap(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        Map<String, Long> items = new LinkedHashMap<>();
        for (String pair : splitPairs(body)) {
            int colon = pair.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = unquote(pair.substring(0, colon));
            try {
                items.put(key, Long.parseLong(pair.substring(colon + 1).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return items;
    }

    private java.util.List<String> splitPairs(String body) {
        java.util.List<String> pairs = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < body.length(); index++) {
            char value = body.charAt(index);
            if (escaped) {
                current.append(value);
                escaped = false;
                continue;
            }
            if (value == '\\') {
                current.append(value);
                escaped = true;
                continue;
            }
            if (value == '"') {
                quoted = !quoted;
            }
            if (value == ',' && !quoted) {
                pairs.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(value);
        }
        if (!current.isEmpty()) {
            pairs.add(current.toString());
        }
        return pairs;
    }

    private String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
