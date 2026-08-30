package com.gtnhblueprints.service;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import com.gtnhblueprints.MatterBlueprints;
import com.recursive_pineapple.matter_manipulator.common.networking.Messages;

/** Routes Matter Manipulator's native planning buttons to an active detached blueprint. */
public final class BlueprintPlanInterceptor {

    private static boolean installed;

    private BlueprintPlanInterceptor() {}

    public static synchronized void install() {
        if (installed) return;
        try {
            Field handlerField = Messages.class.getDeclaredField("handler");
            handlerField.setAccessible(true);
            Object original = handlerField.get(Messages.GetRequiredItems);
            Class<?> handlerType = handlerField.getType();
            Object proxy = Proxy.newProxyInstance(
                handlerType.getClassLoader(),
                new Class<?>[] { handlerType },
                (instance, method, args) -> invoke(original, method, args));
            handlerField.set(Messages.GetRequiredItems, proxy);
            installed = true;
            MatterBlueprints.LOG.info("GTBP blueprint planning is connected to Matter Manipulator's native planning menu");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            MatterBlueprints.LOG.error(
                "Could not connect GTBP plans to Matter Manipulator 0.1.46; /gtbp plan remains available",
                exception);
        }
    }

    private static Object invoke(Object original, Method method, Object[] args) throws Throwable {
        if ("handle".equals(method.getName()) && args != null && args.length == 2
            && args[0] instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) args[0];
            BoundBlueprint bound = BlueprintLibrary.get(player);
            if (bound != null && bound.rightClickArmed && bound.hasOrigin) {
                try {
                    BlueprintPlanService.submit(player, bound, packetFlags(args[1]));
                } catch (RuntimeException exception) {
                    MatterBlueprints.LOG.error("Could not create a blueprint uplink plan", exception);
                    player.addChatMessage(new ChatComponentText("§c[GTBP] 创建计划失败: " + exception.getMessage()));
                }
                return null;
            }
        }
        try {
            method.setAccessible(true);
            return method.invoke(original, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static int packetFlags(Object packet) throws ReflectiveOperationException {
        Field value = packet.getClass().getDeclaredField("value");
        value.setAccessible(true);
        return value.getInt(packet);
    }
}
