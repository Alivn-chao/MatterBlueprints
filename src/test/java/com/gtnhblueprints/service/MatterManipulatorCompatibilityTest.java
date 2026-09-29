package com.gtnhblueprints.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;

import net.minecraft.entity.player.EntityPlayer;

import org.junit.jupiter.api.Test;

import com.recursive_pineapple.matter_manipulator.common.building.PendingBuild;
import com.recursive_pineapple.matter_manipulator.common.networking.Messages;

/** Checks private upstream contracts that a successful compilation cannot validate. */
class MatterManipulatorCompatibilityTest {

    @Test
    void buildQueueRemainsAccessibleAsACollection() throws ReflectiveOperationException {
        Field queue = PendingBuild.class.getDeclaredField("pendingBlocks");
        queue.setAccessible(true);
        assertTrue(Collection.class.isAssignableFrom(queue.getType()));
        assertTrue(!Modifier.isStatic(queue.getModifiers()));
    }

    @Test
    void nativePlanningHandlerCanStillBeProxied() throws ReflectiveOperationException {
        Field handler = Messages.class.getDeclaredField("handler");
        handler.setAccessible(true);
        assertTrue(handler.getType().isInterface());
        assertTrue(!Modifier.isFinal(handler.getModifiers()));
        Class<?> packet = nestedClass("SimplePacket");
        assertEquals(void.class, handler.getType().getDeclaredMethod("handle", EntityPlayer.class, packet).getReturnType());
        assertEquals(Messages.class, Messages.class.getDeclaredField("GetRequiredItems").getType());
    }

    @Test
    void nativePlanningPacketRetainsIntegerFlags() throws ReflectiveOperationException {
        Field flags = nestedClass("IntPacket").getDeclaredField("value");
        flags.setAccessible(true);
        assertEquals(int.class, flags.getType());
    }

    private static Class<?> nestedClass(String name) throws ClassNotFoundException {
        return Class.forName(Messages.class.getName() + "$" + name, false, Messages.class.getClassLoader());
    }
}
