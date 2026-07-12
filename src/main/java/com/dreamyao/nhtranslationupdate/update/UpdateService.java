package com.dreamyao.nhtranslationupdate.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.dreamyao.nhtranslationupdate.NHTranslationUpdate;
import com.dreamyao.nhtranslationupdate.config.UpdateConfig;
import com.dreamyao.nhtranslationupdate.manifest.UpdateManifest;
import com.dreamyao.nhtranslationupdate.manifest.UpdateManifest.Artifact;
import com.dreamyao.nhtranslationupdate.manifest.UpdateManifest.PackRelease;
import com.dreamyao.nhtranslationupdate.net.HttpClient;
import com.dreamyao.nhtranslationupdate.resource.NHTranslationResourcePack;
import com.dreamyao.nhtranslationupdate.update.StateStore.LastKnownGood;
import com.dreamyao.nhtranslationupdate.util.Hashing;
import com.dreamyao.nhtranslationupdate.util.IOUtil;
import com.dreamyao.nhtranslationupdate.version.PackVersionDetector;
import com.dreamyao.nhtranslationupdate.version.PackVersionDetector.Result;
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
        if (!client) return "服务端无需加载客户端汉化资源";
        if (LegacyResourcePackCleanup.run(gameDirectory)) {
            NHTranslationUpdate.LOG.info("Removed the legacy on-disk NHTranslationUpdate resource pack");
        }

        Result detected = PackVersionDetector.detect(gameDirectory, config.packVersion);
        if (detected == null) {
            NHTranslationUpdate.LOG.warn("Could not detect the GTNH version; no translation pack will be applied");
            return "无法检测 GTNH 版本，未应用汉化（可在配置中设置 packVersion）";
        }
        String packVersion = detected.version;
        NHTranslationUpdate.LOG.info("Detected GTNH {} from {}", packVersion, detected.source);

        Files.createDirectories(config.cacheDirectory);
        StateStore state = new StateStore(config.cacheDirectory);
        LastKnownGood loaded = loadLastKnownGood(config, state, packVersion);

        Path manifestCache = config.cacheDirectory.resolve("manifest.json");
        UpdateManifest manifest = loadManifest(config, state, manifestCache);
        if (manifest == null) {
            return loaded == null ? "更新清单不可用，当前版本没有可用的缓存汉化" : "更新清单不可用，继续使用汉化 " + loaded.release;
        }

        PackRelease release = manifest.select(packVersion);
        if (release == null) {
            return loaded == null ? "更新站暂未提供 GTNH " + packVersion + " 的汉化"
                : "更新站暂无 GTNH " + packVersion + " 的新汉化，继续使用 " + loaded.release;
        }
        Artifact artifact = release.translationArtifact();
        if (artifact == null) return "GTNH " + packVersion + " 的发布记录没有翻译包";
        java.util.Set<String> languages = release.supportedLanguages();

        if (loaded != null && artifact.sha256.equalsIgnoreCase(loaded.sha256)) {
            return "GTNH " + packVersion + " 汉化已是最新版本 " + release.release;
        }

        try {
            Path archive = ensureArtifact(config, new HttpClient(config), artifact);
            NHTranslationResourcePack
                .load(archive, config.gameDirectory, languages, config.maxZipEntries, config.maxExtractedBytes);
            state.setLastKnownGood(packVersion, release.release, artifact, languages);
            state.save();
            return "已准备 GTNH " + packVersion + " 汉化 " + release.release;
        } catch (Exception exception) {
            NHTranslationUpdate.LOG.error("Failed to install translation artifact for GTNH " + packVersion, exception);
            return loaded == null ? "汉化 " + release.release + " 更新失败，当前没有可用缓存"
                : "汉化 " + release.release + " 更新失败，继续使用 " + loaded.release;
        }
    }

    private static LastKnownGood loadLastKnownGood(UpdateConfig config, StateStore state, String packVersion) {
        LastKnownGood saved = state.lastKnownGood();
        if (saved == null || !packVersion.equalsIgnoreCase(saved.packVersion)) return null;

        Path archive = artifactPath(config, saved.artifactId, saved.sha256);
        if (!Files.isRegularFile(archive)) return null;
        try {
            if (!saved.sha256.equalsIgnoreCase(Hashing.sha256(archive))) {
                NHTranslationUpdate.LOG.warn("Ignoring corrupt cached translation {}", archive);
                return null;
            }
            NHTranslationResourcePack
                .load(archive, config.gameDirectory, saved.languages, config.maxZipEntries, config.maxExtractedBytes);
            NHTranslationUpdate.LOG
                .info("Loaded last-known-good translation {} for GTNH {}", saved.release, packVersion);
            return saved;
        } catch (Exception exception) {
            NHTranslationUpdate.LOG.warn("Could not load last-known-good translation " + archive, exception);
            return null;
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
                // Do not update lastCheck on failure, so the next launch retries.
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

    private static Path ensureArtifact(UpdateConfig config, HttpClient http, Artifact artifact) throws IOException {
        Path directory = config.cacheDirectory.resolve("artifacts");
        Files.createDirectories(directory);
        Path cached = artifactPath(config, artifact.id, artifact.sha256);
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

    private static Path artifactPath(UpdateConfig config, String artifactId, String sha256) {
        return config.cacheDirectory.resolve("artifacts")
            .resolve(artifactId + "-" + sha256.toLowerCase(java.util.Locale.ROOT) + ".zip");
    }
}
