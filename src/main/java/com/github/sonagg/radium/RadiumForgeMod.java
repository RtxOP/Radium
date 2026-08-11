package com.github.sonagg.radium;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;

// Client-only renderer mod: FML skips loading this @Mod on a dedicated
// server (the coremod is already skipped there via ModSide: CLIENT).
@Mod(modid = "radium", version = RadiumForgeMod.VERSION, name = RadiumForgeMod.NAME, clientSideOnly = true)
public class RadiumForgeMod {
    public static final String MODID = "radium";
    public static final String NAME = "Radium";
    public static final String VERSION = "0.8.15";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        SodiumClientMod.onInitialization(VERSION);
    }
}
