package com.dreamyao.nhtranslationupdate.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

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

    String artifactHash(String id) {
        return properties.getProperty("artifact." + id + ".sha256", "");
    }

    void setArtifactHash(String id, String sha256) {
        properties.setProperty("artifact." + id + ".sha256", sha256);
    }

    void setRelease(String release) {
        properties.setProperty("release", release);
    }

    void save() throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "NH Translation Update managed state");
        }
        IOUtil.atomicMove(temporary, file);
    }
}
