package com.gtnhblueprints.loading;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
public final class HostedLateMixinLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.matterblueprints.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return loadedMods.contains("gregtech") ? java.util.Arrays.asList("MixinAntimatterForge", "MixinGTAssemblyPorts", "MixinHatchElementInput", "MixinHatchElementOutput", "MixinHatchElementClasses")
            : Collections.emptyList();
    }
}
