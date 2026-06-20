package kr.lunaf.cloudislands.paper.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandVisitMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandVisitReviewCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final Runtime runtime;

    IslandVisitReviewCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("visit") || subcommand.equals("방문")) {
            if (args.length < 2) {
                IslandVisitMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
            } else if (args[1].equalsIgnoreCase("random") || args[1].equals("랜덤")) {
                routeRandomVisit(player);
            } else {
                routeVisitTarget(player, args[1]);
            }
            return true;
        }
        if (subcommand.equals("randomvisit") || subcommand.equals("random-visit") || subcommand.equals("랜덤방문")) {
            routeRandomVisit(player);
            return true;
        }
        if (subcommand.equals("public-islands") || subcommand.equals("publicislands") || subcommand.equals("visit-list") || subcommand.equals("공개섬") || subcommand.equals("방문목록")) {
            listPublicIslands(player, rankingLimit(args, 1));
            return true;
        }
        if (subcommand.equals("reviews") || subcommand.equals("review-list") || subcommand.equals("후기") || subcommand.equals("후기목록")) {
            listIslandReviews(player, args.length > 1 ? integer(args[1], 10) : 10);
            return true;
        }
        if (subcommand.equals("rate") || subcommand.equals("review") || subcommand.equals("평가")) {
            if (args.length < 3) {
                runtime.message(player, runtime.routeMessage("input-review-required", "평가할 섬과 1~5점 평점을 입력해주세요."));
                return true;
            }
            rateIslandReview(player, args[1], integer(args[2], 0), args.length > 3 ? joined(args, 3) : "");
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action) {
        String actionId = action.actionId();
        Map<String, String> data = action.data();
        return switch (actionId) {
            case "island.visit.open" -> {
                IslandVisitMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
                yield true;
            }
            case "island.visit.random" -> {
                routeRandomVisit(player);
                yield true;
            }
            case "island.visit.public.open" -> {
                listPublicIslands(player, 10);
                yield true;
            }
            case "island.visit.target" -> {
                routeVisitTarget(player, data.getOrDefault("target", ""));
                yield true;
            }
            default -> false;
        };
    }

    private void routeVisitTarget(Player player, String target) {
        UUID islandId = uuid(target);
        if (islandId != null) {
            routeVisit(player, islandId);
            return;
        }
        coreApiClient.playerInfoByName(target).thenAccept(body -> {
            UUID primaryIslandId = uuid(text(body, "primaryIslandId"));
            if (primaryIslandId != null) {
                UUID ownerUuid = uuid(text(body, "playerUuid"));
                if (ownerUuid != null) {
                    runtime.routeTicket(player, runtime.mutate("route.ticket.visit.owner", () -> coreApiClient.createVisitTicketForOwner(player.getUniqueId(), ownerUuid)), "해당 섬에 방문할 수 없습니다.");
                } else {
                    routeVisit(player, primaryIslandId);
                }
                return;
            }
            routeVisitName(player, target);
        }).exceptionally(error -> {
            routeVisitName(player, target);
            return null;
        });
    }

    private void routeVisitName(Player player, String islandName) {
        runtime.routeTicket(player, runtime.mutate("route.ticket.visit.name", () -> coreApiClient.createVisitTicket(player.getUniqueId(), islandName)), "해당 섬에 방문할 수 없습니다.");
    }

    private void routeVisit(Player player, UUID islandId) {
        runtime.routeTicket(player, runtime.mutate("route.ticket.visit", () -> coreApiClient.createVisitTicket(player.getUniqueId(), islandId)), "해당 섬에 방문할 수 없습니다.");
    }

    private void routeRandomVisit(Player player) {
        runtime.routeTicket(player, runtime.mutate("route.ticket.random-visit", () -> coreApiClient.createRandomVisitTicket(player.getUniqueId())), "방문 가능한 공개 섬을 찾지 못했습니다.");
    }

    private void listPublicIslands(Player player, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        coreApiClient.listPublicIslands(cappedLimit)
            .thenAccept(body -> runtime.message(player, publicIslandListMessage(body)))
            .exceptionally(error -> {
                runtime.message(player, "공개 섬 목록을 불러오지 못했습니다.");
                return null;
            });
    }

    private void listIslandReviews(Player player, int limit) {
        runtime.currentIsland(player, "섬 안에서만 후기를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandReviews(islandId, Math.max(1, Math.min(limit, 100)))
                .thenAccept(body -> runtime.message(player, reviewListMessage(body)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 후기를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void rateIslandReview(Player player, String target, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            runtime.message(player, runtime.routeMessage("input-review-rating-invalid", "평점은 1~5 사이여야 합니다."));
            return;
        }
        UUID islandId = uuid(target);
        if (islandId == null && (target.equalsIgnoreCase("current") || target.equals("현재"))) {
            runtime.currentIsland(player, "섬 안에서만 현재 섬을 평가할 수 있습니다.").ifPresent(current -> submitIslandReview(player, current, rating, comment));
            return;
        }
        if (islandId == null) {
            runtime.message(player, runtime.routeMessage("input-island-uuid-invalid", "섬 UUID가 올바르지 않습니다."));
            return;
        }
        submitIslandReview(player, islandId, rating, comment);
    }

    private void submitIslandReview(Player player, UUID islandId, int rating, String comment) {
        runtime.mutateIdempotent("island.review.set", () -> coreApiClient.setIslandReview(islandId, player.getUniqueId(), rating, comment))
            .thenAccept(body -> {
                String code = text(body, "code");
                if (!code.isBlank()) {
                    runtime.message(player, runtime.playerCodeMessage(code, "섬 평가를 저장하지 못했습니다."));
                    return;
                }
                runtime.message(player, "섬 평가 저장 완료: " + rating + "/5");
            })
            .exceptionally(error -> {
                runtime.message(player, runtime.coreWriteFailureMessage(error, "섬 평가를 저장하지 못했습니다."));
                return null;
            });
    }

    private static String publicIslandListMessage(String body) {
        if (body == null || body.isBlank()) {
            return "공개 섬이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        int index = body.indexOf("\"islands\"");
        while (index >= 0 && index < body.length() && entries.size() < 20) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String islandId = text(object, "islandId");
            if (!islandId.isBlank()) {
                String name = text(object, "name");
                long level = (long) decimal(object, "level");
                String worth = text(object, "worth");
                if (worth.isBlank()) {
                    worth = Long.toString((long) decimal(object, "worth"));
                }
                entries.add((entries.size() + 1) + ". " + (name.isBlank() ? "이름 없는 섬" : name) + " (ID=" + compactId(islandId) + ", 레벨=" + level + ", 가치=" + worth + ")");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "공개 섬이 없습니다." : "공개 섬: " + String.join(" | ", entries);
    }

    private static String reviewListMessage(String body) {
        if (body == null || body.isBlank()) {
            return "섬 후기가 없습니다.";
        }
        long count = (long) decimal(body, "count");
        String average = String.format(Locale.ROOT, "%.2f", decimal(body, "average"));
        List<String> entries = new ArrayList<>();
        int index = body.indexOf("\"reviews\"");
        while (index >= 0 && index < body.length() && entries.size() < 10) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String reviewerUuid = text(object, "reviewerUuid");
            long rating = (long) decimal(object, "rating");
            String comment = text(object, "comment");
            if (!reviewerUuid.isBlank()) {
                entries.add(compactId(reviewerUuid) + "=" + rating + "/5" + (comment.isBlank() ? "" : " " + comment));
            }
            index = objectEnd + 1;
        }
        if (entries.isEmpty()) {
            return "섬 후기가 없습니다.";
        }
        return "섬 후기: 평균=" + average + " 개수=" + count + " | " + String.join(" | ", entries);
    }

    private static String joined(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int rankingLimit(String[] args, int index) {
        if (args.length <= index) {
            return 10;
        }
        return integer(args[index], 10);
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String compactId(String value) {
        return value == null || value.length() <= 8 ? String.valueOf(value) : value.substring(0, 8);
    }

    private static String text(String json, String key) {
        if (json == null || key == null) {
            return "";
        }
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return "";
        }
        int valueStart = start + pattern.length();
        int valueEnd = jsonStringEnd(json, valueStart);
        return valueEnd < 0 ? "" : unescape(json.substring(valueStart, valueEnd));
    }

    private static double decimal(String json, String key) {
        if (json == null || key == null) {
            return 0D;
        }
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) {
            return 0D;
        }
        int valueStart = start + pattern.length();
        int valueEnd = valueStart;
        while (valueEnd < json.length()) {
            char current = json.charAt(valueEnd);
            if ((current >= '0' && current <= '9') || current == '-' || current == '+'
                || current == '.' || current == 'e' || current == 'E') {
                valueEnd++;
            } else {
                break;
            }
        }
        try {
            return Double.parseDouble(json.substring(valueStart, valueEnd));
        } catch (RuntimeException ignored) {
            return 0D;
        }
    }

    private static int jsonStringEnd(String value, int start) {
        boolean escaping = false;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!escaping && current == '"') {
                return index;
            }
            escaping = !escaping && current == '\\';
        }
        return -1;
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean escaping = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!escaping) {
                if (current == '\\') {
                    escaping = true;
                } else {
                    builder.append(current);
                }
                continue;
            }
            switch (current) {
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                case '/' -> builder.append('/');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                default -> builder.append(current);
            }
            escaping = false;
        }
        if (escaping) {
            builder.append('\\');
        }
        return builder.toString();
    }

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        void message(Player player, String message);

        String routeMessage(String key, String fallback);

        String playerCodeMessage(String code, String fallback);

        String coreWriteFailureMessage(Throwable error, String fallback);

        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);

        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);

        MessageRenderer messagesFor(Player player);

        void routeTicket(Player player, CompletableFuture<RouteTicket> ticketFuture, String failureMessage);
    }
}
