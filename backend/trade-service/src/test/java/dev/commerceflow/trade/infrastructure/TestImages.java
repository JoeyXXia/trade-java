package dev.commerceflow.trade.infrastructure;

import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class TestImages {
    private TestImages() {}

    static Path repositoryRoot() {
        String root = System.getProperty("commerceflow.root");
        if (root == null || root.isBlank()) {
            throw new IllegalStateException("Run Maven from the repository root with -Pintegration");
        }
        return Path.of(root);
    }

    static DockerImageName locked(String key, String officialRepository) {
        var versions = new Properties();
        try (var stream = Files.newInputStream(repositoryRoot().resolve("deploy/versions.env"))) {
            versions.load(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read locked image versions", e);
        }
        String image = versions.getProperty(key);
        if (image == null || !image.matches(".+@sha256:[a-f0-9]{64}")) {
            throw new IllegalStateException(key + " must contain a pinned image digest");
        }
        return DockerImageName.parse(image).asCompatibleSubstituteFor(officialRepository);
    }
}
