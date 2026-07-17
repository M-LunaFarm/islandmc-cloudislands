package kr.lunaf.cloudislands.paper.command;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.IslandVisitorStatsView;
import kr.lunaf.cloudislands.paper.PlayerConnectionSession;
import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase;
import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase.ReviewActionResult;
import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase.ReviewListView;
import kr.lunaf.cloudislands.paper.application.IslandNavigationUseCase.ReviewView;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.PublicIslandView;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandReviewMenu;
import kr.lunaf.cloudislands.paper.gui.IslandVisitMenu;
import kr.lunaf.cloudislands.paper.gui.IslandVisitorStatsMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandVisitReviewCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final IslandNavigationUseCase navigationUseCase;
    private final IslandTargetResolver targetResolver;
    private final Runtime runtime;

    IslandVisitReviewCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.navigationUseCase = new IslandNavigationUseCase(coreApiClient);
        this.targetResolver = new IslandTargetResolver(coreApiClient);
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
        if (subcommand.equals("reviews") || subcommand.equals("review-list") || subcommand.equals("ratings") || subcommand.equals("후기") || subcommand.equals("후기목록") || subcommand.equals("평가목록")) {
            listIslandReviews(player, args.length > 1 ? integer(args[1], 10) : 10);
            return true;
        }
        if (subcommand.equals("visitors")) {
            listCurrentVisitors(player);
            return true;
        }
        if (subcommand.equals("visitor-stats") || subcommand.equals("visitorstats") || subcommand.equals("방문통계") || subcommand.equals("방문자통계")) {
            listVisitorStats(player, args.length > 1 ? integer(args[1], 10) : 10);
            return true;
        }
        if (subcommand.equals("rate") || subcommand.equals("review") || subcommand.equals("평가")) {
            if (args.length < 3) {
                runtime.message(player, message("input-review-required", "평가할 섬과 1~5점 평점을 입력해주세요."));
                return true;
            }
            rateIslandReview(player, args[1], integer(args[2], 0), args.length > 3 ? joined(args, 3) : "");
            return true;
        }
        if (subcommand.equals("report-review") || subcommand.equals("review-report") || subcommand.equals("reviewreport") || subcommand.equals("후기신고") || subcommand.equals("평가신고")) {
            if (args.length < 3) {
                runtime.message(player, message("input-review-report-required", "신고할 섬과 후기 작성자를 입력해주세요."));
                return true;
            }
            reportIslandReview(player, args[1], args[2], args.length > 3 ? joined(args, 3) : "");
            return true;
        }
        if (subcommand.equals("delete-review") || subcommand.equals("review-delete") || subcommand.equals("reviewdel") || subcommand.equals("후기삭제") || subcommand.equals("평가삭제")) {
            deleteIslandReview(player, args.length > 1 ? args[1] : "current");
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action) {
        if (action instanceof GuiAction.VisitTarget visitTarget) {
            routeVisitTarget(player, visitTarget.target());
            return true;
        }
        if (action instanceof GuiAction.PublicIslandPage page) {
            IslandVisitMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player), page.page());
            return true;
        }
        if (action instanceof GuiAction.ReviewSet reviewSet) {
            submitIslandReview(player, reviewSet.islandId(), reviewSet.rating(), reviewSet.comment());
            return true;
        }
        if (action instanceof GuiAction.ReviewDelete reviewDelete) {
            submitReviewDelete(player, reviewDelete.islandId());
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
                case REVIEWS_OPEN -> {
                    openReviewMenu(player);
                    yield true;
                }
                case VISITOR_STATS_OPEN -> {
                    openVisitorStatsMenu(player);
                    yield true;
                }
                default -> false;
            };
        }
        return false;
    }

    private void openReviewMenu(Player player) {
        runtime.currentIsland(player, message("review-menu-island-required", "섬 안에서만 후기 메뉴를 열 수 있습니다.")).ifPresent(islandId -> IslandReviewMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void openVisitorStatsMenu(Player player) {
        runtime.currentIsland(player, message("visitor-stats-menu-island-required", "섬 안에서만 방문 통계 메뉴를 열 수 있습니다.")).ifPresent(islandId -> IslandVisitorStatsMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void listCurrentVisitors(Player player) {
        PlayerConnectionSession viewerSession = PlayerConnectionSession.capture(player);
        runtime.currentIsland(player, message("visitors-island-required", "섬 안에서만 현재 방문자를 확인할 수 있습니다.")).ifPresent(islandId -> {
            UUID viewerUuid = viewerSession.playerUuid();
            coreApiClient.islands().memberSnapshots(islandId).whenComplete((members, error) -> PaperSchedulers.run(plugin, () -> {
                Player activeViewer = plugin.getServer().getPlayer(viewerUuid);
                if (!viewerSession.isCurrent(activeViewer)) {
                    return;
                }
                if (error != null || members == null) {
                    runtime.message(activeViewer, message("visitors-load-failed", "현재 방문자를 불러오지 못했습니다."));
                    return;
                }
                if (!runtime.currentIsland(activeViewer).filter(islandId::equals).isPresent()) {
                    runtime.message(activeViewer, message("visitors-island-required", "섬 안에서만 현재 방문자를 확인할 수 있습니다."));
                    return;
                }
                Map<UUID, String> roles = new HashMap<>();
                for (IslandMemberSnapshot member : members) {
                    if (member != null && member.playerUuid() != null) {
                        roles.putIfAbsent(member.playerUuid(), member.effectiveRoleKey());
                    }
                }
                List<String> visitors = plugin.getServer().getOnlinePlayers().stream()
                    .filter(candidate -> !candidate.getUniqueId().equals(viewerUuid))
                    .filter(activeViewer::canSee)
                    .filter(candidate -> CurrentIslandVisitorPolicy.visitor(
                        islandId,
                        runtime.currentIsland(candidate).orElse(null),
                        roles.get(candidate.getUniqueId())
                    ))
                    .map(Player::getName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
                runtime.message(activeViewer, visitors.isEmpty()
                    ? message("visitors-empty", "현재 섬에 방문자가 없습니다.")
                    : message("visitors-prefix", "현재 방문자: ") + String.join(", ", visitors));
            }));
        });
    }

    private void routeVisitTarget(Player player, String target) {
        runtime.routeTicket(player, navigationUseCase.resolveVisitTarget(player.getUniqueId(), target, runtime::mutate), message("visit-target-failed", "해당 섬에 방문할 수 없습니다."));
    }

    private void routeRandomVisit(Player player) {
        runtime.routeTicket(player, navigationUseCase.createRandomVisitTicket(player.getUniqueId(), runtime::mutate), message("visit-random-failed", "방문 가능한 공개 섬을 찾지 못했습니다."));
    }

    private void listPublicIslands(Player player, int limit) {
        PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
        navigationUseCase.publicIslandViews(limit)
            .thenAccept(islands -> deliverMessage(playerSession, publicIslandListMessage(islands)))
            .exceptionally(error -> {
                deliverMessage(playerSession, message("public-island-list-load-failed", "공개 섬 목록을 불러오지 못했습니다."));
                return null;
            });
    }

    private void listIslandReviews(Player player, int limit) {
        runtime.currentIsland(player, message("review-list-island-required", "섬 안에서만 후기를 확인할 수 있습니다.")).ifPresent(islandId -> {
            PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
            navigationUseCase.reviewViews(islandId, limit)
                .thenAccept(reviews -> deliverIslandMessage(playerSession, islandId, reviewListMessage(reviews)))
                .exceptionally(error -> {
                    deliverIslandMessage(playerSession, islandId, message("review-list-load-failed", "섬 후기를 불러오지 못했습니다."));
                    return null;
                });
        });
    }

    private void listVisitorStats(Player player, int limit) {
        runtime.currentIsland(player, message("visitor-stats-island-required", "섬 안에서만 방문 통계를 확인할 수 있습니다.")).ifPresent(islandId -> {
            PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
            navigationUseCase.visitorStats(islandId, limit)
                .thenAccept(stats -> deliverIslandMessage(playerSession, islandId, visitorStatsMessage(stats)))
                .exceptionally(error -> {
                    deliverIslandMessage(playerSession, islandId, message("visitor-stats-load-failed", "방문 통계를 불러오지 못했습니다."));
                    return null;
                });
        });
    }

    private void rateIslandReview(Player player, String target, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            runtime.message(player, message("input-review-rating-invalid", "평점은 1~5 사이여야 합니다."));
            return;
        }
        PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
        if (target.equalsIgnoreCase("current") || target.equals("현재")) {
            runtime.currentIsland(player, message("review-current-island-required", "섬 안에서만 현재 섬을 평가할 수 있습니다.")).ifPresent(current -> submitIslandReview(playerSession, current, rating, comment));
            return;
        }
        targetResolver.resolve(target)
            .thenAccept(islandId -> submitIslandReview(playerSession, islandId, rating, comment))
            .exceptionally(error -> {
                deliverMessage(playerSession, message("review-target-not-found", "평가할 섬 또는 플레이어를 찾지 못했습니다."));
                return null;
            });
    }

    private void submitIslandReview(Player player, UUID islandId, int rating, String comment) {
        submitIslandReview(PlayerConnectionSession.capture(player), islandId, rating, comment);
    }

    private void submitIslandReview(PlayerConnectionSession playerSession, UUID islandId, int rating, String comment) {
        UUID playerUuid = playerSession.playerUuid();
        navigationUseCase.setReviewAction(islandId, playerUuid, rating, comment, runtime::mutateIdempotent)
            .thenAccept(result -> {
                if (!result.accepted()) {
                    deliverMessage(playerSession, reviewFailureMessage(result));
                    return;
                }
                deliverMessage(playerSession, message("review-save-success-prefix", "섬 평가 저장 완료: ") + rating + "/5");
            })
            .exceptionally(error -> {
                deliverMessage(playerSession, runtime.coreWriteFailureMessage(error, message("review-save-failed", "섬 평가를 저장하지 못했습니다.")));
                return null;
            });
    }

    private void reportIslandReview(Player player, String islandTarget, String reviewerTarget, String reason) {
        PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
        if (islandTarget.equalsIgnoreCase("current") || islandTarget.equals("현재")) {
            runtime.currentIsland(player, message("review-report-current-island-required", "섬 안에서만 현재 섬 후기를 신고할 수 있습니다."))
                .ifPresent(islandId -> resolveAndSubmitReviewReport(playerSession, islandId, reviewerTarget, reason));
            return;
        }
        targetResolver.resolve(islandTarget)
            .thenAccept(islandId -> resolveAndSubmitReviewReport(playerSession, islandId, reviewerTarget, reason))
            .exceptionally(error -> {
                deliverMessage(playerSession, message("review-report-target-not-found", "신고할 섬 또는 후기 작성자를 찾지 못했습니다."));
                return null;
            });
    }

    private void resolveAndSubmitReviewReport(PlayerConnectionSession playerSession, UUID islandId, String reviewerTarget, String reason) {
        targetResolver.resolvePlayerUuid(reviewerTarget)
            .thenAccept(reviewerUuid -> submitReviewReport(playerSession, islandId, reviewerUuid, reason))
            .exceptionally(error -> {
                deliverMessage(playerSession, message("review-report-target-not-found", "신고할 섬 또는 후기 작성자를 찾지 못했습니다."));
                return null;
            });
    }

    private void submitReviewReport(PlayerConnectionSession playerSession, UUID islandId, UUID reviewerUuid, String reason) {
        navigationUseCase.reportReviewAction(islandId, reviewerUuid, playerSession.playerUuid(), reason, runtime::mutateIdempotent)
            .thenAccept(result -> {
                if (!result.accepted()) {
                    deliverMessage(playerSession, runtime.playerCodeMessage(result.code(), message("review-report-failed", "섬 후기를 신고하지 못했습니다.")));
                    return;
                }
                deliverMessage(playerSession, message("review-report-success-prefix", "후기 신고 접수 완료: 누적 신고=") + result.reportCount());
            })
            .exceptionally(error -> {
                deliverMessage(playerSession, runtime.coreWriteFailureMessage(error, message("review-report-failed", "섬 후기를 신고하지 못했습니다.")));
                return null;
            });
    }

    private void deleteIslandReview(Player player, String target) {
        PlayerConnectionSession playerSession = PlayerConnectionSession.capture(player);
        if (target == null || target.isBlank() || target.equalsIgnoreCase("current") || target.equals("현재")) {
            runtime.currentIsland(player, message("review-delete-current-island-required", "섬 안에서만 현재 섬 후기를 삭제할 수 있습니다.")).ifPresent(current -> submitReviewDelete(playerSession, current));
            return;
        }
        targetResolver.resolve(target)
            .thenAccept(islandId -> submitReviewDelete(playerSession, islandId))
            .exceptionally(error -> {
                deliverMessage(playerSession, message("review-target-not-found", "평가할 섬 또는 플레이어를 찾지 못했습니다."));
                return null;
            });
    }

    private void submitReviewDelete(Player player, UUID islandId) {
        submitReviewDelete(PlayerConnectionSession.capture(player), islandId);
    }

    private void submitReviewDelete(PlayerConnectionSession playerSession, UUID islandId) {
        UUID playerUuid = playerSession.playerUuid();
        navigationUseCase.deleteReviewAction(islandId, playerUuid, runtime::mutateIdempotent)
            .thenAccept(result -> {
                if (!result.accepted()) {
                    deliverMessage(playerSession, result.code().equals("REVIEW_NOT_FOUND")
                        ? message("review-delete-not-found", "삭제할 섬 후기가 없습니다.")
                        : runtime.playerCodeMessage(result.code(), message("review-delete-failed", "섬 후기를 삭제하지 못했습니다.")));
                    return;
                }
                deliverMessage(playerSession, message("review-delete-success", "섬 후기 삭제 완료"));
            })
            .exceptionally(error -> {
                deliverMessage(playerSession, runtime.coreWriteFailureMessage(error, message("review-delete-failed", "섬 후기를 삭제하지 못했습니다.")));
                return null;
            });
    }

    private String reviewFailureMessage(ReviewActionResult result) {
        return result.code().isBlank()
            ? message("review-save-failed", "섬 평가를 저장하지 못했습니다.")
            : runtime.playerCodeMessage(result.code(), message("review-save-failed", "섬 평가를 저장하지 못했습니다."));
    }

    private String publicIslandListMessage(List<PublicIslandView> islands) {
        List<PublicIslandView> safeIslands = islands == null ? List.of() : islands;
        java.util.ArrayList<String> entries = new java.util.ArrayList<>();
        for (PublicIslandView island : safeIslands) {
            if (entries.size() >= 20) {
                break;
            }
            if (island.islandId().isBlank()) {
                continue;
            }
            String name = island.name().isBlank() ? message("public-island-unnamed", "이름 없는 섬") : island.name();
            String worth = island.worth().isBlank() ? "0" : island.worth();
            entries.add((entries.size() + 1) + ". " + name
                + " (" + message("public-island-id-label", "ID=") + compactId(island.islandId())
                + ", " + message("public-island-level-label", "레벨=") + island.level()
                + ", " + message("public-island-worth-label", "가치=") + worth + ")");
        }
        return entries.isEmpty() ? message("public-island-list-empty", "공개 섬이 없습니다.") : message("public-island-list-prefix", "공개 섬: ") + String.join(" | ", entries);
    }

    private String reviewListMessage(ReviewListView reviews) {
        if (reviews == null || reviews.reviews().isEmpty()) {
            return message("review-list-empty", "섬 후기가 없습니다.");
        }
        String average = String.format(Locale.ROOT, "%.2f", reviews.average());
        List<String> entries = reviews.reviews().stream()
            .limit(10)
            .filter(review -> !review.reviewerUuid().isBlank())
            .map(IslandVisitReviewCommandHandler::reviewEntry)
            .toList();
        if (entries.isEmpty()) {
            return message("review-list-empty", "섬 후기가 없습니다.");
        }
        return message("review-list-prefix", "섬 후기: ")
            + message("review-list-average-label", "평균=") + average + " "
            + message("review-list-count-label", "개수=") + reviews.count()
            + " | " + String.join(" | ", entries);
    }

    static String reviewEntry(ReviewView review) {
        return reviewerDisplayName(review) + "=" + review.rating() + "/5" + (review.comment().isBlank() ? "" : " " + review.comment());
    }

    static String reviewerDisplayName(ReviewView review) {
        return review == null || review.reviewerName().isBlank() ? compactId(review == null ? "" : review.reviewerUuid()) : review.reviewerName().trim();
    }

    private String visitorStatsMessage(IslandVisitorStatsView stats) {
        if (stats == null) {
            return message("visitor-stats-load-failed", "방문 통계를 불러오지 못했습니다.");
        }
        List<String> recent = stats.recentVisitors().stream()
            .limit(10)
            .filter(visitor -> !visitor.visitorUuid().isBlank())
            .map(IslandVisitReviewCommandHandler::visitorEntry)
            .toList();
        return message("visitor-stats-prefix", "방문 통계: ")
            + message("visitor-stats-total-label", "전체=") + stats.totalVisits()
            + " " + message("visitor-stats-unique-label", "고유=") + stats.uniqueVisitors()
            + (recent.isEmpty() ? "" : " " + message("visitor-stats-recent-label", "최근=") + String.join(", ", recent));
    }

    static String visitorEntry(IslandVisitorStatsView.RecentVisitorView visitor) {
        return visitorDisplayName(visitor) + (visitor.lastVisitedAt().isBlank() ? "" : "@" + visitor.lastVisitedAt());
    }

    static String visitorDisplayName(IslandVisitorStatsView.RecentVisitorView visitor) {
        return visitor == null || visitor.visitorName().isBlank() ? compactId(visitor == null ? "" : visitor.visitorUuid()) : visitor.visitorName().trim();
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

    private void deliverMessage(PlayerConnectionSession playerSession, String detail) {
        PaperSchedulers.run(plugin, () -> messageCurrentPlayer(playerSession, detail));
    }

    private void deliverIslandMessage(PlayerConnectionSession playerSession, UUID islandId, String detail) {
        PaperSchedulers.run(plugin, () -> {
            Player activePlayer = currentPlayer(playerSession);
            if (activePlayer != null && runtime.currentIsland(activePlayer).filter(islandId::equals).isPresent()) {
                runtime.message(activePlayer, detail);
            }
        });
    }

    private void messageCurrentPlayer(PlayerConnectionSession playerSession, String detail) {
        Player activePlayer = currentPlayer(playerSession);
        if (activePlayer != null) {
            runtime.message(activePlayer, detail);
        }
    }

    private Player currentPlayer(PlayerConnectionSession playerSession) {
        Player activePlayer = plugin.getServer().getPlayer(playerSession.playerUuid());
        return playerSession.isCurrent(activePlayer) ? activePlayer : null;
    }

    private String message(String key, String fallback) {
        return runtime.routeMessage(key, fallback);
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

    private static String compactId(String value) {
        if (value == null || value.length() <= 8) {
            return String.valueOf(value);
        }
        return new StringBuilder(8).append(value, 0, 8).toString();
    }

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        Optional<UUID> currentIsland(Player player);

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
