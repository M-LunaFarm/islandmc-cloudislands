package kr.lunaf.cloudislands.api.addon;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Platform-neutral contract for addon-owned subcommands under the player island command. */
public interface AddonIslandCommand {
    String addonId();

    List<String> aliases();

    default String permission() {
        return "";
    }

    default String usage() {
        return "";
    }

    default String description() {
        return "Addon island command";
    }

    default int minimumArguments() {
        return 0;
    }

    default int maximumArguments() {
        return Integer.MAX_VALUE;
    }

    CompletableFuture<AddonIslandCommandResult> execute(AddonIslandCommandContext context);

    default CompletableFuture<List<String>> tabComplete(AddonIslandCommandContext context) {
        return CompletableFuture.completedFuture(List.of());
    }
}
