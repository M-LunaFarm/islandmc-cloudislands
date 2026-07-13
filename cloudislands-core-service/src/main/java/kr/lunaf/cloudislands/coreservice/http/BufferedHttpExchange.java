package kr.lunaf.cloudislands.coreservice.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

final class BufferedHttpExchange extends HttpExchange {
    private final HttpExchange delegate;
    private final byte[] requestBody;
    private final Headers responseHeaders = new Headers();
    private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
    private int responseCode = -1;

    BufferedHttpExchange(HttpExchange delegate, byte[] requestBody) {
        this.delegate = delegate;
        this.requestBody = requestBody == null ? new byte[0] : requestBody.clone();
    }

    CoreIdempotencyStore.StoredResponse storedResponse() {
        int status = responseCode < 100 ? 500 : responseCode;
        String contentType = responseHeaders.getFirst("Content-Type");
        return new CoreIdempotencyStore.StoredResponse(status, contentType, responseBody.toString(StandardCharsets.UTF_8));
    }

    @Override
    public Headers getRequestHeaders() {
        return delegate.getRequestHeaders();
    }

    @Override
    public Headers getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public URI getRequestURI() {
        return delegate.getRequestURI();
    }

    @Override
    public String getRequestMethod() {
        return delegate.getRequestMethod();
    }

    @Override
    public HttpContext getHttpContext() {
        return delegate.getHttpContext();
    }

    @Override
    public void close() {
        try {
            responseBody.close();
        } catch (IOException ignored) {
            // ByteArrayOutputStream close cannot fail.
        }
    }

    @Override
    public InputStream getRequestBody() {
        return new ByteArrayInputStream(requestBody);
    }

    @Override
    public OutputStream getResponseBody() {
        return responseBody;
    }

    @Override
    public void sendResponseHeaders(int responseCode, long responseLength) {
        this.responseCode = responseCode;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return delegate.getRemoteAddress();
    }

    @Override
    public int getResponseCode() {
        return responseCode;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return delegate.getLocalAddress();
    }

    @Override
    public String getProtocol() {
        return delegate.getProtocol();
    }

    @Override
    public Object getAttribute(String name) {
        return delegate.getAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        delegate.setAttribute(name, value);
    }

    @Override
    public void setStreams(InputStream input, OutputStream output) {
        throw new UnsupportedOperationException("buffered idempotency exchange owns its streams");
    }

    @Override
    public HttpPrincipal getPrincipal() {
        return delegate.getPrincipal();
    }
}
