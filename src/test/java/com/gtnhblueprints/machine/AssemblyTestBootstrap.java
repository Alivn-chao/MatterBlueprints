package com.gtnhblueprints.machine;

/** Minimal registries for native GT recipe tests outside LaunchWrapper; no mod/world lifecycle is simulated. */
final class AssemblyTestBootstrap {
    private static boolean initialized;

    static synchronized void initialize() throws Exception {
        if (initialized) return;
        java.lang.reflect.Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) singleton.get(null);
        java.lang.reflect.Field loader = cpw.mods.fml.common.Loader.class.getDeclaredField("instance");
        loader.setAccessible(true);
        if (loader.get(null) == null) loader.set(null, unsafe.allocateInstance(cpw.mods.fml.common.Loader.class));
        net.minecraft.init.Bootstrap.func_151354_b();
        initialized = true;
    }
}
