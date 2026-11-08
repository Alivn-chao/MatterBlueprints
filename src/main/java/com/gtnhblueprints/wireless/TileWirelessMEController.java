package com.gtnhblueprints.wireless;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import appeng.tile.networking.TileController;
import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

/** A real AE controller segment; native power, color, and multiblock validation are inherited. */
public final class TileWirelessMEController extends TileController implements WirelessMEEndpoint, IGuiHolder<PosGuiData> {
    private final WirelessMEBinding wireless = new WirelessMEBinding(this);
    @Override public WirelessMEBinding wireless() { return wireless; }
    @Override public TileEntity wirelessTile() { return this; }
    @Override public boolean wirelessSource() { return true; }
    @Override public void onReady() { super.onReady(); if (!worldObj.isRemote) WirelessMEService.track(wireless); }
    @Override public void onChunkUnload() { wireless.detach(); WirelessMEService.untrack(wireless); super.onChunkUnload(); }
    @Override public void invalidate() { wireless.detach(); WirelessMEService.untrack(wireless); super.invalidate(); }
    @appeng.tile.TileEvent(appeng.tile.events.TileEventType.WORLD_NBT_READ)
    public void readWireless(NBTTagCompound tag) { wireless.load(tag); }
    @appeng.tile.TileEvent(appeng.tile.events.TileEventType.WORLD_NBT_WRITE)
    public void writeWireless(NBTTagCompound tag) { wireless.save(tag); }
    @Override public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return WirelessMEGui.build(this, sync, data.getPlayer());
    }
}
