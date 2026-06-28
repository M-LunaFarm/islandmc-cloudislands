package kr.lunaf.cloudislands.paper.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class IslandCommandSuggestionService {
    private static final Map<String, String> ALIASES = Map.ofEntries(
        Map.entry("업글", "업그레이드"),
        Map.entry("업글구매", "업그레이드구매"),
        Map.entry("생성기정보보기", "생성기정보"),
        Map.entry("미션리스트", "미션목록"),
        Map.entry("챌린지리스트", "챌린지목록"),
        Map.entry("워프목", "워프목록"),
        Map.entry("홈목", "홈목록"),
        Map.entry("멤버목", "멤버목록"),
        Map.entry("권한목", "권한목록"),
        Map.entry("스냅샷목", "스냅샷목록")
    );

    Optional<String> suggest(String rawInput, List<String> candidates) {
        if (rawInput == null || rawInput.isBlank() || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        String input = rawInput.trim().toLowerCase(Locale.ROOT);
        String alias = ALIASES.get(input);
        if (alias != null && candidates.contains(alias)) {
            return Optional.of(alias);
        }
        LinkedHashMap<String, Integer> distances = new LinkedHashMap<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String normalized = candidate.toLowerCase(Locale.ROOT);
            if (normalized.equals(input)) {
                return Optional.of(candidate);
            }
            if (normalized.startsWith(input) && input.length() >= 3) {
                distances.put(candidate, 1);
                continue;
            }
            int distance = levenshtein(input, normalized);
            if (distance <= threshold(input, normalized)) {
                distances.put(candidate, distance);
            }
        }
        return distances.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry::getKey))
            .map(Map.Entry::getKey)
            .findFirst();
    }

    private int threshold(String input, String candidate) {
        int maxLength = Math.max(input.length(), candidate.length());
        if (maxLength <= 4) {
            return 1;
        }
        if (maxLength <= 8) {
            return 2;
        }
        return 3;
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int cost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                current[rightIndex] = Math.min(
                    Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                    previous[rightIndex - 1] + cost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }
}
