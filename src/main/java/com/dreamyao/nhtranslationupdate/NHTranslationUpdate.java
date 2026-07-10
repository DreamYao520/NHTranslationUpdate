package com.dreamyao.nhtranslationupdate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = NHTranslationUpdate.MOD_ID,
    name = "NH Translation Update",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public final class NHTranslationUpdate {

    public static final String MOD_ID = "nhtranslationupdate";
    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("NH Translation Update {}: {}", Tags.VERSION, UpdateBootstrap.getLastStatus());
    }
}
