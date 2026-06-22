package kr.lunaf.cloudislands.paper.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.IslandVisitorStatsView;
import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase;
import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase.ReviewActionResult;
import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase.ReviewListView;
import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase.ReviewView;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.PublicIslandView;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandVisitMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandVisitReviewCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final IslandNavigationUseCase navigationUseCase;
    private final Runtime runtime;

    IslandVisitReviewCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.navigationUseCase = new IslandNavigationUseCase(coreApiClient);
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
        if (subcommand.equals("visitor-stats") || subcommand.equals("visitorstats") || subcommand.equals("visitors") || subcommand.equals("방문통계") || subcommand.equals("방문자통계")) {
            listVisitorStats(player, args.length > 1 ? integer(args[1], 10) : 10);
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
        if (action instanceof GuiAction.VisitTarget visitTarget) {
            routeVisitTarget(player, visitTarget.target());
            return true;
        }
        if (action instanceof GuiAction.NoPayload noPayload) {
            return switch (noPayload.type()) {
                case VISIT_OPEN -> {
                    IslandVisitMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
                    yield true;
                }
                case VISIT_RANDOM -> {
                    routeRandomVisit(player);
                    yield true;
                }
                case VISIT_PUBLIC_OPEN -> {
                    listPublicIslands(player, 10);
                    yield true;
                }
                default -> false;
            };
        }
        return false;
    }

    private void routeVisitTarget(Player player, String target) {
        runtime.routeTicket(player, navigationUseCase.resolveVisitTarget(player.getUniqueId(), target, runtime::mutate), "해당 섬에 방문할 수 없습니다.");
    }

    private void routeRandomVisit(Player player) {
        runtime.routeTicket(player, navigationUseCase.createRandomVisitTicket(player.getUniqueId(), runtime::mutate), "방문 가능한 공개 섬을 찾지 못했습니다.");
    }

    private void listPublicIslands(Player player, int limit) {
        navigationUseCase.publicIslandViews(limit)
            .thenAccept(islands -> runtime.message(player, publicIslandListMessage(islands)))
            .exceptionally(error -> {
                runtime.message(player, "공개 섬 목록을 불러오지 못했습니다.");
                return null;
            });
    }

    private void listIslandReviews(Player player, int limit) {
        runtime.currentIsland(player, "섬 안에서만 후기를 확인할 수 있습니다.").ifPresent(islandId -> {
            navigationUseCase.reviewViews(islandId, limit)
                .thenAccept(reviews -> runtime.message(player, reviewListMessage(reviews)))
                .exceptionally(error -> {
                    runtime.message(player, "섬 후기를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void listVisitorStats(Player player, int limit) {
        runtime.currentIsland(player, "섬 안에서만 방문 통계를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.visitorStats().stats(islandId, Math.max(1, Math.min(limit, 100)))
                .thenAccept(stats -> runtime.message(player, visitorStatsMessage(stats)))
                .exceptionally(error -> {
                    runtime.message(player, "방문 통계를 불러오지 못했습니다.");
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
        navigationUseCase.setReviewAction(islandId, player.getUniqueId(), rating, comment, runtime::mutateIdempotent)
            .thenAccept(result -> {
                if (!result.accepted()) {
                    runtime.message(player, reviewFailureMessage(result));
                    return;
                }
                runtime.message(player, "섬 평가 저장 완료: " + rating + "/5");
            })
            .exceptionally(error -> {
                runtime.message(player, runtime.coreWriteFailureMessage(error, "섬 평가를 저장하지 못했습니다."));
                return null;
            });
    }

    private String reviewFailureMessage(ReviewActionResult result) {
        return result.code().isBlank()
            ? "섬 평가를 저장하지 못했습니다."
            : runtime.playerCodeMessage(result.code(), "섬 평가를 저장하지 못했습니다.");
    }

    private static String publicIslandListMessage(List<PublicIslandView> islands) {
        List<PublicIslandView> safeIslands = islands == null ? List.of() : islands;
        java.util.ArrayList<String> entries = new java.util.ArrayList<>();
        for (PublicIslandView island : safeIslands) {
            if (entries.size() >= 20) {
                break;
            }
            if (island.islandId().isBlank()) {
                continue;
            }
            String name = island.name().isBlank() ? "이름 없는 섬" : island.name();
            String worth = island.worth().isBlank() ? "0" : island.worth();
            entries.add((entries.size() + 1) + ". " + name + " (ID=" + compactId(island.islandId()) + ", 레벨=" + island.level() + ", 가치=" + worth + ")");
        }
        return entries.isEmpty() ? "공개 섬이 없습니다." : "공개 섬: " + String.join(" | ", entries);
    }

    private static String reviewListMessage(ReviewListView reviews) {
        if (reviews == null || reviews.reviews().isEmpty()) {
            return "섬 후기가 없습니다.";
        }
        String average = String.format(Locale.ROOT, "%.2f", reviews.average());
        List<String> entries = reviews.reviews().stream()
            .limit(10)
            .filter(review -> !review.reviewerUuid().isBlank())
            .map(IslandVisitReviewCommandHandler::reviewEntry)
            .toList();
        if (entries.isEmpty()) {
            return "섬 후기가 없습니다.";
        }
        return "섬 후기: 평균=" + average + " 개수=" + reviews.count() + " | " + String.join(" | ", entries);
    }

    private static String reviewEntry(ReviewView review) {
        return compactId(review.reviewerUuid()) + "=" + review.rating() + "/5" + (review.comment().isBlank() ? "" : " " + review.comment());
    }

    private static String visitorStatsMessage(IslandVisitorStatsView stats) {
        if (stats == null) {
            return "방문 통계를 불러오지 못했습니다.";
        }
        List<String> recent = stats.recentVisitors().stream()
            .limit(10)
            .filter(visitor -> !visitor.visitorUuid().isBlank())
            .map(IslandVisitReviewCommandHandler::visitorEntry)
            .toList();
        return "방문 통계: 전체=" + stats.totalVisits()
            + " 고유=" + stats.uniqueVisitors()
            + (recent.isEmpty() ? "" : " 최근=" + String.join(", ", recent));
    }

    private static String visitorEntry(IslandVisitorStatsView.RecentVisitorView visitor) {
        return compactId(visitor.visitorUuid()) + (visitor.lastVisitedAt().isBlank() ? "" : "@" + visitor.lastVisitedAt());
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
        if (value == null || value.length() <= 8) {
            return String.valueOf(value);
        }
        return new StringBuilder(8).append(value, 0, 8).toString();
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
