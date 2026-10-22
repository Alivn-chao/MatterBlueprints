package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.gtnhblueprints.mixin.MixinGTAssemblyPorts;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;

class MEPortCompatibilityTest {
    @BeforeAll static void initialize() throws Exception { AssemblyTestBootstrap.initialize(); }
    public static final class Input extends MTEMEInputAssembly {
        private Input() { super(0, "unused", "unused"); }
        @Override public boolean isValid() { return true; }
        @Override public boolean isAllowedToWork() { return true; }
    }
    private static final class Ports extends MixinGTAssemblyPorts {
        Ports() { mInputBusses = new ArrayList<>(); mInputHatches = new ArrayList<>(); mOutputBusses = new ArrayList<>(); }
        @Override public boolean addInputBusToMachineList(IGregTechTileEntity tile, int texture) { return true; }
        @Override public boolean addOutputBusToMachineList(IGregTechTileEntity tile, int texture) { return true; }
    }
    private Input input(byte color) throws Exception {
        Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        Input input = (Input) ((sun.misc.Unsafe) singleton.get(null)).allocateInstance(Input.class);
        Field watchers = gregtech.api.metatileentity.implementations.MTEHatch.class.getDeclaredField("watchers"); watchers.setAccessible(true);
        watchers.set(input, new ArrayList<gregtech.common.tileentities.machines.IHatchWatcher>());
        Field limits = MTEQuantizedMEInputBus.class.getDeclaredField("markedAmounts"); limits.setAccessible(true);
        limits.set(input, new MEMarkedAmounts());
        Field port = MTEMEInputAssembly.class.getDeclaredField("fluids"); port.setAccessible(true);
        port.set(input, input.new FluidPort());
        Field processing = gregtech.common.tileentities.machines.MTEHatchInputME.class.getDeclaredField("processingRecipe"); processing.setAccessible(true);
        processing.setBoolean(input.fluids, true);
        IGregTechTileEntity tile = (IGregTechTileEntity) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] { IGregTechTileEntity.class, appeng.api.networking.security.IActionHost.class }, (proxy, method, arguments) -> {
                if (method.getName().equals("getColorization")) return color;
                if (method.getName().equals("getMetaTileEntity")) return input;
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == byte.class) return (byte) 0;
                if (method.getReturnType() == int.class) return 0;
                if (method.getReturnType() == long.class) return 0L;
                return null;
            });
        input.setBaseMetaTileEntity(tile);
        return input;
    }
    private void setView(Input input, int amount) {
        gregtech.common.tileentities.machines.MTEHatchInputME.Slot slot = new gregtech.common.tileentities.machines.MTEHatchInputME.Slot(new FluidStack(FluidRegistry.WATER, 1));
        slot.extracted = new FluidStack(FluidRegistry.WATER, amount); slot.extractedAmount = amount;
        input.fluids.viewSlots()[0] = slot;
    }
    private void invoke(Ports ports, String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = MixinGTAssemblyPorts.class.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(ports, arguments);
    }
    @Test void nativeVirtualAssemblyViewFeedsOrdinaryFluidRecipesAndHonorsColorIsolation() throws Exception {
        Ports ports = new Ports(); Input input = input((byte) 3); ports.mInputBusses.add(input);
        setView(input, 4000);
        CallbackInfoReturnable<ArrayList<FluidStack>> result = new CallbackInfoReturnable<>("getStoredFluidsForColor", false, new ArrayList<>());
        invoke(ports, "matterblueprints$manualFluids", new Class<?>[] { Optional.class, CallbackInfoReturnable.class }, Optional.of((byte) 3), result);
        assertEquals(1, result.getReturnValue().size());
        assertSame(input.fluids.viewSlots()[0].extracted, result.getReturnValue().get(0));
        result = new CallbackInfoReturnable<>("getStoredFluidsForColor", false, new ArrayList<>());
        invoke(ports, "matterblueprints$manualFluids", new Class<?>[] { Optional.class, CallbackInfoReturnable.class }, Optional.of((byte) 4), result);
        assertTrue(result.getReturnValue().isEmpty());
    }
    @Test void customRecipeDepletionUsesOnlyTheRemainderAndSimulationNeverMutatesTheAssembly() throws Exception {
        Ports ports = new Ports(); Input input = input((byte) -1); ports.mInputBusses.add(input);
        setView(input, 1000);
        for (boolean simulate : new boolean[] { true, false }) {
            CallbackInfoReturnable<Integer> result = new CallbackInfoReturnable<>("depleteInputQuantity", true, 200);
            invoke(ports, "matterblueprints$consumeManualFluid", new Class<?>[] { FluidStack.class, boolean.class, CallbackInfoReturnable.class },
                new FluidStack(FluidRegistry.WATER, 700), simulate, result);
            assertEquals(700, result.getReturnValue().intValue());
            assertEquals(simulate ? 1000 : 500, input.fluids.viewSlots()[0].extracted.amount);
        }
    }
    @Test void twoAssembliesExposeOneVirtualMEFluidStockInsteadOfAddingItTwice() throws Exception {
        Ports ports = new Ports(); Input first = input((byte) -1), second = input((byte) -1);
        setView(first, 3000); setView(second, 3000);
        ports.mInputBusses.add(first); ports.mInputBusses.add(second);
        CallbackInfoReturnable<ArrayList<FluidStack>> result = new CallbackInfoReturnable<>("getStoredFluidsForColor", false, new ArrayList<>());
        invoke(ports, "matterblueprints$manualFluids", new Class<?>[] { Optional.class, CallbackInfoReturnable.class }, Optional.empty(), result);
        assertEquals(1, result.getReturnValue().size());
        assertEquals(3000, result.getReturnValue().get(0).amount);
    }
    @Test void itemInventoryAndBothSettlementPhasesAreStillTheNativeImplementations() throws Exception {
        assertEquals(gregtech.common.tileentities.machines.MTEHatchInputBusME.class,
            MTEQuantizedMEInputBus.class.getMethod("getStackInSlot", int.class).getDeclaringClass());
        assertEquals(gregtech.common.tileentities.machines.MTEHatchInputBusME.class,
            MTEQuantizedMEInputBus.class.getMethod("startRecipeProcessing").getDeclaringClass());
        assertEquals(gregtech.common.tileentities.machines.MTEHatchInputBusME.class,
            MTEQuantizedMEInputBus.class.getMethod("endRecipeProcessing", gregtech.api.metatileentity.implementations.MTEMultiBlockBase.class).getDeclaringClass());
        assertEquals(gregtech.common.tileentities.machines.MTEHatchInputME.class,
            MTEQuantizedMEInputHatch.class.getMethod("endRecipeProcessing", gregtech.api.metatileentity.implementations.MTEMultiBlockBase.class).getDeclaringClass());
    }
    @Test void fluidDropDataContainsOnlyMarksAndAmountsNeverARefundableVirtualInventory() throws Exception {
        Input input = input((byte) -1); setView(input, 2000000000);
        input.fluids.setMarkedAmount(0, 256);
        net.minecraft.nbt.NBTTagCompound dropped = new net.minecraft.nbt.NBTTagCompound(); input.fluids.writeMarkedConfiguration(dropped);
        net.minecraft.nbt.NBTTagCompound slot = dropped.getTagList("slots", 10).getCompoundTagAt(0);
        assertTrue(slot.hasKey("config"));
        assertFalse(slot.hasKey("extracted"));
        assertFalse(slot.hasKey("extractedAmount"));
        assertEquals(2000000000, input.fluids.viewSlots()[0].extracted.amount);
        assertEquals(256, dropped.getIntArray("mbMarkedAmounts")[0]);
    }
    @Test void beta3StructureMixinTargetsStillReferToTheInputAndOutputFluidCounters() throws Exception {
        for (String[] target : new String[][] { { "3", "mInputHatches" }, { "5", "mOutputHatches" } }) {
            org.objectweb.asm.tree.ClassNode node = HostedAntimatterCompatibilityTest.read("gregtech/api/enums/HatchElement$" + target[0]);
            boolean correctCounter = false;
            for (org.objectweb.asm.tree.MethodNode method : node.methods) {
                if (!method.name.equals("count") || !method.desc.equals("(Lgregtech/api/metatileentity/implementations/MTEMultiBlockBase;)J")) continue;
                for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode)
                        correctCounter |= ((org.objectweb.asm.tree.FieldInsnNode) instruction).name.equals(target[1]);
                }
            }
            assertTrue(correctCounter, "Beta-3 hatch counter moved: " + target[0]);
        }
    }
    @Test void beta3AppendsTheNativeMEFluidMapAsTheFinalListAddition() throws Exception {
        org.objectweb.asm.tree.ClassNode node = HostedAntimatterCompatibilityTest.read("gregtech/api/metatileentity/implementations/MTEMultiBlockBase");
        org.objectweb.asm.tree.MethodNode method = node.methods.stream().map(m -> (org.objectweb.asm.tree.MethodNode) m)
            .filter(m -> m.name.equals("getStoredFluidsForColor")).findFirst().get();
        org.objectweb.asm.tree.MethodInsnNode previous = null;
        boolean appendedMap = false;
        for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof org.objectweb.asm.tree.MethodInsnNode)) continue;
            org.objectweb.asm.tree.MethodInsnNode call = (org.objectweb.asm.tree.MethodInsnNode) instruction;
            if (call.name.equals("addAll")) {
                assertNotNull(previous);
                assertEquals("java/util/Map", previous.owner);
                assertEquals("values", previous.name);
                appendedMap = true;
            } else if (appendedMap) fail("Native operations after the ME map tail require rechecking the assembly merger");
            previous = call;
        }
        assertTrue(appendedMap);
    }
}
