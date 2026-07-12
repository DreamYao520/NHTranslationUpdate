package com.dreamyao.nhtranslationupdate.resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;

class LanguageGateTest {

    @Test
    void appliesOnlyToLanguagesPublishedForTheRelease() {
        LinkedHashSet<String> supported = new LinkedHashSet<>(Arrays.asList("zh_CN", "ja_JP"));
        assertTrue(LanguageGate.shouldApply("zh_CN", supported));
        assertTrue(LanguageGate.shouldApply("JA_jp", supported));
        assertFalse(LanguageGate.shouldApply("en_US", supported));
        assertFalse(LanguageGate.shouldApply("zh_TW", supported));
        assertFalse(LanguageGate.shouldApply(null, supported));
    }
}
