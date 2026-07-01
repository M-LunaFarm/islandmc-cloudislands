package kr.lunaf.cloudislands.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import kr.lunaf.cloudislands.migration.adapter.ParsedIslandDocument;
import kr.lunaf.cloudislands.migration.adapter.Ss2JsonIslandParser;
import kr.lunaf.cloudislands.migration.adapter.Ss2YamlIslandParser;

public final class SuperiorSkyblock2MigrationScanner {
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Set<String> SUPPORTED_FIELD_KEYS = Set.of(
        "islandid", "islanduuid", "island_uuid", "uuid", "island.uuid", "island.id",
        "owner", "owneruuid", "owner_uuid", "owneruniqueid", "owner.uuid", "owner.uniqueid", "owner.unique-id", "owner.id",
        "size", "islandsize", "island_size", "island.size", "settings.size",
        "level", "islandlevel", "island_level", "island.level", "stats.level",
        "worth", "islandworth", "island_worth", "island.worth", "stats.worth",
        "worldpath", "islandworldpath", "schematicpath", "world.path", "world.source", "schematic.path",
        "worldname", "world", "world.name", "island.world",
        "members", "islandmembers", "teammembers", "coopmembers", "coops", "coop",
        "coowners", "coownermembers", "moderators", "mods", "modmembers", "trusted", "trustedmembers", "trustedplayers", "regularmembers", "normalmembers",
        "bans", "bannedplayers", "bannedmembers", "bannedvisitors", "visitorbans", "banned_users", "banned-users",
        "homename", "homeworld", "homex", "homey", "homez", "homeyaw", "homepitch",
        "spawnname", "spawnworld", "spawnx", "spawny", "spawnz", "spawnyaw", "spawnpitch",
        "warpname", "warpworld", "warpx", "warpy", "warpz", "warpyaw", "warppitch", "warppublic", "publicwarp",
        "centerworld", "centerx", "centery", "centerz", "centeryaw", "centerpitch",
        "islandworld", "islandx", "islandy", "islandz", "islandyaw", "islandpitch",
        "completedmissions", "missionscompleted", "finishedmissions", "completedchallenges", "challengescompleted", "finishedchallenges",
        "biome", "island.biome",
        "bankbalance", "balance", "islandbank", "bank.balance", "bankbalance.amount", "economy.balance",
        "islandchest", "islandstorage", "warehouse", "storage", "chest",
        "public", "ispublic", "publicaccess", "settings.public", "visitors.public",
        "locked", "islocked", "settings.locked",
        "pvp", "mobspawn", "animalspawn", "monsterspawn", "firespread", "explosion", "creeperdamage", "tntdamage",
        "witherdamage", "endermangrief", "waterflow", "lavaflow", "icemelt", "leafdecay", "visitorinteract",
        "visitorcontainer", "visitorpickup", "visitordrop", "visitorpvp", "fly", "keepinventory", "publicwarps",
        "sizeupgrade", "islandsizeupgrade", "membersupgrade", "memberupgrade", "warpsupgrade", "warpupgrade",
        "hoppersupgrade", "hopperupgrade", "spawnersupgrade", "spawnerupgrade", "generatorupgrade", "oregeneratorlevel",
        "mobupgrade", "moblimitupgrade", "cropupgrade", "cropgrowthupgrade", "flyupgrade", "flyaccess", "bankupgrade", "banklimitupgrade",
        "hopperlimit", "maxhoppers", "hopperslimit", "spawnerlimit", "maxspawners", "spawnerslimit", "entitylimit",
        "maxentities", "moblimit", "redstonelimit", "maxredstone", "memberlimit", "maxmembers", "warplimit", "maxwarps"
    );
    private static final List<String> SUPPORTED_FIELD_PREFIXES = List.of(
        "homes.",
        "home.",
        "warps.",
        "warp.",
        "blockvalues.",
        "block-values.",
        "block_values.",
        "worthvalues.",
        "worth-values.",
        "worth_values.",
        "blockcounts.",
        "block-counts.",
        "block_counts.",
        "blockamounts.",
        "block-amounts.",
        "block_amounts.",
        "blocks.",
        "islandchest.",
        "islandstorage.",
        "warehouse.",
        "storage.",
        "chest."
    );
    private final Ss2JsonIslandParser jsonParser = new Ss2JsonIslandParser();
    private final Ss2YamlIslandParser yamlParser = new Ss2YamlIslandParser();
    private final Map<String, ParsedIslandDocument> parsedValues = Collections.synchronizedMap(new IdentityHashMap<>());

    public ScanResult scan(Path superiorSkyblockDataPath) {
        parsedValues.clear();
        List<MigrationManifest> manifests = new ArrayList<>();
        List<MigrationIssue> issues = new ArrayList<>();
        Path islandsDir = findIslandsDirectory(superiorSkyblockDataPath);
        if (islandsDir == null || !Files.isDirectory(islandsDir)) {
            issues.add(new MigrationIssue("ISLAND_DIRECTORY_NOT_FOUND", "could not find SuperiorSkyblock2 island data directory under " + superiorSkyblockDataPath, true));
            return new ScanResult(List.of(), issues);
        }
        List<MigrationBlockValue> globalBlockValues = scanGlobalBlockValues(superiorSkyblockDataPath, islandsDir, issues);
        try (Stream<Path> files = Files.walk(islandsDir, 3)) {
            files.filter(Files::isRegularFile)
                .filter(this::looksLikeIslandFile)
                .forEach(file -> scanFile(file, manifests, issues, globalBlockValues));
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

    private void scanFile(Path file, List<MigrationManifest> manifests, List<MigrationIssue> issues, List<MigrationBlockValue> globalBlockValues) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            UUID islandId = parseUuidAny(content, uuidFromFilename(file), "islandId", "islandUuid", "island_uuid", "uuid", "island.uuid", "island.id");
            UUID ownerUuid = parseUuidAny(content, new UUID(0L, 0L), "owner", "ownerUuid", "ownerUUID", "owner_uuid", "ownerUniqueId", "ownerUniqueID", "owner.uuid", "owner.uniqueId", "owner.unique-id", "owner.id");
            if (ownerUuid.getMostSignificantBits() == 0L && ownerUuid.getLeastSignificantBits() == 0L) {
                issues.add(new MigrationIssue("OWNER_NOT_FOUND", "missing owner uuid in " + file, true));
                return;
            }
            int size = parseIntAny(content, 300, "size", "islandSize", "island_size", "island.size", "settings.size");
            long level = parseLongAny(content, 0L, "level", "islandLevel", "island_level", "island.level", "stats.level");
            String worth = parseStringAny(content, "0.00", "worth", "islandWorth", "island_worth", "island.worth", "stats.worth");
            List<UUID> members = parseUuidList(content, "members", "islandMembers", "teamMembers", "coopMembers", "coops", "coop");
            List<MigrationMemberRole> memberRoles = parseMemberRoles(content, ownerUuid);
            List<UUID> bannedVisitors = parseUuidList(content, "bans", "bannedPlayers", "bannedMembers", "bannedVisitors", "visitorBans", "banned_users", "banned-users");
            List<MigrationHome> homes = parseHomes(content);
            List<MigrationWarp> warps = parseWarps(content);
            MigrationLocation islandLocation = parseIslandLocation(content, homes);
            List<MigrationFlag> flags = parseFlags(content);
            List<MigrationPermission> permissions = parsePermissions(content);
            List<MigrationUpgrade> upgrades = parseUpgrades(content);
            List<MigrationLimit> limits = parseLimits(content);
            List<MigrationMission> completedMissions = parseCompletedMissions(content);
            List<MigrationBlockValue> blockValues = mergeBlockValues(globalBlockValues, parseBlockValues(content));
            List<MigrationBlockCount> blockCounts = parseBlockCounts(content);
            List<MigrationWarehouseItem> warehouseItems = parseWarehouseItems(content);
            String biomeKey = parseBiomeKey(content);
            String bankBalance = parseStringAny(content, "0.00", "bankBalance", "balance", "islandBank", "bank.balance", "bankBalance.amount", "economy.balance");
            boolean publicAccess = parseBooleanAny(content, false, "public", "isPublic", "publicAccess", "settings.public", "visitors.public");
            boolean locked = parseBooleanAny(content, false, "locked", "isLocked", "settings.locked");
            String sourceWorldPath = resolveSourceWorldPath(file, content, islandId);
            LinkedHashSet<UUID> allMembers = new LinkedHashSet<>();
            allMembers.add(ownerUuid);
            allMembers.addAll(members);
            for (MigrationMemberRole memberRole : memberRoles) {
                allMembers.add(memberRole.playerUuid());
            }
            manifests.add(new MigrationManifest(islandId, ownerUuid, List.copyOf(allMembers), memberRoles, bannedVisitors, homes, warps, flags, permissions, upgrades, limits, completedMissions, blockValues, blockCounts, warehouseItems, biomeKey, bankBalance, publicAccess, locked, size, level, worth, islandLocation, sourceWorldPath));
            reportUnsupportedFields(file, content, issues);
        } catch (RuntimeException | IOException exception) {
            issues.add(new MigrationIssue("ISLAND_FILE_PARSE_FAILED", file + ": " + exception.getMessage(), true));
        }
    }

    private List<MigrationBlockValue> scanGlobalBlockValues(Path superiorSkyblockDataPath, Path islandsDir, List<MigrationIssue> issues) {
        LinkedHashMap<String, MigrationBlockValue> values = new LinkedHashMap<>();
        for (Path file : globalBlockValueFiles(superiorSkyblockDataPath, islandsDir)) {
            try {
                for (MigrationBlockValue value : parseBlockValues(Files.readString(file, StandardCharsets.UTF_8))) {
                    values.put(value.materialKey(), value);
                }
            } catch (IOException exception) {
                issues.add(new MigrationIssue("GLOBAL_BLOCK_VALUES_SCAN_FAILED", file + ": " + exception.getMessage(), false));
            }
        }
        return List.copyOf(values.values());
    }

    private List<Path> globalBlockValueFiles(Path root, Path islandsDir) {
        LinkedHashSet<Path> files = new LinkedHashSet<>();
        Path normalizedRoot = root == null ? Path.of(".") : root.normalize();
        Path normalizedIslands = islandsDir == null ? Path.of("__missing__") : islandsDir.normalize();
        addGlobalBlockValueCandidates(files, normalizedRoot);
        if (normalizedRoot.getParent() != null) {
            addGlobalBlockValueCandidates(files, normalizedRoot.getParent());
        }
        try (Stream<Path> stream = Files.walk(normalizedRoot, 3)) {
            stream.filter(Files::isRegularFile)
                .filter(file -> !file.normalize().startsWith(normalizedIslands))
                .filter(this::looksLikeBlockValueConfigFile)
                .forEach(file -> files.add(file.normalize()));
        } catch (IOException ignored) {
        }
        return files.stream().filter(Files::isRegularFile).toList();
    }

    private void addGlobalBlockValueCandidates(Set<Path> files, Path root) {
        for (String name : List.of("block-values.yml", "block-values.yaml", "blockValues.yml", "blockValues.yaml", "block_values.yml", "block_values.yaml", "worth.yml", "worth.yaml", "values.yml", "values.yaml", "config.yml", "config.yaml")) {
            files.add(root.resolve(name).normalize());
            files.add(root.resolve("config").resolve(name).normalize());
            files.add(root.resolve("configs").resolve(name).normalize());
            files.add(root.resolve("SuperiorSkyblock2").resolve(name).normalize());
            files.add(root.resolve("SuperiorSkyblock2").resolve("config").resolve(name).normalize());
            files.add(root.resolve("SuperiorSkyblock2").resolve("configs").resolve(name).normalize());
        }
    }

    private boolean looksLikeBlockValueConfigFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (!(name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json"))) {
            return false;
        }
        return (name.contains("block") && (name.contains("value") || name.contains("worth")))
            || name.contains("worth")
            || name.equals("config.yml")
            || name.equals("config.yaml");
    }

    private List<MigrationBlockValue> mergeBlockValues(List<MigrationBlockValue> globalBlockValues, List<MigrationBlockValue> localBlockValues) {
        LinkedHashMap<String, MigrationBlockValue> merged = new LinkedHashMap<>();
        if (globalBlockValues != null) {
            for (MigrationBlockValue value : globalBlockValues) {
                merged.put(value.materialKey(), value);
            }
        }
        if (localBlockValues != null) {
            for (MigrationBlockValue value : localBlockValues) {
                merged.put(value.materialKey(), value);
            }
        }
        return List.copyOf(merged.values());
    }

    private String resolveSourceWorldPath(Path file, String content, UUID islandId) {
        String configured = parseStringAny(content, "", "worldPath", "islandWorldPath", "schematicPath", "world.path", "world.source", "schematic.path");
        if (!configured.isBlank()) {
            Path configuredPath = Path.of(configured);
            if (configuredPath.isAbsolute()) {
                return configuredPath.normalize().toString();
            }
            return file.getParent().resolve(configuredPath).normalize().toString();
        }
        String worldName = parseStringAny(content, "", "worldName", "world", "world.name", "island.world");
        Path root = file.getParent() == null ? Path.of(".") : file.getParent();
        List<Path> candidates = new ArrayList<>();
        if (!worldName.isBlank()) {
            candidates.add(root.resolve(worldName));
            candidates.add(root.getParent() == null ? root.resolve(worldName) : root.getParent().resolve(worldName));
        }
        candidates.add(root.resolve("world"));
        candidates.add(root.resolve("worlds").resolve(islandId.toString()));
        candidates.add(root.resolve("schematics").resolve(islandId + ".schem"));
        candidates.add(root.resolve("schematics").resolve(islandId + ".schematic"));
        return candidates.stream()
            .filter(Files::exists)
            .findFirst()
            .map(path -> path.normalize().toString())
            .orElse("");
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

    private UUID parseUuidAny(String content, UUID fallback, String... keys) {
        for (String key : keys) {
            String value = parseString(content, key, "");
            if (value.isBlank()) {
                continue;
            }
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return fallback;
    }

    private void reportUnsupportedFields(Path file, String content, List<MigrationIssue> issues) {
        for (String field : parsed(content).keys()) {
            if (!supportedField(field)) {
                issues.add(new MigrationIssue("UNSUPPORTED_FIELD", file + ": unsupported SuperiorSkyblock2 field " + field, false));
            }
        }
    }

    private boolean supportedField(String field) {
        String normalized = normalizeField(field);
        if (normalized.isBlank() || SUPPORTED_FIELD_KEYS.contains(normalized)) {
            return true;
        }
        return SUPPORTED_FIELD_PREFIXES.stream().anyMatch(normalized::startsWith);
    }

    private String normalizeField(String field) {
        return field == null ? "" : field.trim().toLowerCase();
    }

    private String parseString(String content, String key, String fallback) {
        String value = parsed(content).value(key);
        if (value != null && !value.isBlank()) {
            return value;
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

    private int parseIntAny(String content, int fallback, String... keys) {
        for (String key : keys) {
            if (containsAnyKey(content, key)) {
                return parseInt(content, key, fallback);
            }
        }
        return fallback;
    }

    private long parseLong(String content, String key, long fallback) {
        try {
            return Long.parseLong(parseString(content, key, Long.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parseLongAny(String content, long fallback, String... keys) {
        for (String key : keys) {
            if (containsAnyKey(content, key)) {
                return parseLong(content, key, fallback);
            }
        }
        return fallback;
    }

    private boolean parseBoolean(String content, String key, boolean fallback) {
        String value = parseString(content, key, Boolean.toString(fallback));
        return switch (value.toLowerCase()) {
            case "true", "yes", "on", "1", "public", "open" -> true;
            case "false", "no", "off", "0", "private", "closed" -> false;
            default -> fallback;
        };
    }

    private boolean parseBooleanAny(String content, boolean fallback, String... keys) {
        for (String key : keys) {
            if (containsAnyKey(content, key)) {
                return parseBoolean(content, key, fallback);
            }
        }
        return fallback;
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
        Map<String, MigrationHome> homes = new LinkedHashMap<>();
        for (NamedLocationKey key : namedLocationKeys(content, "homes", "home")) {
            String prefix = key.root() + "." + key.name() + ".";
            String world = parseString(content, prefix + "world", parseString(content, "world", ""));
            double x = parseDouble(content, prefix + "x", 0.5D);
            double y = parseDouble(content, prefix + "y", 100.0D);
            double z = parseDouble(content, prefix + "z", 0.5D);
            float yaw = parseFloat(content, prefix + "yaw", 180.0F);
            float pitch = parseFloat(content, prefix + "pitch", 0.0F);
            homes.putIfAbsent(key.name(), new MigrationHome(key.name(), world, x, y, z, yaw, pitch));
        }
        if (homes.isEmpty() && containsAnyKey(content, "homeX", "homeY", "homeZ", "homeWorld", "spawnX", "spawnY", "spawnZ", "spawnWorld")) {
            String name = parseString(content, "homeName", parseString(content, "spawnName", "default"));
            String world = parseString(content, "homeWorld", parseString(content, "spawnWorld", parseString(content, "world", "")));
            double x = parseDouble(content, "homeX", parseDouble(content, "spawnX", parseDouble(content, "x", 0.5D)));
            double y = parseDouble(content, "homeY", parseDouble(content, "spawnY", parseDouble(content, "y", 100.0D)));
            double z = parseDouble(content, "homeZ", parseDouble(content, "spawnZ", parseDouble(content, "z", 0.5D)));
            float yaw = parseFloat(content, "homeYaw", parseFloat(content, "spawnYaw", parseFloat(content, "yaw", 180.0F)));
            float pitch = parseFloat(content, "homePitch", parseFloat(content, "spawnPitch", parseFloat(content, "pitch", 0.0F)));
            homes.put(name.isBlank() ? "default" : name, new MigrationHome(name.isBlank() ? "default" : name, world, x, y, z, yaw, pitch));
        }
        return List.copyOf(homes.values());
    }

    private List<MigrationWarp> parseWarps(String content) {
        Map<String, MigrationWarp> warps = new LinkedHashMap<>();
        for (NamedLocationKey key : namedLocationKeys(content, "warps", "warp")) {
            String prefix = key.root() + "." + key.name() + ".";
            String world = parseString(content, prefix + "world", parseString(content, "world", ""));
            double x = parseDouble(content, prefix + "x", 0.5D);
            double y = parseDouble(content, prefix + "y", 100.0D);
            double z = parseDouble(content, prefix + "z", 0.5D);
            float yaw = parseFloat(content, prefix + "yaw", parseFloat(content, "yaw", 180.0F));
            float pitch = parseFloat(content, prefix + "pitch", parseFloat(content, "pitch", 0.0F));
            boolean publicAccess = parseBoolean(content, prefix + "public", parseBoolean(content, prefix + "publicAccess", true));
            warps.putIfAbsent(key.name(), new MigrationWarp(key.name(), world, x, y, z, yaw, pitch, publicAccess));
        }
        if (warps.isEmpty() && containsAnyKey(content, "warpX", "warpY", "warpZ", "warpWorld")) {
            String name = parseString(content, "warpName", "default");
            String world = parseString(content, "warpWorld", parseString(content, "world", ""));
            double x = parseDouble(content, "warpX", 0.5D);
            double y = parseDouble(content, "warpY", 100.0D);
            double z = parseDouble(content, "warpZ", 0.5D);
            float yaw = parseFloat(content, "warpYaw", parseFloat(content, "yaw", 180.0F));
            float pitch = parseFloat(content, "warpPitch", parseFloat(content, "pitch", 0.0F));
            boolean publicAccess = parseBoolean(content, "warpPublic", parseBoolean(content, "publicWarp", true));
            warps.put(name.isBlank() ? "default" : name, new MigrationWarp(name.isBlank() ? "default" : name, world, x, y, z, yaw, pitch, publicAccess));
        }
        return List.copyOf(warps.values());
    }

    private MigrationLocation parseIslandLocation(String content, List<MigrationHome> homes) {
        boolean hasExplicitLocation = containsAnyKey(content, "centerX", "centerY", "centerZ", "centerWorld", "islandX", "islandY", "islandZ", "islandWorld", "spawnX", "spawnY", "spawnZ", "spawnWorld");
        if (!hasExplicitLocation && homes.isEmpty()) {
            return MigrationLocation.unknown();
        }
        MigrationHome fallback = homes.isEmpty() ? new MigrationHome("origin", parseString(content, "world", ""), 0.5D, 100.0D, 0.5D, 180.0F, 0.0F) : homes.get(0);
        String world = parseString(content, "centerWorld", parseString(content, "islandWorld", parseString(content, "spawnWorld", parseString(content, "world", fallback.worldName()))));
        double x = parseDouble(content, "centerX", parseDouble(content, "islandX", parseDouble(content, "spawnX", fallback.x())));
        double y = parseDouble(content, "centerY", parseDouble(content, "islandY", parseDouble(content, "spawnY", fallback.y())));
        double z = parseDouble(content, "centerZ", parseDouble(content, "islandZ", parseDouble(content, "spawnZ", fallback.z())));
        float yaw = parseFloat(content, "centerYaw", parseFloat(content, "islandYaw", parseFloat(content, "spawnYaw", fallback.yaw())));
        float pitch = parseFloat(content, "centerPitch", parseFloat(content, "islandPitch", parseFloat(content, "spawnPitch", fallback.pitch())));
        return new MigrationLocation(world, x, y, z, yaw, pitch, true);
    }

    private List<NamedLocationKey> namedLocationKeys(String content, String... roots) {
        LinkedHashMap<String, NamedLocationKey> keys = new LinkedHashMap<>();
        for (String root : roots) {
            String prefix = root + ".";
            for (String field : parsed(content).keys()) {
                if (!field.startsWith(prefix) || !matchesAny(field.substring(field.lastIndexOf('.') + 1), "world", "x", "y", "z", "yaw", "pitch", "public", "publicAccess")) {
                    continue;
                }
                String remainder = field.substring(prefix.length());
                int dot = remainder.indexOf('.');
                if (dot > 0) {
                    String name = remainder.substring(0, dot);
                    keys.putIfAbsent(root + ":" + name, new NamedLocationKey(root, name));
                }
            }
            Matcher matcher = Pattern.compile(Pattern.quote(root) + "\\.([A-Za-z0-9_-]+)\\.(?:world|x|y|z|yaw|pitch|public|publicAccess)").matcher(content);
            while (matcher.find()) {
                String name = matcher.group(1);
                keys.putIfAbsent(root + ":" + name, new NamedLocationKey(root, name));
            }
        }
        return List.copyOf(keys.values());
    }

    private record NamedLocationKey(String root, String name) {}

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

    private List<MigrationMemberRole> parseMemberRoles(String content, UUID ownerUuid) {
        List<MigrationMemberRole> roles = new ArrayList<>();
        addMemberRoles(content, roles, ownerUuid, "CO_OWNER", "coOwners", "coOwnerMembers", "coowners");
        addMemberRoles(content, roles, ownerUuid, "MODERATOR", "moderators", "mods", "modMembers");
        addMemberRoles(content, roles, ownerUuid, "TRUSTED", "trusted", "trustedMembers", "trustedPlayers");
        addMemberRoles(content, roles, ownerUuid, "MEMBER", "members", "islandMembers", "teamMembers", "regularMembers", "normalMembers");
        return List.copyOf(roles);
    }

    private void addMemberRoles(String content, List<MigrationMemberRole> roles, UUID ownerUuid, String roleName, String... keys) {
        LinkedHashSet<UUID> players = new LinkedHashSet<>();
        for (String key : keys) {
            players.addAll(parseUuidList(content, key));
        }
        for (UUID playerUuid : players) {
            if (!playerUuid.equals(ownerUuid)) {
                roles.add(new MigrationMemberRole(playerUuid, roleName));
            }
        }
    }

    private List<MigrationUpgrade> parseUpgrades(String content) {
        List<MigrationUpgrade> result = new ArrayList<>();
        addUpgrade(content, result, "size", "sizeUpgrade", "islandSizeUpgrade");
        addUpgrade(content, result, "members", "membersUpgrade", "memberUpgrade");
        addUpgrade(content, result, "warps", "warpsUpgrade", "warpUpgrade");
        addUpgrade(content, result, "hoppers", "hoppersUpgrade", "hopperUpgrade");
        addUpgrade(content, result, "spawners", "spawnersUpgrade", "spawnerUpgrade");
        addUpgrade(content, result, "generator", "generatorUpgrade", "oreGeneratorLevel");
        addUpgrade(content, result, "mob", "mobUpgrade", "mobLimitUpgrade");
        addUpgrade(content, result, "crop", "cropUpgrade", "cropGrowthUpgrade");
        addUpgrade(content, result, "fly", "flyUpgrade", "flyAccess");
        addUpgrade(content, result, "bank", "bankUpgrade", "bankLimitUpgrade");
        return List.copyOf(result);
    }

    private List<MigrationLimit> parseLimits(String content) {
        List<MigrationLimit> result = new ArrayList<>();
        addLimit(content, result, "hoppers", "hopperLimit", "maxHoppers", "hoppersLimit");
        addLimit(content, result, "spawners", "spawnerLimit", "maxSpawners", "spawnersLimit");
        addLimit(content, result, "entities", "entityLimit", "maxEntities", "mobLimit");
        addLimit(content, result, "redstone", "redstoneLimit", "maxRedstone");
        addLimit(content, result, "members", "memberLimit", "maxMembers");
        addLimit(content, result, "warps", "warpLimit", "maxWarps");
        return List.copyOf(result);
    }

    private List<MigrationMission> parseCompletedMissions(String content) {
        List<MigrationMission> result = new ArrayList<>();
        for (String missionKey : parseStringList(content, "completedMissions", "missionsCompleted", "finishedMissions")) {
            result.add(new MigrationMission(missionKey, "MISSION"));
        }
        for (String missionKey : parseStringList(content, "completedChallenges", "challengesCompleted", "finishedChallenges")) {
            result.add(new MigrationMission(missionKey, "CHALLENGE"));
        }
        return List.copyOf(result);
    }

    private List<MigrationBlockValue> parseBlockValues(String content) {
        LinkedHashMap<String, MigrationBlockValue> values = new LinkedHashMap<>();
        for (String field : parsed(content).keys()) {
            String[] parts = field.split("\\.");
            if (parts.length != 3 || !matchesAny(parts[0], "blockValues", "block-values", "block_values", "worthValues", "worth-values", "worth_values")) {
                continue;
            }
            String materialKey = safeMaterialKey(parts[1]);
            String prefix = parts[0] + "." + parts[1] + ".";
            String worth = parseString(content, prefix + "worth", parseString(content, prefix + "value", parseString(content, prefix + "price", "0.00")));
            long levelPoints = parseLong(content, prefix + "levelPoints", parseLong(content, prefix + "points", parseLong(content, prefix + "level", 0L)));
            long limit = parseLong(content, prefix + "limit", parseLong(content, prefix + "max", 0L));
            values.putIfAbsent(materialKey, new MigrationBlockValue(materialKey, worth, levelPoints, limit));
        }
        Matcher matcher = Pattern.compile("(blockValues|block-values|block_values|worthValues|worth-values|worth_values)\\.([A-Za-z0-9:_/-]+)\\.(?:worth|value|price|level|levelPoints|points|limit|max)").matcher(content);
        while (matcher.find()) {
            String root = matcher.group(1);
            String rawMaterialKey = matcher.group(2);
            String materialKey = safeMaterialKey(rawMaterialKey);
            String prefix = root + "." + rawMaterialKey + ".";
            String worth = parseString(content, prefix + "worth", parseString(content, prefix + "value", parseString(content, prefix + "price", "0.00")));
            long levelPoints = parseLong(content, prefix + "levelPoints", parseLong(content, prefix + "points", parseLong(content, prefix + "level", 0L)));
            long limit = parseLong(content, prefix + "limit", parseLong(content, prefix + "max", 0L));
            values.putIfAbsent(materialKey, new MigrationBlockValue(materialKey, worth, levelPoints, limit));
        }
        addYamlBlockValues(values, content, "blockValues", "block-values", "block_values", "worthValues", "worth-values", "worth_values");
        return List.copyOf(values.values());
    }

    private List<MigrationBlockCount> parseBlockCounts(String content) {
        LinkedHashMap<String, MigrationBlockCount> counts = new LinkedHashMap<>();
        for (String field : parsed(content).keys()) {
            String[] parts = field.split("\\.");
            if (parts.length != 2 || !matchesAny(parts[0], "blockCounts", "block-counts", "block_counts", "blockAmounts", "block-amounts", "block_amounts", "blocks")) {
                continue;
            }
            String materialKey = safeMaterialKey(parts[1]);
            long count = Math.max(0L, parseLong(content, field, 0L));
            counts.putIfAbsent(materialKey, new MigrationBlockCount(materialKey, count));
        }
        Matcher matcher = Pattern.compile("(blockCounts|block-counts|block_counts|blockAmounts|block-amounts|block_amounts|blocks)\\.([A-Za-z0-9:_/-]+)").matcher(content);
        while (matcher.find()) {
            String root = matcher.group(1);
            String rawMaterialKey = matcher.group(2);
            String materialKey = safeMaterialKey(rawMaterialKey);
            long count = Math.max(0L, parseLong(content, root + "." + rawMaterialKey, 0L));
            counts.putIfAbsent(materialKey, new MigrationBlockCount(materialKey, count));
        }
        addYamlBlockCounts(counts, content, "blockCounts", "block-counts", "block_counts", "blockAmounts", "block-amounts", "block_amounts", "blocks");
        return List.copyOf(counts.values());
    }

    private List<MigrationWarehouseItem> parseWarehouseItems(String content) {
        LinkedHashMap<String, MigrationWarehouseItem> items = new LinkedHashMap<>();
        for (String field : parsed(content).keys()) {
            String[] parts = field.split("\\.");
            if (parts.length != 2 || !matchesAny(parts[0], "islandChest", "islandStorage", "warehouse", "storage", "chest")) {
                continue;
            }
            String materialKey = safeMaterialKey(parts[1]);
            long amount = Math.max(0L, parseLong(content, field, 0L));
            if (!materialKey.isBlank() && amount > 0L) {
                items.putIfAbsent(materialKey, new MigrationWarehouseItem(materialKey, amount));
            }
        }
        Matcher matcher = Pattern.compile("(islandChest|islandStorage|warehouse|storage|chest)\\.([A-Za-z0-9:_/-]+)").matcher(content);
        while (matcher.find()) {
            String root = matcher.group(1);
            String rawMaterialKey = matcher.group(2);
            String materialKey = safeMaterialKey(rawMaterialKey);
            long amount = Math.max(0L, parseLong(content, root + "." + rawMaterialKey, 0L));
            if (!materialKey.isBlank() && amount > 0L) {
                items.putIfAbsent(materialKey, new MigrationWarehouseItem(materialKey, amount));
            }
        }
        addYamlWarehouseItems(items, content, "islandChest", "islandStorage", "warehouse", "storage", "chest");
        return List.copyOf(items.values());
    }

    private void addYamlBlockValues(LinkedHashMap<String, MigrationBlockValue> values, String content, String... roots) {
        String[] lines = content.split("\\R");
        for (String root : roots) {
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index];
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !root.equals(yamlKey(trimmed))) {
                    continue;
                }
                int parentIndent = line.indexOf(trimmed);
                int child = index + 1;
                while (child < lines.length) {
                    String childLine = lines[child];
                    String childTrimmed = childLine.trim();
                    if (childTrimmed.isEmpty() || childTrimmed.startsWith("#")) {
                        child++;
                        continue;
                    }
                    int childIndent = childLine.indexOf(childTrimmed);
                    if (childIndent <= parentIndent) {
                        break;
                    }
                    String materialKey = safeMaterialKey(yamlKey(childTrimmed));
                    if (materialKey.isBlank()) {
                        child++;
                        continue;
                    }
                    String inlineValue = yamlValue(childTrimmed);
                    String worth = inlineValue.isBlank() || inlineValue.startsWith("{") ? inlineMapValue(inlineValue, "worth", inlineMapValue(inlineValue, "value", inlineMapValue(inlineValue, "price", "0.00"))) : inlineValue;
                    long levelPoints = parseLongLiteral(inlineMapValue(inlineValue, "levelPoints", inlineMapValue(inlineValue, "points", inlineMapValue(inlineValue, "level", "0"))), 0L);
                    long limit = parseLongLiteral(inlineMapValue(inlineValue, "limit", inlineMapValue(inlineValue, "max", "0")), 0L);
                    int grand = child + 1;
                    while (grand < lines.length) {
                        String grandLine = lines[grand];
                        String grandTrimmed = grandLine.trim();
                        if (grandTrimmed.isEmpty() || grandTrimmed.startsWith("#")) {
                            grand++;
                            continue;
                        }
                        int grandIndent = grandLine.indexOf(grandTrimmed);
                        if (grandIndent <= childIndent) {
                            break;
                        }
                        String key = yamlKey(grandTrimmed);
                        String value = yamlValue(grandTrimmed);
                        if (matchesAny(key, "worth", "value", "price")) {
                            worth = value.isBlank() ? worth : value;
                        } else if (matchesAny(key, "levelPoints", "level-points", "points", "level")) {
                            levelPoints = parseLongLiteral(value, levelPoints);
                        } else if (matchesAny(key, "limit", "max", "blockLimit", "block-limit")) {
                            limit = parseLongLiteral(value, limit);
                        }
                        grand++;
                    }
                    values.putIfAbsent(materialKey, new MigrationBlockValue(materialKey, worth, Math.max(0L, levelPoints), Math.max(0L, limit)));
                    child = grand;
                }
            }
        }
    }

    private void addYamlBlockCounts(LinkedHashMap<String, MigrationBlockCount> counts, String content, String... roots) {
        String[] lines = content.split("\\R");
        for (String root : roots) {
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index];
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !root.equals(yamlKey(trimmed))) {
                    continue;
                }
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
                    String materialKey = safeMaterialKey(yamlKey(childTrimmed));
                    String value = yamlValue(childTrimmed);
                    if (!materialKey.isBlank() && !value.isBlank()) {
                        counts.putIfAbsent(materialKey, new MigrationBlockCount(materialKey, Math.max(0L, parseLongLiteral(value, 0L))));
                    }
                }
            }
        }
    }

    private void addYamlWarehouseItems(LinkedHashMap<String, MigrationWarehouseItem> items, String content, String... roots) {
        String[] lines = content.split("\\R");
        for (String root : roots) {
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index];
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !root.equals(yamlKey(trimmed))) {
                    continue;
                }
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
                    String materialKey = safeMaterialKey(yamlKey(childTrimmed));
                    String value = yamlValue(childTrimmed);
                    long amount = Math.max(0L, parseLongLiteral(value, 0L));
                    if (!materialKey.isBlank() && amount > 0L) {
                        items.putIfAbsent(materialKey, new MigrationWarehouseItem(materialKey, amount));
                    }
                }
            }
        }
    }

    private String yamlKey(String trimmedLine) {
        String line = trimmedLine;
        if (line.startsWith("-")) {
            line = line.substring(1).trim();
        }
        int colon = yamlSeparator(line);
        if (colon < 0) {
            return "";
        }
        String key = line.substring(0, colon).trim();
        if ((key.startsWith("\"") && key.endsWith("\"")) || (key.startsWith("'") && key.endsWith("'"))) {
            key = key.substring(1, key.length() - 1);
        }
        return key.trim();
    }

    private String yamlValue(String trimmedLine) {
        String line = trimmedLine;
        if (line.startsWith("-")) {
            line = line.substring(1).trim();
        }
        int colon = yamlSeparator(line);
        if (colon < 0) {
            return "";
        }
        return cleanScalar(line.substring(colon + 1).trim());
    }

    private int yamlSeparator(String line) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (current == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (current == ':' && !singleQuoted && !doubleQuoted) {
                return index;
            }
        }
        return -1;
    }

    private String inlineMapValue(String text, String key, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        Matcher matcher = Pattern.compile("(?i)(?:^|[,{\\s])\"?" + Pattern.quote(key) + "\"?\\s*[:=]\\s*\"?([^,}\\s\"]+)\"?").matcher(text);
        return matcher.find() ? cleanScalar(matcher.group(1)) : fallback;
    }

    private long parseLongLiteral(String value, long fallback) {
        try {
            return Long.parseLong(cleanScalar(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean matchesAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private String safeMaterialKey(String materialKey) {
        return cleanScalar(materialKey).toLowerCase().replace(' ', '_');
    }

    private String cleanScalar(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            return cleaned;
        }
        boolean quoted = (cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'"));
        if (quoted) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        int comment = quoted ? -1 : cleaned.indexOf(" #");
        if (comment >= 0) {
            cleaned = cleaned.substring(0, comment).trim();
        }
        return cleaned;
    }

    private void addLimit(String content, List<MigrationLimit> limits, String limitKey, String... keys) {
        for (String key : keys) {
            if (containsAnyKey(content, key)) {
                limits.add(new MigrationLimit(limitKey, Math.max(0L, parseLong(content, key, 0L))));
                return;
            }
        }
    }

    private void addUpgrade(String content, List<MigrationUpgrade> upgrades, String upgradeKey, String... keys) {
        for (String key : keys) {
            if (containsAnyKey(content, key)) {
                upgrades.add(new MigrationUpgrade(upgradeKey, Math.max(0, parseInt(content, key, 0))));
                return;
            }
        }
    }

    private String parseBiomeKey(String content) {
        if (!containsAnyKey(content, "biome", "biomeKey", "islandBiome", "settings.biome", "island.biome")) {
            return "";
        }
        String biome = parseStringAny(content, "", "biomeKey", "islandBiome", "biome", "settings.biome", "island.biome").trim().toLowerCase();
        if (biome.isBlank()) {
            return "";
        }
        return biome.contains(":") ? biome : "minecraft:" + biome;
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
            if (parsed(content).hasKey(key)) {
                return true;
            }
        }
        return false;
    }

    private String parseStringAny(String content, String fallback, String... keys) {
        for (String key : keys) {
            String value = parseString(content, key, "");
            if (!value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private List<UUID> parseUuidList(String content, String... keys) {
        LinkedHashSet<UUID> uuids = new LinkedHashSet<>();
        for (String key : keys) {
            uuids.addAll(parseJsonUuidArray(content, key));
            uuids.addAll(parseYamlUuidBlock(content, key));
        }
        return List.copyOf(uuids);
    }

    private List<String> parseStringList(String content, String... keys) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String key : keys) {
            values.addAll(parseJsonStringArray(content, key));
            values.addAll(parseYamlStringBlock(content, key));
        }
        return List.copyOf(values);
    }

    private Set<String> parseJsonStringArray(String content, String key) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(parsed(content).list(key));
        if (!values.isEmpty()) {
            return values;
        }
        String needle = "\"" + key + "\"";
        int keyStart = content.indexOf(needle);
        if (keyStart < 0) {
            return values;
        }
        int arrayStart = content.indexOf('[', keyStart + needle.length());
        int arrayEnd = arrayStart < 0 ? -1 : content.indexOf(']', arrayStart + 1);
        if (arrayEnd <= arrayStart) {
            return values;
        }
        collectStringValues(content.substring(arrayStart + 1, arrayEnd), values);
        return values;
    }

    private Set<String> parseYamlStringBlock(String content, String key) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        String[] lines = content.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String trimmed = line.trim();
            if (!trimmed.startsWith(key + ":")) {
                continue;
            }
            collectStringValues(trimmed.substring((key + ":").length()).trim(), values);
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
                collectStringValues(childTrimmed, values);
            }
            break;
        }
        return values;
    }

    private void collectStringValues(String text, Set<String> values) {
        String normalized = text.replace("[", "").replace("]", "");
        for (String token : normalized.split(",")) {
            String value = token.trim();
            if (value.startsWith("-")) {
                value = value.substring(1).trim();
            }
            if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!value.isBlank()) {
                values.add(value.toLowerCase());
            }
        }
    }

    private Set<UUID> parseJsonUuidArray(String content, String key) {
        LinkedHashSet<UUID> uuids = new LinkedHashSet<>();
        for (String value : parsed(content).list(key)) {
            collectUuids(value, uuids);
        }
        if (!uuids.isEmpty()) {
            return uuids;
        }
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

    private ParsedIslandDocument parsed(String content) {
        return parsedValues.computeIfAbsent(content == null ? "" : content, this::parseValues);
    }

    private ParsedIslandDocument parseValues(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("{")) {
            try {
                return jsonParser.parse(content);
            } catch (RuntimeException ignored) {
                return yamlParser.parse(content);
            }
        }
        return yamlParser.parse(content);
    }

    public record ScanResult(List<MigrationManifest> manifests, List<MigrationIssue> issues) {}
}
