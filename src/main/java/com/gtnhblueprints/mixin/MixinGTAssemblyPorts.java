package com.gtnhblueprints.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.gtnhblueprints.machine.MTEMEInputAssembly;
import com.gtnhblueprints.machine.MTEMEOutputAssembly;
import com.gtnhblueprints.machine.MTELongStockInputBus;
import com.gtnhblueprints.machine.MTELongStockInputHatch;
import gregtech.api.interfaces.IOutputHatch;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;

/** Present manual combined ports to the ordinary GT IO path, including controllers with custom recipes. */
@Mixin(value = MTEMultiBlockBase.class, remap = false)
public abstract class MixinGTAssemblyPorts {
    @Shadow public ArrayList<MTEHatchInputBus> mInputBusses;
    @Shadow public ArrayList<MTEHatchInput> mInputHatches;
    @Shadow public ArrayList<MTEHatchOutputBus> mOutputBusses;
    @Shadow public abstract boolean addInputBusToMachineList(IGregTechTileEntity tile, int texture);
    @Shadow public abstract boolean addOutputBusToMachineList(IGregTechTileEntity tile, int texture);

    @Inject(method = "addInputHatchToMachineList", at = @At("HEAD"), cancellable = true, require = 1)
    private void matterblueprints$acceptFluidInputPosition(IGregTechTileEntity tile, int texture, CallbackInfoReturnable<Boolean> result) {
        if (tile != null && tile.getMetaTileEntity() instanceof MTEMEInputAssembly)
            result.setReturnValue(addInputBusToMachineList(tile, texture));
    }
    @Inject(method = "addOutputHatchToMachineList", at = @At("HEAD"), cancellable = true, require = 1)
    private void matterblueprints$acceptFluidOutputPosition(IGregTechTileEntity tile, int texture, CallbackInfoReturnable<Boolean> result) {
        if (tile != null && tile.getMetaTileEntity() instanceof MTEMEOutputAssembly)
            result.setReturnValue(addOutputBusToMachineList(tile, texture));
    }
    @Inject(method = "getStoredFluidsForColor", at = @At("RETURN"), require = 1)
    private void matterblueprints$manualFluids(Optional<Byte> color, CallbackInfoReturnable<ArrayList<FluidStack>> result) {
        ArrayList<FluidStack> output = result.getReturnValue();
        matterblueprints$mergeAssemblyFluids(color, output);
        for (MTEHatchInput hatch : mInputHatches) {
            if (hatch instanceof MTELongStockInputHatch) ((MTELongStockInputHatch) hatch).appendLongInputs(output);
        }
    }

    private void matterblueprints$mergeAssemblyFluids(Optional<Byte> color, List<FluidStack> output) {
        boolean hasAssembly = mInputBusses.stream().anyMatch(bus -> bus instanceof MTEMEInputAssembly && bus.isValid());
        if (!hasAssembly) return;
        java.util.Map<net.minecraftforge.fluids.Fluid, FluidStack> views = new java.util.HashMap<>();
        for (MTEHatchInput hatch : mInputHatches) {
            if (!(hatch instanceof gregtech.common.tileentities.machines.MTEHatchInputME) || !hatch.isValid()) continue;
            if (color.isPresent() && hatch.getColor() != -1 && hatch.getColor() != color.get()) continue;
            for (FluidStack fluid : ((gregtech.common.tileentities.machines.MTEHatchInputME) hatch).getStoredFluids())
                if (fluid != null) views.put(fluid.getFluid(), fluid);
        }
        // Beta-3 appends the deduplicated native ME map at the end of this list.
        // Replace that tail, retaining physical fluid inputs and avoiding duplicate virtual AE stock.
        int nativeViews = views.size();
        output.subList(output.size() - nativeViews, output.size()).clear();
        for (MTEHatchInputBus bus : mInputBusses) {
            if (!(bus instanceof MTEMEInputAssembly) || !bus.isValid()) continue;
            if (color.isPresent() && bus.getColor() != -1 && bus.getColor() != color.get()) continue;
            for (FluidStack fluid : ((MTEMEInputAssembly) bus).getStoredFluids()) if (fluid != null) views.put(fluid.getFluid(), fluid);
        }
        output.addAll(views.values());
    }

    @Inject(method = "getStoredInputsForColor", at = @At("RETURN"), require = 1)
    private void matterblueprints$stockItemInputs(Optional<Byte> color, CallbackInfoReturnable<ArrayList<ItemStack>> result) {
        matterblueprints$appendLongItems(result.getReturnValue());
    }

    // The first List overload is the branch that checks one input bus at a time.
    @ModifyArg(method = "doCheckRecipe", at = @At(value = "INVOKE",
        target = "Lgregtech/api/logic/ProcessingLogic;setInputItems(Ljava/util/List;)Lgregtech/api/logic/ProcessingLogic;",
        ordinal = 0), index = 0, require = 1)
    private List<ItemStack> matterblueprints$separatedStockItems(List<ItemStack> items) {
        matterblueprints$appendLongItems(items);
        return items;
    }

    private void matterblueprints$appendLongItems(List<ItemStack> items) {
        for (MTEHatchInputBus bus : mInputBusses) {
            if (bus instanceof MTELongStockInputBus) ((MTELongStockInputBus) bus).appendLongInputs(items);
        }
    }
    @Inject(method = "depleteInputQuantity", at = @At("RETURN"), cancellable = true, require = 1)
    private void matterblueprints$consumeManualFluid(FluidStack request, boolean simulate, CallbackInfoReturnable<Integer> result) {
        if (request == null || request.amount <= result.getReturnValue()) return;
        int consumed = result.getReturnValue();
        FluidStack remainder = request.copy();
        for (MTEHatchInputBus bus : mInputBusses) {
            if (!(bus instanceof MTEMEInputAssembly) || !bus.isValid()) continue;
            remainder.amount = request.amount - consumed;
            consumed += ((MTEMEInputAssembly) bus).depleteFluid(remainder, simulate);
            if (consumed >= request.amount) break;
        }
        result.setReturnValue(consumed);
    }
    @Inject(method = "getOutputHatches()Ljava/util/List;", at = @At("RETURN"), require = 1)
    private void matterblueprints$fluidOutputs(CallbackInfoReturnable<List<IOutputHatch>> result) {
        for (MTEHatchOutputBus bus : mOutputBusses) {
            if (bus instanceof MTEMEOutputAssembly && bus.isValid())
                result.getReturnValue().add(((MTEMEOutputAssembly) bus).getFluidOutput());
        }
    }
}
