package kr.lunaf.cloudislands.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kr.lunaf.cloudislands.api.environment.IslandBiomePolicy;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

final class IslandCommandTabCompleter implements TabCompleter {
    private final Plugin plugin;
    private final ProtectionController protection;

    IslandCommandTabCompleter(Plugin plugin) {
        this(plugin, null);
    }

    IslandCommandTabCompleter(Plugin plugin, ProtectionController protection) {
        this.plugin = plugin;
        this.protection = protection;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length > 1 && !IslandCommandPermission.hasAccess(sender, args[0])) {
            return List.of();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("command")) {
            return literalMatches(List.of("list", "목록"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("command") && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"))) {
            return literalMatches(helpRootSuggestions(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("command") && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"))) {
            return categoryPageSuggestions(args[2], args[3]);
        }
        if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (isHelpRoot(first)) {
                return literalMatches(helpRootSuggestions(), args[1]);
            }
            if (first.equals("fly") || first.equals("비행") || first.equals("keepinventory") || first.equals("keepinv") || first.equals("인벤보존") || first.equals("pvp") || first.equals("피빕") || first.equals("publicwarps") || first.equals("public-warps") || first.equals("공개워프")) {
                return literalMatches(List.of("true", "false", "on", "off", "yes", "no", "1", "0", "켜기", "끄기"), args[1]);
            }
            if (first.equals("rank") || first.equals("ranking") || first.equals("랭킹")) {
                return literalMatches(List.of("worth", "value", "10", "25", "50"), args[1]);
            }
            if (first.equals("blocks") || first.equals("block-details") || first.equals("block-counts") || first.equals("블록상세") || first.equals("블록목록")) {
                return literalMatches(List.of("10", "25", "50", "100"), args[1]);
            }
            if (first.equals("reviews") || first.equals("review-list") || first.equals("후기") || first.equals("후기목록")) {
                return literalMatches(List.of("5", "10", "20", "50"), args[1]);
            }
            if (first.equals("visitor-stats") || first.equals("visitorstats") || first.equals("visitors") || first.equals("방문통계") || first.equals("방문자통계")) {
                return literalMatches(List.of("5", "10", "20", "50"), args[1]);
            }
            if (first.equals("rate") || first.equals("review") || first.equals("평가")) {
                return literalMatches(List.of("current"), args[1]);
            }
            if (first.equals("delete-review") || first.equals("review-delete") || first.equals("reviewdel") || first.equals("후기삭제") || first.equals("평가삭제")) {
                return literalMatches(List.of("current"), args[1]);
            }
            if (first.equals("limits") || first.equals("limit") || first.equals("제한") || first.equals("setlimit") || first.equals("제한설정")) {
                return literalMatches(List.of("HOPPER", "SPAWNER", "ENTITY", "REDSTONE"), args[1]);
            }
            if (first.equals("setflag") || first.equals("flag-set") || first.equals("플래그설정")) {
                return literalMatches(flagNames(), args[1]);
            }
            if (first.equals("hoppers") || first.equals("호퍼") || first.equals("spawners") || first.equals("스포너") || first.equals("entities") || first.equals("엔티티") || first.equals("redstone") || first.equals("레드스톤")) {
                return literalMatches(List.of("25", "50", "100", "250"), args[1]);
            }
            if (first.equals("warehouse") || first.equals("warehouse-list") || first.equals("창고") || first.equals("창고목록")) {
                return literalMatches(List.of("10", "27", "54", "100"), args[1]);
            }
            if (first.equals("public-warps") || first.equals("publicwarplist") || first.equals("공개워프목록")) {
                return literalMatches(List.of("default", "shop", "farm", "event", "pvp"), args[1]);
            }
            if (first.equals("language") || first.equals("locale") || first.equals("언어")) {
                return literalMatches(List.of("ko_kr", "en_us"), args[1]);
            }
            if (first.equals("warehouse-deposit") || first.equals("warehouse-withdraw") || first.equals("창고입금") || first.equals("창고출금")) {
                return literalMatches(List.of("minecraft:cobblestone", "minecraft:dirt", "minecraft:oak_log", "minecraft:iron_ingot"), args[1]);
            }
            if (first.equals("buyupgrade") || first.equals("upgrade-buy") || first.equals("업그레이드구매")) {
                return literalMatches(IslandCommandCatalog.upgradeKeys(), args[1]);
            }
            if (first.equals("setpermission") || first.equals("permission-set") || first.equals("권한설정")) {
                return literalMatches(roleCatalog(sender, true), args[1]);
            }
            if (first.equals("permission-exception") || first.equals("권한예외")) {
                return onlinePlayerMatches(args[1]);
            }
            if (first.equals("role-upsert") || first.equals("role-edit") || first.equals("역할편집")) {
                return literalMatches(roleCatalog(sender, false), args[1]);
            }
            if (first.equals("biome") || first.equals("바이옴")) {
                return literalMatches(IslandBiomePolicy.supportedBiomes(), args[1]);
            }
            if (first.equals("invite") || first.equals("초대") || first.equals("kick") || first.equals("remove-member") || first.equals("추방") || first.equals("promote") || first.equals("승급") || first.equals("demote") || first.equals("강등") || first.equals("setrole") || first.equals("role-set") || first.equals("역할설정") || first.equals("transfer") || first.equals("양도") || first.equals("trust") || first.equals("신뢰") || first.equals("coop") || first.equals("co-op") || first.equals("협동") || first.equals("untrust") || first.equals("신뢰해제") || first.equals("ban") || first.equals("밴") || first.equals("unban") || first.equals("pardon") || first.equals("밴해제") || first.equals("kickvisitor") || first.equals("방문자추방")) {
                return onlinePlayerMatches(args[1]);
            }
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("setpermission") || args[0].equalsIgnoreCase("permission-set") || args[0].equals("권한설정"))) {
            return literalMatches(permissionNames(), args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("permission-exception") || args[0].equals("권한예외"))) {
            return literalMatches(permissionNames(), args[2]);
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("permission-exception") || args[0].equals("권한예외"))) {
            return literalMatches(List.of("true", "false", "on", "off", "허용", "거부"), args[3]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("setrole") || args[0].equalsIgnoreCase("role-set") || args[0].equals("역할설정"))) {
            return literalMatches(roleCatalog(sender, false), args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("setwarp") || args[0].equals("워프설정"))) {
            return literalMatches(List.of("default", "shop", "farm", "event", "pvp"), args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("rate") || args[0].equalsIgnoreCase("review") || args[0].equals("평가"))) {
            return literalMatches(List.of("5", "4", "3", "2", "1"), args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("role-upsert") || args[0].equalsIgnoreCase("role-edit") || args[0].equals("역할편집"))) {
            return literalMatches(List.of("1", "2", "3", "4", "5", "10"), args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("warehouse-deposit") || args[0].equalsIgnoreCase("warehouse-withdraw") || args[0].equals("창고입금") || args[0].equals("창고출금"))) {
            return literalMatches(List.of("1", "16", "32", "64", "128"), args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("coop") || args[0].equalsIgnoreCase("co-op") || args[0].equals("신뢰") || args[0].equals("협동"))) {
            return literalMatches(List.of("10m", "30m", "1h", "6h", "1d", "7d"), args[2]);
        }
        if (args.length == 3 && isHelpRoot(args[0].toLowerCase(Locale.ROOT))) {
            return categoryPageSuggestions(args[1], args[2]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("border") || args[0].equalsIgnoreCase("border-ui") || args[0].equals("경계"))) {
            return literalMatches(List.of("apply", "visible", "hidden", "show", "hide", "color", "warning", "적용", "표시", "숨김", "색상", "경고"), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("border-color") || args[0].equalsIgnoreCase("경계색상") || ((args[0].equalsIgnoreCase("border") || args[0].equals("경계")) && (args[1].equalsIgnoreCase("color") || args[1].equals("색상"))))) {
            return literalMatches(List.of("blue", "green", "red", "aqua", "yellow", "purple", "파랑", "초록", "빨강", "하늘", "노랑", "보라"), args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("border-visible") || args[0].equalsIgnoreCase("경계표시") || ((args[0].equalsIgnoreCase("border") || args[0].equals("경계")) && (args[1].equalsIgnoreCase("visible") || args[1].equals("표시"))))) {
            return literalMatches(List.of("true", "false", "on", "off", "show", "hide", "켜기", "끄기", "표시", "숨김"), args[2]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("setflag") || args[0].equalsIgnoreCase("flag-set") || args[0].equals("플래그설정"))) {
            return literalMatches(List.of("true", "false", "on", "off", "yes", "no", "1", "0", "켜기", "끄기"), args[2]);
        }
        if (args.length == 4 && (args[0].equalsIgnoreCase("setpermission") || args[0].equalsIgnoreCase("permission-set") || args[0].equals("권한설정"))) {
            return literalMatches(List.of("true", "false", "on", "off", "허용", "거부"), args[3]);
        }
        if (args.length != 1) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String subcommand : IslandCommandBackend.SUBCOMMANDS) {
            if ((typed.isBlank() || subcommand.toLowerCase(Locale.ROOT).startsWith(typed)) && IslandCommandPermission.hasAccess(sender, subcommand)) {
                matches.add(subcommand);
            }
        }
        return matches;
    }

    private List<String> literalMatches(List<String> values, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (lower.isBlank() || value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private List<String> helpRootSuggestions() {
        List<String> values = new ArrayList<>(IslandCommandCatalog.helpCategoryNames());
        values.addAll(commandListPageSuggestions(IslandCommandBackend.HELP_COMMANDS.size()));
        return values;
    }

    private List<String> categoryPageSuggestions(String categoryName, String typed) {
        IslandCommandCatalog.HelpCategory category = IslandCommandCatalog.helpCategory(categoryName);
        if (category == null) {
            return List.of();
        }
        return literalMatches(commandListPageSuggestions(category.commands().size()), typed);
    }

    private List<String> commandListPageSuggestions(int commandCount) {
        int maxPage = CommandListPolicy.pages(commandCount);
        List<String> values = new ArrayList<>();
        for (int page = 1; page <= maxPage; page++) {
            values.add(String.valueOf(page));
        }
        return values;
    }

    private boolean isHelpRoot(String first) {
        return first.equals("help") || first.equals("도움말") || first.equals("commands") || first.equals("command-list") || first.equals("명령어") || first.equals("명령어목록");
    }

    private List<String> flagNames() {
        return java.util.Arrays.stream(IslandFlag.values()).map(Enum::name).toList();
    }

    private List<String> permissionNames() {
        return java.util.Arrays.stream(IslandPermission.values()).map(Enum::name).toList();
    }

    private List<String> memberRoleNames() {
        return IslandRoleKeyPolicy.memberRoleKeys();
    }

    private List<String> roleCatalog(CommandSender sender, boolean includeVisitor) {
        if (sender instanceof Player player && protection != null) {
            java.util.Optional<kr.lunaf.cloudislands.common.protection.IslandRegion> region = protection.regionAt(player.getLocation().getBlock());
            if (region.isPresent()) {
                return protection.roleCatalog(region.get().islandId(), includeVisitor);
            }
        }
        List<String> names = new ArrayList<>(memberRoleNames());
        if (includeVisitor) {
            names.add(IslandRoleKeyPolicy.visitorRoleKey());
        }
        return names;
    }

    private List<String> onlinePlayerMatches(String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            String name = online.getName();
            if (lower.isBlank() || name.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(name);
            }
        }
        return matches;
    }
}
