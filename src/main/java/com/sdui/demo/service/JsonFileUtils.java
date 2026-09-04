package com.sdui.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class JsonFileUtils {

    private final ObjectMapper objectMapper;
    private final String configuredBaseDir;

    public JsonFileUtils(ObjectMapper objectMapper,
                         @Value("${sdui.pages.dir:}") String configuredBaseDir) {
        this.objectMapper = objectMapper;
        this.configuredBaseDir = configuredBaseDir;
    }

    private String classpathLocation(String merchantId, String pageKey) {
        return "data/pages/" + merchantId + "/" + pageKey + ".json";
    }

    private Path fileSystemBaseDir() {
        if (configuredBaseDir != null && !configuredBaseDir.isBlank()) {
            return Paths.get(configuredBaseDir);
        }
        // When running from an IDE / exploded build, resources live under
        // src/main/resources. Resolve the classpath root to a real directory.
        URL url = getClass().getClassLoader().getResource("data/pages");
        if (url != null && "file".equals(url.getProtocol())) {
            try {
                return Paths.get(url.toURI());
            } catch (Exception ignored) {
                // fall through
            }
        }
        // Safe fallback: a directory under the current working directory.
        return Paths.get("data", "pages");
    }

    private Path resolvePath(String merchantId, String pageKey) {
        return fileSystemBaseDir().resolve(merchantId).resolve(pageKey + ".json");
    }

    public <T> T readFile(String merchantId, String pageKey, Class<T> type) throws IOException {
        Path file = resolvePath(merchantId, pageKey);
        if (Files.exists(file)) {
            try (InputStream is = Files.newInputStream(file)) {
                return objectMapper.readValue(is, type);
            }
        }
        // Fallback to classpath resource (read-only inside a packaged jar).
        ClassPathResource resource = new ClassPathResource(classpathLocation(merchantId, pageKey));
        if (resource.exists()) {
            try (InputStream is = resource.getInputStream()) {
                return objectMapper.readValue(is, type);
            }
        }
        return null;
    }

    public void writeFile(String merchantId, String pageKey, Object content) throws IOException {
        Path file = resolvePath(merchantId, pageKey);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), content);
    }

    public boolean deleteFile(String merchantId, String pageKey) throws IOException {
        Path file = resolvePath(merchantId, pageKey);
        if (Files.exists(file)) {
            return Files.deleteIfExists(file);
        }
        return false;
    }

    public List<String> listPages(String merchantId) throws IOException {
        List<String> keys = new ArrayList<>();

        Path dir = fileSystemBaseDir().resolve(merchantId);
        if (dir.getParent() != null) {
            Files.createDirectories(dir.getParent());
        }
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".json"))
                        .map(n -> n.substring(0, n.length() - ".json".length()))
                        .forEach(keys::add);
            }
        }

        // Merge with classpath entries so packaged sample data is discoverable.
        URL url = getClass().getClassLoader().getResource("data/pages/" + merchantId);
        if (url != null && "file".equals(url.getProtocol())) {
            try (Stream<Path> stream = Files.list(Paths.get(url.toURI()))) {
                stream.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".json"))
                        .map(n -> n.substring(0, n.length() - ".json".length()))
                        .forEach(k -> {
                            if (!keys.contains(k)) {
                                keys.add(k);
                            }
                        });
            } catch (Exception ignored) {
                // ignore classpath listing errors
            }
        }

        return keys.stream().sorted().collect(Collectors.toList());
    }
}
