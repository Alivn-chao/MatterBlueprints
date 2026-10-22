package com.gtnhblueprints.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import org.joml.Vector3f;

import com.gtnhblueprints.BlueprintConfig;
import com.gtnhblueprints.service.CrossDimensionMovePlan;
import com.recursive_pineapple.matter_manipulator.client.rendering.BoxRenderer;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PlaceMode;
import com.recursive_pineapple.matter_manipulator.common.utils.MMUtils;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Bounds only: the client cannot inspect blocks in an unloaded remote dimension. */
public final class ClientCrossDimensionMovePreview {

    public static final ClientCrossDimensionMovePreview INSTANCE = new ClientCrossDimensionMovePreview();
    private static final Vector3f COLOUR = new Vector3f(0.15f, 0.85f, 1.0f);

    private ClientCrossDimensionMovePreview() {}

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null) return;
        ItemStack held = player.getCurrentEquippedItem();
        if (held == null || !(held.getItem() instanceof ItemMatterManipulator)) return;
        MMState state = ItemMatterManipulator.getState(held);
        if (state.config.placeMode != PlaceMode.MOVING || state.config.coordA == null || state.config.coordB == null) return;
        Location destination = state.config.getCoordC(player.worldObj, MMUtils.getLookingAtLocation(player));
        if (destination == null || destination.worldId != player.worldObj.provider.dimensionId
            || state.config.coordA.worldId == destination.worldId) return;
        CrossDimensionMovePlan plan;
        try {
            plan = new CrossDimensionMovePlan(state.config.coordA, state.config.coordB, destination,
                BlueprintConfig.maxSpan, BlueprintConfig.maxBlocks);
        } catch (IllegalArgumentException exception) {
            return;
        }
        AxisAlignedBB bounds = AxisAlignedBB.getBoundingBox(plan.minX + plan.offsetX, plan.minY + plan.offsetY,
            plan.minZ + plan.offsetZ, plan.maxX + plan.offsetX + 1, plan.maxY + plan.offsetY + 1, plan.maxZ + plan.offsetZ + 1);
        BoxRenderer.INSTANCE.start(event.partialTicks);
        try {
            BoxRenderer.INSTANCE.drawAround(bounds, COLOUR);
        } finally {
            BoxRenderer.INSTANCE.finish();
        }
    }

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (!(event.itemStack.getItem() instanceof ItemMatterManipulator)) return;
        MMState state = ItemMatterManipulator.getState(event.itemStack);
        if (state.config.placeMode != PlaceMode.MOVING || state.config.coordA == null || state.config.coordC == null
            || state.config.coordA.worldId == state.config.coordC.worldId) return;
        event.toolTip.add("§b" + StatCollector.translateToLocalFormatted("matterblueprints.move.dimensions",
            state.config.coordA.worldId, state.config.coordC.worldId));
        event.toolTip.add("§7" + StatCollector.translateToLocal("matterblueprints.move.keep_loaded"));
    }
}
