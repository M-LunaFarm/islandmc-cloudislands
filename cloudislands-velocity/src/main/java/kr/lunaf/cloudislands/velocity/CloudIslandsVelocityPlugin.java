package kr.lunaf.cloudislands.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.JdkCoreApiClient;
import kr.lunaf.cloudislands.velocity.command.IslandCommandCatalog;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

@Plugin(id = "cloudislands", name = "CloudIslands", version = "0.1.0", description = "Portable island routing and management", authors = {"LeeSeungmin"})
public final class CloudIslandsVelocityPlugin {
    private static final List<String> ALIASES = List.of("is", "island", "섬");
    private final ProxyServer proxy;
    private final Logger logger;
    private final VelocityRoutingController routingController;
    private final List<String> commandAliases;

    @Inject
    public CloudIslandsVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        VelocityConfig config = loadConfig(dataDirectory, logger);
        String coreUrl = System.getProperty("cloudislands.core", config.coreBaseUrl());
        String coreToken = System.getenv().getOrDefault("CI_CORE_TOKEN", config.coreToken());
        String adminToken = System.getenv().getOrDefault("CI_ADMIN_TOKEN", config.adminToken());
        long timeoutMs = Long.getLong("cloudislands.timeoutMs", config.timeoutMs());
        String fallbackServer = System.getProperty("cloudislands.fallback", config.fallbackServer());
        int routeWaitSeconds = Integer.getInteger("cloudislands.routeWaitSeconds", config.routeWaitSeconds());
        CoreApiClient client = new JdkCoreApiClient(URI.create(coreUrl), coreToken, adminToken, Duration.ofMillis(Math.max(1L, timeoutMs)));
        this.routingController = new VelocityRoutingController(proxy, client, fallbackServer, routeWaitSeconds, config.useActionBar(), config.useBossBarLoading(), config.hideNodeNames());
        this.commandAliases = config.aliases();
    }

    private static VelocityConfig loadConfig(Path dataDirectory, Logger logger) {
        Map<String, String> values = new HashMap<>();
        List<String> aliases = new ArrayList<>();
        Path configPath = dataDirectory.resolve("config.yaml");
        try {
            if (Files.notExists(configPath)) {
                Files.createDirectories(dataDirectory);
                try (InputStream defaults = CloudIslandsVelocityPlugin.class.getClassLoader().getResourceAsStream("config.yaml")) {
                    if (defaults != null) {
                        Files.copy(defaults, configPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            if (Files.exists(configPath)) {
                String section = "";
                boolean readingAliases = false;
                for (String rawLine : Files.readAllLines(configPath)) {
                    String line = rawLine.strip();
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    if (readingAliases && line.startsWith("-")) {
                        String alias = unquote(line.substring(1).strip());
                        if (!alias.isBlank()) {
                            aliases.add(alias);
                        }
                        continue;
                    }
                    readingAliases = false;
                    if (!rawLine.startsWith(" ") && line.endsWith(":")) {
                        section = line.substring(0, line.length() - 1).strip();
                        continue;
                    }
                    int colon = line.indexOf(':');
                    if (colon <= 0) {
                        continue;
                    }
                    String key = line.substring(0, colon).strip();
                    String value = unquote(line.substring(colon + 1).strip());
                    if (section.equals("commands") && key.equals("aliases") && value.isBlank()) {
                        readingAliases = true;
                        continue;
                    }
                    values.put(section.isBlank() ? key : section + "." + key, resolveEnv(value));
                }
            }
        } catch (IOException exception) {
            logger.warn("Failed to load CloudIslands Velocity config, using defaults", exception);
        }
        return new VelocityConfig(
            values.getOrDefault("core-api.base-url", "https://core-api.internal:8443"),
            values.getOrDefault("core-api.auth-token", ""),
            values.getOrDefault("core-api.admin-token", ""),
            integer(values.get("core-api.timeout-ms"), 3000),
            values.getOrDefault("routing.fallback-on-failure", values.getOrDefault("routing.default-lobby", "Lobby")),
            integer(values.get("routing.wait-for-activation-timeout-seconds"), 20),
            bool(values.get("routing.hide-node-names"), true),
            bool(values.get("messages.use-actionbar"), true),
            bool(values.get("messages.use-bossbar-loading"), true),
            aliases.isEmpty() ? ALIASES : List.copyOf(aliases)
        );
    }

    private static String unquote(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String resolveEnv(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return System.getenv().getOrDefault(trimmed.substring(2, trimmed.length() - 1), "");
        }
        return trimmed;
    }

    private static int integer(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean bool(String value, boolean fallback) {
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private static String[] commandAliasArray(List<String> aliases) {
        return aliases.stream().filter(alias -> !alias.equalsIgnoreCase("섬")).distinct().toArray(String[]::new);
    }

    private record VelocityConfig(String coreBaseUrl, String coreToken, String adminToken, int timeoutMs, String fallbackServer, int routeWaitSeconds, boolean hideNodeNames, boolean useActionBar, boolean useBossBarLoading, List<String> aliases) {}

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        CommandManager commands = proxy.getCommandManager();
        SimpleCommand islandCommand = new SimpleCommand() {
            @Override
            public void execute(SimpleCommand.Invocation invocation) {
                if (!(invocation.source() instanceof Player player)) {
                    invocation.source().sendMessage(Component.text("플레이어만 사용할 수 있습니다."));
                    return;
                }
                if (!player.hasPermission("cloudislands.player")) {
            player.sendMessage(Component.text("섬 명령을 사용할 권한이 없습니다."));
            return;
        }
        dispatch(player, invocation.arguments());
            }

            @Override
            public List<String> suggest(SimpleCommand.Invocation invocation) {
                return suggestions(IslandCommandCatalog.playerCommands(), "섬", invocation.arguments());
            }
        };
        commands.register(commands.metaBuilder("섬").aliases(commandAliasArray(commandAliases)).build(), islandCommand);
        commands.register(commands.metaBuilder("ciadmin").aliases("섬관리").build(), new SimpleCommand() {
            @Override
            public void execute(SimpleCommand.Invocation invocation) {
                if (!(invocation.source() instanceof Player player)) {
                    invocation.source().sendMessage(Component.text("플레이어만 사용할 수 있습니다."));
                    return;
                }
                if (!hasAdminAccess(player, invocation.arguments())) {
                    player.sendMessage(Component.text("섬 관리 명령을 사용할 권한이 없습니다."));
                    return;
                }
                dispatchAdmin(player, invocation.arguments());
            }

            @Override
            public List<String> suggest(SimpleCommand.Invocation invocation) {
                if (!hasAdminAccess(invocation.source(), invocation.arguments())) {
                    return List.of();
                }
                return adminSuggestions(invocation.arguments());
            }
        });
        routingController.startEventPolling(this);
        logger.info("CloudIslands Velocity router enabled with aliases {}", commandAliases);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        routingController.stopEventPolling();
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        String channel = event.getIdentifier().getId();
        if (channel.equals("cloudislands") || channel.startsWith("cloudislands:")) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        routingController.recordPlayerProfile(event.getPlayer());
    }

    private void dispatchAdmin(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            player.sendMessage(Component.text(routingController.statusSummary()));
            return;
        }
        if (isCommandListRequest(args)) {
            sendCommandList(player, "CloudIslands 관리자 명령어 목록", IslandCommandCatalog.adminCommands(), commandListPage(args), "ciadmin command list");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("info")) {
            routingController.adminIslandInfoTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("where")) {
            routingController.adminIslandWhereTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("tp")) {
            player.sendActionBar(Component.text("섬으로 이동하는 중입니다."));
            routingController.adminTeleportIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("save")) {
            routingController.snapshotTarget(player, args[2], "ADMIN_SAVE");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("snapshot")) {
            routingController.snapshotTarget(player, args[2], args.length > 3 ? joinArgs(args, 3) : "MANUAL");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("snapshots")) {
            routingController.listSnapshotsTarget(player, args[2]);
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("rollback")) {
            routingController.restoreTarget(player, args[2], parseLongOrZero(args[3]));
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("restore")) {
            routingController.restoreTarget(player, args[2], parseLongOrZero(args[3]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("delete")) {
            routingController.adminDeleteIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("repair")) {
            routingController.repairIslandTarget(player, args[2], args.length > 3 ? joinArgs(args, 3) : "admin");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("activate")) {
            routingController.activateIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("deactivate")) {
            routingController.deactivateIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("migrate")) {
            routingController.migrateIslandTarget(player, args[2], args[3]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("quarantine")) {
            routingController.quarantineIslandTarget(player, args[2], args.length > 3 ? joinArgs(args, 3) : "admin");
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("debug")) {
            if (args.length < 3 || args[2].equalsIgnoreCase("all")) {
                routingController.debugRoutes(player, new UUID(0L, 0L));
            } else {
                routingController.debugRoutesTarget(player, args[2]);
            }
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("ticket")) {
            routingController.routeTicketTarget(player, args[2]);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("clear")) {
            UUID ticketId = args.length > 3 ? parseUuidOrNil(args[3]) : new UUID(0L, 0L);
            routingController.clearRouteTarget(player, args.length > 2 ? args[2] : "", ticketId);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("rankings")) {
            String type = args.length > 1 ? args[1] : "level";
            int limit = args.length > 2 ? (int) parseLongOrZero(args[2]) : 10;
            if (type.equalsIgnoreCase("worth") || type.equalsIgnoreCase("value")) {
                routingController.showWorthRanking(player, limit);
            } else {
                routingController.showLevelRanking(player, limit);
            }
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("cache") && args[1].equalsIgnoreCase("clear")) {
            routingController.clearCache(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("events")) {
            routingController.listEvents(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("audit")) {
            routingController.listAuditLogs(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("metrics")) {
            routingController.metrics(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("storage")) {
            routingController.storageStatus(player);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("list")) {
            routingController.listBlockValues(player);
            return;
        }
        if (args.length >= 6 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("set")) {
            routingController.setBlockValue(player, args[2], args[3], parseLongOrZero(args[4]), parseLongOrZero(args[5]));
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("upgrade-rules")) {
            routingController.listUpgradeRules(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            routingController.reload(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("migrate-superiorskyblock2")) {
            String action = args.length > 1 ? args[1] : "scan";
            String path = args.length > 2 ? joinArgs(args, 2) : "";
            routingController.migrateSuperiorSkyblock2(player, action, path);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("player") && args[1].equalsIgnoreCase("info")) {
            routingController.playerInfoTarget(player, args[2]);
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("player") && args[1].equalsIgnoreCase("setisland")) {
            routingController.setPlayerIslandTarget(player, args[2], parseUuidOrNil(args[3]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("player") && args[1].equalsIgnoreCase("clearisland")) {
            routingController.clearPlayerIslandTarget(player, args[2]);
            return;
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("list")) {
            routingController.listTemplates(player);
            return;
        }
        if (args.length >= 4 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("upsert")) {
            routingController.upsertTemplate(player, args[2], args[3], args.length > 4 ? parseToggle(args, 4, true) : true, args.length > 5 ? args[5] : "");
            return;
        }
        if (args.length >= 3 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("enable")) {
            routingController.enableTemplate(player, args[2]);
            return;
        }
        if (args.length >= 3 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("disable")) {
            routingController.disableTemplate(player, args[2]);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("list")) {
            routingController.listNodes(player);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("info")) {
            routingController.nodeInfo(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("islands")) {
            routingController.nodeIslands(player, args[2], args.length > 3 ? (int) parseLongOrZero(args[3]) : 50);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("drain")) {
            routingController.drainNode(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("undrain")) {
            routingController.undrainNode(player, args[2]);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("sweep")) {
            routingController.sweepNode(player, args.length > 2 ? args[2] : "");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("kickall")) {
            routingController.kickAllNode(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("shutdown-safe")) {
            routingController.shutdownSafeNode(player, args[2]);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("list")) {
            routingController.listJobs(player);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("retry")) {
            routingController.retryJob(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("cancel")) {
            routingController.cancelJob(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("recover")) {
            routingController.recoverJobs(player, args.length > 2 ? args[2] : "recovery", args.length > 3 ? parseLongOrZero(args[3]) : 60000L, args.length > 4 ? (int) parseLongOrZero(args[4]) : 16);
            return;
        }
        sendCommandList(player, "CloudIslands 관리자 명령어 목록", IslandCommandCatalog.adminCommands(), 1, "ciadmin command list");
    }

    private void dispatch(Player player, String[] args) {
        if (isCommandListRequest(args)) {
            sendCommandList(player, "섬 명령어 목록", IslandCommandCatalog.playerCommands(), commandListPage(args), "섬 command list");
            return;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("home") || args[0].equals("홈")) {
            player.sendActionBar(Component.text("섬을 준비하는 중입니다."));
            routingController.routeHome(player, args.length > 1 ? args[1] : "default");
            return;
        }
        if (args[0].equalsIgnoreCase("info") || args[0].equals("정보")) {
            routingController.showMyIsland(player);
            return;
        }
        if (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("my") || args[0].equalsIgnoreCase("my-islands") || args[0].equals("목록") || args[0].equals("내섬")) {
            routingController.listMyIslands(player);
            return;
        }
        if (args[0].equalsIgnoreCase("settings") || args[0].equals("설정")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.showIslandSettings(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("level") || args[0].equals("레벨")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.showIslandLevel(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("worth") || args[0].equals("value") || args[0].equals("가치")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.showIslandWorth(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("size") || args[0].equals("크기")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.showIslandSize(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("border") || args[0].equals("경계")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.showIslandBorder(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("biome") || args[0].equals("바이옴")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            if (args.length > 2) {
                routingController.setBiome(player, islandId, args[2]);
            } else {
                routingController.showBiome(player, islandId);
            }
            return;
        }
        if (args[0].equalsIgnoreCase("homes") || args[0].equals("홈목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listHomes(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("sethome") || args[0].equals("셋홈")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            String name = argumentAfterOptionalIsland(args, 1, "default");
            routingController.setHome(player, islandId, name);
            return;
        }
        if (args[0].equalsIgnoreCase("visit") || args[0].equals("방문")) {
            player.sendActionBar(Component.text("방문할 섬을 불러오는 중입니다."));
            if (args.length < 2) {
                routingController.routeRandomVisit(player);
                return;
            }
            UUID targetIslandId = parseUuidOrNil(args[1]);
            if (!targetIslandId.equals(new UUID(0L, 0L))) {
                routingController.routeVisit(player, targetIslandId);
                return;
            }
            routingController.routeVisitNamedTarget(player, args[1]);
            return;
        }
        if (args[0].equalsIgnoreCase("randomvisit") || args[0].equals("랜덤방문")) {
            player.sendActionBar(Component.text("방문 가능한 공개 섬을 찾는 중입니다."));
            routingController.routeRandomVisit(player);
            return;
        }
        if (args[0].equalsIgnoreCase("public-islands") || args[0].equalsIgnoreCase("publicislands") || args[0].equalsIgnoreCase("visit-list") || args[0].equals("공개섬") || args[0].equals("방문목록")) {
            routingController.listPublicIslands(player, args.length > 1 ? (int) parseLongOrZero(args[1]) : 10);
            return;
        }
        if (args[0].equalsIgnoreCase("warps") || args[0].equals("워프목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listWarps(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("publicwarplist") || args[0].equalsIgnoreCase("public-warps") || args[0].equals("공개워프목록")) {
            routingController.listPublicWarps(player);
            return;
        }
        if (args[0].equalsIgnoreCase("setwarp") || args[0].equals("워프설정")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            String warpName = argumentAfterOptionalIsland(args, 1, "default");
            routingController.setWarp(player, islandId, warpName, false);
            return;
        }
        if (args[0].equalsIgnoreCase("deletewarp") || args[0].equals("워프삭제")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            String warpName = argumentAfterOptionalIsland(args, 1, "default");
            routingController.deleteWarp(player, islandId, warpName);
            return;
        }
        if (args[0].equalsIgnoreCase("publicwarp") || args[0].equals("워프공개")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            String warpName = argumentAfterOptionalIsland(args, 1, "default");
            routingController.setWarpPublicAccess(player, islandId, warpName, true);
            return;
        }
        if (args[0].equalsIgnoreCase("privatewarp") || args[0].equals("워프비공개")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            String warpName = argumentAfterOptionalIsland(args, 1, "default");
            routingController.setWarpPublicAccess(player, islandId, warpName, false);
            return;
        }
        if (args[0].equalsIgnoreCase("warp") || args[0].equals("워프")) {
            player.sendActionBar(Component.text("섬 워프로 이동하는 중입니다."));
            UUID targetIslandId = routeTargetIslandId(args, 1);
            String warpName = routeWarpName(args, 1, "default");
            routingController.routeWarp(player, targetIslandId, warpName);
            return;
        }
        if (args[0].equalsIgnoreCase("invite") || args[0].equals("초대")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            routingController.inviteTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""));
            return;
        }
        if (args[0].equalsIgnoreCase("invites") || args[0].equals("초대목록")) {
            routingController.listInvites(player);
            return;
        }
        if (args[0].equalsIgnoreCase("accept") || args[0].equals("수락") || args[0].equals("초대수락")) {
            routingController.acceptInviteTarget(player, args.length > 1 ? args[1] : "");
            return;
        }
        if (args[0].equalsIgnoreCase("decline") || args[0].equals("거절") || args[0].equals("초대거절")) {
            routingController.declineInviteTarget(player, args.length > 1 ? args[1] : "");
            return;
        }
        if (args[0].equalsIgnoreCase("members") || args[0].equals("멤버")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listMembers(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("kick") || args[0].equals("추방")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            routingController.kickMemberTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""));
            return;
        }
        if (args[0].equalsIgnoreCase("promote") || args[0].equals("승급")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            routingController.setRoleTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""), IslandRole.MODERATOR);
            return;
        }
        if (args[0].equalsIgnoreCase("demote") || args[0].equals("강등")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            routingController.setRoleTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""), IslandRole.MEMBER);
            return;
        }
        if (args[0].equalsIgnoreCase("transfer") || args[0].equals("양도")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            routingController.transferOwnershipTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""));
            return;
        }
        if (args[0].equalsIgnoreCase("trust") || args[0].equals("신뢰")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            routingController.setRoleTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""), IslandRole.TRUSTED);
            return;
        }
        if (args[0].equalsIgnoreCase("untrust") || args[0].equals("신뢰해제")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            routingController.setRoleTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""), IslandRole.MEMBER);
            return;
        }
        if (args[0].equalsIgnoreCase("ban") || args[0].equals("밴")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            String target = argumentAfterOptionalIsland(args, 1, "");
            int reasonIndex = indexAfterOptionalIslandValue(args, 1);
            String reason = args.length > reasonIndex ? joinArgs(args, reasonIndex) : "island ban";
            routingController.banVisitorTarget(player, islandId, target, reason);
            return;
        }
        if (args[0].equalsIgnoreCase("unban") || args[0].equals("밴해제")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            routingController.pardonVisitorTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""));
            return;
        }
        if (args[0].equalsIgnoreCase("kickvisitor") || args[0].equals("방문자추방")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            routingController.kickVisitorTarget(player, islandId, argumentAfterOptionalIsland(args, 1, ""));
            return;
        }
        if (args[0].equalsIgnoreCase("banlist") || args[0].equals("밴목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listBans(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("public") || args[0].equals("공개")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setPublicAccess(player, islandId, true);
            return;
        }
        if (args[0].equalsIgnoreCase("private") || args[0].equals("비공개")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setPublicAccess(player, islandId, false);
            return;
        }
        if (args[0].equalsIgnoreCase("lock") || args[0].equals("잠금")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setLocked(player, islandId, true);
            return;
        }
        if (args[0].equalsIgnoreCase("unlock") || args[0].equals("잠금해제")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setLocked(player, islandId, false);
            return;
        }
        if (args[0].equalsIgnoreCase("fly") || args[0].equals("비행")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            boolean enabled = parseToggle(args, 2, true);
            routingController.setFlyFlag(player, islandId, enabled);
            return;
        }
        if (args[0].equalsIgnoreCase("keepinventory") || args[0].equalsIgnoreCase("keepinv") || args[0].equals("인벤보존")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setBooleanFlag(player, islandId, kr.lunaf.cloudislands.api.model.IslandFlag.KEEP_INVENTORY, parseToggle(args, 2, true), "인벤토리 보존");
            return;
        }
        if (args[0].equalsIgnoreCase("pvp") || args[0].equals("피빕")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setBooleanFlag(player, islandId, kr.lunaf.cloudislands.api.model.IslandFlag.PVP, parseToggle(args, 2, true), "PVP");
            return;
        }
        if (args[0].equalsIgnoreCase("publicwarps") || args[0].equals("공개워프")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setBooleanFlag(player, islandId, kr.lunaf.cloudislands.api.model.IslandFlag.PUBLIC_WARPS, parseToggle(args, 2, true), "공개 워프");
            return;
        }
        if (args[0].equalsIgnoreCase("flags") || args[0].equals("플래그")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listFlags(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("permissions") || args[0].equals("권한")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listPermissions(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("setpermission") || args[0].equals("권한설정")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            IslandRole role = args.length > 2 ? parseRole(args[2]) : IslandRole.MEMBER;
            IslandPermission permission = args.length > 3 ? parsePermission(args[3]) : IslandPermission.BUILD;
            boolean allowed = args.length > 4 && Boolean.parseBoolean(args[4]);
            routingController.setPermission(player, islandId, role, permission, allowed);
            return;
        }
        if (args[0].equalsIgnoreCase("logs") || args[0].equals("로그")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listIslandLogs(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("bank") || args[0].equals("은행")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.showBank(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("deposit") || args[0].equals("입금")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            String amount = argumentAfterOptionalIsland(args, 1, "0");
            routingController.depositBank(player, islandId, amount);
            return;
        }
        if (args[0].equalsIgnoreCase("withdraw") || args[0].equals("출금")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            String amount = argumentAfterOptionalIsland(args, 1, "0");
            routingController.withdrawBank(player, islandId, amount);
            return;
        }
        if (args[0].equalsIgnoreCase("worthrank") || args[0].equalsIgnoreCase("valuerank") || args[0].equals("가치랭킹") || (args.length > 1 && (args[0].equalsIgnoreCase("rank") || args[0].equalsIgnoreCase("ranking") || args[0].equals("랭킹")) && (args[1].equalsIgnoreCase("worth") || args[1].equalsIgnoreCase("value") || args[1].equals("가치")))) {
            int limit = args[0].equalsIgnoreCase("rank") || args[0].equalsIgnoreCase("ranking") || args[0].equals("랭킹")
                ? (args.length > 2 ? (int) parseLongOrZero(args[2]) : 10)
                : (args.length > 1 ? (int) parseLongOrZero(args[1]) : 10);
            routingController.showWorthRanking(player, limit);
            return;
        }
        if (args[0].equalsIgnoreCase("rank") || args[0].equals("ranking") || args[0].equals("랭킹")) {
            routingController.showLevelRanking(player, args.length > 1 ? (int) parseLongOrZero(args[1]) : 10);
            return;
        }
        if (args[0].equalsIgnoreCase("levelcalc") || args[0].equals("레벨계산")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.recalculateLevel(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("upgrade") || args[0].equals("업그레이드")) {
            routingController.listUpgradeRules(player);
            return;
        }
        if (args[0].equalsIgnoreCase("upgrades") || args[0].equals("업그레이드목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listUpgrades(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("buyupgrade") || args[0].equals("업그레이드구매")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            String upgradeKey = argumentAfterOptionalIsland(args, 1, "size");
            routingController.purchaseUpgrade(player, islandId, upgradeKey);
            return;
        }
        if (args[0].equalsIgnoreCase("mission") || args[0].equals("missions") || args[0].equals("미션")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            if (args.length > 2) {
                routingController.completeMission(player, islandId, args[2]);
            } else {
                routingController.listMissions(player, islandId);
            }
            return;
        }
        if (args[0].equalsIgnoreCase("challenge") || args[0].equals("challenges") || args[0].equals("챌린지")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            if (args.length > 2) {
                routingController.completeChallenge(player, islandId, args[2]);
            } else {
                routingController.listChallenges(player, islandId);
            }
            return;
        }
        if (args[0].equalsIgnoreCase("chat") || args[0].equals("채팅")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            routingController.sendIslandChat(player, islandId, "ISLAND", joinArgs(args, hasOptionalIslandIdArgument(args, 1) ? 2 : 1));
            return;
        }
        if (args[0].equalsIgnoreCase("teamchat") || args[0].equals("팀채팅")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            routingController.sendIslandChat(player, islandId, "TEAM", joinArgs(args, hasOptionalIslandIdArgument(args, 1) ? 2 : 1));
            return;
        }
        if (args[0].equalsIgnoreCase("limits") || args[0].equals("제한")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            if (args.length > 3) {
                routingController.setLimit(player, islandId, args[2], parseLongOrZero(args[3]));
            } else {
                routingController.listLimits(player, islandId);
            }
            return;
        }
        if (args[0].equalsIgnoreCase("hoppers") || args[0].equals("호퍼")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setLimit(player, islandId, "HOPPER", args.length > 2 ? parseLongOrZero(args[2]) : 0L);
            return;
        }
        if (args[0].equalsIgnoreCase("spawners") || args[0].equals("스포너")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setLimit(player, islandId, "SPAWNER", args.length > 2 ? parseLongOrZero(args[2]) : 0L);
            return;
        }
        if (args[0].equalsIgnoreCase("entities") || args[0].equals("엔티티")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setLimit(player, islandId, "ENTITY", args.length > 2 ? parseLongOrZero(args[2]) : 0L);
            return;
        }
        if (args[0].equalsIgnoreCase("redstone") || args[0].equals("레드스톤")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setLimit(player, islandId, "REDSTONE", args.length > 2 ? parseLongOrZero(args[2]) : 0L);
            return;
        }
        if (args[0].equalsIgnoreCase("snapshots") || args[0].equals("스냅샷목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listSnapshots(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("snapshot") || args[0].equals("스냅샷")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String reason = args.length > 2 ? args[2] : "MANUAL";
            routingController.snapshot(player, islandId, reason);
            return;
        }
        if (args[0].equalsIgnoreCase("restore") || args[0].equals("복원")) {
            UUID islandId = args.length > 2 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            long snapshotNo = args.length > 2 ? parseLongOrZero(args[2]) : (args.length > 1 ? parseLongOrZero(args[1]) : 0L);
            routingController.restore(player, islandId, snapshotNo);
            return;
        }
        if (args[0].equalsIgnoreCase("create") || args[0].equals("생성")) {
            String templateId = args.length > 1 ? args[1] : "default";
            player.sendActionBar(Component.text("섬 생성 요청을 접수했습니다."));
            routingController.createIsland(player, templateId);
            return;
        }
        if (args[0].equalsIgnoreCase("reset") || args[0].equals("리셋")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String reason = args.length > 2 ? joinArgs(args, 2) : "PLAYER_RESET";
            routingController.resetIsland(player, islandId, reason);
            return;
        }
        if (args[0].equalsIgnoreCase("delete") || args[0].equals("삭제")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.deleteIsland(player, islandId);
            return;
        }
        sendCommandList(player, "섬 명령어 목록", IslandCommandCatalog.playerCommands(), 1, "섬 command list");
    }

    private void sendCommandList(Player player, String title, List<String> commands, int page, String nextCommand) {
        int pageSize = 12;
        int maxPage = Math.max(1, (commands.size() + pageSize - 1) / pageSize);
        int safePage = Math.max(1, Math.min(page, maxPage));
        int from = (safePage - 1) * pageSize;
        int to = Math.min(commands.size(), from + pageSize);
        player.sendMessage(Component.text(title + " " + safePage + "/" + maxPage + " - 1 line > 1 command"));
        for (String command : commands.subList(from, to)) {
            player.sendMessage(Component.text("> /" + command));
        }
        if (safePage < maxPage) {
            player.sendMessage(Component.text("> /" + nextCommand + " " + (safePage + 1)));
        }
    }

    private boolean isCommandListRequest(String[] args) {
        if (args.length == 0) {
            return false;
        }
        String first = args[0].toLowerCase(java.util.Locale.ROOT);
        if (first.equals("help") || first.equals("도움말") || first.equals("commands") || first.equals("command-list") || first.equals("명령어") || first.equals("명령어목록")) {
            return true;
        }
        return first.equals("command") && args.length > 1 && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"));
    }

    private int commandListPage(String[] args) {
        if (args.length > 2 && args[0].equalsIgnoreCase("command") && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"))) {
            return (int) parseLongOrZero(args[2]);
        }
        if (args.length > 1) {
            return (int) parseLongOrZero(args[1]);
        }
        return 1;
    }

    private UUID parseUuidOrNil(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return new UUID(0L, 0L);
        }
    }

    private boolean hasOptionalIslandIdArgument(String[] args, int index) {
        return args.length > index + 1 && isUuid(args[index]);
    }

    private UUID optionalIslandIdArgument(String[] args, int index) {
        return hasOptionalIslandIdArgument(args, index) ? parseUuidOrNil(args[index]) : new UUID(0L, 0L);
    }

    private String argumentAfterOptionalIsland(String[] args, int index, String fallback) {
        if (hasOptionalIslandIdArgument(args, index)) {
            return args.length > index + 1 ? args[index + 1] : fallback;
        }
        return args.length > index ? args[index] : fallback;
    }

    private int indexAfterOptionalIslandValue(String[] args, int index) {
        return hasOptionalIslandIdArgument(args, index) ? index + 2 : index + 1;
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private UUID parsePlayerUuidOrNil(String value) {
        return proxy.getPlayer(value).map(Player::getUniqueId).orElseGet(() -> parseUuidOrNil(value));
    }

    private long parseLongOrZero(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private UUID routeTargetIslandId(String[] args, int index) {
        if (args.length > index && isUuid(args[index])) {
            return parseUuidOrNil(args[index]);
        }
        return new UUID(0L, 0L);
    }

    private String routeWarpName(String[] args, int index, String fallback) {
        if (args.length > index && isUuid(args[index])) {
            return args.length > index + 1 ? args[index + 1] : fallback;
        }
        return args.length > index ? args[index] : fallback;
    }

    private boolean parseToggle(String[] args, int index, boolean fallback) {
        if (args.length <= index) {
            return fallback;
        }
        String value = args[index];
        return value.equalsIgnoreCase("on")
            || value.equalsIgnoreCase("true")
            || value.equalsIgnoreCase("yes")
            || value.equals("켜기");
    }

    private List<String> suggestions(List<String> catalog, String root, String[] args) {
        String typed = String.join(" ", args).toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String command : catalog) {
            String suffix = command.equals(root) ? "" : command.startsWith(root + " ") ? command.substring(root.length() + 1) : command;
            if (suffix.isBlank()) {
                continue;
            }
            if (!typed.isBlank() && !suffix.toLowerCase(Locale.ROOT).startsWith(typed)) {
                continue;
            }
            String[] parts = suffix.split(" ");
            int index = Math.max(0, args.length - 1);
            if (index < parts.length && !matches.contains(parts[index])) {
                matches.add(parts[index]);
            }
        }
        return matches;
    }

    private List<String> adminSuggestions(String[] args) {
        List<String> matches = suggestions(IslandCommandCatalog.adminCommands(), "ciadmin", args);
        if (args.length == 3 && args[0].equalsIgnoreCase("player")) {
            addOnlinePlayerSuggestions(matches, args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("islands")) {
            addLiteralSuggestions(matches, args[3], List.of("25", "50", "100"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("node") && (args[1].equalsIgnoreCase("kickall") || args[1].equalsIgnoreCase("shutdown-safe"))) {
            addLiteralSuggestions(matches, args[3], List.of("maintenance", "restart", "drain"));
        }
        if (args.length == 5 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("upsert")) {
            addLiteralSuggestions(matches, args[4], List.of("true", "false"));
        }
        if (args.length == 6 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("upsert")) {
            addLiteralSuggestions(matches, args[5], List.of("1.0.0", "1.21.0", "1.21.4"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("set")) {
            addLiteralSuggestions(matches, args[2], List.of("minecraft:stone", "minecraft:diamond_block", "minecraft:emerald_block", "minecraft:spawner"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("set")) {
            addLiteralSuggestions(matches, args[3], List.of("1.0", "10.0", "100.0"));
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("set")) {
            addLiteralSuggestions(matches, args[4], List.of("1", "10", "100"));
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("block-values") && args[1].equalsIgnoreCase("set")) {
            addLiteralSuggestions(matches, args[5], List.of("0", "64", "256"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("recover")) {
            addLiteralSuggestions(matches, args[2], List.of("recovery"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("recover")) {
            addLiteralSuggestions(matches, args[3], List.of("60000", "300000", "600000"));
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("recover")) {
            addLiteralSuggestions(matches, args[4], List.of("16", "32", "64"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("migrate-superiorskyblock2")) {
            addLiteralSuggestions(matches, args[2], List.of("plugins/SuperiorSkyblock2"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rankings")) {
            addLiteralSuggestions(matches, args[1], List.of("level", "worth"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("rankings")) {
            addLiteralSuggestions(matches, args[2], List.of("10", "25", "50", "100"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("debug")) {
            if ("all".startsWith(args[2].toLowerCase(Locale.ROOT))) {
                matches.add("all");
            }
            addOnlinePlayerSuggestions(matches, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("ticket")) {
            addOnlinePlayerSuggestions(matches, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("clear")) {
            addOnlinePlayerSuggestions(matches, args[2]);
        }
        return matches;
    }

    private boolean hasAdminAccess(com.velocitypowered.api.command.CommandSource source, String[] args) {
        if (source.hasPermission("cloudislands.admin")) {
            return true;
        }
        String permission = adminPermission(args);
        return !permission.isBlank() && source.hasPermission(permission);
    }

    private String adminPermission(String[] args) {
        if (args.length == 0) {
            return "cloudislands.admin.status";
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if (root.equals("help") || root.equals("commands") || root.equals("command") || root.equals("command-list") || root.equals("명령어") || root.equals("명령어목록")) {
            return "cloudislands.admin.status";
        }
        if (root.equals("template")) {
            root = "templates";
        }
        return switch (root) {
            case "status", "cache", "node", "island", "player", "jobs", "route", "rankings", "events", "audit", "metrics", "storage", "block-values", "upgrade-rules", "templates", "migrate-superiorskyblock2", "reload" -> "cloudislands.admin." + root;
            default -> "";
        };
    }

    private void addLiteralSuggestions(List<String> matches, String typed, List<String> values) {
        String normalized = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if ((normalized.isBlank() || value.toLowerCase(Locale.ROOT).startsWith(normalized)) && !matches.contains(value)) {
                matches.add(value);
            }
        }
    }

    private void addOnlinePlayerSuggestions(List<String> matches, String typed) {
        String normalized = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        for (Player online : proxy.getAllPlayers()) {
            String username = online.getUsername();
            if ((normalized.isBlank() || username.toLowerCase(Locale.ROOT).startsWith(normalized)) && !matches.contains(username)) {
                matches.add(username);
            }
        }
    }

    private String joinArgs(String[] args, int start) {
        if (args.length <= start) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (index > start) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private IslandRole parseRole(String value) {
        try {
            return IslandRole.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return IslandRole.MEMBER;
        }
    }

    private IslandPermission parsePermission(String value) {
        try {
            return IslandPermission.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return IslandPermission.BUILD;
        }
    }
}
