package com.gtnhblueprints.machine;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import appeng.me.GridAccessException;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.tileentities.machines.MTEHatchInputME;
import gregtech.common.gui.modularui.hatch.MTEHatchInputMEGui;

/** Native ME inventory and recipe settlement, with a per-mark maximum visible amount. */
public class MTEQuantizedMEInputHatch extends MTEHatchInputME {
    final MEMarkedAmounts markedAmounts = new MEMarkedAmounts();
    public MTEQuantizedMEInputHatch(int id, String name, String regional) { super(id, true, name, regional); }
    protected MTEQuantizedMEInputHatch(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, true, tier, description, textures);
    }
    @Override public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEQuantizedMEInputHatch(mName, mTier, mDescriptionArray, mTextures);
    }
    @Override public ITexture[] getTexturesActive(ITexture base) { return MEPortTextures.input(base, false, true); }
    @Override public ITexture[] getTexturesInactive(ITexture base) { return getTexturesActive(base); }
    public int getMarkedAmount(int index) { return markedAmounts.get(index); }
    public void setMarkedAmount(int index, int amount) {
        markedAmounts.set(index, amount);
        markDirty();
        notifyWatchers();
    }
    @Override public void updateInformationSlot(int index) throws GridAccessException {
        super.updateInformationSlot(index);
        if (index >= 0 && index < SLOT_COUNT) markedAmounts.apply(index, slots[index]);
    }
    @Override public void saveNBTData(NBTTagCompound tag) { super.saveNBTData(tag); markedAmounts.save(tag); }
    @Override public void loadNBTData(NBTTagCompound tag) { super.loadNBTData(tag); markedAmounts.load(tag); }
    void writeMarkedConfiguration(NBTTagCompound tag) {
        markedAmounts.save(tag);
        tag.setInteger("version", 1);
        NBTTagList configured = new NBTTagList();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) continue;
            NBTTagCompound slot = new NBTTagCompound();
            slot.setTag("config", slots[i].config.writeToNBT(new NBTTagCompound()));
            slot.setInteger("index", i);
            configured.appendTag(slot);
        }
        tag.setTag("slots", configured);
    }
    void readMarkedConfiguration(NBTTagCompound tag) {
        markedAmounts.load(tag);
        java.util.Arrays.fill(slots, null);
        NBTTagList configured = tag.getTagList("slots", 10);
        for (int i = 0; i < configured.tagCount(); i++) {
            NBTTagCompound saved = configured.getCompoundTagAt(i);
            int index = saved.getInteger("index");
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(saved.getCompoundTag("config"));
            if (index >= 0 && index < SLOT_COUNT && fluid != null) setSlotConfig(index, fluid);
        }
    }
    @Override public void onPostTick(IGregTechTileEntity tile, long tick) {
        super.onPostTick(tile, tick);
        if (!processingRecipe) for (int i = 0; i < SLOT_COUNT; i++) markedAmounts.apply(i, slots[i]);
    }
    @Override public FluidStack drain(net.minecraftforge.common.util.ForgeDirection side, FluidStack fluid, int amount, boolean commit) {
        for (int i = 0; i < slots.length; i++) {
            if (fluid != null && slots[i] != null && slots[i].config.isFluidEqual(fluid)) {
                int limited = Math.min(amount, getMarkedAmount(i));
                return amount > 0 && limited == 0 ? null : super.drain(side, fluid, limited, commit);
            }
        }
        return null;
    }
    @Override public void setItemNBT(NBTTagCompound tag) {
        super.setItemNBT(tag);
        writeMarkedConfiguration(tag);
    }
    @Override public String getCopiedDataIdentifier(net.minecraft.entity.player.EntityPlayer player) { return "matterblueprints.marked.fluids"; }
    @Override public NBTTagCompound getCopiedData(net.minecraft.entity.player.EntityPlayer player) {
        NBTTagCompound tag = super.getCopiedData(player);
        tag.setString("type", getCopiedDataIdentifier(player));
        markedAmounts.save(tag);
        return tag;
    }
    @Override public boolean pasteCopiedData(net.minecraft.entity.player.EntityPlayer player, NBTTagCompound tag) {
        if (tag == null || !getCopiedDataIdentifier(player).equals(tag.getString("type"))) return false;
        NBTTagCompound nativeTag = (NBTTagCompound) tag.copy();
        nativeTag.setString("type", COPIED_DATA_IDENTIFIER);
        if (!super.pasteCopiedData(player, nativeTag)) return false;
        markedAmounts.load(tag);
        markDirty();
        notifyWatchers();
        return true;
    }
    @Override public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return new MTEHatchInputMEGui(this, slots) {
            @Override protected int getBasePanelHeight() { return super.getBasePanelHeight() + 92; }
            @Override protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager manager) {
                Flow content = Flow.col().coverChildren()
                    .child(super.createContentSection(panel, manager))
                    .child(MEMarkingGui.amounts(MTEQuantizedMEInputHatch.this::getMarkedAmount, MTEQuantizedMEInputHatch.this::setMarkedAmount));

                return content;
            }
        }.build(data, sync, settings);
    }
    @Override public String[] getDescription() {
        return new String[] { net.minecraft.util.StatCollector.translateToLocal("matterblueprints.stock.description"),
            net.minecraft.util.StatCollector.translateToLocal("matterblueprints.stock.actual"),
            net.minecraft.util.StatCollector.translateToLocal("matterblueprints.stock.marking") };
    }
}
