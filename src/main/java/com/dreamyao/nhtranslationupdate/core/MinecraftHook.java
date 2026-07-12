package com.dreamyao.nhtranslationupdate.core;

import java.util.List;

import net.minecraft.client.Minecraft;

import com.dreamyao.nhtranslationupdate.resource.LanguageGate;
import com.dreamyao.nhtranslationupdate.resource.NHTranslationResourcePack;

/**
 * Hooked into {@code Minecraft.refreshResources()} by the ASM transformer.
 * Inserts the NHTranslationResourcePack at the very end of the resource-pack
 * list so it overrides everything — including TX Loader's forceload pack.
 */
@SuppressWarnings("unused")
public final class MinecraftHook {

    private MinecraftHook() {}

    public static List<Object> insertPack(List<Object> resourcePackList) {
        for (int index = resourcePackList.size() - 1; index >= 0; index--) {
            if (resourcePackList.get(index) instanceof NHTranslationResourcePack) {
                resourcePackList.remove(index);
            }
        }

        NHTranslationResourcePack pack = NHTranslationResourcePack.INSTANCE;
        Minecraft minecraft = Minecraft.getMinecraft();
        String language = minecraft.gameSettings == null ? null : minecraft.gameSettings.language;
        if (pack != null && LanguageGate.shouldApply(language, pack.getSupportedLanguages())) {
            resourcePackList.add(pack);
        }
        return resourcePackList;
    }
}
