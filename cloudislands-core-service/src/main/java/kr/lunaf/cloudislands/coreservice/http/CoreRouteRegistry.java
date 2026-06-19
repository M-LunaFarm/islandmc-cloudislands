package kr.lunaf.cloudislands.coreservice.http;

import com.sun.net.httpserver.HttpHandler;

@FunctionalInterface
public interface CoreRouteRegistry {
    void route(String path, HttpHandler handler);
}
