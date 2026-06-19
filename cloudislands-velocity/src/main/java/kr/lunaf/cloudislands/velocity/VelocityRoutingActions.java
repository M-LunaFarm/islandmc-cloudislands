package kr.lunaf.cloudislands.velocity;

import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.velocity.message.VelocityMessages;
import kr.lunaf.cloudislands.velocity.message.VelocityRoutePrivacyFormatter;
import kr.lunaf.cloudislands.velocity.routing.PendingRouteService;
import kr.lunaf.cloudislands.velocity.routing.RouteFallbackService;
import kr.lunaf.cloudislands.velocity.routing.RouteProgressPresenter;
import kr.lunaf.cloudislands.velocity.routing.RouteRequestGuard;
import kr.lunaf.cloudislands.velocity.routing.RouteTicketRouter;
import kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver;

public final class VelocityRoutingActions {
    private final Supplier<String> statusSummary;
    private final VelocityPlayerRoutingActions playerRouting;
    private final VelocityPlayerMembershipActions playerMembership;
    private final VelocityPlayerProgressionActions playerProgression;
    private final VelocityAdminActions admin;

    public VelocityRoutingActions(
            CoreApiClient coreApiClient,
            boolean hideNodeNames,
            VelocityMessages messages,
            VelocityRoutePrivacyFormatter routePrivacy,
            RouteFallbackService fallbackService,
            RouteProgressPresenter progressPresenter,
            RouteRequestGuard routeRequestGuard,
            RouteTicketRouter routeTickets,
            VelocityTargetResolver targetResolver,
            PendingRouteService pendingRoutes,
            Supplier<String> statusSummary) {
        VelocityActionContext context = new VelocityActionContext(coreApiClient, hideNodeNames, messages, routePrivacy, fallbackService, progressPresenter, routeRequestGuard, routeTickets, targetResolver, pendingRoutes);
        this.statusSummary = statusSummary;
        this.playerRouting = new VelocityPlayerRoutingActions(context);
        this.playerMembership = new VelocityPlayerMembershipActions(context);
        this.playerProgression = new VelocityPlayerProgressionActions(context);
        this.admin = new VelocityAdminActions(context, playerProgression);
    }

    public String statusSummary() {
        return statusSummary.get();
    }

    public VelocityPlayerRoutingActions playerRouting() {
        return playerRouting;
    }

    public VelocityPlayerMembershipActions playerMembership() {
        return playerMembership;
    }

    public VelocityPlayerProgressionActions playerProgression() {
        return playerProgression;
    }

    public VelocityAdminActions admin() {
        return admin;
    }

    String routeClearMessage(String body) {
        return admin.routeClearMessage(body);
    }
}
