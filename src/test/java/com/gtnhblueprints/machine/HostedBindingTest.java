package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.jupiter.api.Test;

class HostedBindingTest {
    private static final String HOST = "0:10:64:10";
    private static final String SOURCE = "0:20:64:20";
    private static final String COPY = "7:40:64:40";

    @Test
    void movingTenMachinesReplacesCoordinatesWithoutIncreasingCountAndSurvivesRestart() {
        HostedBindingData data = new HostedBindingData();
        data.register("center", HOST);
        for (int i = 0; i < 10; i++) data.bind("center", "0:" + i + ":64:0");
        for (int i = 0; i < 10; i++) data.move("0:" + i + ":64:0", "7:" + i + ":80:100");
        data = reload(data);
        assertEquals(10, data.members("center").size());
        for (int i = 0; i < 10; i++) {
            assertNull(data.owner("0:" + i + ":64:0"));
            assertEquals("center", data.owner("7:" + i + ":80:100"));
        }
        data.unbind("7:0:80:100");
        assertEquals(9, data.members("center").size());
    }

    @Test
    void nativeSwapPreservesEachMachinesOriginalOwnerIncludingSameCenter() {
        HostedBindingData data = new HostedBindingData();
        data.register("left", HOST);
        data.register("right", "0:100:64:100");
        data.bind("left", SOURCE);
        data.bind("right", COPY);
        data.move(SOURCE, COPY);
        assertEquals("left", data.owner(COPY));
        assertEquals("right", data.owner(SOURCE));
        assertEquals(1, data.members("left").size());
        assertEquals(1, data.members("right").size());
        data.unbind(SOURCE);
        data.bind("left", SOURCE);
        data.move(SOURCE, COPY);
        data.move(COPY, COPY);
        assertEquals(2, data.members("left").size());
        assertEquals("left", data.owner(SOURCE));
        assertEquals("left", data.owner(COPY));
    }

    @Test
    void failedOrThrowingNativeMoveCannotCommitBindingChanges() {
        HostedBindingData data = new HostedBindingData();
        data.register("center", HOST);
        data.bind("center", SOURCE);
        assertFalse(HostedBindingMove.commit(data, SOURCE, COPY, () -> false));
        assertThrows(IllegalStateException.class,
            () -> HostedBindingMove.commit(data, SOURCE, COPY, () -> { throw new IllegalStateException("cancelled"); }));
        assertEquals("center", data.owner(SOURCE));
        assertNull(data.owner(COPY));
        assertTrue(HostedBindingMove.commit(data, SOURCE, COPY, () -> {
            assertEquals("center", data.owner(SOURCE));
            assertNull(data.owner(COPY));
            return true;
        }));
        assertNull(data.owner(SOURCE));
        assertEquals("center", data.owner(COPY));
    }

    @Test
    void movingDeferredJobsAndExtraTasksKeepsProgressAtNewCoordinates() {
        HostedMachineCoordinator center = legacyCenter();
        NBTTagCompound chunk = new NBTTagCompound(); center.writeToNBT(chunk);
        NBTTagCompound job = new NBTTagCompound();
        job.setString("machine", SOURCE);
        job.setInteger("maxProgress", 100);
        job.setInteger("progress", 50);
        job.setBoolean("equivalentAssembly", true);
        NBTTagList jobs = new NBTTagList(); jobs.appendTag(job);
        chunk.setTag("mbHostClaims", jobs);
        chunk.setTag("mbHostExtraTasks", jobs.copy());
        center.readFromNBT(chunk);
        center.relocateBindings(Collections.singletonMap(SOURCE, COPY));
        NBTTagCompound moved = new NBTTagCompound(); center.writeToNBT(moved);
        assertEquals(COPY, moved.getTagList("mbHostBindings", 8).getStringTagAt(0));
        assertEquals(COPY, moved.getTagList("mbHostClaims", 10).getCompoundTagAt(0).getString("machine"));
        assertEquals(50, moved.getTagList("mbHostClaims", 10).getCompoundTagAt(0).getInteger("progress"));
        assertEquals(COPY, moved.getTagList("mbHostExtraTasks", 10).getCompoundTagAt(0).getString("machine"));
    }

    @Test
    void arrayCopiesJoinOriginalHostWithoutTogglingOrStealing() {
        HostedBindingData data = new HostedBindingData();
        data.register("original", HOST);
        data.register("other", "0:50:64:50");
        assertTrue(data.bind("original", SOURCE));
        for (int i = 0; i < 128; i++) {
            String position = "7:" + i + ":100:100";
            assertTrue(data.bind(data.owner(SOURCE), position));
            assertTrue(data.bind(data.owner(SOURCE), position));
            assertFalse(data.bind("other", position));
        }
        assertEquals(129, data.members("original").size());
        assertFalse(data.bind("original", HOST));
        assertFalse(data.bind("nonexistent", COPY));
    }

    @Test
    void breakingSourceKeepsCopiesButBreakingHostRevokesOldBlueprints() {
        HostedBindingData data = new HostedBindingData();
        data.register("old-center", HOST);
        data.bind("old-center", SOURCE);
        String captured = data.owner(SOURCE);
        data.bind(captured, COPY);
        data.unbind(SOURCE);
        assertNull(data.owner(SOURCE));
        assertEquals(captured, data.owner(COPY));
        data.removeHost(captured);
        data.register("replacement-center", HOST);
        assertNull(data.owner(COPY));
        assertFalse(data.bind(captured, SOURCE));
        assertTrue(data.bind("replacement-center", SOURCE));
    }

    @Test
    void unloadAndRestartKeepCrossDimensionBindings() {
        HostedBindingData data = new HostedBindingData();
        data.register("center", HOST);
        data.bind("center", SOURCE);
        data.bind("center", COPY);
        HostedBindingData restored = reload(data);
        assertEquals(HOST, restored.location("center"));
        assertEquals(data.members("center"), restored.members("center"));
        assertEquals("center", restored.owner(COPY));
        // Repeated load replaces all maps; it never duplicates membership.
        NBTTagCompound nbt = new NBTTagCompound(); data.writeToNBT(nbt);
        restored.readFromNBT(nbt);
        assertEquals(2, restored.members("center").size());
    }

    @Test
    void oldCenterMigratesOnceAndAcceptsCopiedMachines() {
        HostedMachineCoordinator center = legacyCenter();
        HostedBindingData data = new HostedBindingData();
        assertTrue(center.synchronizeBindings(data, HOST));
        String id = data.owner(SOURCE);
        assertNotNull(id);
        assertFalse(center.synchronizeBindings(data, HOST));
        data.bind(id, COPY);
        assertTrue(center.synchronizeBindings(data, HOST));
        assertEquals(2, center.getBindingCount());
        NBTTagCompound item = new NBTTagCompound();
        center.writeMilestonesToItem(item);
        assertFalse(item.hasKey("mbHostBindingIdentity"));
        assertFalse(item.hasKey("mbHostBindingsIndexed"));
    }

    @Test
    void removalWhileCenterUnloadedCannotResurrectFromItsOlderChunkSave() {
        HostedMachineCoordinator center = legacyCenter();
        HostedBindingData data = new HostedBindingData();
        center.synchronizeBindings(data, HOST);
        NBTTagCompound chunk = new NBTTagCompound();
        center.writeToNBT(chunk);
        String id = data.owner(SOURCE);
        // Center is now unloaded. Removal and restart happen without that center object.
        data.unbind(SOURCE);
        data = reload(data);
        HostedMachineCoordinator loaded = new HostedMachineCoordinator(null);
        loaded.readFromNBT(chunk);
        loaded.synchronizeBindings(data, HOST);
        assertEquals(0, loaded.getBindingCount());
        assertNull(data.owner(SOURCE));
        assertEquals(Collections.emptySet(), data.members(id));
        // Replacing a controller at the old position does not create a binding.
        assertFalse(loaded.synchronizeBindings(data, HOST));
        assertNull(data.owner(SOURCE));
    }

    @Test
    void removingAnUnloadedMachineAlsoClearsSavedJobsAndExtraTasks() {
        HostedMachineCoordinator center = legacyCenter();
        HostedBindingData data = new HostedBindingData();
        center.synchronizeBindings(data, HOST);
        NBTTagCompound chunk = new NBTTagCompound(); center.writeToNBT(chunk);
        NBTTagCompound job = new NBTTagCompound();
        job.setString("machine", SOURCE);
        job.setInteger("maxProgress", 100);
        job.setInteger("progress", 50);
        job.setBoolean("equivalentAssembly", true);
        NBTTagList jobs = new NBTTagList(); jobs.appendTag(job);
        chunk.setTag("mbHostClaims", jobs);
        chunk.setTag("mbHostExtraTasks", jobs.copy());
        data.unbind(SOURCE);
        HostedMachineCoordinator loaded = new HostedMachineCoordinator(null);
        loaded.readFromNBT(chunk);
        loaded.synchronizeBindings(data, HOST);
        NBTTagCompound saved = new NBTTagCompound(); loaded.writeToNBT(saved);
        assertEquals(0, saved.getTagList("mbHostClaims", 10).tagCount());
        assertEquals(0, saved.getTagList("mbHostExtraTasks", 10).tagCount());
    }

    private static HostedMachineCoordinator legacyCenter() {
        NBTTagCompound saved = new NBTTagCompound();
        NBTTagList bindings = new NBTTagList(); bindings.appendTag(new NBTTagString(SOURCE));
        saved.setTag("mbHostBindings", bindings);
        HostedMachineCoordinator center = new HostedMachineCoordinator(null);
        center.readFromNBT(saved);
        return center;
    }

    private static HostedBindingData reload(HostedBindingData data) {
        NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
        HostedBindingData restored = new HostedBindingData(); restored.readFromNBT(saved);
        return restored;
    }
}
