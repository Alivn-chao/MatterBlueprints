package com.gtnhblueprints.machine;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
class MEAssemblyFluidOutputTest {
    @BeforeAll static void initialize() throws Exception { AssemblyTestBootstrap.initialize(); }
    @Test void fluidOutputSimulationDoesNotCommitAndFullBuffersKeepRemainders() {
        MEAssemblyFluidOutput output = new MEAssemblyFluidOutput(() -> {});
        FluidStack request = new FluidStack(FluidRegistry.WATER, Integer.MAX_VALUE);
        assertTrue(output.storePartial(request, true));
        assertEquals(0, request.amount);
        assertNull(output.fluids[0]);
        for (int i = 0; i < 16; i++) assertTrue(output.storePartial(new FluidStack(FluidRegistry.WATER, Integer.MAX_VALUE), false));
        FluidStack surplus = new FluidStack(FluidRegistry.WATER, 345);
        assertFalse(output.storePartial(surplus, false));
        assertEquals(345, surplus.amount);
        NBTTagCompound saved = new NBTTagCompound(); output.save(saved);
        MEAssemblyFluidOutput restored = new MEAssemblyFluidOutput(() -> {}); restored.load(saved);
        for (FluidStack fluid : restored.fluids) assertEquals(Integer.MAX_VALUE, fluid.amount);
    }
    @Test void fluidOutputTransactionCommitsOnceAndDoesNotMutateTheRealOutputEarly() {
        MEAssemblyFluidOutput output = new MEAssemblyFluidOutput(() -> {});
        gregtech.api.interfaces.IOutputHatchTransaction transaction = output.createTransaction();
        FluidStack request = new FluidStack(FluidRegistry.LAVA, 1234);
        transaction.storePartial(null, request, 1, 1);
        assertNull(output.fluids[0]);
        transaction.commit();
        assertEquals(1234, output.fluids[0].amount);
        assertThrows(IllegalStateException.class, transaction::commit);
    }
}
