package com.gtnhblueprints;

import java.io.IOException;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.gtnhblueprints.client.ClientBlueprintController;
import com.gtnhblueprints.client.LocalBlueprintCommand;
import com.gtnhblueprints.client.gui.GuiBlueprintConfigurator;
import com.gtnhblueprints.inventory.ContainerBlueprintConfigurator;
import com.gtnhblueprints.io.BlueprintIO;
import com.gtnhblueprints.network.BlueprintNetwork;
import com.gtnhblueprints.registry.ModBlocks;
import com.gtnhblueprints.tile.TileBlueprintConfigurator;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.ClientCommandHandler;

public final class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        BlueprintNetwork.initializeClient();
        ClientCommandHandler.instance.registerCommand(new LocalBlueprintCommand());
        MinecraftForge.EVENT_BUS.register(ClientBlueprintController.INSTANCE);
        FMLCommonHandler.instance().bus().register(ClientBlueprintController.INSTANCE);
    }

    @Override
    public void receiveActivatedBlueprint(final String name, final byte[] bytes) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {

            @Override
            public void run() {
                ClientBlueprintController.INSTANCE.activate(name, bytes);
            }
        });
    }

    @Override
    public void receiveInteractionStatus(
        final byte kind,
        final int originX,
        final int originY,
        final int originZ,
        final int quarterTurns,
        final boolean mirrorX,
        final boolean mirrorZ,
        final String text
    ) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {

            @Override
            public void run() {
                ClientBlueprintController.INSTANCE.status(
                    kind,
                    originX,
                    originY,
                    originZ,
                    quarterTurns,
                    mirrorX,
                    mirrorZ,
                    text);
            }
        });
    }

    @Override
    public void receiveDownloadedBlueprint(final String name, final byte[] bytes) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {

            @Override
            public void run() {
                saveDownloadedBlueprint(name, bytes);
            }
        });
    }

    private static void saveDownloadedBlueprint(String name, byte[] bytes) {
        try {
            BlueprintIO.write(com.gtnhblueprints.service.BlueprintLibrary.directory(), name, bytes);
            GuiBlueprintConfigurator.localLibraryChanged();
            if (Minecraft.getMinecraft().thePlayer != null) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(
                    new ChatComponentText("§a[GTBP] 已下载到本地: " + name + ".gtbp"));
            }
        } catch (IOException exception) {
            MatterBlueprints.LOG.error("Could not save downloaded blueprint", exception);
            if (Minecraft.getMinecraft().thePlayer != null) {
                Minecraft.getMinecraft().thePlayer.addChatMessage(
                    new ChatComponentText("§c[GTBP] 保存下载蓝图失败: " + exception.getMessage()));
            }
        }
    }

    @Override
    public void receiveServerBlueprintList(final List<String> names) {
        Minecraft.getMinecraft().func_152344_a(() -> GuiBlueprintConfigurator.receiveServerList(names));
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != ModBlocks.CONFIGURATOR_GUI_ID) return null;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileBlueprintConfigurator)) return null;
        ContainerBlueprintConfigurator container = new ContainerBlueprintConfigurator(
            player.inventory,
            (TileBlueprintConfigurator) tile);
        return new GuiBlueprintConfigurator(container);
    }
}
