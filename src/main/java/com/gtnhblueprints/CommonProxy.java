package com.gtnhblueprints;

import java.io.File;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

import com.gtnhblueprints.command.BlueprintCommand;
import com.gtnhblueprints.inventory.ContainerBlueprintConfigurator;
import com.gtnhblueprints.machine.HostedMachineRegistry;
import com.gtnhblueprints.network.BlueprintNetwork;
import com.gtnhblueprints.registry.ModBlocks;
import com.gtnhblueprints.registry.ModMachines;
import com.gtnhblueprints.registry.ModItems;
import com.gtnhblueprints.service.BlueprintBuildService;
import com.gtnhblueprints.service.BlueprintLibrary;
import com.gtnhblueprints.service.BlueprintPlanInterceptor;
import com.gtnhblueprints.tile.TileBlueprintConfigurator;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.common.network.NetworkRegistry;

public class CommonProxy implements IGuiHandler {

    public void preInit(FMLPreInitializationEvent event) {
        BlueprintConfig.load(event.getSuggestedConfigurationFile());
        ModBlocks.preInit();
        ModItems.preInit();
        ModMachines.preInit();
        File gameDir = event.getModConfigurationDirectory().getParentFile();
        BlueprintLibrary.initialize(new File(gameDir, "matter-blueprints"));
        BlueprintNetwork.initializeServer();
        NetworkRegistry.INSTANCE.registerGuiHandler(MatterBlueprints.instance, this);
    }

    public void init(FMLInitializationEvent event) {
        ModBlocks.init();
        ModItems.init();
        ModMachines.init();
        BlueprintPlanInterceptor.install();
        BlueprintBuildService service = BlueprintBuildService.INSTANCE;
        FMLCommonHandler.instance().bus().register(service);
        FMLCommonHandler.instance().bus().register(BlueprintNetwork.SERVER_QUEUE);
        MinecraftForge.EVENT_BUS.register(service);
        MinecraftForge.EVENT_BUS.register(HostedMachineRegistry.INSTANCE);
        MatterBlueprints.LOG.info(
            "Matter Blueprints {} targets GTNH {} / GTNewHorizonsCoreMod {} / Matter Manipulator {}",
            MatterBlueprints.VERSION,
            MatterBlueprints.TARGET_GTNH,
            MatterBlueprints.TARGET_GTNH_CORE,
            MatterBlueprints.TARGET_MM);
    }

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new BlueprintCommand());
    }

    public void receiveDownloadedBlueprint(String name, byte[] bytes) {
        MatterBlueprints.LOG.warn("Ignoring a client blueprint download on the dedicated server: {}", name);
    }

    public void receiveActivatedBlueprint(String name, byte[] bytes) {
        MatterBlueprints.LOG.warn("Ignoring a client blueprint activation on the dedicated server: {}", name);
    }

    public void receiveInteractionStatus(
        byte kind,
        int originX,
        int originY,
        int originZ,
        int quarterTurns,
        boolean mirrorX,
        boolean mirrorZ,
        String text
    ) {}

    public void receiveServerBlueprintList(List<String> names) {}

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != ModBlocks.CONFIGURATOR_GUI_ID) return null;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileBlueprintConfigurator)) return null;
        return new ContainerBlueprintConfigurator(player.inventory, (TileBlueprintConfigurator) tile);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        return null;
    }
}
