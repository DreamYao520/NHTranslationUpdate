package com.dreamyao.nhtranslationupdate.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import com.dreamyao.nhtranslationupdate.manifest.UpdateManifest.Artifact;
import com.dreamyao.nhtranslationupdate.util.IOUtil;

final class StateStore {

    private final Path file;
    private final Properties properties = new Properties();

    StateStore(Path cacheDirectory) throws IOException {
        Files.createDirectories(cacheDirectory);
        file = cacheDirectory.resolve("state.properties");
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            }
        }
    }

    long lastCheck() {
        try {
            return Long.parseLong(properties.getProperty("lastCheckEpochMillis", "0"));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    void setLastCheck(long value) {
        properties.setProperty("lastCheckEpochMillis", Long.toString(value));
    }

    LastKnownGood lastKnownGood() {
        String packVersion = properties.getProperty("lastKnownGood.packVersion", "");
        String release = properties.getProperty("lastKnownGood.release", "");
        String artifactId = properties.getProperty("lastKnownGood.artifactId", "");
        String sha256 = properties.getProperty("lastKnownGood.sha256", "");
        String languagesValue = properties.getProperty("lastKnownGood.languages", "zh_CN");
        if (packVersion.isEmpty() || release.isEmpty()
            || !artifactId.matches("[A-Za-z0-9._-]{1,80}")
            || !sha256.matches("(?i)[0-9a-f]{64}")) {
            return null;
        }
        Set<String> languages = new LinkedHashSet<>();
        for (String language : languagesValue.split(",")) {
            String trimmed = language.trim();
            if (!trimmed.matches("[a-z]{2}_[A-Z]{2}")) return null;
            languages.add(trimmed);
        }
        if (languages.isEmpty()) return null;
        return new LastKnownGood(
            packVersion,
            release,
            artifactId,
            sha256.toLowerCase(java.util.Locale.ROOT),
            languages);
    }

    void setLastKnownGood(String packVersion, String release, Artifact artifact, Set<String> languages) {
        properties.setProperty("lastKnownGood.packVersion", packVersion);
        properties.setProperty("lastKnownGood.release", release);
        properties.setProperty("lastKnownGood.artifactId", artifact.id);
        properties.setProperty("lastKnownGood.sha256", artifact.sha256);
        properties.setProperty("lastKnownGood.languages", String.join(",", languages));
    }

    void save() throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "NH Translation Update managed state");
        }
        IOUtil.atomicMove(temporary, file);
    }

    static final class LastKnownGood {

        final String packVersion;
        final String release;
        final String artifactId;
        final String sha256;
        final Set<String> languages;

        private LastKnownGood(String packVersion, String release, String artifactId, String sha256,
            Set<String> languages) {
            this.packVersion = packVersion;
            this.release = release;
            this.artifactId = artifactId;
            this.sha256 = sha256;
            this.languages = languages;
        }
    }
}
