package com.dreamyao.nhtranslationupdate.resource;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

/**
 * In-memory resource pack that serves translation files for both standard
 * Minecraft resources ({@code assets/domain/path}) and TX Loader resources
 * ({@code txloader/domain/path}) from a single unified ZIP.
 *
 * <p>
 * Inserted at the highest priority position in the resource-pack chain so it
 * overrides all other packs — including TX Loader's forceload pack.
 * </p>
 */
public final class NHTranslationResourcePack implements IResourcePack {

    static volatile NHTranslationResourcePack INSTANCE;

    private final Map<String, byte[]> entries;
    private final Set<String> domains;

    private NHTranslationResourcePack(Map<String, byte[]> entries) {
        this.entries = entries;
        Set<String> domainSet = new HashSet<>();
        for (String key : entries.keySet()) {
            if (key.startsWith("assets/")) {
                // assets/domain/... -> domain
                int start = "assets/".length();
                int slash = key.indexOf('/', start);
                if (slash > start) domainSet.add(key.substring(start, slash));
            } else if (key.startsWith("txloader/")) {
                // txloader/domain/... -> domain
                int start = "txloader/".length();
                int slash = key.indexOf('/', start);
                if (slash > start) domainSet.add(key.substring(start, slash));
            }
        }
        this.domains = Collections.unmodifiableSet(domainSet);
    }

    /**
     * Loads a translation ZIP into memory and publishes it as the active pack.
     * Replaces any previously loaded pack atomically via the volatile INSTANCE
     * field.
     */
    public static void load(Path zipPath, int maxEntries, long maxBytes) throws IOException {
        Map<String, byte[]> entries = new ConcurrentHashMap<>();
        int count = 0;
        long expanded = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream fileIn = java.nio.file.Files.newInputStream(zipPath);
                ZipInputStream zip = new ZipInputStream(fileIn)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > maxEntries) {
                    throw new IOException("Translation pack has too many entries: " + count);
                }
                String name = entry.getName();
                if (name == null || name.isEmpty() || name.indexOf('\0') >= 0
                        || name.indexOf('\\') >= 0 || name.startsWith("/")
                        || name.contains("..") || name.contains(":")) {
                    throw new IOException("Unsafe archive path: " + name);
                }
                if (entry.isDirectory()) continue;

                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    expanded += read;
                    if (expanded > maxBytes) {
                        throw new IOException("Translation pack expands past limit");
                    }
                    bos.write(buffer, 0, read);
                }
                entries.put(name, bos.toByteArray());
                zip.closeEntry();
            }
        }
        INSTANCE = new NHTranslationResourcePack(entries);
    }

    // ---- IResourcePack ---------------------------------------------------

    @Override
    public InputStream getInputStream(ResourceLocation rl) throws IOException {
        byte[] data = lookup(rl);
        if (data == null) {
            throw new IOException("Resource not found: " + rl);
        }
        return new ByteArrayInputStream(data);
    }

    @Override
    public boolean resourceExists(ResourceLocation rl) {
        return lookup(rl) != null;
    }

    @Override
    public Set<String> getResourceDomains() {
        return domains;
    }

    @Override
    public IMetadataSection getPackMetadata(IMetadataSerializer serializer, String section) {
        return null;
    }

    @Override
    public BufferedImage getPackImage() {
        return null;
    }

    @Override
    public String getPackName() {
        return "NH Translation Update";
    }

    // ---- internal --------------------------------------------------------

    private byte[] lookup(ResourceLocation rl) {
        String domain = rl.getResourceDomain();
        String path = rl.getResourcePath();

        // Standard resource pack format: assets/domain/path
        byte[] data = entries.get("assets/" + domain + "/" + path);
        if (data != null) return data;

        // TX Loader format: txloader/domain/path
        return entries.get("txloader/" + domain + "/" + path);
    }

    /**
     * Returns the raw entry data for a path that is not accessed through the
     * resource-pack API (e.g. BetterLoadingScreen config files). Returns
     * {@code null} if no such entry exists.
     */
    public byte[] getRaw(String path) {
        return entries.get(path);
    }

    /**
     * Returns all raw paths in this pack that start with the given prefix.
     */
    public Set<String> getRawPathsUnder(String prefix) {
        Set<String> result = new HashSet<>();
        for (String key : entries.keySet()) {
            if (key.startsWith(prefix)) result.add(key);
        }
        return result;
    }
}
