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
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import gregtech.common.gui.modularui.hatch.MTEHatchInputBusMEGui;

/** Native ME inventory and recipe settlement, with a per-mark maximum visible amount. */
public class MTEQuantizedMEInputBus extends MTEHatchInputBusME {
    final MEMarkedAmounts markedAmounts = new MEMarkedAmounts();
    public MTEQuantizedMEInputBus(int id, String name, String regional) { super(id, true, name, regional); }
    protected MTEQuantizedMEInputBus(String name, int tier, String[] description, ITexture[][][] textures) {
        super(name, true, tier, description, textures);
    }
    @Override public MetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTEQuantizedMEInputBus(mName, mTier, mDescriptionArray, mTextures);
    }
    @Override public ITexture[] getTexturesActive(ITexture base) { return MEPortTextures.input(base, true, false); }
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
    @Override protected void refreshItemList() {
        super.refreshItemList();
        for (int i = 0; i < SLOT_COUNT; i++) markedAmounts.apply(i, slots[i]);
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
    @Override public void setItemNBT(NBTTagCompound tag) {
        super.setItemNBT(tag);
        writeMarkedConfiguration(tag);
    }
    @Override public String getCopiedDataIdentifier(net.minecraft.entity.player.EntityPlayer player) { return "matterblueprints.marked.items"; }
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
    protected void addFluidConfigurationButton(Flow content, ModularPanel panel, PanelSyncManager manager) {}
    protected int amountGUIExtraHeight() { return 92; }
    @Override public ModularPanel buildUI(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return new MTEHatchInputBusMEGui(this, slots) {
            @Override protected int getBasePanelHeight() { return super.getBasePanelHeight() + amountGUIExtraHeight(); }
            @Override protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager manager) {
                Flow content = Flow.col().coverChildren()
                    .child(super.createContentSection(panel, manager))
                    .child(MEMarkingGui.amounts(MTEQuantizedMEInputBus.this::getMarkedAmount, MTEQuantizedMEInputBus.this::setMarkedAmount));
                addFluidConfigurationButton(content, panel, manager);

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
