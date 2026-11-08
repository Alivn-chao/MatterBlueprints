package com.gtnhblueprints.wireless;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import com.cleanroommc.modularui.api.value.sync.IServerMouseAction;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class WirelessMEGuiRegressionTest {
    @Test void clientClickDoesNotTouchServerDataAndTransmittedClickRunsOnce() throws Exception {
        AtomicInteger actions = new AtomicInteger();
        InteractionSyncHandler handler = WirelessMEGui.actionHandler(null, actions::incrementAndGet);
        Field pressed = InteractionSyncHandler.class.getDeclaredField("mousePressed");
        pressed.setAccessible(true);
        IServerMouseAction callback = (IServerMouseAction) pressed.get(handler);
        MouseData click = new MouseData(Side.CLIENT, 0, false, false, false);
        // This is MUI's local callback, which used to access WirelessMEData and crash the client.
        callback.onServerMouseAction(click);
        assertEquals(0, actions.get());
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            click.writeToPacket(packet);
            handler.readOnServer(1, packet);
            assertEquals(1, actions.get());
        } finally { packet.release(); }
        callback.onServerMouseAction(new MouseData(Side.SERVER, 1, false, false, false));
        assertEquals(1, actions.get(), "right clicks must not create or change channels");
    }

    @Test void controllerUsesGuiDataPlayerBeforeContainerExists() throws Exception {
        ClassNode node = read("com/gtnhblueprints/wireless/TileWirelessMEController");
        boolean suppliedPlayer = node.methods.stream().filter(m -> m.name.equals("buildUI"))
            .flatMap(m -> java.util.Arrays.stream(m.instructions.toArray()))
            .filter(i -> i instanceof MethodInsnNode).map(i -> (MethodInsnNode) i)
            .anyMatch(i -> i.owner.equals("com/cleanroommc/modularui/factory/PosGuiData") && i.name.equals("getPlayer"));
        assertTrue(suppliedPlayer);
    }

    @Test void controllerHasItemRenderer() throws Exception {
        ClassNode client = read("com/gtnhblueprints/ClientProxy");
        assertTrue(client.methods.stream().filter(m -> m.name.equals("init"))
            .flatMap(m -> java.util.Arrays.stream(m.instructions.toArray()))
            .filter(i -> i instanceof MethodInsnNode).map(i -> (MethodInsnNode) i)
            .anyMatch(i -> i.owner.equals("net/minecraftforge/client/MinecraftForgeClient") && i.name.equals("registerItemRenderer")));
    }

    private ClassNode read(String name) throws Exception {
        try (java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
