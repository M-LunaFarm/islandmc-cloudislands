package kr.lunaf.cloudislands.velocity.message;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.arrayValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.longValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.matchingObjectEnd;

import java.util.ArrayList;
import java.util.List;

public final class VelocityIslandMessageFormatter {
    public String playerIslands(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(body, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String islandId = jsonValue(object, "islandId");
            if (!islandId.isBlank()) {
                String name = jsonValue(object, "name");
                String role = jsonValue(object, "role");
                long level = longValue(object, "level");
                entries.add((name.isBlank() ? "이름 없는 섬" : name)
                    + " (ID=" + shortId(islandId)
                    + ", 역할=" + (role.isBlank() ? "MEMBER" : role)
                    + ", 레벨=" + level + ")");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "속한 섬이 없습니다." : "내 섬 목록: " + String.join(" / ", entries);
    }

    public String publicIslands(String body) {
        if (body == null || body.isBlank()) {
            return "공개 섬이 없습니다.";
        }
        String islands = arrayValue(body, "islands");
        if (islands.isBlank()) {
            return "공개 섬이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < islands.length() && entries.size() < 20) {
            int objectStart = islands.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(islands, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = islands.substring(objectStart, objectEnd + 1);
            String islandId = jsonValue(object, "islandId");
            if (!islandId.isBlank()) {
                String name = jsonValue(object, "name");
                long level = longValue(object, "level");
                String worth = jsonValue(object, "worth");
                entries.add((entries.size() + 1) + ". "
                    + (name.isBlank() ? "이름 없는 섬" : name)
                    + " (ID=" + shortId(islandId)
                    + ", 레벨=" + level
                    + ", 가치=" + (worth.isBlank() ? "0" : worth) + ")");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "공개 섬이 없습니다." : "공개 섬: " + String.join(" | ", entries);
    }

    public String invites(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(body, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String inviteId = jsonValue(object, "inviteId");
            if (!inviteId.isBlank()) {
                String islandId = jsonValue(object, "islandId");
                String inviterUuid = jsonValue(object, "inviterUuid");
                entries.add(shortId(inviteId)
                    + (islandId.isBlank() ? "" : " 섬=" + shortId(islandId))
                    + (inviterUuid.isBlank() ? "" : " 초대한사람=" + shortId(inviterUuid)));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "대기 중인 섬 초대가 없습니다." : "섬 초대: " + String.join(", ", entries);
    }

    private static String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() > 8 ? value.substring(0, 8) : value;
    }
}
