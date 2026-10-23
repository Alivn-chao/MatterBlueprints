package com.gtnhblueprints.machine;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;
import com.gtnhblueprints.loading.HostedLateMixinLoader;
import gregtech.api.enums.MetaTileEntityIDs;
class MEPortPackagingTest {
    @Test void lateLoaderIsOutsideEveryReservedMixinPackage() throws Exception {
        ClassLoader resources = getClass().getClassLoader();
        for (String config : new String[] { "mixins.matterblueprints.json", "mixins.matterblueprints.late.json" }) {
            try (InputStream stream = resources.getResourceAsStream(config)) {
                assertNotNull(stream, config);
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    String mixinPackage = new JsonParser().parse(reader).getAsJsonObject().get("package").getAsString();
                    assertFalse(HostedLateMixinLoader.class.getName().startsWith(mixinPackage + "."), config);
                }
            }
        }
    }
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
