package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;

final class CoreJobJson {
    private CoreJobJson() {
    }

    static List<JobView> jobs(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return CoreJson.objects(root, "jobs").stream()
            .map(CoreJobJson::job)
            .toList();
    }

    static JobActionView action(String body, String successCode) {
        Map<?, ?> root = CoreJson.object(body);
        boolean accepted = CoreJson.acceptedWithCode(root, successCode);
        return new JobActionView(accepted, CoreJson.code(root, successCode, accepted));
    }

    static JobRecoveryView recovery(String body) {
        Map<?, ?> root = CoreJson.object(body);
        boolean accepted = CoreJson.acceptedWithCode(root, "RECOVERED");
        String recovered = CoreJson.text(root, "recovered");
        if (recovered.isBlank() && root.containsKey("recovered")) {
            recovered = Long.toString(CoreJson.number(root, "recovered"));
        }
        return new JobRecoveryView(accepted, accepted ? recovered : "", CoreJson.code(root, "RECOVERED", accepted));
    }

    private static JobView job(Map<?, ?> object) {
        String id = CoreJson.text(object, "id");
        if (id.isBlank()) {
            id = CoreJson.text(object, "jobId");
        }
        String error = CoreJson.text(object, "error");
        if (error.isBlank()) {
            error = CoreJson.text(object, "errorMessage");
        }
        return new JobView(
            id,
            CoreJson.text(object, "type"),
            CoreJson.text(object, "islandId"),
            CoreJson.text(object, "targetNode"),
            CoreJson.text(object, "state"),
            (int) CoreJson.number(object, "priority"),
            CoreJson.number(object, "attempts"),
            CoreJson.text(object, "lockedBy"),
            error,
            CoreJson.stringMap(CoreJson.objectValue(object, "payload")),
            CoreJson.text(object, "createdAt"),
            CoreJson.text(object, "updatedAt")
        );
    }
}
