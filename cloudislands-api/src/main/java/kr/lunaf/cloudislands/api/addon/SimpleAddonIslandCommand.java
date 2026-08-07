package kr.lunaf.cloudislands.api.addon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Small command builder for addons that only need an executor and tab suggestions.
 * The command works on both island nodes and CloudIslands {@code LOBBY} Paper nodes.
 */
public final class SimpleAddonIslandCommand implements AddonIslandCommand {
    private final String addonId;
    private final List<String> aliases;
    private final String permission;
    private final String usage;
    private final String description;
    private final int minimumArguments;
    private final int maximumArguments;
    private final Function<AddonIslandCommandContext, CompletableFuture<AddonIslandCommandResult>> executor;
    private final Function<AddonIslandCommandContext, CompletableFuture<List<String>>> completer;

    private SimpleAddonIslandCommand(Builder builder) {
        addonId = builder.addonId;
        aliases = List.copyOf(builder.aliases);
        permission = builder.permission;
        usage = builder.usage;
        description = builder.description;
        minimumArguments = builder.minimumArguments;
        maximumArguments = builder.maximumArguments;
        executor = builder.executor;
        completer = builder.completer;
    }

    public static Builder builder(String addonId, String primaryAlias) {
        return new Builder(addonId, primaryAlias);
    }

    @Override public String addonId() { return addonId; }
    @Override public List<String> aliases() { return aliases; }
    @Override public String permission() { return permission; }
    @Override public String usage() { return usage; }
    @Override public String description() { return description; }
    @Override public int minimumArguments() { return minimumArguments; }
    @Override public int maximumArguments() { return maximumArguments; }
    @Override public CompletableFuture<AddonIslandCommandResult> execute(AddonIslandCommandContext context) { return executor.apply(context); }
    @Override public CompletableFuture<List<String>> tabComplete(AddonIslandCommandContext context) { return completer.apply(context); }

    public static final class Builder {
        private final String addonId;
        private final List<String> aliases = new ArrayList<>();
        private String permission = "";
        private String usage = "";
        private String description = "Addon island command";
        private int minimumArguments;
        private int maximumArguments = Integer.MAX_VALUE;
        private Function<AddonIslandCommandContext, CompletableFuture<AddonIslandCommandResult>> executor =
            _context -> CompletableFuture.completedFuture(AddonIslandCommandResult.rejected("Command executor is not configured"));
        private Function<AddonIslandCommandContext, CompletableFuture<List<String>>> completer =
            _context -> CompletableFuture.completedFuture(List.of());

        private Builder(String addonId, String primaryAlias) {
            this.addonId = required(addonId, "addonId");
            aliases.add(required(primaryAlias, "primaryAlias"));
        }

        public Builder aliases(String... additionalAliases) {
            if (additionalAliases != null) {
                for (String alias : additionalAliases) {
                    if (alias != null && !alias.isBlank()) {
                        aliases.add(alias.trim());
                    }
                }
            }
            return this;
        }

        public Builder permission(String value) { permission = text(value); return this; }
        public Builder usage(String value) { usage = text(value); return this; }
        public Builder description(String value) { description = text(value); return this; }

        public Builder arguments(int minimum, int maximum) {
            if (minimum < 0 || maximum < minimum) {
                throw new IllegalArgumentException("invalid command argument range");
            }
            minimumArguments = minimum;
            maximumArguments = maximum;
            return this;
        }

        public Builder executor(Function<AddonIslandCommandContext, AddonIslandCommandResult> value) {
            Objects.requireNonNull(value, "executor");
            executor = context -> CompletableFuture.completedFuture(value.apply(context));
            return this;
        }

        public Builder asyncExecutor(Function<AddonIslandCommandContext, CompletableFuture<AddonIslandCommandResult>> value) {
            executor = Objects.requireNonNull(value, "executor");
            return this;
        }

        public Builder suggestions(Function<AddonIslandCommandContext, List<String>> value) {
            Objects.requireNonNull(value, "suggestions");
            completer = context -> CompletableFuture.completedFuture(value.apply(context));
            return this;
        }

        public Builder asyncSuggestions(Function<AddonIslandCommandContext, CompletableFuture<List<String>>> value) {
            completer = Objects.requireNonNull(value, "suggestions");
            return this;
        }

        public SimpleAddonIslandCommand build() {
            return new SimpleAddonIslandCommand(this);
        }

        private static String required(String value, String field) {
            String text = text(value);
            if (text.isBlank()) {
                throw new IllegalArgumentException(field + " is required");
            }
            return text;
        }

        private static String text(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
