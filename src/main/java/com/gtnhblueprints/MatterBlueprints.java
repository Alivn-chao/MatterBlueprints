package com.gtnhblueprints;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = MatterBlueprints.MODID,
    version = MatterBlueprints.VERSION,
    name = MatterBlueprints.NAME,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:gregtech;required-after:matter-manipulator")
public final class MatterBlueprints {

    @Mod.Instance("matterblueprints")
    public static MatterBlueprints instance;

    public static final String MODID = "matterblueprints";
    public static final String NAME = "Matter Blueprints";
    public static final String VERSION = "0.3.1-beta2";
    public static final String TARGET_GTNH = "2.9.0-beta-2";
    public static final String TARGET_GTNH_CORE = "2.9.12";
    public static final String TARGET_MM = "0.1.46-GTNH";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.gtnhblueprints.ClientProxy", serverSide = "com.gtnhblueprints.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
