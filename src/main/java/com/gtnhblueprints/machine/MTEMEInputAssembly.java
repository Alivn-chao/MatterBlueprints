package com.gtnhblueprints.machine;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.common.util.ForgeDirection;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.GridAccessException;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;

/** Two native virtual input views share one AE connection. No patterns and no physical input buffers. */
public class MTEMEInputAssembly extends MTEQuantizedMEInputBus {
    final FluidPort fluids = new FluidPort();
    final class FluidPort extends MTEQuantizedMEInputHatch {
        FluidPort() { super("matterblueprints.embedded_fluid_port", 8, new String[0], null); }
        @Override public AENetworkProxy getProxy() { return MTEMEInputAssembly.this.getProxy(); }
        @Override public boolean isAllowedToWork() { return MTEMEInputAssembly.this.isAllowedToWork(); }
        @Override public void markDirty() { MTEMEInputAssembly.this.markDirty(); }
        @Override public void updateInformationSlot(int index) throws GridAccessException {
            requestSource = MTEMEInputAssembly.this.getRequestSource();
            super.updateInformationSlot(index);
        }
        @Override public void startRecipeProcessing() {
            requestSource = MTEMEInputAssembly.this.getRequestSource();
            super.startRecipeProcessing();
        }
        @Override public void setMarkedAmount(int index, int amount) {
            super.setMarkedAmount(index, amount);
            MTEMEInputAssembly.this.notifyWatchers();
        }
        void prepareSource() { requestSource = MTEMEInputAssembly.this.getRequestSource(); }
        @Override public FluidTankInfo[] getTankInfo(ForgeDirection side) { prepareSource(); return super.getTankInfo(side); }
        void markFluid(int index, FluidStack fluid) {
            setSlotConfig(index, fluid);
            markDirty();
            MTEMEInputAssembly.this.notifyWatchers();
            IGregTechTileEntity tile = MTEMEInputAssembly.this.getBaseMetaTileEntity();
            if (tile != null && tile.isServerSide()) {
                try { updateInformationSlot(index); } catch (GridAccessException ignored) {}
            }
        }
        gregtech.common.tileentities.machines.MTEHatchInputME.Slot[] viewSlots() { return slots; }
    }
    public MTEMEInputAssembly(int id, String name, String regional) { super(id, name, regional); }
    private MTEMEInputAssembly(String name, int tier, String[] description, ITexture[][][] textures) { super(name, tier, description, textures); }
    @Override public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) { return new MTEMEInputAssembly(mName, mTier, mDescriptionArray, mTextures); }
    @Override public ITexture[] getTexturesActive(ITexture base) { return MEPortTextures.input(base, true, true); }
    @Override public boolean needsPeriodicChecks() { return true; }
    @Override public void startRecipeProcessing() { super.startRecipeProcessing(); fluids.startRecipeProcessing(); }
    @Override public CheckRecipeResult endRecipeProcessing(MTEMultiBlockBase controller) {
        CheckRecipeResult items = super.endRecipeProcessing(controller);
        CheckRecipeResult fluid = fluids.endRecipeProcessing(controller);
        return items.wasSuccessful() ? fluid : items;
    }
    public FluidStack[] getStoredFluids() { return fluids.getStoredFluids(); }
    public int depleteFluid(FluidStack request, boolean simulate) {
        fluids.prepareSource();
        FluidStack result = fluids.drain(ForgeDirection.UNKNOWN, request, !simulate);
        return result == null ? 0 : result.amount;
    }
    @Override public FluidTankInfo[] getTankInfo(ForgeDirection side) { return fluids.getTankInfo(side); }
    private void writeFluids(NBTTagCompound tag) {
        NBTTagCompound fluidTag = new NBTTagCompound(); fluids.writeMarkedConfiguration(fluidTag); tag.setTag("mbFluidMarks", fluidTag);
    }
    @Override public void saveNBTData(NBTTagCompound tag) { super.saveNBTData(tag); writeFluids(tag); }
    @Override public void loadNBTData(NBTTagCompound tag) { super.loadNBTData(tag); fluids.readMarkedConfiguration(tag.getCompoundTag("mbFluidMarks")); }
    @Override public void setItemNBT(NBTTagCompound tag) { super.setItemNBT(tag); writeFluids(tag); }
    @Override public String getCopiedDataIdentifier(net.minecraft.entity.player.EntityPlayer player) { return "matterblueprints.marked.assembly"; }
    @Override public NBTTagCompound getCopiedData(net.minecraft.entity.player.EntityPlayer player) {
        NBTTagCompound tag = super.getCopiedData(player); writeFluids(tag); return tag;
    }
    @Override public boolean pasteCopiedData(net.minecraft.entity.player.EntityPlayer player, NBTTagCompound tag) {
        if (!super.pasteCopiedData(player, tag)) return false;
        fluids.readMarkedConfiguration(tag.getCompoundTag("mbFluidMarks")); return true;
    }
    @Override protected int amountGUIExtraHeight() { return 112; }
    @Override protected void addFluidConfigurationButton(Flow content, ModularPanel panel, PanelSyncManager manager) {
        IPanelHandler handler = manager.syncedPanel("fluidMarks", true, (sync, ignored) -> {
            MEMarkingGui.syncFluids(fluids, sync);
            return ModularPanel.defaultPanel("matterblueprints.fluidMarks", 390, 128)
                .child(MEMarkingGui.fluids(fluids).left(6).top(15));
        });
        content.child(new ConfigureFluidButton(handler).overlay(IKey.lang("matterblueprints.stock.configure_fluids")).size(146, 18));
    }
    private static final class ConfigureFluidButton extends ButtonWidget<ConfigureFluidButton> {
        private final IPanelHandler handler;
        ConfigureFluidButton(IPanelHandler handler) { this.handler = handler; }
        @Override public Result onMousePressed(int button) {
            if (handler.isPanelOpen()) handler.closePanel(); else handler.openPanel();
            return Result.SUCCESS;
        }
    }
}
