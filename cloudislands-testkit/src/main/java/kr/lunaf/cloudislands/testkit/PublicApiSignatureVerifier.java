package kr.lunaf.cloudislands.testkit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

public final class PublicApiSignatureVerifier {
    public static final String BASELINE_RESOURCE = "api-compatibility/cloudislands-api-1.0.0.signatures";
    private static final String API_PACKAGE = "kr.lunaf.cloudislands.api";

    private PublicApiSignatureVerifier() {
    }

    public static PublicApiSignatureReport verifyBaseline() {
        return verifyBaseline(BASELINE_RESOURCE, readBaseline(BASELINE_RESOURCE));
    }

    static PublicApiSignatureReport verifyBaseline(String baselineResource, List<String> baselineSignatures) {
        Set<String> current = currentSignatures();
        List<String> missing = baselineSignatures.stream()
            .filter(signature -> !current.contains(signature))
            .toList();
        return new PublicApiSignatureReport(baselineResource, baselineSignatures.size(), current.size(), missing);
    }

    public static Set<String> currentSignatures() {
        try {
            List<Class<?>> classes = loadApiClasses();
            LinkedHashSet<String> signatures = new LinkedHashSet<>();
            for (Class<?> type : classes) {
                signatures.add(typeSignature(type));
                constructorSignatures(type).forEach(signatures::add);
                methodSignatures(type).forEach(signatures::add);
                fieldSignatures(type).forEach(signatures::add);
            }
            return Set.copyOf(signatures);
        } catch (IOException | URISyntaxException exception) {
            throw new IllegalStateException("Unable to scan CloudIslands public API signatures", exception);
        }
    }

    private static List<String> readBaseline(String resource) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream stream = loader == null
            ? PublicApiSignatureVerifier.class.getClassLoader().getResourceAsStream(resource)
            : loader.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Missing public API signature baseline resource: " + resource);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .distinct()
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read public API signature baseline resource: " + resource, exception);
        }
    }

    private static List<Class<?>> loadApiClasses() throws IOException, URISyntaxException {
        URL location = kr.lunaf.cloudislands.api.CloudIslandsApi.class.getProtectionDomain().getCodeSource().getLocation();
        Path path = Path.of(location.toURI());
        List<String> classNames = Files.isDirectory(path)
            ? classNamesFromDirectory(path)
            : classNamesFromJar(path);
        List<Class<?>> classes = new ArrayList<>();
        ClassLoader loader = kr.lunaf.cloudislands.api.CloudIslandsApi.class.getClassLoader();
        for (String className : classNames) {
            try {
                Class<?> type = Class.forName(className, false, loader);
                if (publicApiType(type)) {
                    classes.add(type);
                }
            } catch (ClassNotFoundException | LinkageError ignored) {
                // Optional platform dependencies must not make the API signature scan unstable.
            }
        }
        classes.sort(Comparator.comparing(Class::getName));
        return List.copyOf(classes);
    }

    private static List<String> classNamesFromDirectory(Path root) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            return paths
                .filter(path -> path.toString().endsWith(".class"))
                .map(root::relativize)
                .map(Path::toString)
                .map(PublicApiSignatureVerifier::classNameFromEntry)
                .filter(PublicApiSignatureVerifier::apiClassName)
                .toList();
        }
    }

    private static List<String> classNamesFromJar(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.stream()
                .filter(entry -> entry.getName().endsWith(".class"))
                .map(entry -> classNameFromEntry(entry.getName()))
                .filter(PublicApiSignatureVerifier::apiClassName)
                .toList();
        }
    }

    private static String classNameFromEntry(String entry) {
        return entry.replace('\\', '.').replace('/', '.').replaceAll("\\.class$", "");
    }

    private static boolean apiClassName(String className) {
        return className.startsWith(API_PACKAGE + ".") || className.equals(API_PACKAGE);
    }

    private static boolean publicApiType(Class<?> type) {
        return !type.isSynthetic()
            && Modifier.isPublic(type.getModifiers())
            && type.getName().startsWith(API_PACKAGE);
    }

    private static String typeSignature(Class<?> type) {
        return "type " + type.getName() + " " + typeKind(type);
    }

    private static String typeKind(Class<?> type) {
        if (type.isAnnotation()) {
            return "annotation";
        }
        if (type.isEnum()) {
            return "enum";
        }
        if (type.isRecord()) {
            return "record";
        }
        if (type.isInterface()) {
            return "interface";
        }
        return "class";
    }

    private static List<String> constructorSignatures(Class<?> type) {
        List<String> signatures = new ArrayList<>();
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.isSynthetic() || !publicOrProtected(constructor.getModifiers())) {
                continue;
            }
            signatures.add("ctor " + type.getName() + "(" + parameterTypes(constructor.getParameterTypes()) + ")");
        }
        signatures.sort(String::compareTo);
        return List.copyOf(signatures);
    }

    private static List<String> methodSignatures(Class<?> type) {
        List<String> signatures = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge() || enumGeneratedMethod(type, method) || !publicOrProtected(method.getModifiers())) {
                continue;
            }
            signatures.add("method " + type.getName() + "#" + method.getName() + "(" + parameterTypes(method.getParameterTypes()) + "):" + typeName(method.getReturnType()));
        }
        signatures.sort(String::compareTo);
        return List.copyOf(signatures);
    }

    private static List<String> fieldSignatures(Class<?> type) {
        List<String> signatures = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic() || !publicOrProtected(field.getModifiers())) {
                continue;
            }
            signatures.add("field " + type.getName() + "#" + field.getName() + ":" + typeName(field.getType()));
        }
        signatures.sort(String::compareTo);
        return List.copyOf(signatures);
    }

    private static boolean enumGeneratedMethod(Class<?> type, Method method) {
        return type.isEnum()
            && (method.getName().equals("values") && method.getParameterCount() == 0
                || method.getName().equals("valueOf") && method.getParameterCount() == 1 && method.getParameterTypes()[0] == String.class);
    }

    private static boolean publicOrProtected(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static String parameterTypes(Class<?>[] parameterTypes) {
        List<String> values = new ArrayList<>();
        for (Class<?> parameterType : parameterTypes) {
            values.add(typeName(parameterType));
        }
        return String.join(",", values);
    }

    private static String typeName(Class<?> type) {
        if (type.isArray()) {
            return typeName(type.getComponentType()) + "[]";
        }
        return type.getName();
    }
}
