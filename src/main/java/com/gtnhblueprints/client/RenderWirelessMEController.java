package com.gtnhblueprints.client;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import appeng.block.networking.BlockController;
import appeng.client.render.blocks.RenderBlockController;
import com.gtnhblueprints.wireless.BlockWirelessMEController;

/** Native controller casing and animation, with AE's purple wireless antenna as a face badge. */
@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public final class RenderWirelessMEController extends RenderBlockController {
    @Override
    public boolean renderInWorld(BlockController block, IBlockAccess world, int x, int y, int z, RenderBlocks renderer) {
        boolean rendered = super.renderInWorld(block, world, x, y, z, renderer);
        IIcon previous = renderer.overrideBlockTexture;
        if (previous != null) return rendered;
        try {
            renderer.setOverrideBlockTexture(((BlockWirelessMEController) block).wirelessBadge());
            renderer.setRenderBounds(-0.001, -0.001, -0.001, 1.001, 1.001, 1.001);
            return renderer.renderStandardBlock(block, x, y, z) || rendered;
        } finally {
            renderer.overrideBlockTexture = previous;
            renderer.setRenderBoundsFromBlock(block);
        }
    }

    @Override
    public void renderInventory(BlockController block, ItemStack stack, RenderBlocks renderer, ItemRenderType type, Object[] data) {
        super.renderInventory(block, stack, renderer, type, data);
        IIcon previous = renderer.overrideBlockTexture;
        try {
            renderer.setOverrideBlockTexture(((BlockWirelessMEController) block).wirelessBadge());
            renderer.setRenderBounds(-0.001, -0.001, -0.001, 1.001, 1.001, 1.001);
            super.renderInventory(block, stack, renderer, type, data);
        } finally {
            renderer.overrideBlockTexture = previous;
            renderer.setRenderBoundsFromBlock(block);
        }
    }
}
