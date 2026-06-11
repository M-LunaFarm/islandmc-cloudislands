package kr.lunaf.cloudislands.common.security;

public record FencingToken(long value) implements Comparable<FencingToken> {
    public FencingToken next() {
        return new FencingToken(value + 1L);
    }

    public boolean accepts(FencingToken presented) {
        return presented != null && presented.value == value;
    }

    @Override
    public int compareTo(FencingToken other) {
        return Long.compare(value, other.value);
    }
}
