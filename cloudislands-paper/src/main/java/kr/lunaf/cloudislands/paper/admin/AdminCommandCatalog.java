package kr.lunaf.cloudislands.paper.admin;

import java.util.List;
import kr.lunaf.cloudislands.common.feature.SuperiorSkyblockReplacementFeaturePolicy;

final class AdminCommandCatalog {
    static final List<String> ROOT_COMMANDS = List.of("help", "commands", "command", "command-list", "명령어", "명령어목록", "status", "dashboard", "doctor", "config", "cache", "addons", "integrations", "node", "island", "player", "jobs", "route", "rankings", "events", "audit", "metrics", "storage", "diagnostics", "block-values", "upgrade-rules", "template", "templates", "migrate-superiorskyblock2", "reload");
    static final List<String> CONFIG_COMMANDS = List.of("show", "validate", "diff", "reload", "effective", "sources");
    static final List<String> CACHE_COMMANDS = List.of("clear");
    static final List<String> ADDON_COMMANDS = List.of("list", "info", "feature", "enable", "disable", "reload", "state", "state-summary", "endpoints");
    static final List<String> ADDON_FEATURES = List.of("commands", "machines", "storage", "factories", "generators", "upgrades", "missions", "menus", "gui", "lifecycle", "resource-nodes", "market", "contracts", "research", "maintenance", "placeholders", "migration", "addon-state", "route-events");
    static final List<String> NODE_COMMANDS = List.of("menu", "list", "info", "islands", "drain", "undrain", "sweep", "kickall", "shutdown-safe");
    static final List<String> ISLAND_COMMANDS = List.of("info", "where", "visitor-stats", "visitors", "tp", "activate", "deactivate", "migrate", "save", "snapshot", "snapshots", "restore", "rollback", "quarantine", "recover", "repair", "delete");
    static final List<String> PLAYER_COMMANDS = List.of("info", "setisland", "clearisland");
    static final List<String> JOB_COMMANDS = List.of("list", "retry", "cancel", "recover");
    static final List<String> ROUTE_COMMANDS = List.of("debug", "ticket", "tickets", "clear");
    static final List<String> STORAGE_COMMANDS = List.of("status", "verify");
    static final List<String> DIAGNOSTICS_COMMANDS = List.of("export");
    static final List<String> RANKING_COMMANDS = List.of("level", "worth");
    static final List<String> BLOCK_VALUE_COMMANDS = List.of("list", "search", "set");
    static final List<String> BLOCK_VALUE_MATERIALS = List.of("minecraft:stone", "minecraft:diamond_block", "minecraft:emerald_block", "minecraft:spawner");
    static final List<String> TEMPLATE_COMMANDS = List.of("list", "import", "upsert", "enable", "disable", "preview", "validate");
    static final List<String> MIGRATION_COMMANDS = List.of("scan", "status", "dryrun", "dry-run", "extract", "extract-worlds", "world-extract", "import", "verify", "verify-no-legacy-provider", "rollback");
    static final List<String> FORBIDDEN_LEGACY_SKYBLOCK_PROVIDERS = SuperiorSkyblockReplacementFeaturePolicy.forbiddenRuntimeProviders();
    static final List<String> NODE_DANGER_REASONS = List.of("maintenance", "restart", "drain");
    static final List<String> HELP_COMMANDS = List.of(
        "ciadmin status",
        "ciadmin dashboard",
        "ciadmin doctor",
        "ciadmin config",
        "ciadmin config validate",
        "ciadmin config diff",
        "ciadmin config reload",
        "ciadmin config effective",
        "ciadmin config sources",
        "ciadmin help [page]",
        "ciadmin command list [page]",
        "ciadmin cache clear",
        "ciadmin addons list",
        "ciadmin addons info <addonId>",
        "ciadmin addons feature <addonId> <feature>",
        "ciadmin addons feature <addonId> <feature> <true|false>",
        "ciadmin addons enable <addonId>",
        "ciadmin addons disable <addonId>",
        "ciadmin addons reload [addonId]",
        "ciadmin addons state",
        "ciadmin addons state-summary",
        "ciadmin addons endpoints",
        "ciadmin integrations",
        "ciadmin node menu",
        "ciadmin node list",
        "ciadmin node info <node>",
        "ciadmin node islands <node> [limit]",
        "ciadmin node drain <node>",
        "ciadmin node undrain <node>",
        "ciadmin node sweep [node]",
        "ciadmin node kickall <node> [reason]",
        "ciadmin node shutdown-safe <node> [reason]",
        "ciadmin island info <island>",
        "ciadmin island where <player|island>",
        "ciadmin island visitor-stats <island>",
        "ciadmin island tp <island>",
        "ciadmin island activate <island>",
        "ciadmin island deactivate <island>",
        "ciadmin island migrate <island> <node>",
        "ciadmin island save <island>",
        "ciadmin island snapshot <island> [reason]",
        "ciadmin island snapshots <island>",
        "ciadmin island restore <island> <snapshot>",
        "ciadmin island rollback <island> <snapshot>",
        "ciadmin island quarantine <island> [reason]",
        "ciadmin island recover <island> [reason]",
        "ciadmin island repair <island> [reason]",
        "ciadmin island delete <island>",
        "ciadmin player info <player>",
        "ciadmin player setisland <player> <islandUuid>",
        "ciadmin player clearisland <player>",
        "ciadmin jobs list",
        "ciadmin jobs retry <jobId>",
        "ciadmin jobs cancel <jobId>",
        "ciadmin jobs recover [nodeId] [minIdleMillis] [maxJobs]",
        "ciadmin route debug [all|player]",
        "ciadmin route ticket <ticket|player>",
        "ciadmin route tickets <player>",
        "ciadmin route clear <player> [ticket]",
        "ciadmin rankings level [limit]",
        "ciadmin rankings worth [limit]",
        "ciadmin events",
        "ciadmin audit",
        "ciadmin metrics",
        "ciadmin storage",
        "ciadmin storage verify <island>",
        "ciadmin diagnostics export",
        "ciadmin block-values list",
        "ciadmin block-values search <query> [limit]",
        "ciadmin block-values set <materialKey> <worth> <levelPoints> <limit>",
        "ciadmin upgrade-rules",
        "ciadmin template list",
        "ciadmin template import <name>",
        "ciadmin template upsert <id> <name> [enabled|disabled] [minNodeVersion]",
        "ciadmin template enable <id>",
        "ciadmin template disable <id>",
        "ciadmin template preview <id>",
        "ciadmin template validate <id>",
        "ciadmin templates list",
        "ciadmin templates import <name>",
        "ciadmin templates upsert <id> <name> [enabled|disabled] [minNodeVersion]",
        "ciadmin templates enable <id>",
        "ciadmin templates disable <id>",
        "ciadmin templates preview <id>",
        "ciadmin templates validate <id>",
        "ciadmin migrate-superiorskyblock2 scan [path]",
        "ciadmin migrate-superiorskyblock2 status",
        "ciadmin migrate-superiorskyblock2 dryrun [path]",
        "ciadmin migrate-superiorskyblock2 dry-run [path]",
        "ciadmin migrate-superiorskyblock2 extract [outputPath]",
        "ciadmin migrate-superiorskyblock2 import <approvalToken>",
        "ciadmin migrate-superiorskyblock2 verify [path]",
        "ciadmin migrate-superiorskyblock2 verify-no-legacy-provider",
        "ciadmin migrate-superiorskyblock2 rollback",
        "ciadmin reload"
    );
    static final List<String> MIGRATION_HELP_COMMANDS = List.of(
        "ciadmin migrate-superiorskyblock2 scan [path]",
        "ciadmin migrate-superiorskyblock2 status",
        "ciadmin migrate-superiorskyblock2 dryrun [path]",
        "ciadmin migrate-superiorskyblock2 dry-run [path]",
        "ciadmin migrate-superiorskyblock2 extract [outputPath]",
        "ciadmin migrate-superiorskyblock2 import <approvalToken>",
        "ciadmin migrate-superiorskyblock2 verify [path]",
        "ciadmin migrate-superiorskyblock2 verify-no-legacy-provider",
        "ciadmin migrate-superiorskyblock2 rollback"
    );

    private AdminCommandCatalog() {
    }
}
