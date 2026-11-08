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
import com.gtnhblueprints.machine.MTEMEInputAssembly;

/** Native inventory/recipe/output behavior; wireless adds only the AE connection. */
public class MTEWirelessInputAssembly extends MTEMEInputAssembly implements WirelessMEEndpoint {
    private final WirelessMEBinding wireless = new WirelessMEBinding(this);
    public MTEWirelessInputAssembly(int id, String name, String regional) { super(id, name, regional); }
    private MTEWirelessInputAssembly(String name, int tier, String[] description, ITexture[][][] textures) { super(name, tier, description, textures); }
    @Override public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) { return new MTEWirelessInputAssembly(mName, mTier, mDescriptionArray, mTextures); }
    @Override public WirelessMEBinding wireless() { return wireless; }
    @Override public TileEntity wirelessTile() { return (TileEntity) getBaseMetaTileEntity(); }
    @Override public String getLocalName() { return net.minecraft.util.StatCollector.translateToLocal("gt.blockmachines.matterblueprints.hatch.wireless_input_assembly.name"); }
    @Override public void onFirstTick(IGregTechTileEntity tile) { super.onFirstTick(tile); if (tile.isServerSide()) WirelessMEService.track(wireless); }
    @Override public void onRemoval() { wireless.detach(); WirelessMEService.untrack(wireless); super.onRemoval(); }
    @Override public void onBlockDestroyed() { super.onBlockDestroyed(); wireless.removed(); }
    @Override public void saveNBTData(NBTTagCompound tag) { super.saveNBTData(tag); wireless.save(tag); }
    @Override public void loadNBTData(NBTTagCompound tag) { super.loadNBTData(tag); wireless.load(tag); }
    @Override public void setItemNBT(NBTTagCompound tag) { super.setItemNBT(tag); wireless.save(tag); }
    @Override public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return WirelessMEGui.decorate(super.buildUI(data, sync, settings), this, sync);
    }
    @Override public String[] getDescription() { return WirelessMEGui.description(super.getDescription()); }
}
