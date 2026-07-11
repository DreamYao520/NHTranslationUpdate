package com.dreamyao.nhtranslationupdate.resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LanguageGateTest {

    @Test
    void appliesOnlyToSimplifiedChinese() {
        assertTrue(LanguageGate.shouldApply("zh_CN"));
        assertTrue(LanguageGate.shouldApply("ZH_cn"));
        assertFalse(LanguageGate.shouldApply("en_US"));
        assertFalse(LanguageGate.shouldApply("zh_TW"));
        assertFalse(LanguageGate.shouldApply(null));
    }
}
