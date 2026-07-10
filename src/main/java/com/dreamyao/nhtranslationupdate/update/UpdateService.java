package com.dreamyao.nhtranslationupdate.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.dreamyao.nhtranslationupdate.NHTranslationUpdate;
import com.dreamyao.nhtranslationupdate.config.UpdateConfig;
import com.dreamyao.nhtranslationupdate.install.OverlayInstaller;
import com.dreamyao.nhtranslationupdate.install.ResourcePackInstaller;
import com.dreamyao.nhtranslationupdate.manifest.UpdateManifest;
import com.dreamyao.nhtranslationupdate.manifest.UpdateManifest.Artifact;
import com.dreamyao.nhtranslationupdate.net.HttpClient;
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
        ResourcePackInstaller resourcePackInstaller = new ResourcePackInstaller(config);
        Path manifestCache = config.cacheDirectory.resolve("manifest.json");
        UpdateManifest manifest = loadManifest(config, state, manifestCache);

        if (manifest == null) {
            if (client && config.enableResourcePack) resourcePackInstaller.activate();
            return "远程清单不可用，继续使用现有汉化";
        }
        if (!manifest.supportsPackVersion(config.packVersion)) {
            return "汉化版本 " + manifest.release + " 不支持配置的 GTNH " + config.packVersion;
        }
        if (config.packVersion.isEmpty() && manifest.packVersions != null && !manifest.packVersions.isEmpty()) {
            NHTranslationUpdate.LOG
                .warn("packVersion is empty; applying the latest translation without an exact GTNH version check");
        }

        HttpClient http = new HttpClient(config);
        int installed = 0;
        int failed = 0;
        for (Artifact artifact : manifest.artifacts) {
            boolean applicable = ("resource_pack".equals(artifact.kind) && client && config.enableResourcePack)
                || ("overlay".equals(artifact.kind) && config.enableOverlay);
            if (!applicable) continue;
            try {
                if (alreadyInstalled(config, state, artifact)) {
                    if ("resource_pack".equals(artifact.kind)) resourcePackInstaller.activate();
                    continue;
                }
                Path archive = ensureArtifact(config, http, artifact);
                if ("resource_pack".equals(artifact.kind)) {
                    resourcePackInstaller.install(archive, artifact.sha256);
                } else {
                    new OverlayInstaller(config).install(archive, artifact.id, artifact.sha256);
                }
                state.setArtifactHash(artifact.id, artifact.sha256);
                state.save();
                installed++;
            } catch (Exception exception) {
                if (artifact.required) failed++;
                NHTranslationUpdate.LOG.error("Failed to install translation artifact {}", artifact.id, exception);
            }
        }
        state.setRelease(manifest.release);
        state.save();
        if (failed > 0) return "版本 " + manifest.release + " 部分更新失败，旧文件已保留";
        if (installed == 0) return "汉化已是最新版本 " + manifest.release;
        return "已安装汉化版本 " + manifest.release + "（" + installed + " 个组件）";
    }

    private static UpdateManifest loadManifest(UpdateConfig config, StateStore state, Path cache) throws IOException {
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
                NHTranslationUpdate.LOG.warn("Could not refresh translation manifest: {}", exception.toString());
                state.setLastCheck(System.currentTimeMillis());
                state.save();
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

    private static boolean alreadyInstalled(UpdateConfig config, StateStore state, Artifact artifact)
        throws IOException {
        if (!artifact.sha256.equalsIgnoreCase(state.artifactHash(artifact.id))) return false;
        if ("overlay".equals(artifact.kind)) return true;
        Path pack = config.gameDirectory.resolve("resourcepacks")
            .resolve(ResourcePackInstaller.FILE_NAME);
        return Files.isRegularFile(pack) && artifact.sha256.equalsIgnoreCase(Hashing.sha256(pack));
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
}
