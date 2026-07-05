package kr.lunaf.cloudislands.velocity.command;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.protocol.command.IslandPlayerCommandRegistry;

public final class IslandCommandCatalog {
    private IslandCommandCatalog() {}

    public static List<String> playerCommands() {
        return IslandPlayerCommandRegistry.playerCommands();
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
