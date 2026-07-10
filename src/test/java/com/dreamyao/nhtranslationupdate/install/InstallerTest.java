package com.dreamyao.nhtranslationupdate.install;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dreamyao.nhtranslationupdate.config.UpdateConfig;
import com.dreamyao.nhtranslationupdate.util.Hashing;

class InstallerTest {

    @TempDir
    Path gameDirectory;

    @Test
    void installsAndActivatesResourcePack() throws Exception {
        Path archive = gameDirectory.resolve("resource-pack.zip");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("pack.mcmeta", "{\"pack\":{\"pack_format\":1,\"description\":\"test\"}}");
        entries.put("assets/example/lang/zh_CN.lang", "example.key=示例");
        writeZip(archive, entries);

        ResourcePackInstaller installer = new ResourcePackInstaller(UpdateConfig.load(gameDirectory));
        installer.install(archive, Hashing.sha256(archive));

        assertTrue(Files.isRegularFile(gameDirectory.resolve("resourcepacks/NHTranslationUpdate.zip")));
        String options = new String(Files.readAllBytes(gameDirectory.resolve("options.txt")), StandardCharsets.UTF_8);
        assertTrue(options.contains("NHTranslationUpdate.zip"));
        assertTrue(options.contains("lang:zh_CN"));
    }

    @Test
    void removesOnlyUnmodifiedStaleOverlayFiles() throws Exception {
        UpdateConfig config = UpdateConfig.load(gameDirectory);
        OverlayInstaller installer = new OverlayInstaller(config);
        Path first = gameDirectory.resolve("first.zip");
        writeZip(first, singleEntry("resources/example/lang/zh_CN.lang", "first"));
        installer.install(first, "overlay", Hashing.sha256(first));

        Path installed = gameDirectory.resolve("resources/example/lang/zh_CN.lang");
        assertTrue(Files.isRegularFile(installed));
        Path second = gameDirectory.resolve("second.zip");
        writeZip(second, singleEntry("config/txloader/example/lang/zh_CN.lang", "second"));
        installer.install(second, "overlay", Hashing.sha256(second));
        assertFalse(Files.exists(installed));

        installer.install(first, "overlay-two", Hashing.sha256(first));
        Files.write(installed, "user change".getBytes(StandardCharsets.UTF_8));
        installer.install(second, "overlay-two", Hashing.sha256(second));
        assertTrue(Files.exists(installed));
    }

    private static Map<String, String> singleEntry(String name, String value) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(name, value);
        return entries;
    }

    private static void writeZip(Path target, Map<String, String> entries) throws IOException {
        try (OutputStream output = Files.newOutputStream(target); ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(
                    entry.getValue()
                        .getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }
}
