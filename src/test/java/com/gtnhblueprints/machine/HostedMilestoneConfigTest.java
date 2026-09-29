package com.gtnhblueprints.machine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.common.config.Configuration;
import org.junit.jupiter.api.io.TempDir;

import com.gtnhblueprints.HostedMilestoneConfig;
import org.junit.jupiter.api.Test;

class HostedMilestoneConfigTest {
    @TempDir
    Path directory;

    @Test
    void exampleLoadsThroughForgeAndMatchesDefaults() throws Exception {
        Path file = directory.resolve("matterblueprints.cfg");
        Files.copy(java.nio.file.Paths.get("docs/host-milestones-example.cfg"), file);
        java.lang.reflect.Field home = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        Object previousHome = home.get(null);
        Configuration config;
        try {
            home.set(null, directory.toFile());
            config = new Configuration(file.toFile());
        } finally {
            home.set(null, previousHome);
        }
        config.get("hostMilestones", "plasmaFuelLevel", 6).set(6);
        HostedMilestoneConfig.load(config);
        assertFalse(config.hasKey("hostMilestones", "plasmaFuelLevel"));
        assertEquals(8, HostedMilestoneConfig.plasmaFuelHours);
        assertEquals(576000L, HostedMilestoneConfig.plasmaFuelTicks());
        assertArrayEquals(new int[] { 75, 50, 25 }, HostedMilestoneConfig.durationPercent);
        assertTrue(HostedMilestoneConfig.energy[5].compareTo(java.math.BigInteger.valueOf(Long.MAX_VALUE)) > 0);
        assertArrayEquals(new long[] { 12000, 72000, 432000, 1728000, 5184000, 12096000 },
            HostedMilestoneConfig.runtimeTicks);
    }

    @Test
    void acceptsLongThresholdsBeyondIntegerRange() {
        assertArrayEquals(new long[] { 1, 2, 3, 4, 5, 3000000000L },
            HostedMilestoneConfig.parseThresholds(new String[] { "1", "2", "3", "4", "5", "3000000000" }));
    }

    @Test
    void rejectsMalformedOrUnorderedThresholds() {
        for (String[] values : new String[][] { { "1" }, { "1", "2", "2", "4", "5", "6" },
            { "0", "2", "3", "4", "5", "6" }, { "1", "2", "3", "4", "5", "1e21" } }) {
            assertThrows(IllegalArgumentException.class, () -> HostedMilestoneConfig.parseThresholds(values));
        }
    }
}
