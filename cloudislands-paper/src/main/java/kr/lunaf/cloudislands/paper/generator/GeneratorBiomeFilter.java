package kr.lunaf.cloudislands.paper.generator;

import java.util.Locale;

public final class GeneratorBiomeFilter {
    private GeneratorBiomeFilter() {}

    public static boolean accepts(String ruleBiomeKey, String blockBiomeKey) {
        String rule = normalize(ruleBiomeKey);
        if (rule.equals("*")) {
            return true;
        }
        String block = normalize(blockBiomeKey);
        return rule.equals(block) || rule.equals("minecraft:" + block) || ("minecraft:" + rule).equals(block);
    }

    private static String normalize(String biomeKey) {
        return biomeKey == null || biomeKey.isBlank() ? "*" : biomeKey.trim().toLowerCase(Locale.ROOT);
    }
}
