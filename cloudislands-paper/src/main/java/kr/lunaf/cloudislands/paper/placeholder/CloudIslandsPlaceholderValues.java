package kr.lunaf.cloudislands.paper.placeholder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class CloudIslandsPlaceholderValues {
    private CloudIslandsPlaceholderValues() {
    }

    static String value(Data data, String params) {
        String key = params == null ? "" : params.toLowerCase(Locale.ROOT).replace('-', '_');
        if (data == null) {
            return booleanWhenMissing(key) ? "false" : "";
        }
        String role = normalize(data.role());
        boolean coop = "TRUSTED".equals(role);
        boolean owner = "OWNER".equals(role);
        boolean member = !role.isBlank() && !coop;
        List<Member> team = data.members().stream().filter(item -> !"TRUSTED".equals(normalize(item.role()))).toList();
        List<Member> coops = data.members().stream().filter(item -> "TRUSTED".equals(normalize(item.role()))).toList();
        List<Member> orderedTeam = ordered(team);
        String leader = orderedTeam.stream().filter(item -> "OWNER".equals(normalize(item.role()))).findFirst().map(Member::displayName).orElse(data.ownerUuid());
        if (key.startsWith("member_") && key.length() > "member_".length()) {
            return indexedMember(orderedTeam, key.substring("member_".length()));
        }
        if (key.startsWith("upgrade_") && key.length() > "upgrade_".length()) {
            return upgradeLevel(data.upgrades(), key.substring("upgrade_".length()));
        }
        return switch (key) {
            case "has_island" -> Boolean.toString(member);
            case "has_associated_island" -> "true";
            case "island_id", "id" -> data.islandId();
            case "island_name", "name" -> data.name();
            case "owner_uuid", "owner" -> data.ownerUuid();
            case "leader", "island_leader" -> leader;
            case "state", "island_state" -> data.state();
            case "size", "island_size" -> Long.toString(data.size());
            case "border", "island_border" -> Long.toString(data.border());
            case "level", "island_level" -> Long.toString(data.level());
            case "level_int", "level_raw", "island_level_int", "island_level_raw", "raw_level" -> Long.toString(data.level());
            case "level_format", "island_level_format" -> compact(data.level());
            case "worth", "value", "island_worth" -> data.worth();
            case "worth_int", "island_worth_int" -> integer(data.worth());
            case "worth_raw", "island_worth_raw", "raw_worth" -> data.worth();
            case "worth_format", "island_worth_format" -> compact(data.worth());
            case "rank", "worth_rank", "island_rank", "island_worth_rank" -> rank(data.worthRank());
            case "level_rank", "island_level_rank" -> rank(data.levelRank());
            case "public", "public_access", "is_public" -> Boolean.toString(data.publicAccess());
            case "locked", "island_locked" -> Boolean.toString(data.locked());
            case "bank", "bank_balance", "balance" -> data.bankBalance();
            case "bank_int", "island_bank_int" -> integer(data.bankBalance());
            case "bank_raw", "island_bank_raw" -> data.bankBalance();
            case "bank_format", "island_bank_format" -> compact(data.bankBalance());
            case "role", "island_role" -> role;
            case "is_member", "island_is_member" -> Boolean.toString(member);
            case "is_coop", "island_is_coop" -> Boolean.toString(coop);
            case "is_owner", "is_leader", "island_is_owner", "island_is_leader" -> Boolean.toString(owner);
            case "member_count", "team_size", "island_team_size" -> Integer.toString(team.size());
            case "team_size_online", "island_team_size_online" -> Long.toString(team.stream().filter(Member::online).count());
            case "coop_count", "coop_size", "island_coop_size" -> Integer.toString(coops.size());
            case "member_limit", "team_limit", "island_team_limit" -> Long.toString(data.memberLimit());
            case "coop_limit", "island_coop_limit" -> Long.toString(data.coopLimit());
            case "member_list", "team_list" -> names(team);
            case "coop_list" -> names(coops);
            case "biome", "island_biome" -> data.biome();
            case "bans_count", "island_bans_count" -> Integer.toString(data.bans().size());
            case "bans_list", "island_bans_list" -> sortedValues(data.bans());
            case "home", "island_home" -> location(data.home());
            case "home_x", "island_home_x" -> coordinate(data.home(), Axis.X);
            case "home_y", "island_home_y" -> coordinate(data.home(), Axis.Y);
            case "home_z", "island_home_z" -> coordinate(data.home(), Axis.Z);
            case "world", "island_world" -> data.home() == null ? "" : data.home().worldName();
            case "warps", "island_warps" -> Integer.toString(data.warpCount());
            case "warps_limit", "island_warps_limit", "raw_warps_limit" -> Long.toString(data.warpLimit());
            case "created_at", "creation_time" -> data.createdAt();
            case "updated_at", "last_time_updated" -> data.updatedAt();
            default -> "";
        };
    }

    private static boolean booleanWhenMissing(String key) {
        return key.equals("has_island") || key.equals("has_associated_island") || key.startsWith("is_") || key.startsWith("island_is_");
    }

    private static String names(List<Member> members) {
        return ordered(members).stream()
            .map(Member::displayName)
            .filter(name -> !name.isBlank())
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }

    private static List<Member> ordered(List<Member> members) {
        return members.stream().sorted(Comparator.comparingInt((Member member) -> "OWNER".equals(normalize(member.role())) ? 0 : 1)
            .thenComparing(Member::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(member -> member.playerUuid() == null ? "" : member.playerUuid())).toList();
    }

    private static String indexedMember(List<Member> members, String indexValue) {
        try {
            int index = Integer.parseInt(indexValue);
            return index >= 0 && index < members.size() ? members.get(index).displayName() : "";
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private static String upgradeLevel(List<Upgrade> upgrades, String requestedKey) {
        String normalizedKey = requestedKey.replace('-', '_');
        return upgrades.stream()
            .filter(upgrade -> upgrade != null && upgrade.key() != null && upgrade.key().toLowerCase(Locale.ROOT).replace('-', '_').equals(normalizedKey))
            .map(upgrade -> Integer.toString(upgrade.level()))
            .findFirst()
            .orElse("0");
    }

    private static String sortedValues(List<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank()).sorted(String.CASE_INSENSITIVE_ORDER)
            .reduce((left, right) -> left + ", " + right).orElse("");
    }

    private static String location(Home home) {
        if (home == null) {
            return "";
        }
        return home.worldName() + ", " + decimal(home.x()) + ", " + decimal(home.y()) + ", " + decimal(home.z());
    }

    private static String coordinate(Home home, Axis axis) {
        if (home == null) {
            return "";
        }
        return decimal(switch (axis) {
            case X -> home.x();
            case Y -> home.y();
            case Z -> home.z();
        });
    }

    private static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String integer(String value) {
        try {
            return new BigDecimal(value).setScale(0, RoundingMode.DOWN).toPlainString();
        } catch (NumberFormatException ignored) {
            return value == null ? "" : value;
        }
    }

    private static String compact(long value) {
        return compact(BigDecimal.valueOf(value).toPlainString());
    }

    private static String compact(String value) {
        try {
            BigDecimal number = new BigDecimal(value);
            BigDecimal absolute = number.abs();
            String[] suffixes = {"", "K", "M", "B", "T", "Q"};
            int suffix = 0;
            BigDecimal thousand = BigDecimal.valueOf(1_000L);
            while (absolute.compareTo(thousand) >= 0 && suffix < suffixes.length - 1) {
                number = number.divide(thousand);
                absolute = absolute.divide(thousand);
                suffix++;
            }
            return number.setScale(number.abs().compareTo(BigDecimal.TEN) < 0 && suffix > 0 ? 1 : 0, RoundingMode.DOWN).stripTrailingZeros().toPlainString() + suffixes[suffix];
        } catch (NumberFormatException ignored) {
            return value == null ? "" : value;
        }
    }

    private static String normalize(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    private static String rank(int rank) {
        return rank <= 0 ? "" : Integer.toString(rank);
    }

    record Member(String playerUuid, String playerName, String role, String presenceState) {
        String displayName() {
            return playerName == null || playerName.isBlank() ? (playerUuid == null ? "" : playerUuid) : playerName;
        }

        boolean online() {
            return "ONLINE".equals(normalize(presenceState));
        }
    }

    record Home(String worldName, double x, double y, double z) {
        Home {
            worldName = worldName == null ? "" : worldName;
        }
    }

    record Upgrade(String key, int level) {
    }

    private enum Axis {
        X,
        Y,
        Z
    }

    record Data(String islandId, String name, String ownerUuid, String state, long size, long border, long level,
                String worth, boolean publicAccess, boolean locked, String createdAt, String updatedAt,
                String bankBalance, String role, List<Member> members, long memberLimit, long coopLimit,
                int worthRank, int levelRank, String biome, List<String> bans, Home home, int warpCount,
                long warpLimit, List<Upgrade> upgrades) {
        Data(String islandId, String name, String ownerUuid, String state, long size, long border, long level,
             String worth, boolean publicAccess, boolean locked, String createdAt, String updatedAt,
             String bankBalance, String role, List<Member> members, long memberLimit, long coopLimit,
             int worthRank, int levelRank) {
            this(islandId, name, ownerUuid, state, size, border, level, worth, publicAccess, locked, createdAt, updatedAt,
                bankBalance, role, members, memberLimit, coopLimit, worthRank, levelRank, "", List.of(), null, 0, 1L, List.of());
        }

        Data {
            members = members == null ? List.of() : List.copyOf(members);
            biome = biome == null ? "" : biome;
            bans = bans == null ? List.of() : List.copyOf(bans);
            upgrades = upgrades == null ? List.of() : List.copyOf(upgrades);
        }
    }
}
