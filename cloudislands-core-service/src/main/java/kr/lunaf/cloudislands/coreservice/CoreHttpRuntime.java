package kr.lunaf.cloudislands.coreservice;

import com.sun.net.httpserver.HttpServer;
import java.time.Duration;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpRequestExecutor;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpRouteRegistrar;

public record CoreHttpRuntime(
    HttpServer server,
    HttpServer adminServer,
    CoreHttpRequestExecutor httpExecutor,
    CoreHttpRequestExecutor adminHttpExecutor,
    Duration httpShutdownGrace,
    CoreHttpRouteRegistrar routeRegistrar,
    CoreHttpRouteRegistrar adminRouteRegistrar
) {}
