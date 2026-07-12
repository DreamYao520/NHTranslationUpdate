package com.dreamyao.nhtranslationupdate.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyResourcePackCleanupTest {

    @TempDir
    Path gameDirectory;

    @Test
    void removesOnlyTheLegacyPackAndKeepsLanguageAndOtherPacks() throws Exception {
        Path legacy = gameDirectory.resolve("resourcepacks/NHTranslationUpdate.zip");
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, new byte[] { 1 });
        Path options = gameDirectory.resolve("options.txt");
        Files.write(
            options,
            ("lang:en_US\nresourcePacks:[\"Other.zip\",\"NHTranslationUpdate.zip\"]\n")
                .getBytes(StandardCharsets.UTF_8));

        assertTrue(LegacyResourcePackCleanup.run(gameDirectory));

        String updated = new String(Files.readAllBytes(options), StandardCharsets.UTF_8);
        assertFalse(Files.exists(legacy));
        assertTrue(updated.contains("lang:en_US"));
        assertTrue(updated.contains("Other.zip"));
        assertFalse(updated.contains("NHTranslationUpdate.zip"));
    }
}
