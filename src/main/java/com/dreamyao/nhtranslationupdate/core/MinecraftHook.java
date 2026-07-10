package com.dreamyao.nhtranslationupdate.core;

import java.util.List;

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
        NHTranslationResourcePack pack = NHTranslationResourcePack.INSTANCE;
        if (pack != null) {
            resourcePackList.add(pack);
        }
        return resourcePackList;
    }
}
