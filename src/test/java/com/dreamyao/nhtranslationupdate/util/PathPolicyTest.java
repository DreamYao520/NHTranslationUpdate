package com.dreamyao.nhtranslationupdate.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class PathPolicyTest {

    @Test
    void rejectsArchiveTraversalAndWindowsPaths() {
        assertThrows(IllegalArgumentException.class, () -> PathPolicy.safeRelative("../mods/evil.jar"));
        assertThrows(IllegalArgumentException.class, () -> PathPolicy.safeRelative("C:/mods/evil.jar"));
        assertThrows(IllegalArgumentException.class, () -> PathPolicy.safeRelative("config\\txloader\\evil.lang"));
    }

    @Test
    void acceptsOnlyConfiguredRoots() {
        assertEquals("config/txloader/lang/zh_CN.lang", PathPolicy.safeRelative("config/txloader/lang/zh_CN.lang"));
        assertTrue(PathPolicy.isAllowed("config/txloader/lang/zh_CN.lang", Arrays.asList("config/txloader")));
        assertFalse(PathPolicy.isAllowed("mods/evil.jar", Arrays.asList("config/txloader")));
    }
}
