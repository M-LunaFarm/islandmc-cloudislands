package kr.lunaf.cloudislands.coreservice.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreHttpRequestBodyPolicyTest {
    @Test
    void coreHttpRoutesUseTheBoundedRequestBodyReader() throws Exception {
        Path sourceRoot = Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http");
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (path.getFileName().toString().equals("CoreHttpResponses.java")
                        || path.getFileName().toString().equals("BufferedHttpExchange.java")) {
                    continue;
                }
                String source = Files.readString(path);
                for (String token : List.of("getRequestBody(", "readAllBytes(", "readNBytes(")) {
                    if (source.contains(token)) {
                        violations.add(sourceRoot.relativize(path) + ":" + token);
                    }
                }
            }
        }

        assertEquals(List.of(), violations, "Core HTTP handlers must read request bodies through CoreHttpResponses.readBody");
        String idempotencyExecutor = Files.readString(sourceRoot.resolve("CoreIdempotencyExecutor.java"));
        assertTrue(idempotencyExecutor.contains("CoreHttpResponses.readBody(exchange)"), "idempotency buffering must enforce the shared request-body limit before replaying the body to a route");
    }
}
