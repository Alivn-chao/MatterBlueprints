package com.gtnhblueprints.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
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
import java.util.List;
import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
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

import gregtech.api.casing.Casings;
import gregtech.api.enums.Textures;
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
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

public class MTEHostedMachineController extends MTEEnhancedMultiBlockBase<MTEHostedMachineController>
    implements ISurvivalConstructable, ICasingTextureProvider {

    private static final String STRUCTURE_PIECE = "main";
    private static final int OFFSET_X = 4;
    private static final int OFFSET_Y = 1;
    private static final int OFFSET_Z = 0;
    private static final int MINIMUM_CASINGS = 32;
    private static final String[][] STRUCTURE_SHAPE = transpose(HostedMachineStructure.LAYERS);
    private static IStructureDefinition<MTEHostedMachineController> structureDefinition;

    private final HostedMachineCoordinator coordinator = new HostedMachineCoordinator(this);
    private int casingCount;
    private int guiHostedProgress;
    private int guiHostedMaxProgress;

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
                    'C',
                    buildHatchAdder(MTEHostedMachineController.class)
                        .atLeast(
                            InputHatch.or(InputBus),
                            OutputHatch.or(OutputBus),
                            Maintenance,
                            Energy.or(ExoticEnergy)
                                .or(Dynamo)
                                .or(ExoticDynamo))
                        .casingIndex(Casings.ZPMMachineCasing.textureId)
                        .hint(1)
                        .buildAndChain(
                            onElementPass(
                                machine -> ++machine.casingCount,
                                ofBlock(ModBlocks.HOSTED_MACHINE_CASING, BlockHostedMachineCasing.CASING))))
                .addElement('P', ofBlock(ModBlocks.HOSTED_MACHINE_CASING, BlockHostedMachineCasing.CASING))
                .addElement('L', ofBlock(ModBlocks.HOSTED_MACHINE_CASING, BlockHostedMachineCasing.FLOW_LIGHT))
                .addElement('R', ofBlock(ModBlocks.HOSTED_MACHINE_CASING, BlockHostedMachineCasing.RECEIVER))
                .addElement('F', Casings.SuperplasticizerTreatedHighStrengthConcrete.asElement())
                .build();
        }
        return structureDefinition;
    }

    @Override
    public void checkMachine(IGregTechTileEntity tileEntity, ItemStack stack, List<StructureError> errors) {
        casingCount = 0;
        if (!checkPiece(STRUCTURE_PIECE, OFFSET_X, OFFSET_Y, OFFSET_Z, errors)) return;
        checkCasingMin(errors, casingCount, MINIMUM_CASINGS);
        checkHasAnyInput(errors);
        checkHasAnyOutput(errors);
        checkOneMaintenanceHatch(errors);
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
    public void onPostTick(IGregTechTileEntity tileEntity, long tick) {
        super.onPostTick(tileEntity, tick);
        if (!tileEntity.isServerSide()) return;
        if (getmStartUpCheck() >= 0) {
            synchronizeHostedDisplayState();
            return;
        }
        if (!mMachine) {
            coordinator.releaseAll();
            synchronizeHostedDisplayState();
            return;
        }
        if (tick % BlueprintConfig.hostedMachineScanIntervalTicks == 0) coordinator.refresh();
        coordinator.tick(tick, tileEntity.isAllowedToWork());
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
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        coordinator.readFromNBT(tag);
    }

    @Override
    public void onDisableWorking() {
        super.onDisableWorking();
    }

    @Override
    public void onRemoval() {
        coordinator.releaseAll();
        super.onRemoval();
    }

    @Override
    public void onUnload() {
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
            coordinator.getActiveParallels(),
            coordinator.getActiveEnergyUsage());
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
        String[] result = Arrays.copyOf(parent, parent.length + 6 + machineLines.size());
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
            coordinator.isGeneratorMode() ? "matterblueprints.host.info.central_generation"
                : "matterblueprints.host.info.central_power",
            coordinator.getEnergySpentThisTick(),
            coordinator.getCentralPowerCapacity());
        result[parent.length + 4] = StatCollector.translateToLocal("matterblueprints.host.info.status") + " "
            + StatCollector.translateToLocalFormatted(coordinator.getStatusKey(), coordinator.getHostedCount());
        result[parent.length + 5] = StatCollector.translateToLocal(
            coordinator.isGeneratorMode() ? "matterblueprints.host.info.generation" : "matterblueprints.host.info.power");
        for (int i = 0; i < machineLines.size(); i++) result[parent.length + 6 + i] = machineLines.get(i);
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
        tag.setBoolean("mbHostGeneratorMode", coordinator.isGeneratorMode());
        tag.setString("mbHostStatus", coordinator.getStatusKey());
        ItemStack selector = getControllerSlot();
        tag.setString("mbHostSelector", selector == null ? "" : selector.getDisplayName());
        NBTTagList machineLines = new NBTTagList();
        for (String line : coordinator.getMachineStatusLines(4)) machineLines.appendTag(new NBTTagString(line));
        tag.setTag("mbHostMachineLines", machineLines);
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
        String selector = tag.getString("mbHostSelector");
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
                tag.getBoolean("mbHostGeneratorMode") ? "matterblueprints.host.waila.generation"
                    : "matterblueprints.host.waila.power",
                tag.getLong("mbHostPowerUsed"),
                tag.getLong("mbHostPowerCapacity")));
        tooltip.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                "matterblueprints.host.waila.selector",
                selector.isEmpty() ? StatCollector.translateToLocal("matterblueprints.host.none") : selector));
        String statusKey = tag.getString("mbHostStatus");
        if (!statusKey.isEmpty()) {
            tooltip.add(
                EnumChatFormatting.YELLOW + StatCollector.translateToLocalFormatted(statusKey, count));
        }
        NBTTagList machineLines = tag.getTagList("mbHostMachineLines", 8);
        for (int i = 0; i < machineLines.tagCount(); i++) {
            tooltip.add(EnumChatFormatting.GRAY + machineLines.getStringTagAt(i));
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
            .addSeparator()
            .addInfo(tr("matterblueprints.host.tooltip.link_title"))
            .addInfo(tr("matterblueprints.host.tooltip.link_1"))
            .addInfo(tr("matterblueprints.host.tooltip.link_2"))
            .addInfo(tr("matterblueprints.host.tooltip.link_3"))
            .addInfo(tr("matterblueprints.host.tooltip.link_4"))
            .beginStructureBlock(9, 7, 6, true)
            .addController(tr("matterblueprints.host.tooltip.controller"))
            .addCasing(MINIMUM_CASINGS + "+", tr("matterblueprints.host.tooltip.casing"), false)
            .addOtherStructurePart(tr("matterblueprints.host.tooltip.floor"), tr("matterblueprints.host.tooltip.floor_position"))
            .addOtherStructurePart(tr("matterblueprints.host.tooltip.light"), tr("matterblueprints.host.tooltip.light_position"))
            .addOtherStructurePart(
                tr("matterblueprints.host.tooltip.receiver"),
                tr("matterblueprints.host.tooltip.receiver_position"))
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
            .addAir(tr("matterblueprints.host.tooltip.center"))
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
