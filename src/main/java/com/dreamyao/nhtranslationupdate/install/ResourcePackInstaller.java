package com.dreamyao.nhtranslationupdate.install;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.dreamyao.nhtranslationupdate.config.UpdateConfig;
import com.dreamyao.nhtranslationupdate.util.Hashing;
import com.dreamyao.nhtranslationupdate.util.IOUtil;
import com.dreamyao.nhtranslationupdate.util.PathPolicy;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

public final class ResourcePackInstaller {

    public static final String FILE_NAME = "NHTranslationUpdate.zip";
    private static final Gson GSON = new Gson();
    private final UpdateConfig config;

    public ResourcePackInstaller(UpdateConfig config) {
        this.config = config;
    }

    public void install(Path archive, String expectedHash) throws IOException {
        validate(archive);
        Path target = config.gameDirectory.resolve("resourcepacks")
            .resolve(FILE_NAME);
        if (!Files.isRegularFile(target) || !expectedHash.equalsIgnoreCase(Hashing.sha256(target))) {
            IOUtil.atomicCopy(archive, target);
        }
        activate();
    }

    public void activate() throws IOException {
        Path target = config.gameDirectory.resolve("resourcepacks")
            .resolve(FILE_NAME);
        if (!Files.isRegularFile(target)) return;

        Path options = config.gameDirectory.resolve("options.txt");
        List<String> original = Files.isRegularFile(options) ? Files.readAllLines(options, StandardCharsets.UTF_8)
            : new ArrayList<>();
        Map<String, String> values = new LinkedHashMap<>();
        List<String> passthrough = new ArrayList<>();
        for (String line : original) {
            int separator = line.indexOf(':');
            if (separator <= 0) passthrough.add(line);
            else values.put(line.substring(0, separator), line.substring(separator + 1));
        }

        List<String> packs;
        try {
            packs = GSON.fromJson(
                values.containsKey("resourcePacks") ? values.get("resourcePacks") : "[]",
                new TypeToken<List<String>>() {}.getType());
        } catch (RuntimeException ignored) {
            packs = new ArrayList<>();
        }
        if (packs == null) packs = new ArrayList<>();
        packs.removeIf(
            pack -> pack != null && pack.toLowerCase()
                .contains("nhtranslationupdate"));
        packs.add(FILE_NAME);
        values.put("resourcePacks", GSON.toJson(packs));
        if (!config.forceLanguage.isEmpty()) values.put("lang", config.forceLanguage);

        List<String> updated = new ArrayList<>(passthrough);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            updated.add(entry.getKey() + ":" + entry.getValue());
        }
        String content = String.join("\n", updated) + "\n";
        String oldContent = String.join("\n", original) + (original.isEmpty() ? "" : "\n");
        if (content.equals(oldContent)) return;

        Path backup = options.resolveSibling("options.txt.nhtranslationupdate.bak");
        if (Files.isRegularFile(options) && !Files.exists(backup)) {
            Files.copy(options, backup, StandardCopyOption.COPY_ATTRIBUTES);
        }
        IOUtil.atomicWriteUtf8(options, content);
    }

    private void validate(Path archive) throws IOException {
        boolean metadata = false;
        boolean assets = false;
        ByteArrayOutputStream metadataBytes = new ByteArrayOutputStream();
        int entries = 0;
        long expanded = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > config.maxZipEntries) throw new IOException("Resource pack has too many entries");
                String relative;
                try {
                    relative = PathPolicy.safeRelative(entry.getName());
                } catch (IllegalArgumentException exception) {
                    throw new IOException(exception.getMessage(), exception);
                }
                if ("pack.mcmeta".equals(relative)) metadata = true;
                if (relative.startsWith("assets/") && !entry.isDirectory()) assets = true;
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    expanded += read;
                    if (expanded > config.maxExtractedBytes) throw new IOException("Resource pack expands past limit");
                    if ("pack.mcmeta".equals(relative)) {
                        if (metadataBytes.size() + read > 64 * 1024) throw new IOException("pack.mcmeta is too large");
                        metadataBytes.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
        if (!metadata || !assets) throw new IOException("Invalid resource pack: pack.mcmeta or assets are missing");
        try {
            JsonObject root = GSON
                .fromJson(new String(metadataBytes.toByteArray(), StandardCharsets.UTF_8), JsonObject.class);
            if (root == null || root.getAsJsonObject("pack") == null
                || root.getAsJsonObject("pack")
                    .get("pack_format")
                    .getAsInt() != 1) {
                throw new IOException("Resource pack must use pack_format 1");
            }
        } catch (RuntimeException exception) {
            throw new IOException("Invalid pack.mcmeta", exception);
        }
    }
}
