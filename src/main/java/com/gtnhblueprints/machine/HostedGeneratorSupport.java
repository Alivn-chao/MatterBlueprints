package com.gtnhblueprints.machine;

import java.util.Locale;

final class HostedGeneratorSupport {

    private HostedGeneratorSupport() {}

    static boolean isSupportedClassName(String className) {
        String normalized = className == null ? "" : className.toLowerCase(Locale.ROOT);
        return normalized.contains("turbine")
            || normalized.contains("naquadahreactor")
            || normalized.contains("nqgenerator")
            || normalized.contains("combustionengine")
            || normalized.contains("dieselengine")
            || normalized.contains("chemicalfuelengine");
    }
}
