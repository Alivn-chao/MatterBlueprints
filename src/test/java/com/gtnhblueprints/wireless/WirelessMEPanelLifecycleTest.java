package com.gtnhblueprints.wireless;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import com.cleanroommc.modularui.value.sync.DynamicLinkedSyncHandler;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.WidgetTree;
import org.junit.jupiter.api.Test;

class WirelessMEPanelLifecycleTest {
    @Test void oldPerChannelRegistrationMutatesTheMapWhileMuiIsIterating() throws Exception {
        PanelSyncManager panel = new PanelSyncManager(new ModularSyncManager(false), true);
        String[] snapshot = { "[]" };
        StringSyncValue channels = new StringSyncValue(() -> snapshot[0]) {
            @Override public void detectAndSendChanges(boolean init) { updateCacheFromSource(init); }
        };
        DynamicLinkedSyncHandler<StringSyncValue> oldRows = new DynamicLinkedSyncHandler<>(channels)
            .widgetProvider((manager, value) -> {
                com.cleanroommc.modularui.widget.ParentWidget<?> parent = new com.cleanroommc.modularui.widget.ParentWidget<>();
                com.google.gson.JsonArray array = new com.google.gson.JsonParser().parse(value.getStringValue()).getAsJsonArray();
                for (com.google.gson.JsonElement row : array) {
                    String id = row.getAsJsonObject().get("id").getAsString();
                    for (int action = 0; action < 3; action++) {
                        parent.child(new com.cleanroommc.modularui.widgets.ButtonWidget<>().syncHandler(
                            manager.getOrCreateSyncHandler("oldAction:" + action + ":" + id, 0,
                                com.cleanroommc.modularui.value.sync.InteractionSyncHandler.class,
                                com.cleanroommc.modularui.value.sync.InteractionSyncHandler::new)));
                    }
                }
                return parent;
            });
        panel.syncValue("channels", channels);
        panel.syncValue("rows", oldRows);
        panel.initialize("oldWirelessLifecycle");
        snapshot[0] = snapshot(32);
        Method tick = PanelSyncManager.class.getDeclaredMethod("detectAndSendChanges", boolean.class);
        tick.setAccessible(true);
        try {
            tick.invoke(panel, false);
        } catch (java.lang.reflect.InvocationTargetException failure) {
            assertInstanceOf(NullPointerException.class, failure.getCause());
            assertEquals("com.cleanroommc.modularui.value.sync.PanelSyncManager", failure.getCause().getStackTrace()[0].getClassName());
        }
        // The null slot encountered depends on the random UUID hashes. The unsafe registry mutation is unconditional.
        Field registry = PanelSyncManager.class.getDeclaredField("syncHandlers");
        registry.setAccessible(true);
        assertTrue(((Map<?, ?>) registry.get(panel)).size() > 2);

    }

    @Test void openingWithExistingChannelsAndRefreshingDuringServerTicksDoesNotMutateRegistry() throws Exception {
        // Exercise the actual MUI version used by beta3, including its real handler-map iterator.
        Method tick = PanelSyncManager.class.getDeclaredMethod("detectAndSendChanges", boolean.class);
        tick.setAccessible(true);
        Field registry = PanelSyncManager.class.getDeclaredField("syncHandlers");
        registry.setAccessible(true);
        for (int open = 0; open < 10; open++) {
            PanelSyncManager panel = new PanelSyncManager(new ModularSyncManager(false), true);
            String[] snapshot = { snapshot(32) };
            StringSyncValue channels = new StringSyncValue(() -> snapshot[0]) {
                @Override public void detectAndSendChanges(boolean init) {
                    // Keep the real value change listener and dynamic rebuild; omit outbound transport.
                    updateCacheFromSource(init);
                }
            };
            WirelessMEChannelActions actions = new WirelessMEChannelActions((action, id) -> {});
            DynamicLinkedSyncHandler<StringSyncValue> rows = new DynamicLinkedSyncHandler<>(channels)
                .widgetProvider((manager, value) -> WirelessMEGui.channelRows(value.getStringValue(), actions));
            panel.syncValue("channels", channels);
            panel.syncValue("actions", actions);
            panel.syncValue("rows", rows);
            assertDoesNotThrow(() -> panel.initialize("wirelessLifecycle"));
            Map<?, ?> handlers = (Map<?, ?>) registry.get(panel);
            assertEquals(3, handlers.size());
            for (int refresh = 0; refresh < 64; refresh++) {
                // Different IDs represent new channels, page changes, deleted channels and empty lists.
                snapshot[0] = snapshot(refresh % 33);
                assertDoesNotThrow(() -> tick.invoke(panel, false));
                assertEquals(3, handlers.size());
                assertFalse(handlers.containsValue(null));
                assertEquals(0, WidgetTree.countUnregisteredSyncHandlers(panel,
                    WirelessMEGui.channelRows(snapshot[0], actions)));
            }
            panel.onClose();
        }
    }

    private String snapshot(int count) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (int i = 0; i < count; i++) {
            com.google.gson.JsonObject row = new com.google.gson.JsonObject();
            row.addProperty("id", UUID.randomUUID().toString());
            row.addProperty("name", "Channel " + i);
            row.addProperty("favorite", i == 0);
            row.addProperty("selected", i == 1);
            row.addProperty("sources", 1);
            row.addProperty("terminals", i);
            array.add(row);
        }
        return array.toString();
    }
}
