package kr.lunaf.cloudislands.exampleaddon;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.addon.AddonIslandCommand;
import kr.lunaf.cloudislands.api.addon.AddonIslandCommandContext;
import kr.lunaf.cloudislands.api.addon.AddonIslandCommandResult;

final class ExampleAddonIslandSubcommand implements AddonIslandCommand {
    @Override
    public String addonId() {
        return ExampleCloudIslandsAddonDefinition.ADDON_ID;
    }

    @Override
    public List<String> aliases() {
        return List.of("example", "example-addon");
    }

    @Override
    public String permission() {
        return "cloudislands.example.use";
    }

    @Override
    public String usage() {
        return "[status|events]";
    }

    @Override
    public String description() {
        return "Shows the example addon's CloudIslands integration status.";
    }

    @Override
    public int maximumArguments() {
        return 1;
    }

    @Override
    public CompletableFuture<AddonIslandCommandResult> execute(AddonIslandCommandContext context) {
        String mode = context.arguments().isEmpty() ? "status" : context.arguments().get(0).toLowerCase(java.util.Locale.ROOT);
        return CompletableFuture.completedFuture(AddonIslandCommandResult.message("Example addon " + mode + " is available for " + context.playerUuid()));
    }

    @Override
    public CompletableFuture<List<String>> tabComplete(AddonIslandCommandContext context) {
        return CompletableFuture.completedFuture(List.of("status", "events"));
    }
}
