package com.gtnhblueprints.machine;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import com.gtnhblueprints.mixin.HostedLateMixinLoader;
import gregtech.api.enums.MetaTileEntityIDs;
class MEPortPackagingTest {
    @Test void earlyManifestConfigurationAndLateGTConfigurationBothExist() {
        ClassLoader resources = getClass().getClassLoader();
        assertNotNull(resources.getResource("mixins.matterblueprints.json"));
        HostedLateMixinLoader late = new HostedLateMixinLoader();
        assertNotNull(resources.getResource(late.getMixinConfig()));
        assertFalse(late.getMixins(Collections.singleton("gregtech")).isEmpty());
        for (String mixin : late.getMixins(Collections.singleton("gregtech")))
            assertNotNull(resources.getResource("com/gtnhblueprints/mixin/" + mixin + ".class"), mixin);
    }
    @Test void newDefaultHatchIdsAreNotReservedByBeta3GregTech() {
        for (int id = 32601; id <= 32604; id++)
            for (MetaTileEntityIDs official : MetaTileEntityIDs.values()) assertNotEquals(id, official.ID, official.name());
    }
}
