package kr.lunaf.cloudislands.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SuperiorSkyblock2MigrationScanner {
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public ScanResult scan(Path superiorSkyblockDataPath) {
        List<MigrationManifest> manifests = new ArrayList<>();
        List<MigrationIssue> issues = new ArrayList<>();
        Path islandsDir = findIslandsDirectory(superiorSkyblockDataPath);
        if (islandsDir == null || !Files.isDirectory(islandsDir)) {
            issues.add(new MigrationIssue("ISLAND_DIRECTORY_NOT_FOUND", "could not find SuperiorSkyblock2 island data directory under " + superiorSkyblockDataPath, true));
            return new ScanResult(List.of(), issues);
        }
        try (Stream<Path> files = Files.walk(islandsDir, 3)) {
            files.filter(Files::isRegularFile)
                .filter(this::looksLikeIslandFile)
                .forEach(file -> scanFile(file, manifests, issues));
        } catch (IOException exception) {
            issues.add(new MigrationIssue("SCAN_FAILED", exception.getMessage(), true));
        }
        return new ScanResult(List.copyOf(manifests), List.copyOf(issues));
    }

    private Path findIslandsDirectory(Path root) {
        List<Path> candidates = List.of(
            root.resolve("islands"),
            root.resolve("database").resolve("islands"),
            root.resolve("data").resolve("islands"),
            root.resolve("SuperiorSkyblock2").resolve("islands")
        );
        return candidates.stream().filter(Files::isDirectory).findFirst().orElse(root);
    }

    private boolean looksLikeIslandFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".json") || name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private void scanFile(Path file, List<MigrationManifest> manifests, List<MigrationIssue> issues) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            UUID islandId = parseUuid(content, "islandId", parseUuid(content, "uuid", uuidFromFilename(file)));
            UUID ownerUuid = parseUuid(content, "owner", parseUuid(content, "ownerUuid", new UUID(0L, 0L)));
            if (ownerUuid.getMostSignificantBits() == 0L && ownerUuid.getLeastSignificantBits() == 0L) {
                issues.add(new MigrationIssue("OWNER_NOT_FOUND", "missing owner uuid in " + file, true));
                return;
            }
            int size = parseInt(content, "size", 300);
            long level = parseLong(content, "level", 0L);
            String worth = parseString(content, "worth", "0.00");
            List<UUID> members = parseUuidList(content, "members", "islandMembers", "coopMembers", "coops");
            List<UUID> bannedVisitors = parseUuidList(content, "bans", "bannedPlayers", "bannedVisitors", "visitorBans");
            List<MigrationHome> homes = parseHomes(content);
            List<MigrationWarp> warps = parseWarps(content);
            List<MigrationFlag> flags = parseFlags(content);
            List<MigrationPermission> permissions = parsePermissions(content);
            boolean publicAccess = parseBoolean(content, "public", parseBoolean(content, "isPublic", parseBoolean(content, "publicAccess", false)));
            boolean locked = parseBoolean(content, "locked", parseBoolean(content, "isLocked", false));
            LinkedHashSet<UUID> allMembers = new LinkedHashSet<>();
            allMembers.add(ownerUuid);
            allMembers.addAll(members);
            manifests.add(new MigrationManifest(islandId, ownerUuid, List.copyOf(allMembers), bannedVisitors, homes, warps, flags, permissions, publicAccess, locked, size, level, worth));
        } catch (RuntimeException | IOException exception) {
            issues.add(new MigrationIssue("ISLAND_FILE_PARSE_FAILED", file + ": " + exception.getMessage(), true));
        }
    }

    private UUID uuidFromFilename(Path file) {
        String name = file.getFileName().toString();
        int dot = name.indexOf('.');
        String raw = dot < 0 ? name : name.substring(0, dot);
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
        }
    }

    private UUID parseUuid(String content, String key, UUID fallback) {
        try {
            return UUID.fromString(parseString(content, key, fallback.toString()));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private String parseString(String content, String key, String fallback) {
        String jsonNeedle = "\"" + key + "\"";
        int jsonStart = content.indexOf(jsonNeedle);
        if (jsonStart >= 0) {
            int colon = content.indexOf(':', jsonStart + jsonNeedle.length());
            if (colon >= 0) {
                int valueStart = colon + 1;
                while (valueStart < content.length() && Character.isWhitespace(content.charAt(valueStart))) {
                    valueStart++;
                }
                if (valueStart < content.length() && content.charAt(valueStart) == '"') {
                    int end = content.indexOf('"', valueStart + 1);
                    if (end > valueStart) {
                        return content.substring(valueStart + 1, end);
                    }
                } else if (valueStart < content.length()) {
                    int valueEnd = valueStart;
                    while (valueEnd < content.length()) {
                        char current = content.charAt(valueEnd);
                        if (current == ',' || current == '}' || current == ']' || current == '\n' || current == '\r') {
                            break;
                        }
                        valueEnd++;
                    }
                    String value = content.substring(valueStart, valueEnd).trim();
                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        String yamlNeedle = key + ":";
        int yamlStart = content.indexOf(yamlNeedle);
        if (yamlStart >= 0) {
            int valueStart = yamlStart + yamlNeedle.length();
            int lineEnd = content.indexOf('\n', valueStart);
            String value = content.substring(valueStart, lineEnd < 0 ? content.length() : lineEnd).trim();
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private int parseInt(String content, String key, int fallback) {
        try {
            return Integer.parseInt(parseString(content, key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parseLong(String content, String key, long fallback) {
        try {
            return Long.parseLong(parseString(content, key, Long.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean parseBoolean(String content, String key, boolean fallback) {
        String value = parseString(content, key, Boolean.toString(fallback));
        return switch (value.toLowerCase()) {
            case "true", "yes", "on", "1", "public", "open" -> true;
            case "false", "no", "off", "0", "private", "closed" -> false;
            default -> fallback;
        };
    }

    private double parseDouble(String content, String key, double fallback) {
        try {
            return Double.parseDouble(parseString(content, key, Double.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private float parseFloat(String content, String key, float fallback) {
        try {
            return Float.parseFloat(parseString(content, key, Float.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private List<MigrationHome> parseHomes(String content) {
        if (!containsAnyKey(content, "homeX", "homeY", "homeZ", "homeWorld", "spawnX", "spawnY", "spawnZ", "spawnWorld")) {
            return List.of();
        }
        String name = parseString(content, "homeName", parseString(content, "spawnName", "default"));
        String world = parseString(content, "homeWorld", parseString(content, "spawnWorld", parseString(content, "world", "")));
        double x = parseDouble(content, "homeX", parseDouble(content, "spawnX", parseDouble(content, "x", 0.5D)));
        double y = parseDouble(content, "homeY", parseDouble(content, "spawnY", parseDouble(content, "y", 100.0D)));
        double z = parseDouble(content, "homeZ", parseDouble(content, "spawnZ", parseDouble(content, "z", 0.5D)));
        float yaw = parseFloat(content, "homeYaw", parseFloat(content, "spawnYaw", parseFloat(content, "yaw", 180.0F)));
        float pitch = parseFloat(content, "homePitch", parseFloat(content, "spawnPitch", parseFloat(content, "pitch", 0.0F)));
        return List.of(new MigrationHome(name.isBlank() ? "default" : name, world, x, y, z, yaw, pitch));
    }

    private List<MigrationWarp> parseWarps(String content) {
        if (!containsAnyKey(content, "warpX", "warpY", "warpZ", "warpWorld")) {
            return List.of();
        }
        String name = parseString(content, "warpName", "default");
        String world = parseString(content, "warpWorld", parseString(content, "world", ""));
        double x = parseDouble(content, "warpX", 0.5D);
        double y = parseDouble(content, "warpY", 100.0D);
        double z = parseDouble(content, "warpZ", 0.5D);
        float yaw = parseFloat(content, "warpYaw", parseFloat(content, "yaw", 180.0F));
        float pitch = parseFloat(content, "warpPitch", parseFloat(content, "pitch", 0.0F));
        boolean publicAccess = parseBoolean(content, "warpPublic", parseBoolean(content, "publicWarp", true));
        return List.of(new MigrationWarp(name.isBlank() ? "default" : name, world, x, y, z, yaw, pitch, publicAccess));
    }

    private List<MigrationFlag> parseFlags(String content) {
        List<MigrationFlag> flags = new ArrayList<>();
        addFlag(content, flags, "PVP", "pvp");
        addFlag(content, flags, "MOB_SPAWN", "mobSpawn");
        addFlag(content, flags, "ANIMAL_SPAWN", "animalSpawn");
        addFlag(content, flags, "MONSTER_SPAWN", "monsterSpawn");
        addFlag(content, flags, "FIRE_SPREAD", "fireSpread");
        addFlag(content, flags, "EXPLOSION", "explosion");
        addFlag(content, flags, "CREEPER_DAMAGE", "creeperDamage");
        addFlag(content, flags, "TNT_DAMAGE", "tntDamage");
        addFlag(content, flags, "WITHER_DAMAGE", "witherDamage");
        addFlag(content, flags, "ENDERMAN_GRIEF", "endermanGrief");
        addFlag(content, flags, "WATER_FLOW", "waterFlow");
        addFlag(content, flags, "LAVA_FLOW", "lavaFlow");
        addFlag(content, flags, "ICE_MELT", "iceMelt");
        addFlag(content, flags, "LEAF_DECAY", "leafDecay");
        addFlag(content, flags, "VISITOR_INTERACT", "visitorInteract");
        addFlag(content, flags, "VISITOR_CONTAINER", "visitorContainer");
        addFlag(content, flags, "VISITOR_PICKUP", "visitorPickup");
        addFlag(content, flags, "VISITOR_DROP", "visitorDrop");
        addFlag(content, flags, "VISITOR_PVP", "visitorPvp");
        addFlag(content, flags, "FLY", "fly");
        addFlag(content, flags, "KEEP_INVENTORY", "keepInventory");
        addFlag(content, flags, "PUBLIC_WARPS", "publicWarps");
        return List.copyOf(flags);
    }

    private void addFlag(String content, List<MigrationFlag> flags, String flagName, String key) {
        String enumKey = flagName.toLowerCase();
        if (!containsAnyKey(content, key, enumKey, flagName)) {
            return;
        }
        String value = parseString(content, key, parseString(content, enumKey, parseString(content, flagName, "")));
        if (!value.isBlank()) {
            flags.add(new MigrationFlag(flagName, value));
        }
    }

    private List<MigrationPermission> parsePermissions(String content) {
        String[] roles = {"OWNER", "CO_OWNER", "MODERATOR", "MEMBER", "TRUSTED", "VISITOR"};
        String[] permissions = {
            "BUILD", "BREAK", "INTERACT", "OPEN_CONTAINER", "USE_DOOR", "USE_BUTTON", "USE_PRESSURE_PLATE", "USE_REDSTONE",
            "PLACE_LIQUID", "BREAK_LIQUID", "ATTACK_PLAYER", "ATTACK_MOB", "PICKUP_ITEM", "DROP_ITEM", "USE_SPAWNER",
            "USE_ANVIL", "USE_ENCHANT_TABLE", "USE_BREWING_STAND", "MANAGE_MEMBERS", "MANAGE_ROLES", "MANAGE_FLAGS",
            "MANAGE_WARPS", "MANAGE_UPGRADES", "START_LEVEL_CALC", "BAN_VISITOR", "KICK_VISITOR", "SET_HOME",
            "SET_BIOME", "WITHDRAW_BANK", "DEPOSIT_BANK"
        };
        List<MigrationPermission> result = new ArrayList<>();
        for (String role : roles) {
            for (String permission : permissions) {
                String camelKey = toCamel(role) + toPascal(permission);
                String dottedKey = role.toLowerCase() + "." + permission.toLowerCase();
                String snakeKey = role.toLowerCase() + "_" + permission.toLowerCase();
                String upperKey = role + "_" + permission;
                if (!containsAnyKey(content, camelKey, dottedKey, snakeKey, upperKey)) {
                    continue;
                }
                boolean allowed = parseBoolean(content, camelKey, parseBoolean(content, dottedKey, parseBoolean(content, snakeKey, parseBoolean(content, upperKey, false))));
                result.add(new MigrationPermission(role, permission, allowed));
            }
        }
        return List.copyOf(result);
    }

    private String toCamel(String enumName) {
        String pascal = toPascal(enumName);
        return pascal.isEmpty() ? pascal : Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    private String toPascal(String enumName) {
        StringBuilder builder = new StringBuilder();
        for (String part : enumName.toLowerCase().split("_")) {
            if (!part.isEmpty()) {
                builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private boolean containsAnyKey(String content, String... keys) {
        for (String key : keys) {
            if (content.contains("\"" + key + "\"") || content.contains(key + ":")) {
                return true;
            }
        }
        return false;
    }

    private List<UUID> parseUuidList(String content, String... keys) {
        LinkedHashSet<UUID> uuids = new LinkedHashSet<>();
        for (String key : keys) {
            uuids.addAll(parseJsonUuidArray(content, key));
            uuids.addAll(parseYamlUuidBlock(content, key));
        }
        return List.copyOf(uuids);
    }

    private Set<UUID> parseJsonUuidArray(String content, String key) {
        LinkedHashSet<UUID> uuids = new LinkedHashSet<>();
        String needle = "\"" + key + "\"";
        int keyStart = content.indexOf(needle);
        if (keyStart < 0) {
            return uuids;
        }
        int arrayStart = content.indexOf('[', keyStart + needle.length());
        int arrayEnd = arrayStart < 0 ? -1 : content.indexOf(']', arrayStart + 1);
        if (arrayEnd <= arrayStart) {
            return uuids;
        }
        collectUuids(content.substring(arrayStart + 1, arrayEnd), uuids);
        return uuids;
    }

    private Set<UUID> parseYamlUuidBlock(String content, String key) {
        LinkedHashSet<UUID> uuids = new LinkedHashSet<>();
        String[] lines = content.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.trim();
            if (!trimmed.startsWith(key + ":")) {
                continue;
            }
            String inline = trimmed.substring((key + ":").length()).trim();
            collectUuids(inline, uuids);
            int parentIndent = line.indexOf(trimmed);
            for (int child = index + 1; child < lines.length; child++) {
                String childLine = lines[child];
                String childTrimmed = childLine.trim();
                if (childTrimmed.isEmpty() || childTrimmed.startsWith("#")) {
                    continue;
                }
                int childIndent = childLine.indexOf(childTrimmed);
                if (childIndent <= parentIndent) {
                    break;
                }
                collectUuids(childTrimmed, uuids);
            }
            break;
        }
        return uuids;
    }

    private void collectUuids(String text, Set<UUID> uuids) {
        Matcher matcher = UUID_PATTERN.matcher(text);
        while (matcher.find()) {
            uuids.add(UUID.fromString(matcher.group()));
        }
    }

    public record ScanResult(List<MigrationManifest> manifests, List<MigrationIssue> issues) {}
}
