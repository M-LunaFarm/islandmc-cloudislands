package kr.lunaf.cloudislands.velocity.message;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;

import kr.lunaf.cloudislands.protocol.route.RouteFailureMessagePolicy;

public final class VelocityPlayerPayloadFormatter {
    public String bodyResultMessage(String body, String emptyMessage) {
        if (body == null || body.isBlank()) {
            return emptyMessage;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return playerPayloadMessage(trimmed, emptyMessage, emptyMessage);
        }
        return trimmed;
    }

    public String playerPayloadMessage(String body, String emptyMessage, String successMessage) {
        if (body == null || body.isBlank()) {
            return emptyMessage;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{\"error\"")) {
            String code = jsonValue(trimmed, "code");
            return playerErrorMessage(code, emptyMessage);
        }
        if (trimmed.startsWith("{") && trimmed.contains("\"accepted\":false")) {
            String code = jsonValue(trimmed, "code");
            return playerErrorMessage(code, emptyMessage);
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return successMessage;
        }
        return trimmed;
    }

    private String playerErrorMessage(String code, String fallback) {
        return RouteFailureMessagePolicy.playerMessage(code, fallback);
    }
}
