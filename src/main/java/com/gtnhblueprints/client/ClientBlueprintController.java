package com.gtnhblueprints.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.gtnhblueprints.BlueprintConfig;
import com.gtnhblueprints.MatterBlueprints;
import com.gtnhblueprints.io.BlueprintIO;
import com.gtnhblueprints.model.Blueprint;
import com.gtnhblueprints.model.PendingBlockTraits;
import com.gtnhblueprints.network.BlueprintInteractionStatusMessage;
import com.gtnhblueprints.network.BlueprintNetwork;
import com.gtnhblueprints.registry.ModBlocks;
import com.gtnhblueprints.service.BoundBlueprint;
import com.recursive_pineapple.matter_manipulator.client.rendering.BoxRenderer;
import com.recursive_pineapple.matter_manipulator.common.building.PendingBlock;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Location;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PendingAction;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.MMState.PlaceMode;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.RenderHints;
import com.recursive_pineapple.matter_manipulator.common.items.manipulator.Transform;
import com.recursive_pineapple.matter_manipulator.common.utils.MMUtils;

import org.joml.Vector3i;
import org.joml.Vector3f;
import org.lwjgl.input.Keyboard;

import cpw.mods.fml.common.eventhandler.Event.Result;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientBlueprintController {

    public static final ClientBlueprintController INSTANCE = new ClientBlueprintController();
    private static final short[] NORMAL_TINT = {
        255, 255, 255, 255
    };
    private static final short[] ORIGIN_TINT = {
        255, 170, 0, 255
    };
    private static final Vector3f SELECTION_COLOUR = new Vector3f(0.75f, 0.5f, 0.15f);

    private BoundBlueprint preview;
    private boolean locked;
    private boolean serverOriginConfirmed;
    private boolean awaitingServer;
    private boolean needsRebuild;
    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;
    private int lastZ = Integer.MIN_VALUE;
    private int forcedRefreshes;
    private AxisAlignedBB projectionBounds;
    private String lastManipulatorSignature = "";
    private String lastRenderTransformSignature = "";
    private List<PendingBlock> rawPreviewBlocks = Collections.emptyList();
    private List<RenderEntry> transformedPreviewBlocks = Collections.emptyList();
    private Vector3i transientArraySpan;
    private boolean dynamicPreview;
    private long lastProjectionBuildMs;
    private long suppressAirRightClickUntil;
    private boolean cancelRequested;

    private ClientBlueprintController() {}

    public void activate(String name, byte[] bytes) {
        try {
            Blueprint blueprint = BlueprintIO.decode(bytes);
            blueprint.name = name;
            preview = new BoundBlueprint(blueprint);
            preview.worldId = clientPlayer().worldObj.provider.dimensionId;
            preview.hasOrigin = true;
            locked = false;
            serverOriginConfirmed = false;
            awaitingServer = false;
            cancelRequested = false;
            needsRebuild = true;
            forcedRefreshes = 2;
            lastX = Integer.MIN_VALUE;
            rawPreviewBlocks = nonAirBlocks(blueprint.instantiate(preview.worldId));
            transformedPreviewBlocks = Collections.emptyList();
            lastRenderTransformSignature = "";
            switchHeldManipulatorToPaste();
            if (blueprint.blocks.size() > BlueprintConfig.maxPreviewBlocks) {
                chat(
                    "§e[GTBP] 蓝图较大，投影最多显示 " + BlueprintConfig.maxPreviewBlocks
                        + " 个非空气方块；实际搭建仍使用完整蓝图");
            }
        } catch (IOException | RuntimeException exception) {
            clearPreview();
            MatterBlueprints.LOG.error("Could not activate client blueprint projection", exception);
            chat("§c[GTBP] 无法显示蓝图投影: " + exception.getMessage());
        }
    }

    public void status(
        byte kind,
        int originX,
        int originY,
        int originZ,
        int quarterTurns,
        boolean mirrorX,
        boolean mirrorZ,
        String text
    ) {
        if (kind == BlueprintInteractionStatusMessage.BUILD_STARTED
            || kind == BlueprintInteractionStatusMessage.PREVIEW_CLEARED) {
            clearPreview();
            if (!text.isEmpty()) chat(text);
            return;
        }
        if (kind == BlueprintInteractionStatusMessage.ERROR) {
            awaitingServer = false;
            if (!serverOriginConfirmed) locked = false;
            needsRebuild = true;
            if (!text.isEmpty()) chat(text);
            return;
        }
        if (preview == null) return;

        preview.quarterTurns = Math.floorMod(quarterTurns, 4);
        preview.mirrorX = mirrorX;
        preview.mirrorZ = mirrorZ;
        lastRenderTransformSignature = "";
        if (kind == BlueprintInteractionStatusMessage.ORIGIN_CONFIRMED) {
            preview.originX = originX;
            preview.originY = originY;
            preview.originZ = originZ;
            locked = true;
            serverOriginConfirmed = true;
            setHeldManipulatorOrigin(originX, originY, originZ);
        }
        awaitingServer = false;
        needsRebuild = true;
        if (!text.isEmpty()) chat(text);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent event) {
        if (preview == null || event.entityPlayer != clientPlayer()) return;
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
            && event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        if (isControlDown()) {
            cancelInteraction(event);
            requestCancel();
            return;
        }
        long now = System.currentTimeMillis();
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR && now <= suppressAirRightClickUntil) {
            cancelInteraction(event);
            return;
        }
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
            && event.entityPlayer.worldObj.getBlock(event.x, event.y, event.z) == ModBlocks.BLUEPRINT_CONFIGURATOR) {
            suppressAirRightClickUntil = now + 250L;
            cancelInteraction(event);
            BlueprintNetwork.openConfigurator(event.x, event.y, event.z);
            return;
        }
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
            && event.entityPlayer.worldObj.getTileEntity(event.x, event.y, event.z) != null) {
            suppressAirRightClickUntil = now + 250L;
            return;
        }
        if (!isHoldingCopyManipulator(event.entityPlayer)) return;

        MMState state = ItemMatterManipulator.getState(event.entityPlayer.getCurrentEquippedItem());
        if (locked && state.config.action == PendingAction.MARK_ARRAY) {
            event.setCanceled(true);
            event.useBlock = Result.DENY;
            event.useItem = Result.DENY;
            markArrayLocally(event.entityPlayer, state);
            BlueprintNetwork.markArray();
            return;
        }
        if (locked
            && (state.config.action == PendingAction.MOVING_COORDS
                || state.config.action == PendingAction.MARK_PASTE)) return;

        if (locked && !event.entityPlayer.isSneaking()) return;

        event.setCanceled(true);
        event.useBlock = Result.DENY;
        event.useItem = Result.DENY;
        if (awaitingServer) return;
        if (!locked) {
            Vector3i lookingAt = MMUtils.getLookingAtLocation(event.entityPlayer);
            setOrigin(lookingAt.x, lookingAt.y, lookingAt.z);
            locked = true;
            needsRebuild = true;
        }
        awaitingServer = true;
        BlueprintNetwork.rightClick();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        EntityPlayer player = clientPlayer();
        if (preview == null || player == null || !isHoldingCopyManipulator(player)) return;
        long now = System.currentTimeMillis();
        boolean mayRebuild = !dynamicPreview
            || forcedRefreshes > 0
            || now - lastProjectionBuildMs >= BlueprintConfig.previewUpdateIntervalMs;
        if (needsRebuild && mayRebuild) {
            rebuildProjection(player);
            lastProjectionBuildMs = now;
            if (forcedRefreshes > 0) forcedRefreshes--;
        }
        if (projectionBounds == null) return;
        BoxRenderer.INSTANCE.start(event.partialTicks);
        try {
            BoxRenderer.INSTANCE.drawAround(projectionBounds, SELECTION_COLOUR);
        } finally {
            BoxRenderer.INSTANCE.finish();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || preview == null) return;
        EntityPlayer player = clientPlayer();
        if (player == null || player.worldObj == null) {
            clearPreview();
            return;
        }
        if (!isHoldingCopyManipulator(player)) {
            RenderHints.INSTANCE.reset();
            needsRebuild = true;
            return;
        }
        ItemStack held = player.getCurrentEquippedItem();
        MMState state = ItemMatterManipulator.getState(held);
        if (!isBlueprintPlacementState(state)) {
            requestCancel();
            return;
        }
        Vector3i nextTransientArray = state.config.action == PendingAction.MARK_ARRAY
            ? calculateArraySpan(player, state)
            : null;
        if (!Objects.equals(transientArraySpan, nextTransientArray)) {
            transientArraySpan = nextTransientArray;
            needsRebuild = true;
        }
        dynamicPreview = !locked
            || state.config.action == PendingAction.MARK_ARRAY
            || state.config.action == PendingAction.MOVING_COORDS
            || state.config.action == PendingAction.MARK_PASTE
            || state.config.coordCOffset != null;
        String signature = manipulatorSignature(state);
        if (!signature.equals(lastManipulatorSignature)) {
            lastManipulatorSignature = signature;
            needsRebuild = true;
        }
        if (!locked) {
            Vector3i lookingAt = MMUtils.getLookingAtLocation(player);
            if (lookingAt.x != lastX || lookingAt.y != lastY || lookingAt.z != lastZ) {
                setOrigin(lookingAt.x, lookingAt.y, lookingAt.z);
                needsRebuild = true;
            }
        } else {
            Location destination = state.config.getCoordC(player.worldObj, MMUtils.getLookingAtLocation(player));
            if (destination != null
                && destination.isInWorld(player.worldObj)
                && (destination.x != lastX || destination.y != lastY || destination.z != lastZ)) {
                setOrigin(destination.x, destination.y, destination.z);
                needsRebuild = true;
            }
        }
        if (forcedRefreshes > 0) needsRebuild = true;
    }

    private void rebuildProjection(EntityPlayer player) {
        try {
            preview.worldId = player.worldObj.provider.dimensionId;
            preview.hasOrigin = true;
            MMState state = ItemMatterManipulator.getState(player.getCurrentEquippedItem());
            ensureTransformedPreview(state.config.transform);
            Vector3i arraySpan = transientArraySpan == null ? state.config.arraySpan : transientArraySpan;
            List<Vector3i> offsets = arrayOffsets(arraySpan, state.config.transform);
            RenderHints.INSTANCE.start();
            RenderHints.INSTANCE.setDepthTest(true);
            int shown = 0;
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (Vector3i offset : offsets) {
                for (RenderEntry entry : transformedPreviewBlocks) {
                    if (shown >= BlueprintConfig.maxPreviewBlocks) break;
                    int x = preview.originX + offset.x + entry.x;
                    int y = preview.originY + offset.y + entry.y;
                    int z = preview.originZ + offset.z + entry.z;
                    short[] tint = offset.x == 0 && offset.y == 0 && offset.z == 0
                        && entry.x == 0 && entry.y == 0 && entry.z == 0
                            ? ORIGIN_TINT
                            : entry.tint;
                    RenderHints.INSTANCE.addHint(x, y, z, entry.block, entry.metadata, tint);
                    shown++;
                }
            }

            Vector3i deltas = preview.sourceDeltas();
            for (Vector3i offset : boundaryOffsets(arraySpan, state.config.transform)) {
                for (int mask = 0; mask < 8; mask++) {
                    Vector3i corner = new Vector3i(
                        (mask & 1) == 0 ? 0 : deltas.x,
                        (mask & 2) == 0 ? 0 : deltas.y,
                        (mask & 4) == 0 ? 0 : deltas.z);
                    applyTransforms(corner, state.config.transform);
                    int x = preview.originX + offset.x + corner.x;
                    int y = preview.originY + offset.y + corner.y;
                    int z = preview.originZ + offset.z + corner.z;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                }
            }
            RenderHints.INSTANCE.finish();
            projectionBounds = minX == Integer.MAX_VALUE
                ? null
                : AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
            needsRebuild = false;
        } catch (RuntimeException exception) {
            MatterBlueprints.LOG.error("Could not rebuild blueprint projection", exception);
            clearPreview();
            chat("§c[GTBP] 蓝图投影失败: " + exception.getMessage());
        }
    }

    private void switchHeldManipulatorToPaste() {
        EntityPlayer player = clientPlayer();
        if (player == null) return;
        ItemStack stack = player.getCurrentEquippedItem();
        if (stack == null || !(stack.getItem() instanceof ItemMatterManipulator)) return;
        MMState state = ItemMatterManipulator.getState(stack).clone();
        state.config.placeMode = PlaceMode.COPYING;
        state.config.coordA = null;
        state.config.coordB = null;
        state.config.coordC = null;
        state.config.coordAOffset = null;
        state.config.coordBOffset = null;
        state.config.coordCOffset = null;
        state.config.action = PendingAction.MARK_PASTE;
        state.config.transform = new com.recursive_pineapple.matter_manipulator.common.items.manipulator.Transform();
        state.config.arraySpan = null;
        ItemMatterManipulator.setState(stack, state);
    }

    private static void setHeldManipulatorOrigin(int x, int y, int z) {
        EntityPlayer player = clientPlayer();
        if (player == null) return;
        ItemStack stack = player.getCurrentEquippedItem();
        if (stack == null || !(stack.getItem() instanceof ItemMatterManipulator)) return;
        MMState state = ItemMatterManipulator.getState(stack).clone();
        state.config.action = null;
        state.config.coordC = new Location(player.worldObj, x, y, z);
        state.config.coordCOffset = null;
        ItemMatterManipulator.setState(stack, state);
    }

    private void markArrayLocally(EntityPlayer player, MMState original) {
        MMState state = original.clone();
        Location destination = new Location(player.worldObj, preview.originX, preview.originY, preview.originZ);
        state.config.arraySpan = calculateArraySpan(player, state);
        state.config.action = null;
        state.config.coordC = destination;
        ItemMatterManipulator.setState(player.getCurrentEquippedItem(), state);
        needsRebuild = true;
    }

    private Vector3i calculateArraySpan(EntityPlayer player, MMState state) {
        Vector3i deltas = preview.sourceDeltas();
        Location sourceA = new Location(player.worldObj, 0, 0, 0);
        Location sourceB = new Location(player.worldObj, deltas.x, deltas.y, deltas.z);
        Location destination = new Location(player.worldObj, preview.originX, preview.originY, preview.originZ);
        return state.config.getArrayMult(
            player.worldObj,
            sourceA,
            sourceB,
            destination,
            MMUtils.getLookingAtLocation(player));
    }

    private void ensureTransformedPreview(Transform manipulatorTransform) {
        String signature = preview.quarterTurns + ":" + preview.mirrorX + ':' + preview.mirrorZ + ':'
            + (manipulatorTransform == null ? "null" : manipulatorTransform.toString());
        if (signature.equals(lastRenderTransformSignature)) return;
        List<RenderEntry> entries = new ArrayList<>(rawPreviewBlocks.size());
        Transform commandTransform = commandTransform();
        for (PendingBlock raw : rawPreviewBlocks) {
            PendingBlock pending = PendingBlockTraits.copy(raw);
            Vector3i position = commandTransform.apply(new Vector3i(pending.x, pending.y, pending.z));
            PendingBlockTraits.transform(pending, commandTransform);
            if (manipulatorTransform != null) {
                manipulatorTransform.apply(position);
                PendingBlockTraits.transform(pending, manipulatorTransform);
            }
            Block block = pending.getPreviewBlock();
            int metadata;
            if (block == null) {
                block = pending.getBlock();
                metadata = pending.spec.getBlockMeta();
            } else {
                metadata = pending.getPreviewMeta();
            }
            if (block != null) {
                int colour = block.getRenderColor(metadata);
                short[] tint = colour == 0xFFFFFF
                    ? NORMAL_TINT
                    : new short[] {
                        (short) ((colour >> 16) & 0xFF),
                        (short) ((colour >> 8) & 0xFF),
                        (short) (colour & 0xFF),
                        255
                    };
                entries.add(new RenderEntry(position.x, position.y, position.z, block, metadata, tint));
            }
        }
        transformedPreviewBlocks = entries;
        lastRenderTransformSignature = signature;
    }

    private List<Vector3i> arrayOffsets(Vector3i arraySpan, final Transform manipulatorTransform) {
        if (arraySpan == null) return Collections.singletonList(new Vector3i());
        final List<Vector3i> offsets = new ArrayList<>();
        offsets.add(new Vector3i());
        Vector3i deltas = preview.sourceDeltas();
        int stepX = deltas.x + (deltas.x < 0 ? -1 : 1);
        int stepY = deltas.y + (deltas.y < 0 ? -1 : 1);
        int stepZ = deltas.z + (deltas.z < 0 ? -1 : 1);
        int perCopy = Math.max(1, transformedPreviewBlocks.size());
        int maxCopies = Math.max(1, (BlueprintConfig.maxPreviewBlocks + perCopy - 1) / perCopy);
        outer:
        for (int ay = Math.min(arraySpan.y, 0); ay <= Math.max(arraySpan.y, 0); ay++) {
            for (int az = Math.min(arraySpan.z, 0); az <= Math.max(arraySpan.z, 0); az++) {
                for (int ax = Math.min(arraySpan.x, 0); ax <= Math.max(arraySpan.x, 0); ax++) {
                    if (ax == 0 && ay == 0 && az == 0) continue;
                    Vector3i offset = new Vector3i(ax * stepX, ay * stepY, az * stepZ);
                    applyTransforms(offset, manipulatorTransform);
                    offsets.add(offset);
                    if (offsets.size() >= maxCopies) break outer;
                }
            }
        }
        return offsets;
    }

    private List<Vector3i> boundaryOffsets(Vector3i arraySpan, Transform manipulatorTransform) {
        if (arraySpan == null) return Collections.singletonList(new Vector3i());
        Vector3i deltas = preview.sourceDeltas();
        int stepX = deltas.x + (deltas.x < 0 ? -1 : 1);
        int stepY = deltas.y + (deltas.y < 0 ? -1 : 1);
        int stepZ = deltas.z + (deltas.z < 0 ? -1 : 1);
        int[] xs = {
            Math.min(arraySpan.x, 0), Math.max(arraySpan.x, 0)
        };
        int[] ys = {
            Math.min(arraySpan.y, 0), Math.max(arraySpan.y, 0)
        };
        int[] zs = {
            Math.min(arraySpan.z, 0), Math.max(arraySpan.z, 0)
        };
        List<Vector3i> result = new ArrayList<>(8);
        for (int ay : ys) {
            for (int az : zs) {
                for (int ax : xs) {
                    Vector3i offset = new Vector3i(ax * stepX, ay * stepY, az * stepZ);
                    applyTransforms(offset, manipulatorTransform);
                    result.add(offset);
                }
            }
        }
        return result;
    }

    private void applyTransforms(Vector3i value, Transform manipulatorTransform) {
        commandTransform().apply(value);
        if (manipulatorTransform != null) manipulatorTransform.apply(value);
    }

    private Transform commandTransform() {
        Transform transform = new Transform();
        transform.flipX = preview.mirrorX;
        transform.flipZ = preview.mirrorZ;
        transform.rotate(ForgeDirection.UP, preview.quarterTurns);
        return transform;
    }

    private static List<PendingBlock> nonAirBlocks(List<PendingBlock> blocks) {
        List<PendingBlock> result = new ArrayList<>();
        for (PendingBlock block : blocks) {
            if (block != null && block.spec != null && !block.spec.isAir()) result.add(block);
        }
        return result;
    }

    private static String manipulatorSignature(MMState state) {
        String transform = state.config.transform == null ? "null" : state.config.transform.toString();
        String array = state.config.arraySpan == null ? "null" : state.config.arraySpan.toString();
        String coord = state.config.coordC == null ? "null" : state.config.coordC.toString();
        return transform + '|' + array + '|' + coord + '|' + state.config.action;
    }

    private void setOrigin(int x, int y, int z) {
        preview.originX = x;
        preview.originY = y;
        preview.originZ = z;
        lastX = x;
        lastY = y;
        lastZ = z;
    }

    private void clearPreview() {
        preview = null;
        locked = false;
        serverOriginConfirmed = false;
        awaitingServer = false;
        needsRebuild = false;
        forcedRefreshes = 0;
        projectionBounds = null;
        lastManipulatorSignature = "";
        lastRenderTransformSignature = "";
        rawPreviewBlocks = Collections.emptyList();
        transformedPreviewBlocks = Collections.emptyList();
        transientArraySpan = null;
        dynamicPreview = false;
        lastProjectionBuildMs = 0;
        suppressAirRightClickUntil = 0;
        cancelRequested = false;
        RenderHints.INSTANCE.reset();
    }

    private void requestCancel() {
        if (cancelRequested) return;
        cancelRequested = true;
        BlueprintNetwork.cancelBlueprint();
    }

    private boolean isBlueprintPlacementState(MMState state) {
        if (state.config.placeMode != PlaceMode.COPYING) return false;
        PendingAction action = state.config.action;
        return action == null
            || action == PendingAction.MOVING_COORDS
            || action == PendingAction.MARK_ARRAY
            || action == PendingAction.MARK_PASTE && (!locked || awaitingServer);
    }

    private static boolean isControlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private static void cancelInteraction(PlayerInteractEvent event) {
        event.setCanceled(true);
        event.useBlock = Result.DENY;
        event.useItem = Result.DENY;
    }

    private static boolean isHoldingCopyManipulator(EntityPlayer player) {
        ItemStack stack = player.getCurrentEquippedItem();
        if (stack == null || !(stack.getItem() instanceof ItemMatterManipulator)) return false;
        ItemMatterManipulator manipulator = (ItemMatterManipulator) stack.getItem();
        return (manipulator.tier.capabilities & ItemMatterManipulator.ALLOW_COPYING) != 0;
    }

    private static EntityPlayer clientPlayer() {
        return Minecraft.getMinecraft().thePlayer;
    }

    private static void chat(String text) {
        EntityPlayer player = clientPlayer();
        if (player != null) player.addChatMessage(new ChatComponentText(text));
    }

    private static final class RenderEntry {

        private final int x;
        private final int y;
        private final int z;
        private final Block block;
        private final int metadata;
        private final short[] tint;

        private RenderEntry(int x, int y, int z, Block block, int metadata, short[] tint) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.metadata = metadata;
            this.tint = tint;
        }
    }
}
