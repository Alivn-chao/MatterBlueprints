package com.gtnhblueprints.wireless;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

class WirelessMEDataTest {
    @Test void gtTeamMembersShareAccessAndFavoritesButKeepCreatorOwnershipAcrossReloadAndLeaving() {
        UUID creator = UUID.randomUUID(), member = UUID.randomUUID(), outsider = UUID.randomUUID();
        java.util.Map<UUID, UUID> teams = gregtech.common.misc.spaceprojects.SpaceProjectManager.spaceTeams;
        teams.put(creator, creator); teams.put(member, creator);
        try {
            WirelessMEData data = new WirelessMEData();
            String id = data.create(creator, "factory").id;
            assertEquals(1, data.visible(member).size());
            assertEquals(creator, data.require(member, id).owner);
            assertTrue(data.visible(outsider).isEmpty());
            data.toggleFavorite(member, id);
            assertEquals("", data.favorite(creator));
            data.claimSource(member, id, new WirelessMEData.Location(0, 1, 2, 3));
            assertThrows(IllegalArgumentException.class, () -> data.delete(member, id));
            NBTTagCompound tag = new NBTTagCompound(); data.writeToNBT(tag);
            WirelessMEData restored = new WirelessMEData(); restored.readFromNBT(tag);
            assertEquals(id, restored.favorite(member));
            assertEquals(creator, restored.get(id).owner);
            teams.put(member, member);
            assertTrue(restored.visible(member).isEmpty());
            assertEquals("", restored.favorite(member));
            assertThrows(IllegalArgumentException.class, () -> restored.require(member, id));
            assertThrows(IllegalArgumentException.class, () -> restored.claimSource(member, id,
                new WirelessMEData.Location(0, 1, 2, 3)));
            restored.delete(creator, id);
            assertNull(restored.get(id));
        } finally { teams.remove(creator); teams.remove(member); teams.remove(outsider); }
    }

    @Test void channelsHaveStableIdsAndArePrivateToTheirCreator() {
        WirelessMEData data = new WirelessMEData(); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        WirelessMEData.Channel first = data.create(a, "factory"), second = data.create(a, "factory");
        assertNotEquals(first.id, second.id);
        assertEquals(2, data.visible(a).size()); assertTrue(data.visible(b).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> data.require(b, first.id));
        assertThrows(IllegalArgumentException.class, () -> data.toggleFavorite(b, first.id));
        assertThrows(IllegalArgumentException.class, () -> data.delete(b, first.id));
    }
    @Test void eachPlayerHasExactlyOneFavoriteAndItCanBeCleared() {
        WirelessMEData data = new WirelessMEData(); UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        String x = data.create(a, "x").id, y = data.create(a, "y").id, z = data.create(b, "z").id;
        data.toggleFavorite(a, x); data.toggleFavorite(a, y); data.toggleFavorite(b, z);
        assertEquals(y, data.favorite(a)); assertEquals(z, data.favorite(b));
        data.toggleFavorite(a, y); assertEquals("", data.favorite(a)); assertEquals(z, data.favorite(b));
    }
    @Test void onlyOneControllerCanClaimAChannelAndDimensionsArePartOfItsIdentity() {
        WirelessMEData data = new WirelessMEData(); UUID owner = UUID.randomUUID(); String id = data.create(owner, "a").id;
        WirelessMEData.Location a = new WirelessMEData.Location(0, 1, 2, 3), b = new WirelessMEData.Location(-1, 1, 2, 3);
        data.claimSource(owner, id, a); data.claimSource(owner, id, a);
        assertThrows(IllegalArgumentException.class, () -> data.claimSource(owner, id, b));
        data.releaseSource(id, b); assertEquals(a, data.get(id).source);
        data.releaseSource(id, a); data.claimSource(owner, id, b); assertEquals(b, data.get(id).source);
    }
    @Test void restartRetainsChannelsFavoritesAndCrossDimensionSourceCoordinates() {
        WirelessMEData data = new WirelessMEData(); UUID owner = UUID.randomUUID();
        String id = data.create(owner, "Main production").id;
        data.claimSource(owner, id, new WirelessMEData.Location(42, -123, 80, 456)); data.toggleFavorite(owner, id);
        NBTTagCompound nbt = new NBTTagCompound(); data.writeToNBT(nbt);
        WirelessMEData loaded = new WirelessMEData(); loaded.readFromNBT(nbt);
        assertEquals("Main production", loaded.get(id).name); assertEquals(owner, loaded.get(id).owner);
        assertEquals(id, loaded.favorite(owner)); assertEquals(new WirelessMEData.Location(42, -123, 80, 456), loaded.get(id).source);
        loaded.delete(owner, id); assertNull(loaded.get(id)); assertEquals("", loaded.favorite(owner));
    }
    @Test void metadataHasNoSmallChannelCountLimitAndRejectsInvalidNames() {
        WirelessMEData data = new WirelessMEData(); UUID owner = UUID.randomUUID();
        for (int i = 0; i < 1024; i++) data.create(owner, "channel " + i);
        assertEquals(1024, data.visible(owner).size());
        assertThrows(IllegalArgumentException.class, () -> data.create(owner, "   "));
        assertThrows(IllegalArgumentException.class, () -> data.create(owner, "x\ny"));
        assertThrows(IllegalArgumentException.class, () -> data.create(owner, new String(new char[49]).replace('\0', 'x')));
    }
    @Test void newerDataVersionsAreNotSilentlyOverwritten() {
        NBTTagCompound nbt = new NBTTagCompound(); nbt.setInteger("version", 2);
        assertThrows(IllegalStateException.class, () -> new WirelessMEData().readFromNBT(nbt));
    }
}
