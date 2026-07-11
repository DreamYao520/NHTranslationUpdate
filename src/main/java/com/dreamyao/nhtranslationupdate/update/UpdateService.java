package com.dreamyao.nhtranslationupdate.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dreamyao.nhtranslationupdate.NHTranslationUpdate;
import com.dreamyao.nhtranslationupdate.config.UpdateConfig;
import com.dreamyao.nhtranslationupdate.manifest.UpdateManifest;
import com.dreamyao.nhtranslationupdate.manifest.UpdateManifest.Artifact;
import com.dreamyao.nhtranslationupdate.net.HttpClient;
import com.dreamyao.nhtranslationupdate.resource.NHTranslationResourcePack;
import com.dreamyao.nhtranslationupdate.util.Hashing;
import com.dreamyao.nhtranslationupdate.util.IOUtil;
import com.google.gson.Gson;

public final class UpdateService {

    private static final int MAXIMUM_MANIFEST_BYTES = 1024 * 1024;
    private static final Gson GSON = new Gson();
    private final Path gameDirectory;
    private final boolean client;

    public UpdateService(Path gameDirectory, boolean client) {
        this.gameDirectory = gameDirectory;
        this.client = client;
    }

    public String run() throws IOException {
        UpdateConfig config = UpdateConfig.load(gameDirectory);
        if (!config.enabled) return "自动更新已禁用";

        Files.createDirectories(config.cacheDirectory);
        StateStore state = new StateStore(config.cacheDirectory);
        Path manifestCache = config.cacheDirectory.resolve("manifest.json");
        UpdateManifest manifest = loadManifest(config, state, manifestCache);

        if (manifest == null) {
            if (client) activateOptions(config);
            return "远程清单不可用，继续使用现有汉化";
        }
        if (!manifest.supportsPackVersion(config.packVersion)) {
            return "汉化版本 " + manifest.release + " 不支持配置的 GTNH " + config.packVersion;
        }
        if (config.packVersion.isEmpty() && manifest.packVersions != null && !manifest.packVersions.isEmpty()) {
            NHTranslationUpdate.LOG
                .warn("packVersion is empty; applying the latest translation without an exact GTNH version check");
        }

        Artifact translationArtifact = null;
        for (Artifact artifact : manifest.artifacts) {
            if ("translation".equals(artifact.kind)) {
                translationArtifact = artifact;
                break;
            }
        }
        if (translationArtifact == null) {
            return "远程清单未包含翻译组件";
        }

        HttpClient http = new HttpClient(config);
        try {
            if (alreadyLoaded(state, translationArtifact)) {
                if (client) activateOptions(config);
                return "汉化已是最新版本 " + manifest.release;
            }
            Path archive = ensureArtifact(config, http, translationArtifact);
            NHTranslationResourcePack.load(archive, config.maxZipEntries, config.maxExtractedBytes);
            state.setArtifactHash(translationArtifact.id, translationArtifact.sha256);
            state.setRelease(manifest.release);
            state.save();
            if (client) activateOptions(config);
            return "已安装汉化版本 " + manifest.release;
        } catch (Exception exception) {
            NHTranslationUpdate.LOG.error("Failed to install translation artifact", exception);
            if (client && NHTranslationResourcePack.INSTANCE == null) {
                // First install failed — try to load from a cached artifact
                Path cached = config.cacheDirectory.resolve("artifacts")
                    .resolve(translationArtifact.id + "-" + translationArtifact.sha256 + ".zip");
                if (Files.isRegularFile(cached)) {
                    try {
                        NHTranslationResourcePack.load(cached, config.maxZipEntries, config.maxExtractedBytes);
                        NHTranslationUpdate.LOG.warn("Loaded cached translation after fresh install failure");
                    } catch (Exception e2) {
                        NHTranslationUpdate.LOG.warn("Cached translation also failed to load", e2);
                    }
                }
            }
            if (client) activateOptions(config);
            if (translationArtifact.required) {
                return "版本 " + manifest.release + " 更新失败，旧文件已保留";
            }
            return "版本 " + manifest.release + " 更新失败（非必需），继续使用现有汉化";
        }
    }

    // ---- manifest loading -------------------------------------------------

    static UpdateManifest loadManifest(UpdateConfig config, StateStore state, Path cache) throws IOException {
        boolean due = !Files.isRegularFile(cache)
            || System.currentTimeMillis() - state.lastCheck() >= config.checkIntervalMillis;
        if (due && !config.manifestUrl.isEmpty()) {
            try {
                String json = new HttpClient(config).getUtf8(config.manifestUrl, MAXIMUM_MANIFEST_BYTES);
                UpdateManifest manifest = parseManifest(json);
                IOUtil.atomicWriteUtf8(cache, json);
                state.setLastCheck(System.currentTimeMillis());
                state.save();
                return manifest;
            } catch (Exception exception) {
                NHTranslationUpdate.LOG.warn(
                    "Could not refresh translation manifest, will retry on next launch: {}",
                    exception.toString());
                // Don't update lastCheck on failure — retry on next launch
            }
        }
        if (!Files.isRegularFile(cache)) return null;
        try {
            return parseManifest(new String(Files.readAllBytes(cache), StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            NHTranslationUpdate.LOG.error("Cached translation manifest is invalid", exception);
            return null;
        }
    }

    private static UpdateManifest parseManifest(String json) {
        UpdateManifest manifest = GSON.fromJson(json, UpdateManifest.class);
        if (manifest == null) throw new IllegalArgumentException("Manifest is empty");
        manifest.validate();
        return manifest;
    }

    // ---- artifact management ----------------------------------------------

    private static boolean alreadyLoaded(StateStore state, Artifact artifact) {
        return artifact.sha256.equalsIgnoreCase(state.artifactHash(artifact.id))
            && NHTranslationResourcePack.INSTANCE != null;
    }

    private static Path ensureArtifact(UpdateConfig config, HttpClient http, Artifact artifact) throws IOException {
        Path directory = config.cacheDirectory.resolve("artifacts");
        Files.createDirectories(directory);
        Path cached = directory.resolve(artifact.id + "-" + artifact.sha256 + ".zip");
        if (Files.isRegularFile(cached)) {
            if (artifact.sha256.equalsIgnoreCase(Hashing.sha256(cached))) return cached;
            Files.delete(cached);
        }
        Path partial = cached.resolveSibling(cached.getFileName() + ".part");
        http.download(artifact.url, partial, artifact.size);
        String actual = Hashing.sha256(partial);
        if (!artifact.sha256.equalsIgnoreCase(actual)) {
            Files.deleteIfExists(partial);
            throw new IOException("SHA-256 mismatch for " + artifact.id);
        }
        IOUtil.atomicMove(partial, cached);
        return cached;
    }

    // ---- options.txt ------------------------------------------------------

    private static void activateOptions(UpdateConfig config) throws IOException {
        if (!config.forceLanguage.isEmpty()) {
            setOption(config.gameDirectory, "lang", config.forceLanguage);
        }
    }

    private static void setOption(Path gameDirectory, String key, String value) throws IOException {
        Path options = gameDirectory.resolve("options.txt");
        List<String> original = Files.isRegularFile(options) ? Files.readAllLines(options, StandardCharsets.UTF_8)
            : new ArrayList<String>();
        Map<String, String> values = new LinkedHashMap<>();
        List<String> passthrough = new ArrayList<>();
        for (String line : original) {
            int separator = line.indexOf(':');
            if (separator <= 0) passthrough.add(line);
            else values.put(line.substring(0, separator), line.substring(separator + 1));
        }
        values.put(key, value);

        List<String> updated = new ArrayList<>(passthrough);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            updated.add(entry.getKey() + ":" + entry.getValue());
        }
        String content = String.join("\n", updated) + "\n";
        String oldContent = String.join("\n", original) + (original.isEmpty() ? "" : "\n");
        if (content.equals(oldContent)) return;
        IOUtil.atomicWriteUtf8(options, content);
    }
}
