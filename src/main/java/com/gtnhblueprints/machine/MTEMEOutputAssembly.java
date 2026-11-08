package com.gtnhblueprints.machine;

import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.common.util.ForgeDirection;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.IOutputHatch;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;

/** One native item output and one native fluid output provider, using one AE grid connection. */
public class MTEMEOutputAssembly extends MTEHatchOutputBusME {
    private boolean dropPrepared;
    final MEAssemblyFluidPort fluids = new MEAssemblyFluidPort(this);
    public MTEMEOutputAssembly(int id, String name, String regional) { super(id, name, regional); }
    protected MTEMEOutputAssembly(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, tier, description, textures);
    }
    @Override public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEMEOutputAssembly(mName, mTier, mDescriptionArray, mTextures);
    }
    @Override public ITexture[] getTexturesActive(ITexture base) { return MEPortTextures.output(base); }
    @Override public ITexture[] getTexturesInactive(ITexture base) { return getTexturesActive(base); }
    @Override public String getLocalName() {
        return net.minecraft.util.StatCollector.translateToLocal("gt.blockmachines.matterblueprints.hatch.me_output_assembly.name");
    }
    public IOutputHatch getFluidOutput() { return fluids; }
    @Override public void onFirstTick(IGregTechTileEntity tile) {
        super.onFirstTick(tile);
        fluids.provider.updateState();
    }
    @Override public void onPostTick(IGregTechTileEntity tile, long tick) {
        super.onPostTick(tile, tick);
        dropPrepared = false;
        fluids.provider.onPostTick(tile, tick);
    }
    @Override public List<IMEInventoryHandler> getCellArray(StorageChannel channel) {
        return channel == StorageChannel.FLUIDS ? fluids.provider.getCellArray(channel) : super.getCellArray(channel);
    }
    @Override @MENetworkEventSubscribe public void updateCell(MENetworkChannelsChanged event) {
        super.updateCell(event);
        if (fluids != null) fluids.provider.updateCell();
    }
    @Override public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        List<IAEFluidStack> cache = fluids.provider.getCacheList();
        FluidTankInfo[] result = new FluidTankInfo[cache.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = new FluidTankInfo(cache.get(i).getFluidStack(),
                (int) Math.min(Integer.MAX_VALUE, fluids.provider.getCacheCapacity()));
        }
        return result;
    }
    private void saveFluids(NBTTagCompound tag) {
        NBTTagCompound contents = new NBTTagCompound(); fluids.save(contents); tag.setTag("mbFluidOutput", contents);
    }
    @Override public void saveNBTData(NBTTagCompound tag) { super.saveNBTData(tag); saveFluids(tag); }
    @Override public void loadNBTData(NBTTagCompound tag) {
        if (!tag.hasKey("baseCapacity")) tag.setLong("baseCapacity", 1_600);
        super.loadNBTData(tag);
        if (tag.hasKey("mbFluidOutput")) fluids.load(tag.getCompoundTag("mbFluidOutput"));
        else fluids.migrateLegacy(tag.getCompoundTag("mbAssemblyOutput"));
    }
    private void prepareDrop() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (dropPrepared || tile == null || !tile.isServerSide()) return;
        dropPrepared = true;
        getProvider().flushCachedStack();
        fluids.provider.flushCachedStack();
        markDirty();
    }
    @Override public void onBlockDestroyed() { prepareDrop(); super.onBlockDestroyed(); }
    @Override public void setItemNBT(NBTTagCompound tag) {
        prepareDrop();
        super.setItemNBT(tag);
        // Native setItemNBT only saves configuration; preserve unaccepted output while moving this assembly.
        getProvider().saveNBTData(tag);
        saveFluids(tag);
    }
    @Override public com.cleanroommc.modularui.screen.ModularPanel buildUI(
        com.cleanroommc.modularui.factory.PosGuiData data,
        com.cleanroommc.modularui.value.sync.PanelSyncManager sync,
        com.cleanroommc.modularui.screen.UISettings settings) {
        return new MEOutputAssemblyGui(this).build(data, sync, settings);
    }
    @Override public String[] getDescription() {
        return new String[] { net.minecraft.util.StatCollector.translateToLocal("matterblueprints.output.description"),
            net.minecraft.util.StatCollector.translateToLocal("matterblueprints.output.buffer") };
    }
}
