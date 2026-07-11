package com.dreamyao.nhtranslationupdate.resource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NHTranslationResourcePackTest {

    @TempDir
    Path temporary;

    @Test
    void loadsAssetsAndInstallsOnlyTheKnownGregtechLanguageFile() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        byte[] language = "example.key=示例\n".getBytes(StandardCharsets.UTF_8);
        byte[] gregtech = "languagefile { S:key=格雷科技 }\n".getBytes(StandardCharsets.UTF_8);
        entries.put("assets/example/lang/zh_CN.lang", language);
        entries.put("install/config/GregTech_zh_CN.lang", gregtech);
        entries.put("install/config/should-not-be-written.txt", new byte[] { 1 });
        Path archive = writeZip("valid.zip", entries);
        Path gameDirectory = temporary.resolve("game");

        NHTranslationResourcePack.load(archive, gameDirectory, 10, 1024 * 1024);

        assertTrue(
            NHTranslationResourcePack.INSTANCE.resourceExists(new ResourceLocation("example", "lang/zh_CN.lang")));
        assertArrayEquals(gregtech, Files.readAllBytes(gameDirectory.resolve("config/GregTech_zh_CN.lang")));
        assertTrue(Files.notExists(gameDirectory.resolve("config/should-not-be-written.txt")));
    }

    @Test
    void invalidCandidateDoesNotReplaceTheLoadedPack() throws Exception {
        Map<String, byte[]> validEntries = new LinkedHashMap<>();
        validEntries.put("assets/example/lang/zh_CN.lang", "key=value\n".getBytes(StandardCharsets.UTF_8));
        NHTranslationResourcePack.load(writeZip("valid.zip", validEntries), temporary.resolve("game"), 10, 1024);
        NHTranslationResourcePack loaded = NHTranslationResourcePack.INSTANCE;

        Map<String, byte[]> invalidEntries = new LinkedHashMap<>();
        invalidEntries.put("../outside", new byte[] { 1 });
        assertThrows(
            IOException.class,
            () -> NHTranslationResourcePack
                .load(writeZip("invalid.zip", invalidEntries), temporary.resolve("game"), 10, 1024));
        assertSame(loaded, NHTranslationResourcePack.INSTANCE);
    }

    private Path writeZip(String name, Map<String, byte[]> entries) throws IOException {
        Path archive = temporary.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return archive;
    }
}
