package com.dreamyao.nhtranslationupdate.manifest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class UpdateManifestTest {

    @Test
    void validatesCompatibilityAndArtifacts() {
        UpdateManifest manifest = validManifest();
        manifest.validate();
        assertTrue(manifest.supportsPackVersion("2.8.4"));
    }

    @Test
    void rejectsDuplicateArtifactIds() {
        UpdateManifest manifest = validManifest();
        manifest.artifacts = Arrays.asList(artifact(), artifact());
        assertThrows(IllegalArgumentException.class, manifest::validate);
    }

    private static UpdateManifest validManifest() {
        UpdateManifest manifest = new UpdateManifest();
        manifest.schemaVersion = 1;
        manifest.release = "2.8.4-cn.1";
        manifest.minecraftVersion = "1.7.10";
        manifest.packVersions = Arrays.asList("2.8.4");
        manifest.artifacts = Arrays.asList(artifact());
        return manifest;
    }

    private static UpdateManifest.Artifact artifact() {
        UpdateManifest.Artifact artifact = new UpdateManifest.Artifact();
        artifact.id = "resource-pack";
        artifact.kind = "resource_pack";
        artifact.url = "https://example.invalid/resource-pack.zip";
        artifact.sha256 = "0000000000000000000000000000000000000000000000000000000000000000";
        artifact.size = 1;
        return artifact;
    }
}
