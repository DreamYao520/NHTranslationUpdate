package com.dreamyao.nhtranslationupdate.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import com.dreamyao.nhtranslationupdate.NHTranslationUpdate;

public final class UpdateConfig {

    private static final String DEFAULT_MANIFEST = "https://dreamyao520.github.io/NHTranslationUpdate/manifest.json";
    private static final String DEFAULT_ROOTS = "resources,config/txloader,config/Betterloadingscreen,config/amazingtrophies,config/InGameInfoXML";

    public final boolean enabled;
    public final boolean allowHttp;
    public final boolean enableResourcePack;
    public final boolean enableOverlay;
    public final String manifestUrl;
    public final String packVersion;
    public final String forceLanguage;
    public final long checkIntervalMillis;
    public final int connectTimeoutMillis;
    public final int readTimeoutMillis;
    public final long maxDownloadBytes;
    public final long maxExtractedBytes;
    public final int maxZipEntries;
    public final int keepBackups;
    public final List<String> allowedOverlayRoots;
    public final Path gameDirectory;
    public final Path cacheDirectory;

    private UpdateConfig(Path gameDirectory, Properties properties) {
        this.gameDirectory = gameDirectory.toAbsolutePath()
            .normalize();
        cacheDirectory = this.gameDirectory.resolve("nhtranslationupdate");
        enabled = bool(properties, "enabled", true);
        allowHttp = bool(properties, "allowHttp", false);
        enableResourcePack = bool(properties, "enableResourcePack", true);
        enableOverlay = bool(properties, "enableOverlay", true);
        manifestUrl = properties.getProperty("manifestUrl", DEFAULT_MANIFEST)
            .trim();
        packVersion = properties.getProperty("packVersion", "")
            .trim();
        forceLanguage = properties.getProperty("forceLanguage", "zh_CN")
            .trim();
        checkIntervalMillis = positiveLong(properties, "checkIntervalHours", 24L) * 60L * 60L * 1000L;
        connectTimeoutMillis = positiveInt(properties, "connectTimeoutSeconds", 5) * 1000;
        readTimeoutMillis = positiveInt(properties, "readTimeoutSeconds", 30) * 1000;
        maxDownloadBytes = positiveLong(properties, "maxDownloadMiB", 256L) * 1024L * 1024L;
        maxExtractedBytes = positiveLong(properties, "maxExtractedMiB", 512L) * 1024L * 1024L;
        maxZipEntries = positiveInt(properties, "maxZipEntries", 30000);
        keepBackups = positiveInt(properties, "keepBackups", 3);

        List<String> roots = new ArrayList<>();
        for (String root : properties.getProperty("allowedOverlayRoots", DEFAULT_ROOTS)
            .split(",")) {
            String clean = root.trim()
                .replace('\\', '/');
            while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
            if (!clean.isEmpty() && !clean.startsWith("/") && !clean.contains("..") && !clean.contains(":")) {
                roots.add(clean);
            }
        }
        allowedOverlayRoots = Collections.unmodifiableList(roots);
    }

    public static UpdateConfig load(Path gameDirectory) throws IOException {
        Path configDirectory = gameDirectory.resolve("config");
        Files.createDirectories(configDirectory);
        Path file = configDirectory.resolve("nhtranslationupdate.properties");
        Properties properties = defaults();
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            }
        } else {
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "NH Translation Update - restart the game after editing");
            }
            NHTranslationUpdate.LOG.info("Created default updater configuration at {}", file);
        }
        return new UpdateConfig(gameDirectory, properties);
    }

    private static Properties defaults() {
        Properties properties = new Properties();
        properties.setProperty("enabled", "true");
        properties.setProperty("manifestUrl", DEFAULT_MANIFEST);
        properties.setProperty("packVersion", "");
        properties.setProperty("checkIntervalHours", "24");
        properties.setProperty("connectTimeoutSeconds", "5");
        properties.setProperty("readTimeoutSeconds", "30");
        properties.setProperty("maxDownloadMiB", "256");
        properties.setProperty("maxExtractedMiB", "512");
        properties.setProperty("maxZipEntries", "30000");
        properties.setProperty("keepBackups", "3");
        properties.setProperty("allowHttp", "false");
        properties.setProperty("enableResourcePack", "true");
        properties.setProperty("enableOverlay", "true");
        properties.setProperty("forceLanguage", "zh_CN");
        properties.setProperty("allowedOverlayRoots", DEFAULT_ROOTS);
        return properties;
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(fallback)));
    }

    private static int positiveInt(Properties properties, String key, int fallback) {
        try {
            int value = Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long positiveLong(Properties properties, String key, long fallback) {
        try {
            long value = Long.parseLong(properties.getProperty(key, Long.toString(fallback)));
            return value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
