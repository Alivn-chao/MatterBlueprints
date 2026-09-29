package com.gtnhblueprints.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.Dynamo;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.ExoticDynamo;
import static gregtech.api.enums.HatchElement.ExoticEnergy;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.HatchElement.OutputHatch;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_BOARD_PROCESSOR;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_BOARD_PROCESSOR_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_BOARD_PROCESSOR_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_BOARD_PROCESSOR_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.modularui.common.widget.DynamicPositionedColumn;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnhblueprints.BlueprintConfig;
import com.gtnhblueprints.block.BlockHostedMachineCasing;
import com.gtnhblueprints.registry.ModBlocks;
import com.gtnhblueprints.MatterBlueprints;

import gregtech.api.enums.Textures;
import gregtech.api.enums.MetaTileEntityIDs;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.misc.WirelessNetworkManager;
import gregtech.common.misc.spaceprojects.SpaceProjectManager;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.nbthandlers.MTEHatchCatalysts;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

public class MTEHostedMachineController extends MTEEnhancedMultiBlockBase<MTEHostedMachineController>
    implements ISurvivalConstructable, ICasingTextureProvider {

    private static final String STRUCTURE_PIECE = "main";
    private static final int OFFSET_X = HostedMachineStructure.CONTROLLER_X;
    private static final int OFFSET_Y = HostedMachineStructure.CONTROLLER_Y;
    private static final int OFFSET_Z = HostedMachineStructure.CONTROLLER_Z;
    private static final int MINIMUM_CASINGS = 32;
    private static final String[][] STRUCTURE_SHAPE = transpose(HostedMachineStructure.LAYERS);
    private static IStructureDefinition<MTEHostedMachineController> structureDefinition;

    private final HostedMachineCoordinator coordinator = new HostedMachineCoordinator(this);
    private final ArrayList<MTEHatchCatalysts> catalystHatches = new ArrayList<MTEHatchCatalysts>();
    private final ArrayList<gregtech.api.metatileentity.implementations.MTEHatchDataAccess> dataHatches = new ArrayList<>();
    private int casingCount;
    private int guiHostedProgress;
    private int guiHostedMaxProgress;
    // GT multiblocks reserve slot 0 but do not use it; slot 1 remains the machine selector.
    public static final int WIRELESS_UPGRADE_SLOT = 0;
    private boolean wirelessMode;
    private final WirelessEnergyBuffer wirelessBuffer = new WirelessEnergyBuffer();
    private static final WirelessEnergyBuffer.Ledger WIRELESS_LEDGER = WirelessNetworkManager::addEUToGlobalEnergyMap;

    public MTEHostedMachineController(int id, String name, String regionalName) {
        super(id, name, regionalName);
    }

    public MTEHostedMachineController(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new MTEHostedMachineController(mName);
    }

    @Override
    public IStructureDefinition<MTEHostedMachineController> getStructureDefinition() {
        if (structureDefinition == null) {
            structureDefinition = StructureDefinition.<MTEHostedMachineController>builder()
                .addShape(
                    STRUCTURE_PIECE,
                    STRUCTURE_SHAPE)
                .addElement(
                    'H',
                    ofChain(
                        buildHatchAdder(MTEHostedMachineController.class)
                            .hatchId(MetaTileEntityIDs.DATA_ACCESS_HATCH.ID)
                            .adder(MTEHostedMachineController::addDataHatch)
                            .casingIndex(ModBlocks.HOSTED_HATCH_CASING_TEXTURE_ID)
                            .hint(1)
                            .build(),
                        buildHatchAdder(MTEHostedMachineController.class)
                            .hatchId(MetaTileEntityIDs.Bus_Catalysts.ID)
                            .adder(MTEHostedMachineController::addCatalystHatch)
                            .casingIndex(ModBlocks.HOSTED_HATCH_CASING_TEXTURE_ID)
                            .hint(1)
                            .build(),
                        buildHatchAdder(MTEHostedMachineController.class)
                            .atLeast(
                                InputHatch.or(InputBus),
                                OutputHatch.or(OutputBus),
                                Maintenance,
                                Energy.or(ExoticEnergy)
                                    .or(Dynamo)
                                    .or(ExoticDynamo)
                                    .withCount((MTEHostedMachineController machine) -> machine.hasWirelessUpgrade() ? 1
                                        : machine.getExoticAndNormalEnergyHatchList().size()
                                            + machine.mDynamoHatches.size() + machine.getExoticDynamoHatches().size()))
                            .casingIndex(ModBlocks.HOSTED_HATCH_CASING_TEXTURE_ID)
                            .hint(1)
                            .buildAndChain(
                                onElementPass(
                                    machine -> ++machine.casingCount,
                                    ofBlock(ModBlocks.HOSTED_MACHINE_CASING, BlockHostedMachineCasing.CASING)))))
                .addElement('L', ofBlock(ModBlocks.HOSTED_MACHINE_CASING, BlockHostedMachineCasing.FLOW_LIGHT))
                .addElement('V', ofBlock(ModBlocks.HOSTED_MACHINE_CASING, BlockHostedMachineCasing.COOLING_FAN))
                .build();
        }
        return structureDefinition;
    }

    @Override
    public void checkMachine(IGregTechTileEntity tileEntity, ItemStack stack, List<StructureError> errors) {
        casingCount = 0;
        catalystHatches.clear();
        dataHatches.clear();
        if (!checkPiece(STRUCTURE_PIECE, OFFSET_X, OFFSET_Y, OFFSET_Z, errors)) return;
        checkCasingMin(errors, casingCount, MINIMUM_CASINGS);
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
        checkOneMaintenanceHatch(errors);
        if (!hasWirelessUpgrade() && getExoticAndNormalEnergyHatchList().isEmpty()
            && mDynamoHatches.isEmpty() && getExoticDynamoHatches().isEmpty()) {
            checkHasEnergyHatch(errors);
        }
    }

    public boolean hasWirelessUpgrade() {
        ItemStack stack = mInventory[WIRELESS_UPGRADE_SLOT];
        return isWirelessUpgrade(stack);
    }

    private static boolean isWirelessUpgrade(ItemStack stack) {
        return stack != null && stack.stackSize > 0
            && gregtech.api.enums.ItemList.EnergisedTesseract.get(1).isItemEqual(stack);
    }

    public boolean isWirelessMode() {
        return wirelessMode && hasWirelessUpgrade();
    }

    public boolean canConfigureWireless(EntityPlayer player) {
        return player != null && getBaseMetaTileEntity() != null
            && player.getUniqueID().equals(getBaseMetaTileEntity().getOwnerUuid());
    }

    public void setWirelessMode(boolean enabled, EntityPlayer player) {
        if (getBaseMetaTileEntity() == null) return;
        if (getBaseMetaTileEntity().isServerSide()) {
            if (!canConfigureWireless(player) || (enabled && !hasWirelessUpgrade())) return;
            if (!enabled && !refundWirelessEnergy()) return;
        }
        wirelessMode = enabled;
        markDirty();
    }

    public boolean canRemoveWirelessUpgrade() {
        return mMaxProgresstime <= 0 && wirelessBuffer.getStored() == 0;
    }

    @Override
    public boolean isValidSlot(int index) {
        return index == WIRELESS_UPGRADE_SLOT || super.isValidSlot(index);
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return index == WIRELESS_UPGRADE_SLOT
            ? isWirelessUpgrade(stack)
            : super.isItemValidForSlot(index, stack);
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity tile, int index, ForgeDirection side, ItemStack stack) {
        return index != WIRELESS_UPGRADE_SLOT && super.allowPutStack(tile, index, side, stack);
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity tile, int index, ForgeDirection side, ItemStack stack) {
        return index != WIRELESS_UPGRADE_SLOT && super.allowPullStack(tile, index, side, stack);
    }

    boolean consumeWirelessEnergy(long amount, int remainingTicks) {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null || !tile.isServerSide() || !isWirelessMode()) return false;
        UUID player = tile.getOwnerUuid();
        if (player == null || player.equals(new UUID(0, 0))) return false;
        UUID account = SpaceProjectManager.getLeader(player);
        boolean result = wirelessBuffer.consume(account, amount, remainingTicks, WIRELESS_LEDGER);
        markDirty();
        return result;
    }

    boolean canGenerateWirelessly() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return tile != null && tile.isServerSide() && isWirelessMode()
            && tile.getOwnerUuid() != null && !tile.getOwnerUuid().equals(new UUID(0, 0));
    }

    boolean generateWirelessEnergy(long amount) {
        if (!canGenerateWirelessly()) return false;
        UUID account = SpaceProjectManager.getLeader(getBaseMetaTileEntity().getOwnerUuid());
        return WirelessEnergyBuffer.deposit(account, amount, WIRELESS_LEDGER);
    }

    private boolean refundWirelessEnergy() {
        if (wirelessBuffer.getStored() == 0) return true;
        boolean refunded = wirelessBuffer.refund(WIRELESS_LEDGER);
        if (refunded) markDirty();
        return refunded;
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new HostedMachineGui(this);
    }

    private boolean addCatalystHatch(IGregTechTileEntity tile, int textureIndex) {
        if (tile == null || !(tile.getMetaTileEntity() instanceof MTEHatchCatalysts)) return false;
        MTEHatchCatalysts hatch = (MTEHatchCatalysts) tile.getMetaTileEntity();
        hatch.updateTexture(textureIndex);
        if (!catalystHatches.contains(hatch)) catalystHatches.add(hatch);
        return true;
    }

    private boolean addDataHatch(IGregTechTileEntity tile, int textureIndex) {
        if (tile == null || !(tile.getMetaTileEntity() instanceof gregtech.api.metatileentity.implementations.MTEHatchDataAccess)) return false;
        gregtech.api.metatileentity.implementations.MTEHatchDataAccess hatch =
            (gregtech.api.metatileentity.implementations.MTEHatchDataAccess) tile.getMetaTileEntity();
        hatch.updateTexture(textureIndex);
        if (!dataHatches.contains(hatch)) dataHatches.add(hatch);
        addIfSmartInput(hatch);
        return true;
    }

    List<gregtech.api.util.GTRecipe.RecipeAssemblyLine> getHostedAssemblyRecipes() {
        java.util.Set<gregtech.api.util.GTRecipe.RecipeAssemblyLine> unique =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<gregtech.api.util.GTRecipe.RecipeAssemblyLine> result = new ArrayList<>();
        for (gregtech.api.metatileentity.implementations.MTEHatchDataAccess hatch : dataHatches) {
            if (!hatch.isValid()) continue;
            for (gregtech.api.util.GTRecipe.RecipeAssemblyLine recipe : hatch.getAssemblyLineRecipes()) {
                if (unique.add(recipe)) result.add(recipe);
            }
        }
        return result;
    }

    List<ItemStack> getHostedCatalysts() {
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (MTEHatchCatalysts hatch : catalystHatches) {
            if (hatch == null || !hatch.isValid()) continue;
            hatch.tryFillUsageSlots();
            result.addAll(hatch.getContentUsageSlots());
        }
        return result;
    }

    @Override
    public void construct(ItemStack stack, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE, stack, hintsOnly, OFFSET_X, OFFSET_Y, OFFSET_Z);
    }

    @Override
    public int survivalConstruct(ItemStack stack, int elementBudget, ISurvivalBuildEnvironment environment) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE,
            stack,
            OFFSET_X,
            OFFSET_Y,
            OFFSET_Z,
            elementBudget,
            environment,
            false,
            true);
    }

    @Override
    public ItemStack getMachineCraftingIcon() {
        ItemStack selector = getControllerSlot();
        if (selector != null) {
            int id = selector.getItemDamage();
            if (id >= 0 && id < gregtech.api.GregTechAPI.METATILEENTITIES.length) {
                IMetaTileEntity machine = gregtech.api.GregTechAPI.METATILEENTITIES[id];
                if (machine instanceof MTEMultiBlockBase && selector.isItemEqual(machine.getStackForm(1L))) {
                    ItemStack icon = selector.copy();
                    icon.stackSize = 1;
                    return icon;
                }
            }
        }
        return super.getMachineCraftingIcon();
    }

    private void refreshPatternTerminalIcons() {
        ItemStack icon = getMachineCraftingIcon();
        // Native structure checks set this icon once. Refresh it when the selector changes without rebuilding.
        for (gregtech.common.tileentities.machines.IDualInputHatch hatch : mDualInputHatches) {
            if (hatch != null) hatch.updateCraftingIcon(icon);
        }
        for (gregtech.api.metatileentity.implementations.MTEHatchInputBus bus : mInputBusses) {
            if (bus != null) bus.updateCraftingIcon(icon);
        }
        for (gregtech.api.metatileentity.implementations.MTEHatchInput hatch : mInputHatches) {
            if (hatch != null) hatch.updateCraftingIcon(icon);
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity tileEntity, long tick) {
        super.onPostTick(tileEntity, tick);
        if (!tileEntity.isServerSide()) return;
        if (!hasWirelessUpgrade()) wirelessMode = false;
        if (!isWirelessMode()) refundWirelessEnergy();
        if (getmStartUpCheck() >= 0) {
            synchronizeHostedDisplayState();
            return;
        }
        if (!mMachine) {
            refundWirelessEnergy();
            coordinator.releaseAll();
            synchronizeHostedDisplayState();
            return;
        }
        if (tick % 20L == 0L) refreshPatternTerminalIcons();
        if (tick % BlueprintConfig.hostedMachineScanIntervalTicks == 0) coordinator.refresh();
        coordinator.tick(tick, tileEntity.isAllowedToWork());
        if (coordinator.getRunningCount() == 0 || coordinator.isGeneratorMode()) refundWirelessEnergy();
        synchronizeHostedDisplayState();
        tileEntity.setActive(coordinator.getRunningCount() > 0);
    }

    /**
     * The host owns no GT recipe of its own. Its progress fields are display mirrors for the one aggregate hosted job,
     * so the normal GT GUI can synchronize progress and output-rate widgets without running a second recipe loop.
     */
    @Override
    protected void runMachine(IGregTechTileEntity tileEntity, long tick) {}

    @Override
    protected void drawTexts(DynamicPositionedColumn screenElements, SlotWidget controllerSlot) {
        super.drawTexts(screenElements, controllerSlot);
        screenElements.widget(
            new FakeSyncWidget.IntegerSyncer(
                () -> guiHostedProgress,
                value -> {
                    guiHostedProgress = value;
                    mProgresstime = value;
                }));
        screenElements.widget(
            new FakeSyncWidget.IntegerSyncer(
                () -> guiHostedMaxProgress,
                value -> {
                    guiHostedMaxProgress = value;
                    mMaxProgresstime = value;
                }));
    }

    private void synchronizeHostedDisplayState() {
        guiHostedProgress = coordinator.getActiveProgress();
        guiHostedMaxProgress = coordinator.getActiveMaxProgress();
        mProgresstime = guiHostedProgress;
        mMaxProgresstime = guiHostedMaxProgress;
        mOutputItems = coordinator.getActiveOutputItems();
        mOutputFluids = coordinator.getActiveOutputFluids();
        if (mMaxProgresstime > 0) {
            mEfficiency = coordinator.getActiveEfficiency();
            setCheckRecipeResult(CheckRecipeResultRegistry.SUCCESSFUL);
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        coordinator.writeToNBT(tag);
        tag.setBoolean("mbHostWireless", wirelessMode);
        tag.setLong("mbHostWirelessEU", wirelessBuffer.getStored());
        if (wirelessBuffer.getAccount() != null) tag.setString("mbHostWirelessAccount", wirelessBuffer.getAccount().toString());
        else tag.removeTag("mbHostWirelessAccount");
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        coordinator.readFromNBT(tag);
        wirelessMode = tag.getBoolean("mbHostWireless");
        String account = tag.getString("mbHostWirelessAccount");
        try {
            wirelessBuffer.restore(account.isEmpty() ? null : UUID.fromString(account), tag.getLong("mbHostWirelessEU"));
        } catch (IllegalArgumentException error) {
            wirelessMode = false;
            MatterBlueprints.LOG.error("Invalid hosted wireless buffer data; wireless mode disabled", error);
        }
    }

    @Override
    public void onDisableWorking() {
        super.onDisableWorking();
    }

    @Override
    public void onRemoval() {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isServerSide()) refundWirelessEnergy();
        coordinator.releaseAll();
        super.onRemoval();
    }

    @Override
    public void onUnload() {
        // Keep prepaid EU in NBT. Refunding after a chunk save would duplicate it on the next load.
        coordinator.releaseAll();
        super.onUnload();
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        return CheckRecipeResultRegistry.NO_RECIPE;
    }

    @Override
    public boolean supportsBatchMode() {
        return true;
    }

    @Override
    public String generateCurrentRecipeInfoString() {
        // MUI2 renders this on the client; its standard sync handlers update the base progress fields.
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isClientSide()) {
            if (mMaxProgresstime <= 0) return "";
            return StatCollector.translateToLocalFormatted(
                "matterblueprints.host.gui.progress",
                formatSeconds(mProgresstime),
                formatSeconds(mMaxProgresstime),
                String.format(Locale.ROOT, "%.1f", Math.min(100.0D, mProgresstime * 100.0D / mMaxProgresstime)));
        }
        int maximum = coordinator.getActiveMaxProgress();
        if (maximum <= 0) return "";
        int progress = coordinator.getActiveProgress();
        double progressSeconds = progress / 20.0D;
        double maximumSeconds = maximum / 20.0D;
        double percent = Math.min(100.0D, progress * 100.0D / maximum);
        return StatCollector.translateToLocalFormatted(
            coordinator.isGeneratorMode() ? "matterblueprints.host.gui.generator_recipe"
                : "matterblueprints.host.gui.recipe",
            String.format(Locale.ROOT, "%.2f", progressSeconds),
            String.format(Locale.ROOT, "%.2f", maximumSeconds),
            String.format(Locale.ROOT, "%.1f", percent),
            coordinator.getHostedCount(),
            HostedNumberFormatter.format(coordinator.getActiveParallels()),
            HostedNumberFormatter.format(coordinator.getActiveEnergyUsage()));
    }

    /**
     * GT's black machine-status panel obtains its progress line from this method rather than from
     * {@link #generateCurrentRecipeInfoString()}. The host mirrors its aggregate job into the normal progress fields,
     * so keeping this override explicit also avoids version-specific differences in the GT base implementation.
     */
    @Override
    protected String generateCurrentProgress() {
        // The coordinator is server-only. These dedicated values are synchronized explicitly in drawTexts().
        int maximum = guiHostedMaxProgress;
        if (maximum <= 0) return "";
        int progress = guiHostedProgress;
        double percent = Math.min(100.0D, progress * 100.0D / maximum);
        return StatCollector.translateToLocalFormatted(
            "matterblueprints.host.gui.progress",
            formatSeconds(progress),
            formatSeconds(maximum),
            String.format(Locale.ROOT, "%.1f", percent));
    }

    public BindingResult toggleHostedBinding(MTEMultiBlockBase machine) {
        boolean bound = coordinator.toggleBinding(machine);
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile instanceof TileEntity) ((TileEntity) tile).markDirty();
        return new BindingResult(
            bound ? "matterblueprints.binder.machine_added" : "matterblueprints.binder.machine_removed",
            coordinator.getBindingCount());
    }

    public static final class BindingResult {

        public final String translationKey;
        public final int boundCount;

        private BindingResult(String translationKey, int boundCount) {
            this.translationKey = translationKey;
            this.boundCount = boundCount;
        }
    }

    void releaseHostedMachine(gregtech.api.metatileentity.implementations.MTEMultiBlockBase machine) {
        coordinator.releaseRemote(machine);
    }

    @Override
    public String[] getInfoData() {
        String[] parent = super.getInfoData();
        List<String> machineLines = coordinator.getMachineStatusLines(6);
        List<String> progressionLines = coordinator.getProgressionStatusLines();
        String[] result = Arrays.copyOf(parent, parent.length + 6 + machineLines.size() + progressionLines.size());
        result[parent.length] = StatCollector.translateToLocalFormatted(
            "matterblueprints.host.info.count",
            coordinator.getHostedCount());
        result[parent.length + 1] = StatCollector.translateToLocalFormatted(
            "matterblueprints.host.info.running",
            coordinator.getRunningCount(),
            coordinator.getHostedCount());
        result[parent.length + 2] = StatCollector.translateToLocalFormatted(
            "matterblueprints.host.info.progress",
            coordinator.getAggregateProgressPercent());
        result[parent.length + 3] = StatCollector.translateToLocalFormatted(
            coordinator.isGeneratorMode() ? (isWirelessMode() ? "matterblueprints.wireless.generation_power" : "matterblueprints.host.info.central_generation")
                : isWirelessMode() ? "matterblueprints.wireless.power"
                : "matterblueprints.host.info.central_power",
            HostedNumberFormatter.format(coordinator.getEnergySpentThisTick()),
            HostedNumberFormatter.format(coordinator.getCentralPowerCapacity()));
        result[parent.length + 4] = StatCollector.translateToLocal("matterblueprints.host.info.status") + " "
            + StatCollector.translateToLocalFormatted(coordinator.getStatusKey(), coordinator.getHostedCount());
        result[parent.length + 5] = StatCollector.translateToLocal(
            coordinator.isGeneratorMode() ? (isWirelessMode() ? "matterblueprints.wireless.generating" : "matterblueprints.host.info.generation")
                : isWirelessMode() ? "matterblueprints.wireless.owner_only" : "matterblueprints.host.info.power");
        for (int i = 0; i < machineLines.size(); i++) result[parent.length + 6 + i] = machineLines.get(i);
        int progressionStart = parent.length + 6 + machineLines.size();
        for (int i = 0; i < progressionLines.size(); i++) result[progressionStart + i] = progressionLines.get(i);
        return result;
    }

    @Override
    public void getWailaNBTData(
        net.minecraft.entity.player.EntityPlayerMP player,
        TileEntity tile,
        NBTTagCompound tag,
        World world,
        int x,
        int y,
        int z
    ) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        tag.setInteger("mbHostCount", coordinator.getHostedCount());
        tag.setInteger("mbHostRunning", coordinator.getRunningCount());
        tag.setInteger("mbHostProgress", coordinator.getAggregateProgressPercent());
        tag.setInteger("mbHostProgressTicks", coordinator.getActiveProgress());
        tag.setInteger("mbHostMaxProgressTicks", coordinator.getActiveMaxProgress());
        tag.setLong("mbHostPowerUsed", coordinator.getEnergySpentThisTick());
        tag.setLong("mbHostPowerCapacity", coordinator.getCentralPowerCapacity());
        tag.setLong("mbHostVoltage", coordinator.getHudVoltage());
        tag.setBoolean("mbHostGeneratorMode", coordinator.isGeneratorMode());
        tag.setBoolean("mbHostWirelessMode", isWirelessMode());
        tag.setLong("mbHostWirelessBuffer", wirelessBuffer.getStored());
        tag.setString("mbHostStatus", coordinator.getStatusKey());
        ItemStack selector = getControllerSlot();
        if (selector != null) tag.setTag("mbHostSelectorItem", selector.writeToNBT(new NBTTagCompound()));
        tag.setTag("mbHostMachineLines", HostedHudLine.write(coordinator.getMachineHudLines(4)));
        tag.setTag("mbHostProgressionLines", HostedHudLine.write(coordinator.getProgressionHudLines()));
    }

    @Override
    public void getWailaBody(
        ItemStack stack,
        List<String> tooltip,
        IWailaDataAccessor accessor,
        IWailaConfigHandler config
    ) {
        super.getWailaBody(stack, tooltip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        int count = tag.getInteger("mbHostCount");
        ItemStack selectorItem = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("mbHostSelectorItem"));
        String selector = selectorItem == null ? "" : selectorItem.getDisplayName();
        tooltip.add(
            EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted("matterblueprints.host.waila.count", count));
        int progressTicks = tag.getInteger("mbHostProgressTicks");
        int maximumTicks = tag.getInteger("mbHostMaxProgressTicks");
        if (maximumTicks > 0) {
            tooltip.add(
                EnumChatFormatting.GREEN + StatCollector.translateToLocalFormatted(
                    "matterblueprints.host.waila.running",
                    count,
                    formatSeconds(progressTicks),
                    formatSeconds(maximumTicks),
                    tag.getInteger("mbHostProgress")));
        } else {
            tooltip.add(
                EnumChatFormatting.GRAY
                    + StatCollector.translateToLocalFormatted("matterblueprints.host.waila.idle", count));
        }
        tooltip.add(
            EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                tag.getBoolean("mbHostGeneratorMode") ? (tag.getBoolean("mbHostWirelessMode")
                    ? "matterblueprints.wireless.generation_power" : "matterblueprints.host.waila.generation")
                    : tag.getBoolean("mbHostWirelessMode") ? "matterblueprints.wireless.power"
                    : "matterblueprints.host.waila.power",
                HostedNumberFormatter.format(tag.getLong("mbHostPowerUsed")),
                HostedNumberFormatter.format(tag.getLong("mbHostPowerCapacity"))));
        tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
            "matterblueprints.host.hud.max_power", HostedNumberFormatter.amperage(tag.getLong("mbHostPowerUsed"),
                gregtech.api.enums.GTValues.V[gregtech.api.enums.VoltageIndex.MAX])));
        long voltage = tag.getLong("mbHostVoltage");
        if (voltage > 0) {
            int tier = Math.min(gregtech.api.enums.GTValues.VN.length - 1, gregtech.api.util.GTUtility.getTier(voltage));
            boolean unlimited = tag.getBoolean("mbHostWirelessMode") && !tag.getBoolean("mbHostGeneratorMode");
            tooltip.add(EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
                "matterblueprints.host.hud.amps", HostedNumberFormatter.amperage(tag.getLong("mbHostPowerUsed"), voltage),
                unlimited ? "∞" : HostedNumberFormatter.amperage(tag.getLong("mbHostPowerCapacity"), voltage),
                gregtech.api.enums.GTValues.VN[tier], HostedNumberFormatter.format(voltage)));
            if (tag.getBoolean("mbHostWirelessMode")) tooltip.add(EnumChatFormatting.GRAY
                + StatCollector.translateToLocal("matterblueprints.host.hud."
                    + (unlimited ? "wireless_limit" : "wireless_generation_limit")));
        }
        if (tag.getBoolean("mbHostWirelessMode") && !tag.getBoolean("mbHostGeneratorMode")) {
            tooltip.add(StatCollector.translateToLocalFormatted(
                "matterblueprints.wireless.buffer",
                HostedNumberFormatter.format(tag.getLong("mbHostWirelessBuffer"))));
        }
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                "matterblueprints.host.waila.selector",
                selector.isEmpty() ? StatCollector.translateToLocal("matterblueprints.host.none") : selector));
        String statusKey = tag.getString("mbHostStatus");
        if (!statusKey.isEmpty()) {
            tooltip.add(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocalFormatted(statusKey, count));
        }
        NBTTagList machineLines = tag.getTagList("mbHostMachineLines", 10);
        for (int i = 0; i < machineLines.tagCount(); i++) {
            tooltip.add(EnumChatFormatting.GRAY + HostedHudLine.read(machineLines.getCompoundTagAt(i)).render());
        }
        NBTTagList progressionLines = tag.getTagList("mbHostProgressionLines", 10);
        for (int i = 0; i < progressionLines.tagCount(); i++) {
            tooltip.add(EnumChatFormatting.LIGHT_PURPLE + HostedHudLine.read(progressionLines.getCompoundTagAt(i)).render());
        }
        // GT adds aggregate output quantities before our own lines. Compact the complete tooltip so inherited
        // production amounts use the same notation as host power and progression statistics.
        for (int i = 0; i < tooltip.size(); i++) {
            tooltip.set(i, HostedNumberFormatter.compactLargeIntegers(tooltip.get(i)));
        }
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.2f", ticks / 20.0D);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType(tr("matterblueprints.host.tooltip.type"))
            .addInfo(tr("matterblueprints.host.tooltip.summary"))
            .addInfo(tr("matterblueprints.host.tooltip.remote_physical"))
            .addInfo(tr("matterblueprints.host.tooltip.selector"))
            .addInfo(tr("matterblueprints.host.tooltip.mechanics"))
            .addInfo(tr("matterblueprints.host.tooltip.batch"))
            .addInfo(tr("matterblueprints.host.tooltip.generator"))
            .addInfo(tr("matterblueprints.host.tooltip.chemical_catalyst"))
            .addInfo(tr("matterblueprints.host.tooltip.chemical_tiers"))
            .addInfo(tr("matterblueprints.host.tooltip.progression"))
            .addInfo(tr("matterblueprints.wireless.upgrade_desc"))
            .addSeparator()
            .addInfo(tr("matterblueprints.host.tooltip.link_title"))
            .addInfo(tr("matterblueprints.host.tooltip.link_1"))
            .addInfo(tr("matterblueprints.host.tooltip.link_2"))
            .addInfo(tr("matterblueprints.host.tooltip.link_3"))
            .addInfo(tr("matterblueprints.host.tooltip.link_4"))
            .beginStructureBlock(
                HostedMachineStructure.WIDTH,
                HostedMachineStructure.DEPTH,
                HostedMachineStructure.HEIGHT,
                true)
            .addController(tr("matterblueprints.host.tooltip.controller"))
            .addCasing(MINIMUM_CASINGS + "+", tr("matterblueprints.host.tooltip.casing"), false)
            .addOtherStructurePart(tr("matterblueprints.host.tooltip.light"), tr("matterblueprints.host.tooltip.light_position"))
            .addOtherStructurePart(
                tr("matterblueprints.host.tooltip.fan"),
                tr("matterblueprints.host.tooltip.fan_position"))
            .addMaintenanceHatch(
                tr("matterblueprints.host.tooltip.maintenance_count"),
                tr("matterblueprints.host.tooltip.any_casing"),
                1)
            .addEnergyHatch(tr("matterblueprints.host.tooltip.energy"), tr("matterblueprints.host.tooltip.any_casing"), 1)
            .addDynamoHatch(
                tr("matterblueprints.host.tooltip.dynamo"),
                tr("matterblueprints.host.tooltip.any_casing"),
                1)
            .addTecTechHatchInfo()
            .addInputAny("1+", tr("matterblueprints.host.tooltip.any_casing"), 1)
            .addOutputAny("1+", tr("matterblueprints.host.tooltip.any_casing"), 1)
            .addMiscHatch(
                "0+",
                tr("matterblueprints.host.tooltip.catalyst_hatch"),
                tr("matterblueprints.host.tooltip.any_casing"),
                1)
            .addAir(tr("matterblueprints.host.tooltip.center"))
            .addMiscHatch("0+", tr("matterblueprints.host.tooltip.data_hatch"),
                tr("matterblueprints.host.tooltip.any_casing"), 1)
            .toolTipFinisher();
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    @Override
    public ITexture[] getTexture(
        IGregTechTileEntity tileEntity,
        ForgeDirection side,
        ForgeDirection facing,
        int colorIndex,
        boolean active,
        boolean redstoneLevel
    ) {
        return Textures.BlockIcons.createTextureWithCasing(
            this,
            side,
            facing,
            active,
            OVERLAY_FRONT_BOARD_PROCESSOR,
            OVERLAY_FRONT_BOARD_PROCESSOR_GLOW,
            OVERLAY_FRONT_BOARD_PROCESSOR_ACTIVE,
            OVERLAY_FRONT_BOARD_PROCESSOR_ACTIVE_GLOW);
    }

    @Override
    public ITexture getCasingTexture() {
        return TextureFactory.of(ModBlocks.HOSTED_MACHINE_CASING, BlockHostedMachineCasing.CASING);
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    public boolean supportsInputSeparation() {
        return true;
    }
}
