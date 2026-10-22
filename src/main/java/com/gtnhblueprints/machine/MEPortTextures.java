package com.gtnhblueprints.machine;

import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;
import gregtech.client.iconContainers.blocks.GTCustomBlockIconContainer;

/** Original GTO/GTCEu overlays, registered before the terrain atlas is stitched. */
public final class MEPortTextures {
    private static final ITexture FRAME = texture("overlay_color");
    private static final ITexture ITEM_INPUT = texture("me_input_bus");
    private static final ITexture FLUID_INPUT = texture("me_input_hatch");
    private static final ITexture INPUT_ASSEMBLY = texture("me_buffer_hatch");
    private static final ITexture OUTPUT_ASSEMBLY = texture("me_dual_output");
    private MEPortTextures() {}
    public static void init() {}
    private static ITexture texture(String name) {
        return TextureFactory.of(GTCustomBlockIconContainer.create("matterblueprints", "ports/" + name));
    }
    static ITexture[] input(ITexture base, boolean items, boolean fluids) {
        return new ITexture[] { base, FRAME, items && fluids ? INPUT_ASSEMBLY : items ? ITEM_INPUT : FLUID_INPUT };
    }
    static ITexture[] output(ITexture base) { return new ITexture[] { base, FRAME, OUTPUT_ASSEMBLY }; }
}
