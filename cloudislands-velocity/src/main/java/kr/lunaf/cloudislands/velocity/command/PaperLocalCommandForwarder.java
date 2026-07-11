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
        "fly", "비행"
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
        return roots.contains(arguments[0].toLowerCase(Locale.ROOT))
            && PAPER_LOCAL_SUBCOMMANDS.contains(arguments[1].toLowerCase(Locale.ROOT));
    }
}
