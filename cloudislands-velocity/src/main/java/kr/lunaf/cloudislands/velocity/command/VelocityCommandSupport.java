package kr.lunaf.cloudislands.velocity.command;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.velocity.VelocityAdminActions;
import kr.lunaf.cloudislands.velocity.VelocityPlayerMembershipActions;
import kr.lunaf.cloudislands.velocity.VelocityPlayerProgressionActions;
import kr.lunaf.cloudislands.velocity.VelocityPlayerRoutingActions;
import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
import kr.lunaf.cloudislands.velocity.VelocityRoutingActions;
import kr.lunaf.cloudislands.velocity.VelocityRoutingController;
import kr.lunaf.cloudislands.velocity.config.VelocityConfig;
import net.kyori.adventure.text.Component;

abstract class VelocityCommandSupport {
    protected final ProxyServer proxy;
    protected final VelocityRoutingActions routingController;
    protected final VelocityPlayerRoutingActions playerRouting;
    protected final VelocityPlayerMembershipActions playerMembership;
    protected final VelocityPlayerProgressionActions playerProgression;
    protected final VelocityAdminActions adminActions;
    protected final VelocityConfig config;

    protected VelocityCommandSupport(ProxyServer proxy, VelocityRoutingController routingController, VelocityConfig config) {
        this.proxy = proxy;
        this.routingController = routingController.actions();
        this.playerRouting = this.routingController.playerRouting();
        this.playerMembership = this.routingController.playerMembership();
        this.playerProgression = this.routingController.playerProgression();
        this.adminActions = this.routingController.admin();
        this.config = config;
    }

    protected void sendCommandList(Player player, String title, List<String> commands, int page, String nextCommand) {
        CommandListPolicy.Page commandPage = CommandListPolicy.page(commands, page, nextCommand);
        player.sendMessage(Component.text(title + " " + commandPage.page() + "/" + commandPage.pages() + " commands=" + commandPage.rangeSummary() + CommandListPolicy.HEADER_SUFFIX));
        for (String line : CommandListPolicy.displayLines(commandPage)) {
            player.sendMessage(Component.text(line));
        }
    }

    protected boolean isCommandListRequest(String[] args) {
        if (args.length == 0) {
            return false;
        }
        String first = args[0].toLowerCase(java.util.Locale.ROOT);
        if (first.equals("help") || first.equals("도움말") || first.equals("commands") || first.equals("command") || first.equals("command-list") || first.equals("명령어") || first.equals("명령어목록")) {
            return true;
        }
        return first.equals("command") && args.length > 1 && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"));
    }

    protected int commandListPage(String[] args) {
        if (args.length > 2 && isCommandListRoot(args[0]) && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"))) {
            return (int) parseLongOrZero(args[2]);
        }
        if (args.length > 1) {
            return (int) parseLongOrZero(args[1]);
        }
        return 1;
    }

    protected boolean isCommandListRoot(String value) {
        return value.equalsIgnoreCase("command")
            || value.equalsIgnoreCase("commands")
            || value.equalsIgnoreCase("command-list")
            || value.equals("명령어")
            || value.equals("명령어목록");
    }

    protected UUID parseUuidOrNil(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return new UUID(0L, 0L);
        }
    }

    protected boolean hasOptionalIslandIdArgument(String[] args, int index) {
        return args.length > index + 1 && isUuid(args[index]);
    }

    protected UUID optionalIslandIdArgument(String[] args, int index) {
        return hasOptionalIslandIdArgument(args, index) ? parseUuidOrNil(args[index]) : new UUID(0L, 0L);
    }

    protected boolean hasIslandIdArgument(String[] args, int index) {
        return args.length > index && isUuid(args[index]);
    }

    protected UUID islandIdArgument(String[] args, int index) {
        return hasIslandIdArgument(args, index) ? parseUuidOrNil(args[index]) : new UUID(0L, 0L);
    }

    protected String argumentAfterOptionalIsland(String[] args, int index, String fallback) {
        if (hasOptionalIslandIdArgument(args, index)) {
            return args.length > index + 1 ? args[index + 1] : fallback;
        }
        return args.length > index ? args[index] : fallback;
    }

    protected String argumentAfterIslandId(String[] args, int index, String fallback) {
        if (hasIslandIdArgument(args, index)) {
            return args.length > index + 1 ? args[index + 1] : fallback;
        }
        return args.length > index ? args[index] : fallback;
    }

    protected int indexAfterOptionalIslandValue(String[] args, int index) {
        return hasOptionalIslandIdArgument(args, index) ? index + 2 : index + 1;
    }

    protected boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    protected UUID parsePlayerUuidOrNil(String value) {
        return proxy.getPlayer(value).map(Player::getUniqueId).orElseGet(() -> parseUuidOrNil(value));
    }

    protected long parseLongOrZero(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    protected UUID routeTargetIslandId(String[] args, int index) {
        if (args.length > index && isUuid(args[index])) {
            return parseUuidOrNil(args[index]);
        }
        return new UUID(0L, 0L);
    }

    protected String routeWarpName(String[] args, int index, String fallback) {
        if (args.length > index && isUuid(args[index])) {
            return args.length > index + 1 ? args[index + 1] : fallback;
        }
        return args.length > index ? args[index] : fallback;
    }

    protected boolean parseToggle(String[] args, int index, boolean fallback) {
        if (args.length <= index) {
            return fallback;
        }
        String value = args[index].toLowerCase(Locale.ROOT);
        if (value.equals("on") || value.equals("true") || value.equals("yes") || value.equals("1") || value.equals("enable") || value.equals("enabled") || value.equals("켜기") || value.equals("허용") || value.equals("활성")) {
            return true;
        }
        if (value.equals("off") || value.equals("false") || value.equals("no") || value.equals("0") || value.equals("disable") || value.equals("disabled") || value.equals("끄기") || value.equals("거부") || value.equals("비활성")) {
            return false;
        }
        return Boolean.parseBoolean(args[index]);
    }

    protected List<String> suggestions(List<String> catalog, String root, String[] args) {
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

    protected List<String> adminCommands() {
        return IslandCommandCatalog.adminCommands(config.superiorSkyblock2MigrationEnabled());
    }

    protected void addLiteralSuggestions(List<String> matches, String typed, List<String> values) {
        String normalized = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if ((normalized.isBlank() || value.toLowerCase(Locale.ROOT).startsWith(normalized)) && !matches.contains(value)) {
                matches.add(value);
            }
        }
    }

    protected void addOnlinePlayerSuggestions(List<String> matches, String typed) {
        String normalized = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        for (Player online : proxy.getAllPlayers()) {
            String username = online.getUsername();
            if ((normalized.isBlank() || username.toLowerCase(Locale.ROOT).startsWith(normalized)) && !matches.contains(username)) {
                matches.add(username);
            }
        }
    }

    protected String joinArgs(String[] args, int start) {
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

    protected IslandRole parseRole(String value) {
        IslandRole role = parseRoleOrNull(value);
        return role == null ? IslandRole.MEMBER : role;
    }

    protected IslandRole parseRoleOrNull(String value) {
        try {
            return IslandRole.valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    protected List<String> memberRoleNames() {
        return java.util.Arrays.stream(IslandRole.values())
            .filter(role -> role.islandMemberRole() && role != IslandRole.OWNER)
            .map(Enum::name)
            .toList();
    }

    protected List<String> permissionNames() {
        return java.util.Arrays.stream(IslandPermission.values())
            .map(Enum::name)
            .toList();
    }

    protected List<String> flagNames() {
        return java.util.Arrays.stream(kr.lunaf.cloudislands.api.model.IslandFlag.values())
            .map(Enum::name)
            .toList();
    }

    protected IslandPermission parsePermission(String value) {
        try {
            return IslandPermission.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return IslandPermission.BUILD;
        }
    }

    protected kr.lunaf.cloudislands.api.model.IslandFlag parseFlag(String value) {
        try {
            return kr.lunaf.cloudislands.api.model.IslandFlag.valueOf(value.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return kr.lunaf.cloudislands.api.model.IslandFlag.FLY;
        }
    }
}
