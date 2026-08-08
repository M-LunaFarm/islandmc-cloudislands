package kr.lunaf.cloudislands.velocity.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.lunaf.cloudislands.protocol.command.CommandHelpCategory;
import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
import kr.lunaf.cloudislands.protocol.command.IslandPlayerCommandRegistry;

public final class IslandCommandCatalog {
    private IslandCommandCatalog() {}

    public static List<String> playerCommands() {
        return IslandPlayerCommandRegistry.playerCommands();
    }

    /**
     * Player-facing help intentionally contains one preferred spelling per action.
     * Execution aliases stay in the shared descriptor registry, but must not flood
     * chat help and the first tab-completion response.
     */
    public static List<String> playerDisplayCommands() {
        Map<String, String> commands = new LinkedHashMap<>();
        for (CommandHelpCategory category : playerHelpCategories()) {
            for (String command : category.commands()) {
                String oneLine = CommandListPolicy.oneLine(command);
                commands.putIfAbsent(oneLine.toLowerCase(Locale.ROOT), oneLine);
            }
        }
        return List.copyOf(commands.values());
    }

    public static List<CommandHelpCategory> playerHelpCategories() {
        return IslandPlayerCommandRegistry.helpCategories();
    }

    public static CommandHelpCategory playerHelpCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String requested = value.trim().toLowerCase(Locale.ROOT);
        for (CommandHelpCategory category : playerHelpCategories()) {
            if (category.aliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(requested))) {
                return category;
            }
        }
        return null;
    }

    public static List<String> playerPrimaryRoots(String language) {
        if (language != null && language.toLowerCase(Locale.ROOT).startsWith("en")) {
            return List.of(
                "menu", "create", "home", "info", "list", "members", "invite", "visit", "warp",
                "ranking", "level", "worth", "bank", "upgrade", "mission", "settings", "fly", "permissions", "help"
            );
        }
        return List.of(
            "메뉴", "생성", "홈", "정보", "목록", "멤버", "초대", "방문", "워프",
            "랭킹", "레벨", "가치", "은행", "업그레이드", "미션", "설정", "비행", "권한", "도움말"
        );
    }

    public static List<String> playerAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        IslandPlayerCommandRegistry.playerDescriptors().stream()
            .flatMap(descriptor -> descriptor.aliases().stream())
            .filter(alias -> !alias.isBlank())
            .forEach(alias -> aliases.putIfAbsent(alias.toLowerCase(Locale.ROOT), alias));
        return List.copyOf(aliases.values());
    }

    public static List<String> adminCommands() {
        return adminCommands(true);
    }

    public static List<String> adminCommands(boolean includeMigration) {
        List<String> commands = new ArrayList<>(List.of(
            "ciadmin",
            "섬관리",
            "ciadmin status",
            "ciadmin dashboard",
            "ciadmin doctor",
            "ciadmin setup start",
            "ciadmin setup core",
            "ciadmin setup redis",
            "ciadmin setup database",
            "ciadmin setup storage",
            "ciadmin setup velocity",
            "ciadmin setup paper-node",
            "ciadmin setup verify",
            "ciadmin config",
            "ciadmin help [page]",
            "ciadmin command list [page]",
            "ciadmin island info <island|player>",
            "ciadmin island where <island>",
            "ciadmin island reviews [limit]",
            "ciadmin island moderate-review <island> <reviewer> <VISIBLE|REPORTED|HIDDEN> [note]",
            "ciadmin island tp <island>",
            "ciadmin island activate <island>",
            "ciadmin island deactivate <island>",
            "ciadmin island migrate <island> <node>",
            "ciadmin island save <island>",
            "ciadmin island snapshot <island> [reason]",
            "ciadmin island snapshots <island>",
            "ciadmin island rollback <island> <snapshot> --confirm",
            "ciadmin island quarantine <island> [reason]",
            "ciadmin island repair <island> [reason]",
            "ciadmin island delete <island> --confirm",
            "ciadmin island restore <island> <snapshot> --confirm",
            "ciadmin player info <player>",
            "ciadmin player setisland <player> <islandUuid>",
            "ciadmin player clearisland <player>",
            "ciadmin node menu",
            "ciadmin node list",
            "ciadmin node info <node>",
            "ciadmin node islands <node> [limit]",
            "ciadmin node drain <node>",
            "ciadmin node undrain <node>",
            "ciadmin node kickall <node> [reason]",
            "ciadmin node sweep [node]",
            "ciadmin node shutdown-safe <node> [reason]",
            "ciadmin jobs list",
            "ciadmin jobs retry <jobId>",
            "ciadmin jobs cancel <jobId>",
            "ciadmin jobs recover [nodeId] [minIdleMillis] [maxJobs]",
            "ciadmin route debug [all|player]",
            "ciadmin route ticket <ticket|player>",
            "ciadmin route clear <player> [ticket]",
            "ciadmin cache clear",
            "ciadmin events",
            "ciadmin audit",
            "ciadmin metrics",
            "ciadmin storage",
            "ciadmin integrations",
            "ciadmin support-bundle create",
            "ciadmin addons state",
            "ciadmin addons state-summary",
            "ciadmin addons endpoints",
            "ciadmin addons list",
            "ciadmin rankings level [limit]",
            "ciadmin rankings worth [limit]",
            "ciadmin block-values list",
            "ciadmin block-values set <materialKey> <worth> <levelPoints> <limit>",
            "ciadmin block-values reload",
            "ciadmin upgrade-rules",
            "ciadmin setblockamount <island> <materialKey> <amount>",
            "ciadmin seteffect <island> <effectKey> <amplifier>",
            "ciadmin setcropgrowth <island> <percent>",
            "ciadmin setmobdrops <island> <percent>",
            "ciadmin setspawnerrates <island> <percent>",
            "ciadmin template list",
            "ciadmin template upsert <id> <name> [enabled|disabled] [minNodeVersion]",
            "ciadmin template seticon <name> <material>",
            "ciadmin template setcost <name> <amount>",
            "ciadmin template setpermission <name> <permission>",
            "ciadmin template enable <id>",
            "ciadmin template disable <id>",
            "ciadmin template verify-bundle <id>",
            "ciadmin template delete <id> --confirm",
            "ciadmin template reorder <id> <sortOrder>",
            "ciadmin templates list",
            "ciadmin templates upsert <id> <name> [enabled|disabled] [minNodeVersion]",
            "ciadmin templates seticon <name> <material>",
            "ciadmin templates setcost <name> <amount>",
            "ciadmin templates setpermission <name> <permission>",
            "ciadmin templates enable <id>",
            "ciadmin templates disable <id>",
            "ciadmin templates verify-bundle <id>",
            "ciadmin templates delete <id> --confirm",
            "ciadmin templates reorder <id> <sortOrder>",
            "ciadmin reload"
        ));
        if (!includeMigration) {
            return List.copyOf(commands);
        }
        commands.addAll(List.of(
            "ciadmin migrate-superiorskyblock2 scan [path]",
            "ciadmin migrate-superiorskyblock2 status",
            "ciadmin migrate-superiorskyblock2 dryrun [path]",
            "ciadmin migrate-superiorskyblock2 dry-run [path]",
            "ciadmin migrate-superiorskyblock2 report",
            "ciadmin migrate-superiorskyblock2 extract [outputPath]",
            "ciadmin migrate-superiorskyblock2 approve <approvalToken>",
            "ciadmin migrate-superiorskyblock2 import <approvalToken>",
            "ciadmin migrate-superiorskyblock2 verify [path]",
            "ciadmin migrate-superiorskyblock2 compare <island>",
            "ciadmin migrate-superiorskyblock2 rollback-plan",
            "ciadmin migrate-superiorskyblock2 rollback",
            "ciadmin migrate superiorskyblock2 scan",
            "ciadmin migrate superiorskyblock2 dry-run",
            "ciadmin migrate superiorskyblock2 status",
            "ciadmin migrate superiorskyblock2 approve <dryRunId>",
            "ciadmin migrate superiorskyblock2 import <approvalToken>",
            "ciadmin migrate superiorskyblock2 verify <batchId>",
            "ciadmin migrate superiorskyblock2 compare <batchId>",
            "ciadmin migrate superiorskyblock2 rollback-plan <batchId>",
            "ciadmin migrate superiorskyblock2 report <id>",
            "ciadmin migrate superiorskyblock2 unlock --confirm <token>"
        ));
        return List.copyOf(commands);
    }
}
