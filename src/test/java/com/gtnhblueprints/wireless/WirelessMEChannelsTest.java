package com.gtnhblueprints.wireless;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.tile.networking.TileController;
import com.gtnhblueprints.mixin.MixinAEWirelessPathGrid;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

class WirelessMEChannelsTest {
    static class PathState extends MixinAEWirelessPathGrid {}

    @Test void nativeControllerFaceAcceptsMoreThan32ChannelsWithWirelessCapacity() throws Exception {
        Class<?> face = Class.forName("appeng.me.pathfinding.PathingCalculation$ControllerFace");
        Constructor<?> constructor = face.getDeclaredConstructor(int.class); constructor.setAccessible(true);
        Method canUse = face.getDeclaredMethod("canUseChannel"), use = face.getDeclaredMethod("useChannel");
        canUse.setAccessible(true); use.setAccessible(true);
        for (boolean wireless : new boolean[] { false, true }) {
            Object instance = constructor.newInstance(WirelessMEChannels.capacity(32, wireless));
            int assigned = 0;
            while (assigned < 1024 && (Boolean) canUse.invoke(instance)) {
                use.invoke(instance); assigned++;
            }
            assertEquals(wireless ? 1024 : 32, assigned);
        }
        assertEquals(Integer.MAX_VALUE, WirelessMEChannels.capacity(8, true));
        assertEquals(8, WirelessMEChannels.capacity(8, false));
        assertEquals(0, WirelessMEChannels.capacity(0, true));
    }

    @Test void removingLastWirelessControllerRestoresNormalCapacityWithoutScanningOnEachQuery() throws Exception {
        PathState state = new PathState();
        Set<TileController> controllers = new HashSet<>();
        Field set = MixinAEWirelessPathGrid.class.getDeclaredField("controllers"); set.setAccessible(true);
        set.set(state, controllers);
        Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) singleton.get(null);
        TileWirelessMEController first = (TileWirelessMEController) unsafe.allocateInstance(TileWirelessMEController.class);
        TileWirelessMEController second = (TileWirelessMEController) unsafe.allocateInstance(TileWirelessMEController.class);
        Method update = MixinAEWirelessPathGrid.class.getDeclaredMethod("matterblueprints$updateCapacity",
            IGridNode.class, IGridHost.class, CallbackInfo.class);
        update.setAccessible(true);
        controllers.add(first); controllers.add(second);
        update.invoke(state, null, first, null);
        assertTrue(state.matterblueprints$unlimitedChannels());
        controllers.remove(first); update.invoke(state, null, first, null);
        assertTrue(state.matterblueprints$unlimitedChannels());
        controllers.remove(second); update.invoke(state, null, second, null);
        assertFalse(state.matterblueprints$unlimitedChannels());
    }
}
