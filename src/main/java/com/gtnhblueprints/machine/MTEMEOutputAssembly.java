package com.gtnhblueprints.machine;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.common.util.ForgeDirection;
import appeng.me.GridAccessException;
import appeng.api.storage.data.IAEFluidStack;
import appeng.util.Platform;
import appeng.util.item.AEFluidStack;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.IOutputHatch;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;

public class MTEMEOutputAssembly extends MTEHatchOutputBusME {
    private boolean dropPrepared;
    private final MEAssemblyFluidOutput fluids = new MEAssemblyFluidOutput(this::markDirty);
    public MTEMEOutputAssembly(int id, String name, String regional) { super(id, name, regional); }
    private MTEMEOutputAssembly(String name, int tier, String[] description, ITexture[][][] textures) {
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
    @Override public void onPostTick(IGregTechTileEntity tile, long tick) {
        super.onPostTick(tile, tick);
        dropPrepared = false;
        if (tile.isServerSide() && tick % 20 == 0 && tile.isAllowedToWork()) flushFluidCache();
    }
    private void flushFluidCache() {
        if (!getProxy().isActive()) return;
        try {
            for (int i = 0; i < fluids.fluids.length; i++) {
                FluidStack stored = fluids.fluids[i];
                if (stored == null || stored.amount <= 0) continue;
                IAEFluidStack left = Platform.poweredInsert(getProxy().getEnergy(), getProxy().getStorage().getFluidInventory(),
                    AEFluidStack.create(stored), getActionSource());
                int remaining = left == null ? 0 : (int) left.getStackSize();
                if (remaining != stored.amount) { markDirty(); notifyWatchers(); }
                stored.amount = remaining;
                if (remaining == 0) fluids.fluids[i] = null;
            }
        } catch (GridAccessException ignored) { /* Unaccepted output remains in the local buffer. */ }
    }
    @Override public FluidTankInfo[] getTankInfo(ForgeDirection side) {
        FluidTankInfo[] result = new FluidTankInfo[16];
        for (int i = 0; i < result.length; i++) result[i] = new FluidTankInfo(fluids.fluids[i], Integer.MAX_VALUE);
        return result;
    }
    @Override public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        NBTTagCompound contents = new NBTTagCompound(); fluids.save(contents); tag.setTag("mbAssemblyOutput", contents);
    }
    @Override public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag); fluids.load(tag.getCompoundTag("mbAssemblyOutput"));
    }
    private void prepareDrop() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (dropPrepared || tile == null || !tile.isServerSide()) return;
        dropPrepared = true;
        getProvider().flushCachedStack();
        flushFluidCache();
        markDirty();
    }
    @Override public void onBlockDestroyed() {
        prepareDrop();
        super.onBlockDestroyed();
    }
    @Override public void setItemNBT(NBTTagCompound tag) {
        prepareDrop();
        super.setItemNBT(tag);
        net.minecraft.nbt.NBTTagList cached = new net.minecraft.nbt.NBTTagList();
        for (appeng.api.storage.data.IAEItemStack stack : getProvider().getCacheList()) cached.appendTag(saveStackToNBT(stack));
        tag.setTag("cache", cached);
        NBTTagCompound contents = new NBTTagCompound(); fluids.save(contents); tag.setTag("mbAssemblyOutput", contents);
    }
    @Override public com.cleanroommc.modularui.screen.ModularPanel buildUI(
        com.cleanroommc.modularui.factory.PosGuiData data,
        com.cleanroommc.modularui.value.sync.PanelSyncManager sync,
        com.cleanroommc.modularui.screen.UISettings settings) {
        return new gregtech.common.gui.modularui.hatch.MTEHatchOutputBusMEGui(this) {
            @Override protected int getBasePanelHeight() { return super.getBasePanelHeight() + 90; }
            @Override protected com.cleanroommc.modularui.widget.ParentWidget<?> createContentSection(
                com.cleanroommc.modularui.screen.ModularPanel panel,
                com.cleanroommc.modularui.value.sync.PanelSyncManager manager) {
                com.cleanroommc.modularui.widgets.layout.Flow content = com.cleanroommc.modularui.widgets.layout.Flow.col().coverChildren()
                    .child(super.createContentSection(panel, manager))
                    .child(com.cleanroommc.modularui.api.drawable.IKey.lang("matterblueprints.output.fluids").asWidget());
                for (int row = 0; row < 4; row++) {
                    com.cleanroommc.modularui.widgets.layout.Flow line = com.cleanroommc.modularui.widgets.layout.Flow.row().coverChildren();
                    for (int col = 0; col < 4; col++) {
                        final int slot = row * 4 + col;
                        net.minecraftforge.fluids.IFluidTank tank = new net.minecraftforge.fluids.IFluidTank() {
                            @Override public FluidStack getFluid() { return fluids.fluids[slot]; }
                            @Override public int getFluidAmount() { return getFluid() == null ? 0 : getFluid().amount; }
                            @Override public int getCapacity() { return Integer.MAX_VALUE; }
                            @Override public FluidTankInfo getInfo() { return new FluidTankInfo(getFluid(), getCapacity()); }
                            @Override public int fill(FluidStack fluid, boolean commit) { return 0; }
                            @Override public FluidStack drain(int amount, boolean commit) { return null; }
                        };
                        line.child(new com.cleanroommc.modularui.widgets.slot.FluidSlot().syncHandler(
                            new com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler(tank).canDrainSlot(false).canFillSlot(false)));
                    }
                    content.child(line);
                }
                return content;
            }
        }.build(data, sync, settings);
    }
    @Override public String[] getDescription() {
        return new String[] { net.minecraft.util.StatCollector.translateToLocal("matterblueprints.output.description"),
            net.minecraft.util.StatCollector.translateToLocal("matterblueprints.output.buffer") };
    }
}
