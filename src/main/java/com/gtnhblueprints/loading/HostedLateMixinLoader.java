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
        List<String> mixins = new java.util.ArrayList<>();
        if (loadedMods.contains("gregtech")) mixins.addAll(java.util.Arrays.asList("MixinAntimatterForge", "MixinGTAssemblyPorts", "MixinHatchElementInput", "MixinHatchElementOutput", "MixinHatchElementClasses", "MixinGTHostedBindingRemoval"));
        if (loadedMods.contains("gregtech") && loadedMods.contains("matter-manipulator")) {
            mixins.add("MixinMMHostedBinding");
            mixins.add("MixinMMHostedMove");
        }
        if (loadedMods.contains("appliedenergistics2")) {
            mixins.add("MixinAEWirelessControllerClass");
            mixins.add("MixinAEWirelessPathGrid");
            mixins.add("MixinAEWirelessPathCalculation");
        }
        if (loadedMods.contains("gregtech")) mixins.add("MixinHostedMachineVisuals");
        return mixins;
    }
}
