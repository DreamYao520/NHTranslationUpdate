package com.dreamyao.nhtranslationupdate.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dreamyao.nhtranslationupdate.manifest.UpdateManifest.Artifact;
import com.dreamyao.nhtranslationupdate.update.StateStore.LastKnownGood;

class StateStoreTest {

    @TempDir
    Path temporary;

    @Test
    void persistsLanguagesWithTheLastKnownGoodArtifact() throws Exception {
        Artifact artifact = new Artifact();
        artifact.id = "gtnh-multilingual-translation";
        artifact.sha256 = "0000000000000000000000000000000000000000000000000000000000000000";
        LinkedHashSet<String> languages = new LinkedHashSet<>(Arrays.asList("ja_JP", "zh_CN"));

        StateStore state = new StateStore(temporary);
        state.setLastKnownGood("2.9.0-beta-2", "release", artifact, languages);
        state.save();

        LastKnownGood loaded = new StateStore(temporary).lastKnownGood();
        assertNotNull(loaded);
        assertEquals(languages, loaded.languages);
    }
}
