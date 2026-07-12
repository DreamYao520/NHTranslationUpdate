package com.dreamyao.nhtranslationupdate.manifest;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

class UpdateManifestTest {

    @Test
    void selectsOnlyTheExactGtnhVersion() {
        UpdateManifest manifest = validManifest();
        manifest.validate();
        assertSame(manifest.packs.get("2.8.4"), manifest.select("2.8.4"));
        assertNull(manifest.select("2.8.3"));
        assertNull(manifest.select(null));
    }

    @Test
    void supportsPublishedLanguagesAndDefaultsOldPacksToChinese() {
        UpdateManifest manifest = validManifest();
        assertTrue(
            manifest.packs.get("2.8.4")
                .supportedLanguages()
                .contains("zh_CN"));
        manifest.packs.get("2.8.4").languages = Arrays.asList("de_DE", "ja_JP");
        manifest.validate();
        assertTrue(
            manifest.packs.get("2.8.4")
                .supportedLanguages()
                .contains("ja_JP"));
    }

    @Test
    void rejectsDuplicateArtifactIds() {
        UpdateManifest manifest = validManifest();
        manifest.packs.get("2.8.4").artifacts = Arrays.asList(artifact(), artifact());
        assertThrows(IllegalArgumentException.class, manifest::validate);
    }

    @Test
    void rejectsSchemaV2() {
        UpdateManifest manifest = validManifest();
        manifest.schemaVersion = 2;
        assertThrows(IllegalArgumentException.class, manifest::validate);
    }

    @Test
    void rejectsOldResourcePackKind() {
        UpdateManifest.Artifact artifact = artifact();
        artifact.kind = "resource_pack";
        UpdateManifest manifest = validManifest();
        manifest.packs.get("2.8.4").artifacts = Arrays.asList(artifact);
        assertThrows(IllegalArgumentException.class, manifest::validate);
    }

    private static UpdateManifest validManifest() {
        UpdateManifest manifest = new UpdateManifest();
        manifest.schemaVersion = 3;
        manifest.minecraftVersion = "1.7.10";
        manifest.packs = new LinkedHashMap<>();
        UpdateManifest.PackRelease release = new UpdateManifest.PackRelease();
        release.release = "2.8.4-cn.1";
        release.artifacts = Arrays.asList(artifact());
        manifest.packs.put("2.8.4", release);
        return manifest;
    }

    private static UpdateManifest.Artifact artifact() {
        UpdateManifest.Artifact artifact = new UpdateManifest.Artifact();
        artifact.id = "gtnh-zh-cn-translation";
        artifact.kind = "translation";
        artifact.url = "https://example.invalid/translation.zip";
        artifact.sha256 = "0000000000000000000000000000000000000000000000000000000000000000";
        artifact.size = 1;
        return artifact;
    }
}
