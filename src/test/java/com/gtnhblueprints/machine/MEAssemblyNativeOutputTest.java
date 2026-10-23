package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import appeng.util.item.AEFluidStack;
import gregtech.api.interfaces.IOutputHatchTransaction;
import gregtech.api.interfaces.IOutputTransaction;
import gregtech.api.util.GTUtility;

class MEAssemblyNativeOutputTest {
    @BeforeAll static void initialize() throws Exception { AssemblyTestBootstrap.initialize(); }
    static final class Owner extends MTEMEOutputAssembly {
        private Owner() { super(0, "unused", "unused"); }
        @Override public void markDirty() {}
        @Override public void notifyOutputSpaceChanged() {}
    }
    private MEAssemblyFluidPort port() throws Exception {
        Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        Owner owner = (Owner) ((sun.misc.Unsafe) singleton.get(null)).allocateInstance(Owner.class);
        return new MEAssemblyFluidPort(owner);
    }
    @Test void emptyFluidCacheHasNativeDefaultCapacityAndOneDriveSlot() throws Exception {
        MEAssemblyFluidPort port = port();
        assertEquals(128_000L, port.provider.getCacheCapacity());
        assertEquals(1, port.cell.getSlots());
        assertEquals(1, port.cell.getSlotLimit(0));
        assertNull(port.cell.getStackInSlot(0));
        assertEquals(appeng.api.storage.StorageChannel.FLUIDS, port.getChannel());
    }
    @Test void simulatedOutputDoesNotConsumeCacheAndCheckModeProtectsItsNativeCapacity() throws Exception {
        MEAssemblyFluidPort port = port();
        port.provider.setCheckMode(true);
        FluidStack input = new FluidStack(FluidRegistry.WATER, 128_000);
        assertTrue(port.provider.storePartial(stack(input.amount), true));
        assertEquals(0L, port.provider.getCachedAmount());
        assertTrue(port.provider.storePartial(stack(128_000), false));
        assertEquals(128_000L, port.provider.getCachedAmount());
        input = new FluidStack(FluidRegistry.WATER, 1);
        assertFalse(port.provider.storePartial(stack(input.amount), false));
        assertEquals(1, input.amount);
    }
    @Test void recipeSimulationUsesNativeDynamicCapacityWithoutChangingCache() throws Exception {
        MEAssemblyFluidPort port = port(); port.provider.setCheckMode(true);
        port.provider.addToCache(stack(127_000));
        IOutputHatchTransaction transaction = port.createTransaction();
        ((IOutputTransaction.IRecipeCheckAware) transaction).setRecipeCheck(true);
        FluidStack output = new FluidStack(FluidRegistry.WATER, 5_000);
        assertTrue(transaction.storePartial(GTUtility.FluidId.create(output), output, 5_000, 5_000));
        assertEquals(4_000, output.amount);
        assertEquals(127_000L, port.provider.getCachedAmount());
    }
    @Test void nativeFluidCacheAndSerializationKeepLongAmountsBeyondVanillaFluidStackLimits() throws Exception {
        MEAssemblyFluidPort port = port();
        port.provider.addToCache(stack(4_000_000_000L));
        assertEquals(4_000_000_000L, port.provider.getCachedAmount());
        assertFalse(port.provider.hasAvailableSpace());
        NBTTagCompound encoded = port.saveStackToNBT(stack(4_000_000_000L));
        assertEquals(4_000_000_000L, encoded.getLong("size"));
        assertEquals(FluidRegistry.WATER, FluidStack.loadFluidStackFromNBT(encoded.getCompoundTag("fluidStack")).getFluid());
    }
    // The cache only needs the AE stack interface. Avoid starting AE's mod registries in a unit test.
    private appeng.api.storage.data.IAEFluidStack stack(long amount) {
        final long[] size = { amount };
        return (appeng.api.storage.data.IAEFluidStack) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] { appeng.api.storage.data.IAEFluidStack.class }, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getStackSize": return size[0];
                    case "setStackSize": size[0] = (Long) args[0]; return proxy;
                    case "copy": return stack(size[0]);
                    case "getFluidStack": return new FluidStack(FluidRegistry.WATER, (int) Math.min(Integer.MAX_VALUE, size[0]));
                    case "getFluid": return FluidRegistry.WATER;
                    case "hashCode": return 1;
                    case "equals": return args[0] == proxy;
                    case "toString": return "Water[" + size[0] + "]";
                    default:
                        if (method.getReturnType() == boolean.class) return false;
                        if (method.getReturnType() == int.class) return 0;
                        if (method.getReturnType() == long.class) return 0L;
                        return null;
                }
            });
    }
}
