package kr.lunaf.cloudislands.velocity.message;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.arrayValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.longValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.matchingObjectEnd;

import java.util.ArrayList;
import java.util.List;

public final class VelocitySnapshotMessageFormatter {
    public String snapshotList(String body) {
        String snapshots = arrayValue(body, "snapshots");
        if (snapshots.isBlank()) {
            return "섬 스냅샷이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < snapshots.length() && entries.size() < 20) {
            int objectStart = snapshots.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(snapshots, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = snapshots.substring(objectStart, objectEnd + 1);
            long snapshotNo = longValue(object, "snapshotNo");
            if (snapshotNo > 0L) {
                String reason = jsonValue(object, "reason");
                long sizeBytes = longValue(object, "sizeBytes");
                String createdAt = jsonValue(object, "createdAt");
                String checksum = jsonValue(object, "checksum");
                entries.add("#" + snapshotNo
                    + (reason.isBlank() ? "" : " 사유=" + reason)
                    + " 크기=" + sizeBytes
                    + (checksum.isBlank() ? "" : " checksum=" + shortChecksum(checksum))
                    + (createdAt.isBlank() ? "" : " 생성=" + createdAt));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 스냅샷이 없습니다." : "섬 스냅샷: " + String.join(" | ", entries);
    }

    private String shortChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) {
            return "";
        }
        return checksum.length() > 12 ? checksum.substring(0, 12) : checksum;
    }
}
