package com.dreamyao.nhtranslationupdate.resource;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

import com.dreamyao.nhtranslationupdate.util.IOUtil;

/**
 * In-memory resource pack serving translation files from a single unified ZIP.
 * Inserted at the highest priority position in the resource-pack chain so it
 * overrides all other packs — including TX Loader's forceload pack.
 *
 * <p>
 * The publisher has already merged standard, TX Loader load, and TX Loader
 * forceload resources into one {@code assets/domain/path} namespace.
 */
public final class NHTranslationResourcePack implements IResourcePack {

    private static final String GREGTECH_LANGUAGE = "install/config/GregTech_zh_CN.lang";
    public static volatile NHTranslationResourcePack INSTANCE;

    private final Map<String, byte[]> entries;
    private final Set<String> domains;

    private NHTranslationResourcePack(Map<String, byte[]> entries) {
        this.entries = entries;
        Set<String> domainSet = new HashSet<>();
        for (String key : entries.keySet()) {
            if (key.startsWith("assets/")) {
                int start = "assets/".length();
                int slash = key.indexOf('/', start);
                if (slash > start) domainSet.add(key.substring(start, slash));
            }
        }
        this.domains = Collections.unmodifiableSet(domainSet);
    }

    public static void load(java.nio.file.Path zipPath, java.nio.file.Path gameDirectory, int maxEntries, long maxBytes)
        throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        Set<String> archiveNames = new HashSet<>();
        byte[] gregtechLanguage = null;
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
                if (name == null || name.isEmpty()
                    || name.indexOf('\0') >= 0
                    || name.indexOf('\\') >= 0
                    || name.startsWith("/")
                    || name.contains("..")
                    || name.contains(":")) {
                    throw new IOException("Unsafe archive path: " + name);
                }
                if (!archiveNames.add(name)) throw new IOException("Duplicate archive path: " + name);
                if (entry.isDirectory()) continue;

                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    expanded += read;
                    if (expanded > maxBytes) {
                        throw new IOException("Translation pack expands past limit");
                    }
                    bos.write(buffer, 0, read);
                }
                byte[] data = bos.toByteArray();
                if (GREGTECH_LANGUAGE.equals(name)) {
                    gregtechLanguage = data;
                } else if (name.startsWith("assets/")) {
                    entries.put(name, data);
                }
                zip.closeEntry();
            }
        }
        if (gregtechLanguage != null) {
            IOUtil.atomicWrite(gameDirectory.resolve("config/GregTech_zh_CN.lang"), gregtechLanguage);
        }
        INSTANCE = new NHTranslationResourcePack(Collections.unmodifiableMap(entries));
    }

    // ---- IResourcePack ---------------------------------------------------

    @Override
    public InputStream getInputStream(ResourceLocation rl) throws IOException {
        byte[] data = lookup(rl);
        if (data == null) throw new IOException("Resource not found: " + rl);
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

    private byte[] lookup(ResourceLocation rl) {
        String domain = rl.getResourceDomain();
        String path = rl.getResourcePath();
        return entries.get("assets/" + domain + "/" + path);
    }
}
