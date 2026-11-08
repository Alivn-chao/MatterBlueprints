package com.gtnhblueprints.wireless;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;

/** Native inventory/recipe/output behavior; wireless adds only the AE connection. */
public class MTEWirelessOutputHatch extends MTEHatchOutputME implements WirelessMEEndpoint {
    private boolean dropPrepared;
    private final WirelessMEBinding wireless = new WirelessMEBinding(this);
    public MTEWirelessOutputHatch(int id, String name, String regional) { super(id, name, regional); }
    private MTEWirelessOutputHatch(String name, int tier, String[] description, ITexture[][][] textures) { super(name, tier, description, textures); }
    @Override public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) { return new MTEWirelessOutputHatch(mName, mTier, mDescriptionArray, mTextures); }
    @Override public WirelessMEBinding wireless() { return wireless; }
    @Override public TileEntity wirelessTile() { return (TileEntity) getBaseMetaTileEntity(); }
    @Override public String getLocalName() { return net.minecraft.util.StatCollector.translateToLocal("gt.blockmachines.matterblueprints.hatch.wireless_output_hatch.name"); }
    @Override public void onFirstTick(IGregTechTileEntity tile) { super.onFirstTick(tile); if (tile.isServerSide()) WirelessMEService.track(wireless); }
    @Override public void onRemoval() { wireless.detach(); WirelessMEService.untrack(wireless); super.onRemoval(); }
    private void prepareDrop() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (dropPrepared || tile == null || !tile.isServerSide()) return;
        dropPrepared = true; getProvider().flushCachedStack(); markDirty();
    }
    @Override public void onPostTick(IGregTechTileEntity tile, long tick) { dropPrepared = false; super.onPostTick(tile, tick); }
    @Override public void onBlockDestroyed() { prepareDrop(); super.onBlockDestroyed(); wireless.removed(); }
    @Override public void saveNBTData(NBTTagCompound tag) { super.saveNBTData(tag); wireless.save(tag); }
    @Override public void loadNBTData(NBTTagCompound tag) { super.loadNBTData(tag); wireless.load(tag); }
    @Override public void setItemNBT(NBTTagCompound tag) { prepareDrop(); super.setItemNBT(tag); getProvider().saveNBTData(tag); wireless.save(tag); }
    @Override public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return WirelessMEGui.decorate(super.buildUI(data, sync, settings), this, sync);
    }
    @Override public String[] getDescription() { return WirelessMEGui.description(super.getDescription()); }
}
