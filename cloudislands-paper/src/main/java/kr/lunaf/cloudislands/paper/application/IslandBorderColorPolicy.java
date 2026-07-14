package kr.lunaf.cloudislands.paper.application;

public final class IslandBorderColorPolicy {
    public static final long COLOR_TRANSITION_TICKS = 2_147_483_647L;
    private static final double COLOR_DELTA = 0.001D;

    private IslandBorderColorPolicy() {
    }

    public static Transition transition(double requestedSize, String requestedColor) {
        double size = Math.max(1.0D, requestedSize);
        String color = IslandBorderRuntimePolicy.normalizeColor(requestedColor);
        return switch (color) {
            case "green" -> new Transition(Math.max(1.0D, size - COLOR_DELTA), size, COLOR_TRANSITION_TICKS, color);
            case "red" -> new Transition(size, Math.max(1.0D, size - COLOR_DELTA), COLOR_TRANSITION_TICKS, color);
            default -> new Transition(size, size, 0L, "blue");
        };
    }

    public record Transition(double initialSize, double targetSize, long durationTicks, String color) {
        public boolean animated() {
            return durationTicks > 0L && Double.compare(initialSize, targetSize) != 0;
        }
    }
}
