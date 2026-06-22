package kr.lunaf.cloudislands.paper.admin;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import org.bukkit.command.CommandSender;

final class AdminMigrationCommandHandler {
    private final CloudIslandsPaperAgent agent;
    private final CoreApiClient coreApiClient;
    private final boolean enabled;
    private final AdminMigrationMessageFormatter formatter;
    private final AdminMigrationMessageFormatter.AdminText text;
    private final CommandRunner runner;
    private final UsageSender usageSender;

    AdminMigrationCommandHandler(
        CloudIslandsPaperAgent agent,
        CoreApiClient coreApiClient,
        boolean enabled,
        AdminMigrationMessageFormatter.AdminText text,
        CommandRunner runner,
        UsageSender usageSender
    ) {
        this.agent = agent;
        this.coreApiClient = coreApiClient;
        this.enabled = enabled;
        this.text = text == null ? (_key, fallback) -> fallback : text;
        this.formatter = new AdminMigrationMessageFormatter(this.text);
        this.runner = runner;
        this.usageSender = usageSender;
    }

    boolean enabled() {
        return enabled;
    }

    boolean handle(CommandSender sender, String[] args) {
        if (!enabled) {
            sender.sendMessage(text.get("admin-command-migration-disabled", "SuperiorSkyblock2 migration is disabled by config."));
            return true;
        }
        String action = args.length > 1 ? args[1] : "scan";
        if (!AdminCommandCatalog.MIGRATION_COMMANDS.contains(action.toLowerCase(Locale.ROOT))) {
            usageSender.send(sender, List.of(
                "/ciadmin migrate-superiorskyblock2 scan [path]",
                "/ciadmin migrate-superiorskyblock2 status",
                "/ciadmin migrate-superiorskyblock2 dryrun [path]",
                "/ciadmin migrate-superiorskyblock2 extract [path]",
                "/ciadmin migrate-superiorskyblock2 import <approvalToken>",
                "/ciadmin migrate-superiorskyblock2 verify [path]",
                "/ciadmin migrate-superiorskyblock2 verify-no-legacy-provider",
                "/ciadmin migrate-superiorskyblock2 rollback"
            ));
            return true;
        }
        if (action.equalsIgnoreCase("verify-no-legacy-provider")) {
            sender.sendMessage(legacyProviderRuntimeMessage());
            return true;
        }
        if (action.equalsIgnoreCase("import") && args.length < 3) {
            sender.sendMessage(text.get("admin-command-migration-import-usage", "사용법: /ciadmin migrate-superiorskyblock2 import <approvalToken>"));
            return true;
        }
        String path = args.length > 2 ? joined(args, 2) : "plugins/SuperiorSkyblock2";
        runner.run(sender, "SuperiorSkyblock2 migration " + action, coreApiClient.migrations().migrateSuperiorSkyblock2(action, path).thenApply(formatter::format));
        return true;
    }

    private String legacyProviderRuntimeMessage() {
        List<String> loadedProviders = AdminCommandCatalog.FORBIDDEN_LEGACY_SKYBLOCK_PROVIDERS.stream()
            .map(provider -> {
                org.bukkit.plugin.Plugin plugin = agent.plugin().getServer().getPluginManager().getPlugin(provider);
                return plugin == null ? "" : provider + "(enabled=" + plugin.isEnabled() + ")";
            })
            .filter(value -> !value.isBlank())
            .toList();
        if (loadedProviders.isEmpty()) {
            return text.get("admin-command-migration-no-legacy-provider", "Legacy skyblock providers: none. Migration input only policy is clean for ")
                + String.join(",", AdminCommandCatalog.FORBIDDEN_LEGACY_SKYBLOCK_PROVIDERS);
        }
        return text.get("admin-command-migration-legacy-provider-detected", "Legacy skyblock providers detected: ")
            + String.join(",", loadedProviders)
            + text.get("admin-command-migration-legacy-provider-policy", ". CloudIslands must not use them as runtime island providers.");
    }

    private static String joined(String[] args, int start) {
        if (args == null || start >= args.length) {
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

    @FunctionalInterface
    interface CommandRunner {
        void run(CommandSender sender, String action, CompletableFuture<String> future);
    }

    @FunctionalInterface
    interface UsageSender {
        void send(CommandSender sender, List<String> commands);
    }
}
