package kr.lunaf.cloudislands.coreclient;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.lunaf.cloudislands.api.model.AddonStateBulkLoadRequest;
import kr.lunaf.cloudislands.api.model.AddonStateBulkSaveRequest;
import org.junit.jupiter.api.Test;

class CoreClientRouteCoverageTest {
    private static final Path CLIENT_SOURCE = sourcePath("cloudislands-core-client", "src/main/java/kr/lunaf/cloudislands/coreclient");
    private static final Path ROUTE_SOURCE = sourcePath("cloudislands-core-service", "src/main/java/kr/lunaf/cloudislands/coreservice/http/routes");
    private static final Pattern STRING_CONSTANT = Pattern.compile("(?:public|private)?\\s*static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*(.+);\\s*");
    private static final List<String> CORE_HELPERS = List.of("postBody", "postResultBody", "getBody", "deleteResultBody");
    private static final List<String> ROUTE_HELPERS = List.of("routePost", "routeGet", "routeMethods");

    @Test
    void jdkCoreClientEndpointsAreRegisteredByCoreServiceRoutes() throws Exception {
        RouteIndex routes = collectRoutes();
        List<String> missing = new ArrayList<>();
        for (ClientEndpoint endpoint : collectClientEndpoints()) {
            if (!routes.covers(endpoint.path())) {
                missing.add(endpoint.file() + " -> " + endpoint.path() + " from " + endpoint.expression());
            }
        }

        missing.sort(Comparator.naturalOrder());
        assertEquals(List.of(), missing, "Every typed Core client endpoint must be registered by the Core service routes");
    }

    private static List<ClientEndpoint> collectClientEndpoints() throws IOException {
        List<ClientEndpoint> endpoints = new ArrayList<>();
        try (var files = Files.list(CLIENT_SOURCE)) {
            for (Path file : files.filter(path -> path.getFileName().toString().startsWith("Jdk"))
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("JdkCoreApiClient.java"))
                    .sorted()
                    .toList()) {
                String source = Files.readString(file);
                Map<String, String> constants = stringConstants(source);
                for (String helper : CORE_HELPERS) {
                    int from = 0;
                    String call = "core." + helper + "(";
                    while (true) {
                        int index = source.indexOf(call, from);
                        if (index < 0) {
                            break;
                        }
                        String expression = firstArgument(source, index + call.length());
                        ResolvedEndpoint endpoint = resolveEndpoint(expression, constants);
                        endpoints.add(new ClientEndpoint(file.getFileName().toString(), endpoint.path(), expression.trim()));
                        from = index + call.length();
                    }
                }
            }
        }
        return endpoints;
    }

    private static RouteIndex collectRoutes() throws IOException {
        Set<String> exact = new LinkedHashSet<>();
        Set<String> prefixes = new LinkedHashSet<>();
        try (var files = Files.list(ROUTE_SOURCE)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList()) {
                String source = Files.readString(file);
                Map<String, String> constants = stringConstants(source);
                for (String helper : ROUTE_HELPERS) {
                    int from = 0;
                    String call = "." + helper + "(";
                    while (true) {
                        int dotIndex = source.indexOf(call, from);
                        if (dotIndex < 0) {
                            break;
                        }
                        String receiver = receiverBefore(source, dotIndex);
                        String expression = firstArgument(source, dotIndex + call.length());
                        ResolvedEndpoint endpoint = resolveEndpoint(expression, constants);
                        if (receiver.equals("prefixRegistry")) {
                            prefixes.add(endpoint.path());
                        } else {
                            exact.add(endpoint.path());
                        }
                        from = dotIndex + call.length();
                    }
                }
            }
        }
        return new RouteIndex(exact, prefixes);
    }

    private static Map<String, String> stringConstants(String source) {
        Map<String, String> constants = externalConstants();
        for (String line : source.lines().toList()) {
            Matcher matcher = STRING_CONSTANT.matcher(line.trim());
            if (matcher.matches()) {
                constants.put(matcher.group(1), resolveEndpoint(matcher.group(2), constants).path());
            }
        }
        return constants;
    }

    private static Path sourcePath(String moduleName, String relativePath) {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path moduleDirectory = workingDirectory.resolve(relativePath);
        if (Files.isDirectory(moduleDirectory)) {
            return moduleDirectory;
        }
        Path rootRelative = workingDirectory.resolve(moduleName).resolve(relativePath);
        if (Files.isDirectory(rootRelative)) {
            return rootRelative;
        }
        Path siblingModule = workingDirectory.getParent() == null ? rootRelative : workingDirectory.getParent().resolve(moduleName).resolve(relativePath);
        if (Files.isDirectory(siblingModule)) {
            return siblingModule;
        }
        return rootRelative;
    }

    private static Map<String, String> externalConstants() {
        Map<String, String> constants = new HashMap<>();
        constants.put("AddonStateBulkSaveRequest.GLOBAL_LEGACY_ENDPOINT", AddonStateBulkSaveRequest.GLOBAL_LEGACY_ENDPOINT);
        constants.put("AddonStateBulkSaveRequest.ISLAND_LEGACY_ENDPOINT", AddonStateBulkSaveRequest.ISLAND_LEGACY_ENDPOINT);
        constants.put("AddonStateBulkSaveRequest.GLOBAL_ENDPOINT", AddonStateBulkSaveRequest.GLOBAL_ENDPOINT);
        constants.put("AddonStateBulkSaveRequest.ISLAND_ENDPOINT", AddonStateBulkSaveRequest.ISLAND_ENDPOINT);
        constants.put("AddonStateBulkSaveRequest.GLOBAL_BULK_SAVE_ALIAS", AddonStateBulkSaveRequest.GLOBAL_BULK_SAVE_ALIAS);
        constants.put("AddonStateBulkSaveRequest.ISLAND_BULK_SAVE_ALIAS", AddonStateBulkSaveRequest.ISLAND_BULK_SAVE_ALIAS);
        constants.put("AddonStateBulkSaveRequest.GLOBAL_BULK_ALIAS", AddonStateBulkSaveRequest.GLOBAL_BULK_ALIAS);
        constants.put("AddonStateBulkSaveRequest.ISLAND_BULK_ALIAS", AddonStateBulkSaveRequest.ISLAND_BULK_ALIAS);
        constants.put("AddonStateBulkSaveRequest.GLOBAL_TABLE_BULK_ENDPOINT", AddonStateBulkSaveRequest.GLOBAL_TABLE_BULK_ENDPOINT);
        constants.put("AddonStateBulkSaveRequest.ISLAND_TABLE_BULK_ENDPOINT", AddonStateBulkSaveRequest.ISLAND_TABLE_BULK_ENDPOINT);
        constants.put("AddonStateBulkSaveRequest.GLOBAL_TABLE_BULK_SET_ENDPOINT", AddonStateBulkSaveRequest.GLOBAL_TABLE_BULK_SET_ENDPOINT);
        constants.put("AddonStateBulkSaveRequest.ISLAND_TABLE_BULK_SET_ENDPOINT", AddonStateBulkSaveRequest.ISLAND_TABLE_BULK_SET_ENDPOINT);
        constants.put("AddonStateBulkLoadRequest.GLOBAL_ENDPOINT", AddonStateBulkLoadRequest.GLOBAL_ENDPOINT);
        constants.put("AddonStateBulkLoadRequest.ISLAND_ENDPOINT", AddonStateBulkLoadRequest.ISLAND_ENDPOINT);
        constants.put("AddonStateBulkLoadRequest.GLOBAL_TABLE_LOAD_ALIAS", AddonStateBulkLoadRequest.GLOBAL_TABLE_LOAD_ALIAS);
        constants.put("AddonStateBulkLoadRequest.ISLAND_TABLE_LOAD_ALIAS", AddonStateBulkLoadRequest.ISLAND_TABLE_LOAD_ALIAS);
        return constants;
    }

    private static ResolvedEndpoint resolveEndpoint(String expression, Map<String, String> constants) {
        String value = expression.trim();
        if (constants.containsKey(value)) {
            return new ResolvedEndpoint(constants.get(value));
        }
        List<String> terms = splitConcatenation(value);
        if (terms.size() == 1) {
            if (isStringLiteral(value)) {
                return new ResolvedEndpoint(unquote(value));
            }
            if (constants.containsKey(value)) {
                return new ResolvedEndpoint(constants.get(value));
            }
            throw new IllegalArgumentException("Unresolved endpoint expression: " + expression);
        }

        StringBuilder endpoint = new StringBuilder();
        for (String term : terms) {
            String trimmed = term.trim();
            if (isStringLiteral(trimmed)) {
                endpoint.append(unquote(trimmed));
                continue;
            }
            String constant = constants.get(trimmed);
            if (constant == null) {
                if (endpoint.isEmpty()) {
                    throw new IllegalArgumentException("Dynamic endpoint must start with a static path prefix: " + expression);
                }
                return new ResolvedEndpoint(endpoint.toString());
            }
            endpoint.append(constant);
        }
        return new ResolvedEndpoint(endpoint.toString());
    }

    private static List<String> splitConcatenation(String expression) {
        List<String> terms = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < expression.length(); i++) {
            char currentChar = expression.charAt(i);
            if (inString) {
                current.append(currentChar);
                if (escaped) {
                    escaped = false;
                } else if (currentChar == '\\') {
                    escaped = true;
                } else if (currentChar == '"') {
                    inString = false;
                }
                continue;
            }
            if (currentChar == '"') {
                inString = true;
                current.append(currentChar);
            } else if (currentChar == '+') {
                terms.add(current.toString());
                current.setLength(0);
            } else {
                current.append(currentChar);
            }
        }
        terms.add(current.toString());
        return terms;
    }

    private static String firstArgument(String source, int start) {
        StringBuilder argument = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < source.length(); i++) {
            char current = source.charAt(i);
            if (inString) {
                argument.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
                argument.append(current);
            } else if (current == '(') {
                depth++;
                argument.append(current);
            } else if (current == ')') {
                if (depth == 0) {
                    return argument.toString();
                }
                depth--;
                argument.append(current);
            } else if (current == ',' && depth == 0) {
                return argument.toString();
            } else {
                argument.append(current);
            }
        }
        throw new IllegalArgumentException("Could not parse first argument from index " + start);
    }

    private static String receiverBefore(String source, int dotIndex) {
        int end = dotIndex - 1;
        while (end >= 0 && Character.isWhitespace(source.charAt(end))) {
            end--;
        }
        int start = end;
        while (start >= 0 && Character.isJavaIdentifierPart(source.charAt(start))) {
            start--;
        }
        return source.substring(start + 1, end + 1);
    }

    private static boolean isStringLiteral(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"");
    }

    private static String unquote(String literal) {
        String value = literal.substring(1, literal.length() - 1);
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private record ClientEndpoint(String file, String path, String expression) {
    }

    private record ResolvedEndpoint(String path) {
    }

    private record RouteIndex(Set<String> exact, Set<String> prefixes) {
        boolean covers(String path) {
            if (exact.contains(path)) {
                return true;
            }
            return prefixes.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix));
        }
    }
}
