package kr.lunaf.cloudislands.velocity.command;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PaperLocalCommandForwarder {
    private static final Set<String> PAPER_LOCAL_SUBCOMMANDS = Set.of(
        "deposit", "bank-deposit", "입금",
        "withdraw", "bank-withdraw", "출금",
        "sethome", "setteleport", "settp", "setgo", "setspawnpoint", "셋홈",
        "setwarp", "warp-set", "워프설정",
        "warehouse-deposit", "창고입금",
        "warehouse-withdraw", "창고출금",
        "chest", "vault", "island-chest", "islandchest", "storage-box", "창고",
        "fly", "비행",
        "biome", "setbiome", "biome-menu", "바이옴",
        "border", "border-ui", "경계",
        "permissions", "permission-menu", "perms", "권한",
        "panel", "manager", "cp",
        "value"
    );
    private static final Set<String> PAPER_MENU_SUBCOMMANDS = Set.of(
        "bank", "은행",
        "homes", "home-menu", "홈관리",
        "warps", "warp-menu", "워프관리",
        "invites", "invite-menu", "초대목록",
        "members", "coops", "member-menu", "멤버", "멤버관리",
        "banlist", "bans", "ban-menu", "밴목록",
        "settings", "setting", "설정",
        "flags", "flag-menu", "플래그",
        "missions", "mission-menu", "challenges", "challenge-menu",
        "upgrade", "upgrades", "top", "ratings", "values", "visitors"
    );

    private PaperLocalCommandForwarder() {
    }

    public static boolean shouldForward(String command, List<String> configuredAliases) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String normalized = command.strip();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).stripLeading();
        }
        String[] arguments = normalized.split("\\s+", 3);
        if (arguments.length < 2) {
            return false;
        }
        Set<String> roots = new HashSet<>();
        roots.add("섬");
        roots.add("is");
        roots.add("island");
        if (configuredAliases != null) {
            configuredAliases.stream()
                .filter(alias -> alias != null && !alias.isBlank())
                .map(alias -> alias.toLowerCase(Locale.ROOT))
                .forEach(roots::add);
        }
        String root = arguments[0].toLowerCase(Locale.ROOT);
        String subcommand = arguments[1].toLowerCase(Locale.ROOT);
        if (!roots.contains(root)) {
            return false;
        }
        if (PAPER_LOCAL_SUBCOMMANDS.contains(subcommand)) {
            return true;
        }
        if (Set.of("teamchat", "team-chat", "teamchat-toggle", "tc", "팀채팅").contains(subcommand)) {
            return arguments.length == 2 || isTeamChatModeArgument(arguments[2]);
        }
        if (!PAPER_MENU_SUBCOMMANDS.contains(subcommand)) {
            return false;
        }
        return arguments.length == 2 || subcommand.equals("bank") && arguments[2].equalsIgnoreCase("logs");
    }

    private static boolean isTeamChatModeArgument(String value) {
        return value.equalsIgnoreCase("toggle") || value.equalsIgnoreCase("mode")
            || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("off")
            || value.equals("전환") || value.equals("모드");
    }
}
