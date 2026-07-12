package com.dreamyao.nhtranslationupdate.resource;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

import com.dreamyao.nhtranslationupdate.util.Hashing;
import com.dreamyao.nhtranslationupdate.util.IOUtil;

/**
 * A virtual, disk-backed resource pack. It is injected directly into the
 * reload list and is never registered in Minecraft's resource-pack menu.
 */
public final class NHTranslationResourcePack implements IResourcePack, Closeable {

    private static final Pattern LOCALE = Pattern.compile("[a-z]{2}_[A-Z]{2}");
    private static final Pattern GREGTECH = Pattern.compile("config/GregTech_([a-z]{2}_[A-Z]{2})\\.lang");
    private static final Pattern LOADING_TIP = Pattern
        .compile("config/Betterloadingscreen/tips/([a-z]{2}_[A-Z]{2})\\.txt");
    private static final Pattern TROPHY = Pattern.compile("config/amazingtrophies/lang/([a-z]{2}_[A-Z]{2})\\.lang");
    private static final Pattern IN_GAME_INFO = Pattern
        .compile("config/InGameInfoXML/InGameInfo_([a-z]{2}_[A-Z]{2})\\.xml");

    public static volatile NHTranslationResourcePack INSTANCE;

    private final ZipFile archive;
    private final Map<String, String> entries;
    private final Set<String> domains;
    private final Set<String> supportedLanguages;

    private NHTranslationResourcePack(ZipFile archive, Map<String, String> entries, Set<String> domains,
        Set<String> supportedLanguages) {
        this.archive = archive;
        this.entries = Collections.unmodifiableMap(entries);
        this.domains = Collections.unmodifiableSet(domains);
        this.supportedLanguages = Collections.unmodifiableSet(supportedLanguages);
    }

    public static void load(Path zipPath, Path gameDirectory, Set<String> languages, int maxEntries, long maxBytes)
        throws IOException {
        Set<String> supported = validateLanguages(languages);
        ZipFile zip = new ZipFile(zipPath.toFile());
        boolean accepted = false;
        try {
            Map<String, String> resourceEntries = new HashMap<>();
            Map<Path, String> installEntries = new LinkedHashMap<>();
            Set<String> domains = new HashSet<>();
            Set<String> archiveNames = new HashSet<>();
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            int count = 0;
            long expanded = 0;
            byte[] buffer = new byte[64 * 1024];

            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (++count > maxEntries) throw new IOException("Translation pack has too many entries: " + count);
                String name = entry.getName();
                requireSafeArchivePath(name);
                if (!archiveNames.add(name)) throw new IOException("Duplicate archive path: " + name);
                if (entry.isDirectory()) continue;

                boolean resource = name.startsWith("assets/");
                String installPath = name.startsWith("install/") ? name.substring("install/".length()) : null;
                if (!resource && (installPath == null || !isAllowedInstallPath(installPath, supported))) {
                    throw new IOException("Unexpected translation archive path: " + name);
                }

                try (InputStream input = zip.getInputStream(entry)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read == 0) continue;
                        expanded += read;
                        if (expanded > maxBytes) throw new IOException("Translation pack expands past limit");
                    }
                }

                if (resource) {
                    resourceEntries.put(name, name);
                    addDomain(name, domains);
                } else {
                    Path target = gameDirectory.resolve(installPath)
                        .toAbsolutePath()
                        .normalize();
                    Path root = gameDirectory.toAbsolutePath()
                        .normalize();
                    if (!target.startsWith(root)) throw new IOException("Install path escapes the game directory");
                    installEntries.put(target, name);
                }
            }
            if (resourceEntries.isEmpty()) throw new IOException("Translation pack contains no resource entries");

            for (Map.Entry<Path, String> install : installEntries.entrySet()) {
                byte[] data = readEntry(zip, install.getValue());
                Path target = install.getKey();
                if (!Files.isRegularFile(target) || Files.size(target) != data.length
                    || !Hashing.sha256(target)
                        .equals(Hashing.sha256(data))) {
                    IOUtil.atomicWrite(target, data);
                }
            }

            NHTranslationResourcePack candidate = new NHTranslationResourcePack(
                zip,
                resourceEntries,
                domains,
                new LinkedHashSet<>(supported));
            NHTranslationResourcePack previous = INSTANCE;
            INSTANCE = candidate;
            accepted = true;
            if (previous != null) {
                try {
                    previous.close();
                } catch (IOException ignored) {
                    // The newly validated pack is already active; an old cache handle must not roll it back.
                }
            }
        } finally {
            if (!accepted) zip.close();
        }
    }

    public Set<String> getSupportedLanguages() {
        return supportedLanguages;
    }

    static void clearForTests() throws IOException {
        NHTranslationResourcePack current = INSTANCE;
        INSTANCE = null;
        if (current != null) current.close();
    }

    @Override
    public InputStream getInputStream(ResourceLocation resource) throws IOException {
        String name = lookup(resource);
        if (name == null) throw new IOException("Resource not found: " + resource);
        ZipEntry entry = archive.getEntry(name);
        if (entry == null) throw new IOException("Resource disappeared from translation archive: " + resource);
        return archive.getInputStream(entry);
    }

    @Override
    public boolean resourceExists(ResourceLocation resource) {
        return lookup(resource) != null;
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
        return "GTNH Translations (managed)";
    }

    @Override
    public void close() throws IOException {
        archive.close();
    }

    private String lookup(ResourceLocation resource) {
        return entries.get("assets/" + resource.getResourceDomain() + "/" + resource.getResourcePath());
    }

    private static Set<String> validateLanguages(Set<String> languages) throws IOException {
        if (languages == null || languages.isEmpty()) throw new IOException("Translation pack has no languages");
        Set<String> result = new LinkedHashSet<>();
        for (String language : languages) {
            if (language == null || !LOCALE.matcher(language)
                .matches()) {
                throw new IOException("Invalid translation language: " + language);
            }
            result.add(language);
        }
        return result;
    }

    private static void requireSafeArchivePath(String name) throws IOException {
        if (name == null || name.isEmpty()
            || name.indexOf('\0') >= 0
            || name.indexOf('\\') >= 0
            || name.startsWith("/")
            || name.contains("..")
            || name.contains(":")) {
            throw new IOException("Unsafe archive path: " + name);
        }
    }

    private static boolean isAllowedInstallPath(String path, Set<String> supported) {
        return matchesSupportedLocale(path, GREGTECH, supported) || matchesSupportedLocale(path, LOADING_TIP, supported)
            || matchesSupportedLocale(path, TROPHY, supported)
            || matchesSupportedLocale(path, IN_GAME_INFO, supported);
    }

    private static boolean matchesSupportedLocale(String path, Pattern pattern, Set<String> supported) {
        Matcher matcher = pattern.matcher(path);
        return matcher.matches() && supported.contains(matcher.group(1));
    }

    private static void addDomain(String name, Set<String> domains) {
        int start = "assets/".length();
        int slash = name.indexOf('/', start);
        if (slash > start) domains.add(name.substring(start, slash));
    }

    private static byte[] readEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) throw new IOException("Install entry disappeared from translation archive: " + name);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = zip.getInputStream(entry)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }
}
