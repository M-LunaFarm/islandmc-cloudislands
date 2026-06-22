package kr.lunaf.cloudislands.coreclient;

record CoreHttpResponse(int statusCode, String body) {
    CoreHttpResponse {
        body = body == null ? "" : body;
    }

    boolean successBody() {
        return statusCode >= 200 && statusCode < 300;
    }

    boolean resultBody() {
        return statusCode >= 200 && statusCode < 500;
    }

    CoreResponseBody responseBody(boolean accepted) {
        return new CoreResponseBody(body, statusCode, accepted);
    }
}
