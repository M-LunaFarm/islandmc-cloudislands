package kr.lunaf.cloudislands.paper.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;

final class AdminIslandInfoSections {
    private static final String EFFECT_PREFIX = "EFFECT:";
    private static final String ROLE_LIMIT_PREFIX = "ROLE_LIMIT:";

    private AdminIslandInfoSections() {
    }

    static List<Section> collect(List<IslandLimitSnapshot> limits, List<CoreGuiViews.UpgradeView> upgrades) {
        List<String> effects = limitEntries(limits, EFFECT_PREFIX);
        List<String> roleLimits = limitEntries(limits, ROLE_LIMIT_PREFIX);
        List<String> upgradeEntries = upgradeEntries(upgrades);
        List<Section> sections = new ArrayList<>(3);
        addIfPresent(sections, Kind.EFFECTS, effects);
        addIfPresent(sections, Kind.ROLE_LIMITS, roleLimits);
        addIfPresent(sections, Kind.UPGRADES, upgradeEntries);
        return List.copyOf(sections);
    }

    private static List<String> limitEntries(List<IslandLimitSnapshot> limits, String prefix) {
        if (limits == null || limits.isEmpty()) {
            return List.of();
        }
        return limits.stream()
            .filter(java.util.Objects::nonNull)
            .filter(limit -> normalizedKey(limit.limitKey()).startsWith(prefix))
            .map(limit -> normalizedKey(limit.limitKey()).substring(prefix.length()) + "=" + limit.value())
            .filter(entry -> !entry.startsWith("="))
            .sorted()
            .toList();
    }

    private static List<String> upgradeEntries(List<CoreGuiViews.UpgradeView> upgrades) {
        if (upgrades == null || upgrades.isEmpty()) {
            return List.of();
        }
        return upgrades.stream()
            .filter(java.util.Objects::nonNull)
            .filter(upgrade -> !singleLine(upgrade.key()).isBlank())
            .sorted(Comparator.comparing(upgrade -> singleLine(upgrade.key()), String.CASE_INSENSITIVE_ORDER))
            .map(AdminIslandInfoSections::upgradeEntry)
            .toList();
    }

    private static String upgradeEntry(CoreGuiViews.UpgradeView upgrade) {
        String level = Integer.toString(Math.max(0, upgrade.level()));
        return singleLine(upgrade.key()) + "=" + level + (upgrade.maxLevel() > 0 ? "/" + upgrade.maxLevel() : "");
    }

    private static String normalizedKey(String value) {
        return singleLine(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.:-]+", "_");
    }

    private static String singleLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        boolean previousWhitespace = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean whitespace = Character.isWhitespace(character) || Character.isISOControl(character);
            if (whitespace) {
                if (!previousWhitespace && builder.length() > 0) {
                    builder.append(' ');
                }
            } else {
                builder.append(character);
            }
            previousWhitespace = whitespace;
        }
        return builder.toString().trim();
    }

    private static void addIfPresent(List<Section> sections, Kind kind, List<String> entries) {
        if (!entries.isEmpty()) {
            sections.add(new Section(kind, String.join(",", entries)));
        }
    }

    enum Kind {
        EFFECTS,
        ROLE_LIMITS,
        UPGRADES
    }

    record Section(Kind kind, String value) {
        Section {
            value = singleLine(value);
        }
    }
}
