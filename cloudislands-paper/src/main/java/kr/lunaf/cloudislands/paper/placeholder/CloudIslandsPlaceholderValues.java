package kr.lunaf.cloudislands.paper.placeholder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.common.permission.defaults.DefaultIslandPermissions;

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
        List<Member> orderedCoops = ordered(coops);
        String leader = orderedTeam.stream().filter(item -> "OWNER".equals(normalize(item.role()))).findFirst().map(Member::displayName).orElse(data.ownerUuid());
        String indexValue;
        if ((indexValue = numericSuffix(key, "ban_")) != null) {
            return indexedValue(data.bans().stream().filter(value -> value != null && !value.isBlank()).sorted(String.CASE_INSENSITIVE_ORDER).toList(), indexValue);
        }
        if ((indexValue = numericSuffix(key, "coop_")) != null) {
            return indexedMember(orderedCoops, indexValue);
        }
        if ((indexValue = numericSuffix(key, "member_index_")) != null) {
            return indexedMemberZeroBased(orderedTeam, indexValue);
        }
        if ((indexValue = numericSuffix(key, "member_")) != null) {
            return indexedMember(orderedTeam, indexValue);
        }
        if (key.startsWith("upgrade_") && key.length() > "upgrade_".length()) {
            return upgradeLevel(data.upgrades(), key.substring("upgrade_".length()));
        }
        if (key.startsWith("permission_") && key.length() > "permission_".length()) {
            return Boolean.toString(permissionAllowed(data, key.substring("permission_".length())));
        }
        if (key.startsWith("flag_") && key.length() > "flag_".length()) {
            return Boolean.toString(flagAllowed(data.flags(), key.substring("flag_".length())));
        }
        return switch (key) {
            case "has_island" -> Boolean.toString(member);
            case "has_associated_island" -> "true";
            case "island_id", "id" -> data.islandId();
            case "island_name", "name" -> data.name();
            case "description", "island_description" -> flagValue(data.flags(), "PROFILE_DESCRIPTION");
            case "discord", "island_discord" -> flagValue(data.flags(), "SOCIAL_DISCORD");
            case "paypal", "island_paypal" -> flagValue(data.flags(), "SOCIAL_PAYPAL");
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
            case "bank_rank", "island_bank_rank" -> rank(data.bankRank());
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

    private static String flagValue(Map<String, String> flags, String key) {
        if (flags == null) {
            return "";
        }
        return flags.getOrDefault(key, "");
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

    private static String indexedMember(List<Member> members, String positionValue) {
        try {
            int position = Integer.parseInt(positionValue);
            int index = position == 0 ? 0 : position - 1;
            return index >= 0 && index < members.size() ? members.get(index).displayName() : "";
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private static String numericSuffix(String key, String prefix) {
        if (!key.startsWith(prefix) || key.length() <= prefix.length()) {
            return null;
        }
        String suffix = key.substring(prefix.length());
        return suffix.chars().allMatch(Character::isDigit) ? suffix : null;
    }

    private static String indexedMemberZeroBased(List<Member> members, String indexValue) {
        try {
            int index = Integer.parseInt(indexValue);
            return index >= 0 && index < members.size() ? members.get(index).displayName() : "";
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private static String indexedValue(List<String> values, String positionValue) {
        try {
            int position = Integer.parseInt(positionValue);
            int index = position == 0 ? 0 : position - 1;
            return index >= 0 && index < values.size() ? values.get(index) : "";
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    static String playerChatValue(String params, String mode) {
        String key = params == null ? "" : params.toLowerCase(Locale.ROOT).replace('-', '_');
        String normalizedMode = normalize(mode);
        return switch (key) {
            case "chat_state", "player_chat_state" -> normalizedMode.isBlank() ? "GLOBAL" : normalizedMode;
            case "local_chat", "player_local_chat" -> Boolean.toString("ISLAND".equals(normalizedMode));
            case "team_chat", "player_team_chat" -> Boolean.toString("TEAM".equals(normalizedMode));
            default -> null;
        };
    }

    private static String upgradeLevel(List<Upgrade> upgrades, String requestedKey) {
        String normalizedKey = requestedKey.replace('-', '_');
        return upgrades.stream()
            .filter(upgrade -> upgrade != null && upgrade.key() != null && upgrade.key().toLowerCase(Locale.ROOT).replace('-', '_').equals(normalizedKey))
            .map(upgrade -> Integer.toString(upgrade.level()))
            .findFirst()
            .orElse("0");
    }

    private static boolean permissionAllowed(Data data, String requestedPermission) {
        IslandPermission permission;
        try {
            permission = IslandPermission.valueOf(normalize(requestedPermission));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        Boolean playerOverride = data.permissions().stream()
            .filter(rule -> rule != null && data.playerUuid().equals(rule.playerUuid()) && permission.name().equals(normalize(rule.permission())))
            .map(Permission::allowed)
            .findFirst()
            .orElse(null);
        if (playerOverride != null) {
            return playerOverride;
        }
        String role = normalize(data.role());
        Boolean roleRule = data.permissions().stream()
            .filter(rule -> rule != null && (rule.playerUuid() == null || rule.playerUuid().isBlank()))
            .filter(rule -> role.equals(normalize(rule.role())) && permission.name().equals(normalize(rule.permission())))
            .map(Permission::allowed)
            .findFirst()
            .orElse(null);
        return roleRule != null ? roleRule : DefaultIslandPermissions.create().allowedRoleKey(role, permission);
    }

    private static boolean flagAllowed(Map<String, String> flags, String requestedFlag) {
        String value = flags.entrySet().stream()
            .filter(entry -> normalize(entry.getKey()).equals(normalize(requestedFlag)))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse("");
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("allow") || value.equalsIgnoreCase("allowed")
            || value.equalsIgnoreCase("enabled") || value.equalsIgnoreCase("on");
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

    record Permission(String role, String playerUuid, String permission, boolean allowed) {
    }

    private enum Axis {
        X,
        Y,
        Z
    }

    record Data(String islandId, String name, String ownerUuid, String state, long size, long border, long level,
                String worth, boolean publicAccess, boolean locked, String createdAt, String updatedAt,
                String bankBalance, String role, List<Member> members, long memberLimit, long coopLimit,
                int worthRank, int levelRank, int bankRank, String biome, List<String> bans, Home home, int warpCount,
                long warpLimit, List<Upgrade> upgrades, String playerUuid, List<Permission> permissions,
                Map<String, String> flags) {
        Data(String islandId, String name, String ownerUuid, String state, long size, long border, long level,
             String worth, boolean publicAccess, boolean locked, String createdAt, String updatedAt,
             String bankBalance, String role, List<Member> members, long memberLimit, long coopLimit,
             int worthRank, int levelRank) {
            this(islandId, name, ownerUuid, state, size, border, level, worth, publicAccess, locked, createdAt, updatedAt,
                bankBalance, role, members, memberLimit, coopLimit, worthRank, levelRank, 0, "", List.of(), null, 0, 1L,
                List.of(), "", List.of(), Map.of());
        }

        Data {
            members = members == null ? List.of() : List.copyOf(members);
            biome = biome == null ? "" : biome;
            bans = bans == null ? List.of() : List.copyOf(bans);
            upgrades = upgrades == null ? List.of() : List.copyOf(upgrades);
            playerUuid = playerUuid == null ? "" : playerUuid;
            permissions = permissions == null ? List.of() : List.copyOf(permissions);
            flags = flags == null ? Map.of() : Map.copyOf(flags);
        }
    }
}
