package kr.lunaf.cloudislands.velocity;

import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.velocity.message.VelocityMessages;
import kr.lunaf.cloudislands.velocity.message.VelocityRoutePrivacyFormatter;
import kr.lunaf.cloudislands.velocity.routing.PendingRouteService;
import kr.lunaf.cloudislands.velocity.routing.RouteFallbackService;
import kr.lunaf.cloudislands.velocity.routing.RouteProgressPresenter;
import kr.lunaf.cloudislands.velocity.routing.RouteRequestGuard;
import kr.lunaf.cloudislands.velocity.routing.RouteTicketRouter;
import kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver;

record VelocityActionContext(
    CoreApiClient coreApiClient,
    boolean hideNodeNames,
    VelocityMessages messages,
    VelocityRoutePrivacyFormatter routePrivacy,
    RouteFallbackService fallbackService,
    RouteProgressPresenter progressPresenter,
    RouteRequestGuard routeRequestGuard,
    RouteTicketRouter routeTickets,
    VelocityTargetResolver targetResolver,
    PendingRouteService pendingRoutes
) {
}
