package com.dreamyao.nhtranslationupdate.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dreamyao.nhtranslationupdate.version.PackVersionDetector.Result;

class PackVersionDetectorTest {

    @TempDir
    Path gameDirectory;

    @Test
    void readsOfficialMainMenuMarkerFirst() throws Exception {
        Path marker = gameDirectory.resolve("config/txloader/load/mainmenu/version.txt");
        Files.createDirectories(marker.getParent());
        Files.write(marker, "GTNH 2.9.0-beta-2 (2026-07-05)\n".getBytes(StandardCharsets.UTF_8));

        Result result = PackVersionDetector.detect(gameDirectory, "");
        assertEquals("2.9.0-beta-2", result.version);
        assertEquals("GTNH main-menu version marker", result.source);
    }

    @Test
    void fallsBackToOfficialDreamcraftConfig() throws Exception {
        Path marker = gameDirectory.resolve("config/GTNewHorizons/dreamcraft.cfg");
        Files.createDirectories(marker.getParent());
        Files.write(marker, "modules {\n    S:ModPackVersion=2.8.4\n}\n".getBytes(StandardCharsets.UTF_8));

        assertEquals("2.8.4", PackVersionDetector.detect(gameDirectory, "").version);
    }

    @Test
    void explicitConfigurationOverridesOfficialMarkers() {
        Result result = PackVersionDetector.detect(gameDirectory, "2.8.4-custom");
        assertEquals("2.8.4-custom", result.version);
        assertEquals("nhtranslationupdate.properties", result.source);
    }

    @Test
    void refusesUnknownOrUnsafeVersions() {
        assertNull(PackVersionDetector.detect(gameDirectory, "../../wrong"));
    }
}
