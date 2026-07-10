package com.dreamyao.nhtranslationupdate.install;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.dreamyao.nhtranslationupdate.NHTranslationUpdate;
import com.dreamyao.nhtranslationupdate.config.UpdateConfig;
import com.dreamyao.nhtranslationupdate.util.Hashing;
import com.dreamyao.nhtranslationupdate.util.IOUtil;
import com.dreamyao.nhtranslationupdate.util.PathPolicy;

public final class OverlayInstaller {

    private final UpdateConfig config;

    public OverlayInstaller(UpdateConfig config) {
        this.config = config;
    }

    public void install(Path archive, String artifactId, String artifactHash) throws IOException {
        Path staging = config.cacheDirectory.resolve("staging")
            .resolve(artifactId + "-" + artifactHash);
        IOUtil.deleteTree(staging);
        Files.createDirectories(staging);
        Map<String, String> next = extract(archive, staging);
        if (next.isEmpty()) throw new IOException("Overlay contains no allowed files");

        Path managedDirectory = config.cacheDirectory.resolve("managed");
        Files.createDirectories(managedDirectory);
        Path managedFile = managedDirectory.resolve(artifactId + ".properties");
        Properties previous = loadProperties(managedFile);
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
        Path backupRoot = config.cacheDirectory.resolve("backups")
            .resolve(timestamp);

        for (Map.Entry<String, String> entry : next.entrySet()) {
            String relative = entry.getKey();
            Path source = PathPolicy.resolveInside(staging, relative);
            Path target = PathPolicy.resolveInside(config.gameDirectory, relative);
            if (Files.isRegularFile(target) && !entry.getValue()
                .equalsIgnoreCase(Hashing.sha256(target))) {
                Path backup = PathPolicy.resolveInside(backupRoot, relative);
                Files.createDirectories(backup.getParent());
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
            IOUtil.atomicCopy(source, target);
        }

        for (String relative : previous.stringPropertyNames()) {
            if (next.containsKey(relative)) continue;
            Path target = PathPolicy.resolveInside(config.gameDirectory, relative);
            if (Files.isRegularFile(target) && previous.getProperty(relative)
                .equalsIgnoreCase(Hashing.sha256(target))) {
                Files.delete(target);
            } else if (Files.exists(target)) {
                NHTranslationUpdate.LOG.warn("Preserved user-modified stale translation file: {}", relative);
            }
        }

        storeProperties(managedFile, next);
        IOUtil.deleteTree(staging);
        pruneBackups();
    }

    private Map<String, String> extract(Path archive, Path staging) throws IOException {
        Map<String, String> files = new TreeMap<>();
        int entries = 0;
        long expanded = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > config.maxZipEntries) throw new IOException("Overlay has too many entries");
                String relative;
                try {
                    relative = PathPolicy.safeRelative(entry.getName());
                } catch (IllegalArgumentException exception) {
                    throw new IOException(exception.getMessage(), exception);
                }
                if (!PathPolicy.isAllowed(relative, config.allowedOverlayRoots)) {
                    throw new IOException("Overlay path is not allowed: " + relative);
                }
                if (entry.isDirectory()) continue;

                Path outputPath = PathPolicy.resolveInside(staging, relative);
                Files.createDirectories(outputPath.getParent());
                try (OutputStream output = Files.newOutputStream(outputPath)) {
                    int read;
                    while ((read = zip.read(buffer)) >= 0) {
                        if (read == 0) continue;
                        expanded += read;
                        if (expanded > config.maxExtractedBytes) throw new IOException("Overlay expands past limit");
                        output.write(buffer, 0, read);
                    }
                }
                files.put(relative, Hashing.sha256(outputPath));
                zip.closeEntry();
            }
        } catch (IOException exception) {
            IOUtil.deleteTree(staging);
            throw exception;
        }
        return files;
    }

    private static Properties loadProperties(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            }
        }
        return properties;
    }

    private static void storeProperties(Path file, Map<String, String> values) throws IOException {
        Properties properties = new Properties();
        properties.putAll(values);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "Files managed by NH Translation Update");
        }
        IOUtil.atomicMove(temporary, file);
    }

    private void pruneBackups() throws IOException {
        Path backups = config.cacheDirectory.resolve("backups");
        if (!Files.isDirectory(backups)) return;
        List<Path> directories;
        try (Stream<Path> stream = Files.list(backups)) {
            directories = stream.filter(Files::isDirectory)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        }
        for (int i = config.keepBackups; i < directories.size(); i++) {
            IOUtil.deleteTree(directories.get(i));
        }
    }
}
