package com.dreamyao.nhtranslationupdate.resource;

public final class LanguageGate {

    private static final String SIMPLIFIED_CHINESE = "zh_CN";

    private LanguageGate() {}

    public static boolean shouldApply(String language) {
        return language != null && SIMPLIFIED_CHINESE.equalsIgnoreCase(language.trim());
    }
}
