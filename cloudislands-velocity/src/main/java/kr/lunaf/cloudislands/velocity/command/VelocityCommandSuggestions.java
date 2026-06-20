package kr.lunaf.cloudislands.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.List;
import java.util.Locale;
import kr.lunaf.cloudislands.velocity.VelocityRoutingController;
import kr.lunaf.cloudislands.velocity.config.VelocityConfig;

final class VelocityCommandSuggestions extends VelocityCommandSupport {
    VelocityCommandSuggestions(ProxyServer proxy, VelocityRoutingController routingController, VelocityConfig config) {
        super(proxy, routingController, config);
    }

    public List<String> playerSuggestions(String[] args) {
        List<String> matches = suggestions(IslandCommandCatalog.playerCommands(), "섬", args);
        if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("fly") || first.equals("비행") || first.equals("keepinventory") || first.equals("keepinv") || first.equals("인벤보존") || first.equals("pvp") || first.equals("피빕") || first.equals("publicwarps") || first.equals("public-warps") || first.equals("공개워프")) {
                addLiteralSuggestions(matches, args[1], List.of("true", "false", "on", "off", "yes", "no", "1", "0", "켜기", "끄기"));
            }
            if (first.equals("rank") || first.equals("ranking") || first.equals("rank-list") || first.equals("랭킹") || first.equals("랭킹목록")) {
                addLiteralSuggestions(matches, args[1], List.of("worth", "value", "10", "25", "50"));
            }
            if (first.equals("limits") || first.equals("limit") || first.equals("limit-list") || first.equals("setlimit") || first.equals("limit-set") || first.equals("제한") || first.equals("제한목록") || first.equals("제한설정")) {
                addLiteralSuggestions(matches, args[1], List.of("HOPPER", "SPAWNER", "ENTITY", "REDSTONE"));
            }
            if (first.equals("hoppers") || first.equals("호퍼") || first.equals("spawners") || first.equals("스포너") || first.equals("entities") || first.equals("엔티티") || first.equals("redstone") || first.equals("레드스톤")) {
                addLiteralSuggestions(matches, args[1], List.of("25", "50", "100", "250"));
            }
            if (first.equals("setpermission") || first.equals("permission-set") || first.equals("권한설정")) {
                addLiteralSuggestions(matches, args[1], List.of("MEMBER", "TRUSTED", "MODERATOR", "CO_OWNER", "VISITOR"));
            }
            if (first.equals("role-upsert") || first.equals("role-edit") || first.equals("역할편집")) {
                addLiteralSuggestions(matches, args[1], memberRoleNames());
            }
            if (first.equals("setflag") || first.equals("flag-set") || first.equals("플래그설정")) {
                addLiteralSuggestions(matches, args[1], flagNames());
            }
            if (first.equals("biome") || first.equals("biome-menu") || first.equals("biome-info") || first.equals("바이옴") || first.equals("바이옴정보")) {
                addLiteralSuggestions(matches, args[1], List.of("minecraft:plains", "minecraft:forest", "minecraft:desert", "minecraft:taiga"));
            }
            if (first.equals("invite") || first.equals("초대") || first.equals("kick") || first.equals("remove-member") || first.equals("추방") || first.equals("promote") || first.equals("승급") || first.equals("demote") || first.equals("강등") || first.equals("setrole") || first.equals("role-set") || first.equals("역할설정") || first.equals("transfer") || first.equals("양도") || first.equals("trust") || first.equals("신뢰") || first.equals("untrust") || first.equals("신뢰해제") || first.equals("ban") || first.equals("밴") || first.equals("unban") || first.equals("pardon") || first.equals("밴해제") || first.equals("kickvisitor") || first.equals("방문자추방")) {
                addOnlinePlayerSuggestions(matches, args[1]);
            }
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("setrole") || args[0].equalsIgnoreCase("role-set") || args[0].equals("역할설정")) && !isUuid(args[1])) {
            addLiteralSuggestions(matches, args[2], memberRoleNames());
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("setrole") || args[0].equalsIgnoreCase("role-set") || args[0].equals("역할설정")) && isUuid(args[1])) {
            addLiteralSuggestions(matches, args[3], memberRoleNames());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("setpermission") || args[0].equalsIgnoreCase("permission-set") || args[0].equals("권한설정"))) {
            addLiteralSuggestions(matches, args[2], permissionNames());
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("setpermission") || args[0].equalsIgnoreCase("permission-set") || args[0].equals("권한설정"))) {
            addLiteralSuggestions(matches, args[3], List.of("true", "false", "on", "off", "허용", "거부"));
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("role-upsert") || args[0].equalsIgnoreCase("role-edit") || args[0].equals("역할편집"))) {
            addLiteralSuggestions(matches, args[2], List.of("1", "2", "3", "4", "5", "10"));
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("setflag") || args[0].equalsIgnoreCase("flag-set") || args[0].equals("플래그설정"))) {
            if (isUuid(args[1])) {
                addLiteralSuggestions(matches, args[2], flagNames());
            } else {
                addLiteralSuggestions(matches, args[2], List.of("true", "false", "on", "off", "yes", "no", "1", "0", "켜기", "끄기"));
            }
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("setflag") || args[0].equalsIgnoreCase("flag-set") || args[0].equals("플래그설정")) && isUuid(args[1])) {
            addLiteralSuggestions(matches, args[3], List.of("true", "false", "on", "off", "yes", "no", "1", "0", "켜기", "끄기"));
        }
        return matches;
    }

    public List<String> adminSuggestions(String[] args) {
        List<String> matches = suggestions(adminCommands(), "ciadmin", args);
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
            addLiteralSuggestions(matches, args[4], List.of("true", "false", "enabled", "disabled", "enable", "disable", "on", "off", "활성", "비활성"));
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
        if (config.superiorSkyblock2MigrationEnabled() && args.length == 2 && args[0].equalsIgnoreCase("migrate-superiorskyblock2")) {
            addLiteralSuggestions(matches, args[1], List.of("scan", "status", "dryrun", "extract", "import", "verify", "rollback"));
        }
        if (config.superiorSkyblock2MigrationEnabled() && args.length == 3 && args[0].equalsIgnoreCase("migrate-superiorskyblock2")) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if (action.equals("scan") || action.equals("dryrun") || action.equals("dry-run")) {
                addLiteralSuggestions(matches, args[2], List.of("plugins/SuperiorSkyblock2"));
            } else if (action.equals("extract") || action.equals("verify")) {
                addLiteralSuggestions(matches, args[2], List.of("cloudislands-storage", "migration-bundles"));
            } else if (action.equals("import")) {
                addLiteralSuggestions(matches, args[2], List.of("<approvalToken>"));
            }
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

    public boolean hasAdminAccess(CommandSource source, String[] args) {
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
        if (root.equals("migrate-superiorskyblock2") && !config.superiorSkyblock2MigrationEnabled()) {
            return "";
        }
        return switch (root) {
            case "status", "config", "cache", "addons", "node", "island", "player", "jobs", "route", "rankings", "events", "audit", "metrics", "storage", "block-values", "upgrade-rules", "templates", "migrate-superiorskyblock2", "reload" -> "cloudislands.admin." + root;
            default -> "";
        };
    }


}
