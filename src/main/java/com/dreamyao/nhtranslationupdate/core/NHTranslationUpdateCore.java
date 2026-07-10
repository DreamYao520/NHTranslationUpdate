package com.dreamyao.nhtranslationupdate.core;

import java.io.File;
import java.util.Map;

import com.dreamyao.nhtranslationupdate.UpdateBootstrap;

import cpw.mods.fml.relauncher.FMLLaunchHandler;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("NHTranslationUpdateCore")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions("com.dreamyao.nhtranslationupdate")
public final class NHTranslationUpdateCore implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        Object location = data.get("mcLocation");
        File gameDirectory = location instanceof File ? (File) location : new File(".");
        UpdateBootstrap.run(
            gameDirectory,
            FMLLaunchHandler.side()
                .isClient());
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
