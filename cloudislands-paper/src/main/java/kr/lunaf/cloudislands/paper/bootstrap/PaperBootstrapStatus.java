package kr.lunaf.cloudislands.paper.bootstrap;

import java.util.Locale;

public final class PaperBootstrapStatus {
    private static final int MAX_MESSAGE_LENGTH = 240;

    private volatile Snapshot snapshot = new Snapshot(State.STOPPED, 0, "", "");

    public synchronized Snapshot starting() {
        snapshot = new Snapshot(State.STARTING, snapshot.attempt() + 1, "", "");
        return snapshot;
    }

    public synchronized Snapshot ready() {
        snapshot = new Snapshot(State.READY, snapshot.attempt(), "", "");
        return snapshot;
    }

    public synchronized Snapshot failed(Throwable failure) {
        Throwable root = rootCause(failure);
        String type = root == null ? "RuntimeException" : root.getClass().getSimpleName();
        String message = sanitize(root == null ? "unknown bootstrap failure" : root.getMessage());
        snapshot = new Snapshot(State.FAILED, Math.max(1, snapshot.attempt()), type, message);
        return snapshot;
    }

    public synchronized Snapshot stopped() {
        snapshot = new Snapshot(State.STOPPED, snapshot.attempt(), "", "");
        return snapshot;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public static String sanitize(String message) {
        String value = message == null || message.isBlank() ? "no detail available" : message;
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        value = redactPrefixedToken(value, "github_pat_");
        for (String prefix : java.util.List.of("ghp_", "gho_", "ghu_", "ghs_", "ghr_")) {
            value = redactPrefixedToken(value, prefix);
        }
        for (String key : java.util.List.of("token", "password", "secret")) {
            value = redactAssignment(value, key);
        }
        value = redactUriUserInfo(value);
        return value.length() <= MAX_MESSAGE_LENGTH ? value : value.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
    }

    private static String redactPrefixedToken(String value, String prefix) {
        int cursor = 0;
        while (true) {
            int start = value.indexOf(prefix, cursor);
            if (start < 0) {
                return value;
            }
            int end = start + prefix.length();
            while (end < value.length()) {
                char character = value.charAt(end);
                if (!Character.isLetterOrDigit(character) && character != '_') {
                    break;
                }
                end++;
            }
            value = value.substring(0, start) + "[redacted-token]" + value.substring(end);
            cursor = start + "[redacted-token]".length();
        }
    }

    private static String redactAssignment(String value, String key) {
        int cursor = 0;
        while (true) {
            String lower = value.toLowerCase(Locale.ROOT);
            int keyStart = lower.indexOf(key, cursor);
            if (keyStart < 0) {
                return value;
            }
            int separator = keyStart + key.length();
            while (separator < value.length() && Character.isWhitespace(value.charAt(separator))) {
                separator++;
            }
            if (separator >= value.length() || (value.charAt(separator) != '=' && value.charAt(separator) != ':')) {
                cursor = keyStart + key.length();
                continue;
            }
            int secretStart = separator + 1;
            while (secretStart < value.length() && Character.isWhitespace(value.charAt(secretStart))) {
                secretStart++;
            }
            int secretEnd = secretStart;
            while (secretEnd < value.length()) {
                char character = value.charAt(secretEnd);
                if (Character.isWhitespace(character) || character == ',' || character == ';') {
                    break;
                }
                secretEnd++;
            }
            value = value.substring(0, secretStart) + "[redacted]" + value.substring(secretEnd);
            cursor = secretStart + "[redacted]".length();
        }
    }

    private static String redactUriUserInfo(String value) {
        int cursor = 0;
        while (true) {
            int scheme = value.indexOf("://", cursor);
            if (scheme < 0) {
                return value;
            }
            int userInfoStart = scheme + 3;
            int at = value.indexOf('@', userInfoStart);
            int slash = value.indexOf('/', userInfoStart);
            int whitespace = firstWhitespace(value, userInfoStart);
            int boundary = minPositive(slash, whitespace);
            if (at < 0 || (boundary >= 0 && at > boundary)) {
                cursor = userInfoStart;
                continue;
            }
            value = value.substring(0, userInfoStart) + "[redacted]@" + value.substring(at + 1);
            cursor = userInfoStart + "[redacted]@".length();
        }
    }

    private static int firstWhitespace(String value, int start) {
        for (int index = start; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static int minPositive(int left, int right) {
        if (left < 0) {
            return right;
        }
        return right < 0 ? left : Math.min(left, right);
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && current.getCause() != null && current.getCause() != current && depth < 16; depth++) {
            current = current.getCause();
        }
        return current;
    }

    public enum State {
        STARTING,
        READY,
        FAILED,
        STOPPED
    }

    public record Snapshot(State state, int attempt, String failureType, String failureMessage) {
        public boolean retryable() {
            return state == State.FAILED;
        }
    }
}
