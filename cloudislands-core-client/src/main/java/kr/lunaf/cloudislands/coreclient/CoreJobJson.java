package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CoreJobJson {
    private CoreJobJson() {
    }

    static List<JobView> jobs(String body) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        return SimpleJson.list(root.get("jobs")).stream()
            .map(SimpleJson::object)
            .filter(object -> !object.isEmpty())
            .map(CoreJobJson::job)
            .toList();
    }

    static JobActionView action(String body, String successCode) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("ok"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String code = text(root, "code");
        if (!code.isBlank() && !code.equals(successCode)) {
            accepted = false;
        }
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        return new JobActionView(accepted, code);
    }

    static JobRecoveryView recovery(String body) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = !root.containsKey("error") && !Boolean.FALSE.equals(root.get("accepted"));
        String recovered = text(root, "recovered");
        if (recovered.isBlank() && root.containsKey("recovered")) {
            recovered = Long.toString(SimpleJson.number(root.get("recovered")));
        }
        String code = text(root, "code");
        if (!code.isBlank() && !code.equals("RECOVERED")) {
            accepted = false;
        }
        if (code.isBlank()) {
            code = accepted ? "RECOVERED" : "FAILED";
        }
        return new JobRecoveryView(accepted, accepted ? recovered : "", code);
    }

    private static JobView job(Map<?, ?> object) {
        String id = text(object, "id");
        if (id.isBlank()) {
            id = text(object, "jobId");
        }
        String error = text(object, "error");
        if (error.isBlank()) {
            error = text(object, "errorMessage");
        }
        return new JobView(
            id,
            text(object, "type"),
            text(object, "state"),
            text(object, "targetNode"),
            SimpleJson.number(object.get("attempts")),
            error
        );
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }
}
