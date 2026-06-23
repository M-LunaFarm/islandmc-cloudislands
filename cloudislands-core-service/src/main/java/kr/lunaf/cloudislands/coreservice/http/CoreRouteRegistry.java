package kr.lunaf.cloudislands.coreservice.http;

import com.sun.net.httpserver.HttpHandler;

@FunctionalInterface
public interface CoreRouteRegistry {
    void route(String path, HttpHandler handler);

    default void routeGet(String path, HttpHandler handler) {
        routeMethods(path, handler, "GET");
    }

    default void routePost(String path, HttpHandler handler) {
        routeMethods(path, handler, "POST");
    }

    default void routeMethods(String path, HttpHandler handler, String... methods) {
        route(path, handler);
    }
}
