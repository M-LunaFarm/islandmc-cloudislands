package kr.lunaf.cloudislands.paper.generator;

import java.util.List;

public final class GeneratorSystemPolicy {
    public static final String CONFIG_SOURCE_POLICY =
            "generator-rules-load-from-rules-generators-yaml-with-data-folder-override";
    public static final String APPLICATION_POLICY =
            "paper-listener-replaces-generated-blocks-on-block-form-and-fluid-collision-events";
    public static final String LEVEL_CACHE_POLICY =
            "island-generator-level-is-read-from-core-upgrades-and-cached-for-event-hot-path";
    public static final String DEFAULT_GENERATOR_KEY = "default";

    private static final List<String> EVENT_SURFACE = List.of(
            "BlockFormEvent",
            "BlockFromToEvent",
            "fluid-collision-detection"
    );

    private static final List<String> DEFAULT_LEVEL_ONE = List.of(
            "minecraft:cobblestone=90",
            "minecraft:coal_ore=10"
    );

    private static final List<String> DEFAULT_LEVEL_TWO = List.of(
            "minecraft:cobblestone=75",
            "minecraft:coal_ore=15",
            "minecraft:iron_ore=10"
    );

    private static final List<String> DEFAULT_LEVEL_THREE = List.of(
            "minecraft:cobblestone=60",
            "minecraft:coal_ore=15",
            "minecraft:iron_ore=15",
            "minecraft:diamond_ore=10"
    );

    private GeneratorSystemPolicy() {
    }

    public static List<String> eventSurface() {
        return EVENT_SURFACE;
    }

    public static List<String> defaultLevelOne() {
        return DEFAULT_LEVEL_ONE;
    }

    public static List<String> defaultLevelTwo() {
        return DEFAULT_LEVEL_TWO;
    }

    public static List<String> defaultLevelThree() {
        return DEFAULT_LEVEL_THREE;
    }

    public static boolean handledEvent(String eventName) {
        return EVENT_SURFACE.contains(eventName);
    }
}
