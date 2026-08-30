package com.gtnhblueprints.network;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import com.gtnhblueprints.MatterBlueprints;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

public final class BlueprintNetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(MatterBlueprints.MODID);
    public static final ServerQueue SERVER_QUEUE = new ServerQueue();
    private static final Queue<Runnable> SERVER_TASKS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger NEXT_TRANSFER_ID = new AtomicInteger();
    private static boolean initialized;

    private BlueprintNetwork() {}

    public static void initializeServer() {
        if (initialized) return;
        initialized = true;
        CHANNEL.registerMessage(BlueprintUploadMessage.Handler.class, BlueprintUploadMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(BlueprintDownloadMessage.Handler.class, BlueprintDownloadMessage.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(BlueprintActivateMessage.Handler.class, BlueprintActivateMessage.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(BlueprintRightClickMessage.Handler.class, BlueprintRightClickMessage.class, 3, Side.SERVER);
        CHANNEL.registerMessage(
            BlueprintInteractionStatusMessage.Handler.class,
            BlueprintInteractionStatusMessage.class,
            4,
            Side.CLIENT);
        CHANNEL.registerMessage(ConfiguratorRequestMessage.Handler.class, ConfiguratorRequestMessage.class, 5, Side.SERVER);
        CHANNEL.registerMessage(ServerBlueprintListMessage.Handler.class, ServerBlueprintListMessage.class, 6, Side.CLIENT);
        CHANNEL.registerMessage(OpenConfiguratorMessage.Handler.class, OpenConfiguratorMessage.class, 7, Side.SERVER);
        CHANNEL.registerMessage(
            BlueprintUploadChunkMessage.Handler.class,
            BlueprintUploadChunkMessage.class,
            8,
            Side.SERVER);
        CHANNEL.registerMessage(
            BlueprintClientChunkMessage.Handler.class,
            BlueprintClientChunkMessage.class,
            9,
            Side.CLIENT);
    }

    public static void initializeClient() {
        initializeServer();
    }

    public static void upload(String name, byte[] bytes) {
        sendUploadChunks(name, bytes);
    }

    public static void download(EntityPlayerMP player, String name, byte[] bytes) {
        sendClientChunks(player, BlueprintClientChunkMessage.DOWNLOAD, name, bytes);
    }

    public static void activate(EntityPlayerMP player, String name, byte[] bytes) {
        sendClientChunks(player, BlueprintClientChunkMessage.ACTIVATE, name, bytes);
    }

    private static void sendUploadChunks(String name, byte[] bytes) {
        validateTransfer(bytes);
        int transferId = NEXT_TRANSFER_ID.incrementAndGet();
        int count = chunkCount(bytes.length);
        for (int index = 0; index < count; index++) {
            CHANNEL.sendToServer(
                new BlueprintUploadChunkMessage(
                    transferId,
                    name,
                    bytes.length,
                    count,
                    index,
                    chunk(bytes, index)));
        }
    }

    private static void sendClientChunks(EntityPlayerMP player, byte kind, String name, byte[] bytes) {
        validateTransfer(bytes);
        int transferId = NEXT_TRANSFER_ID.incrementAndGet();
        int count = chunkCount(bytes.length);
        for (int index = 0; index < count; index++) {
            CHANNEL.sendTo(
                new BlueprintClientChunkMessage(
                    transferId,
                    kind,
                    name,
                    bytes.length,
                    count,
                    index,
                    chunk(bytes, index)),
                player);
        }
    }

    private static int chunkCount(int length) {
        return (length + BlueprintChunkMessage.CHUNK_BYTES - 1) / BlueprintChunkMessage.CHUNK_BYTES;
    }

    private static byte[] chunk(byte[] bytes, int index) {
        int start = index * BlueprintChunkMessage.CHUNK_BYTES;
        int end = Math.min(bytes.length, start + BlueprintChunkMessage.CHUNK_BYTES);
        return Arrays.copyOfRange(bytes, start, end);
    }

    private static void validateTransfer(byte[] bytes) {
        if (bytes == null || bytes.length < 1 || bytes.length > com.gtnhblueprints.BlueprintConfig.maxTransferBytes) {
            throw new IllegalArgumentException("Invalid blueprint payload");
        }
    }

    public static void rightClick() {
        CHANNEL.sendToServer(new BlueprintRightClickMessage(BlueprintRightClickMessage.INTERACT));
    }

    public static void markArray() {
        CHANNEL.sendToServer(new BlueprintRightClickMessage(BlueprintRightClickMessage.MARK_ARRAY));
    }

    public static void cancelBlueprint() {
        CHANNEL.sendToServer(new BlueprintRightClickMessage(BlueprintRightClickMessage.CANCEL));
    }

    public static void status(EntityPlayerMP player, BlueprintInteractionStatusMessage message) {
        CHANNEL.sendTo(message, player);
    }

    public static void requestServerBlueprints() {
        CHANNEL.sendToServer(new ConfiguratorRequestMessage(ConfiguratorRequestMessage.LIST, ""));
    }

    public static void requestDownload(String name) {
        CHANNEL.sendToServer(new ConfiguratorRequestMessage(ConfiguratorRequestMessage.DOWNLOAD, name));
    }

    public static void openConfigurator(int x, int y, int z) {
        CHANNEL.sendToServer(new OpenConfiguratorMessage(x, y, z));
    }

    public static void serverBlueprintList(EntityPlayerMP player, List<String> names) {
        CHANNEL.sendTo(new ServerBlueprintListMessage(names), player);
    }

    public static void enqueueServer(Runnable task) {
        SERVER_TASKS.add(task);
    }

    public static final class ServerQueue {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            Runnable task;
            int processed = 0;
            while (processed++ < 16 && (task = SERVER_TASKS.poll()) != null) {
                try {
                    task.run();
                } catch (RuntimeException exception) {
                    MatterBlueprints.LOG.error("Queued blueprint network task failed", exception);
                }
            }
        }
    }
}
