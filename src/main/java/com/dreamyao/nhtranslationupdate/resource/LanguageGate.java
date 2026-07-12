package com.dreamyao.nhtranslationupdate.resource;

import java.util.Set;

public final class LanguageGate {

    private LanguageGate() {}

    public static boolean shouldApply(String language, Set<String> supportedLanguages) {
        if (language == null || supportedLanguages == null) return false;
        String selected = language.trim();
        for (String supported : supportedLanguages) {
            if (selected.equalsIgnoreCase(supported)) return true;
        }
        return false;
    }
}
