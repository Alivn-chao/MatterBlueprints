package com.gtnhblueprints.wireless;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.StatCollector;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.DynamicLinkedSyncHandler;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.DynamicSyncedWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

/** Channel controls are server actions, including rows reconstructed from server-owned snapshots. */
public final class WirelessMEGui {
    static final int CHANNELS_PER_PAGE = 32;
    private static final WirelessMEFrame ONLINE_FRAME = new WirelessMEFrame(0xff083d14, 0xff03280b, true);
    private static final WirelessMEFrame OFFLINE_FRAME = new WirelessMEFrame(0xff1b2124, 0xff292f32, true);
    private WirelessMEGui() {}
    private static final class ChannelList extends ListWidget<ParentWidget<?>, ChannelList> {}
    public static String[] description(String[] nativeDescription) {
        List<String> lines = new ArrayList<>(Arrays.asList(nativeDescription));
        lines.add(StatCollector.translateToLocal("matterblueprints.wireless.tooltip"));
        lines.add(StatCollector.translateToLocal("matterblueprints.wireless.place"));
        return lines.toArray(new String[0]);
    }
    public static ModularPanel decorate(ModularPanel main, WirelessMEEndpoint endpoint, PanelSyncManager sync) {
        IPanelHandler wireless = sync.syncedPanel("wirelessME", true, (manager, handler) -> build(endpoint, manager));
        main.child(WirelessMEButton.neutral().size(22).leftRel(1).top(4)
            .overlay(new ItemDrawable(appeng.api.AEApi.instance().definitions().blocks().wirelessConnector().maybeStack(1).get()).asIcon().size(16))
            .addTooltipLine(IKey.lang("matterblueprints.wireless.title"))
            .onMousePressed(button -> { if (wireless.isPanelOpen()) wireless.closePanel(); else wireless.openPanel(); return true; }));
        return main;
    }
    static InteractionSyncHandler actionHandler(EntityPlayer player, Runnable action) {
        // MUI invokes this callback locally before sending the same click to the server.
        return new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (mouse.mouseButton != 0 || mouse.isClient()) return;
            try { action.run(); }
            catch (IllegalArgumentException error) { player.addChatMessage(new ChatComponentTranslation(error.getMessage())); }
        });
    }
    private static ButtonWidget<?> action(String label, EntityPlayer player, Runnable action) {
        WirelessMEButton button = label.equals("matterblueprints.wireless.create") ? WirelessMEButton.cyan()
            : label.equals("matterblueprints.wireless.disconnect") ? WirelessMEButton.red() : WirelessMEButton.neutral();
        button.overlay(IKey.lang(label)).size(56, 18).syncHandler(actionHandler(player, action));
        if (!label.isEmpty()) button.addTooltipLine(IKey.lang(label + ".help"));
        button.tooltip().showUpTimer(6);
        return button;
    }
    static ButtonWidget<?> channelAction(WirelessMEChannelActions actions, int action, String channelId, boolean selected) {
        // A local click sends its channel ID through one handler registered before the GUI opens.
        return (action == WirelessMEChannelActions.JOIN ? WirelessMEButton.channel(selected) : WirelessMEButton.neutral())
            .size(56, 18).onMousePressed(button -> {
            if (button != 0) return false;
            actions.send(action, channelId);
            return true;
        });
    }
    public static ModularPanel build(WirelessMEEndpoint endpoint, PanelSyncManager sync) {
        return build(endpoint, sync, sync.getPlayer());
    }
    public static ModularPanel build(WirelessMEEndpoint endpoint, PanelSyncManager sync, EntityPlayer player) {
        WirelessMEBinding binding = endpoint.wireless();
        String[] name = { "" };
        int[] page = { 0 };
        IntSyncValue totalChannels = new IntSyncValue(() -> player.worldObj.isRemote ? 0
            : WirelessMEData.get(player.worldObj).visible(player.getUniqueID()).size());
        sync.syncValue("wirelessTotalChannels", totalChannels);
        StringSyncValue entry = new StringSyncValue(() -> name[0], value -> name[0] = value).allowC2S();
        StringSyncValue status = new StringSyncValue(() -> {
            if (endpoint.wirelessTile().getWorldObj().isRemote) return "";
            WirelessMEData.Channel channel = WirelessMEData.get(player.worldObj).get(binding.channelId());
            return binding.state() + "\n" + (channel == null ? "" : shortName(channel.name));
        });
        sync.syncValue("wirelessStatus", status);
        StringSyncValue channels = new StringSyncValue(() -> snapshot(endpoint, player, page));
        sync.syncValue("wirelessChannels", channels);
        WirelessMEChannelActions channelActions = new WirelessMEChannelActions((action, id) -> {
            try {
                if (!binding.canManage(player)) throw new IllegalArgumentException("matterblueprints.wireless.no_permission");
                WirelessMEData data = WirelessMEData.get(player.worldObj);
                data.require(player.getUniqueID(), id);
                switch (action) {
                    case WirelessMEChannelActions.JOIN: binding.join(player, id); break;
                    case WirelessMEChannelActions.FAVORITE: data.toggleFavorite(player.getUniqueID(), id); break;
                    case WirelessMEChannelActions.DELETE:
                        data.delete(player.getUniqueID(), id);
                        WirelessMEService.detachMembers(id);
                        break;
                    default: break;
                }
            } catch (IllegalArgumentException error) {
                player.addChatMessage(new ChatComponentTranslation(error.getMessage()));
            }
        });
        sync.syncValue("wirelessChannelActions", channelActions);
        DynamicLinkedSyncHandler<StringSyncValue> rows = new DynamicLinkedSyncHandler<>(channels)
            .widgetProvider((manager, value) -> channelRows(value.getStringValue(), channelActions));
        return gregtech.api.modularui2.GTGuis.createPopUpPanel("matterblueprints.wirelessME").size(224, 234)
            .background(new WirelessMEFrame(0xffd8dcde, 0xffa2a9ad, false))
            .child(Flow.col().coverChildren().left(8).top(16).childPadding(5)
                .child(new ParentWidget<>().size(208, 20).background(new WirelessMEFrame(0xff9d7134, 0xff704918, true))
                    .child(IKey.lang(endpoint.wirelessSource() ? "matterblueprints.wireless.source" : "matterblueprints.wireless.terminal")
                        .asWidget().left(7).top(5).size(130, 10).alignment(Alignment.CenterLeft).color(0xffffcb65))
                    .child(IKey.str(String.format("#%06x", java.util.Objects.hash(endpoint.wirelessTile().xCoord,
                        endpoint.wirelessTile().yCoord, endpoint.wirelessTile().zCoord) & 0xffffff))
                        .asWidget().right(7).top(5).size(58, 10).alignment(Alignment.CenterRight).color(0xffc0c0c0)))
                .child(new ParentWidget<>().size(208, 30)
                    .background((context, x, y, width, height, theme) -> {
                        com.cleanroommc.modularui.api.drawable.IDrawable frame = status.getStringValue().startsWith("online\n")
                            ? ONLINE_FRAME : OFFLINE_FRAME;
                        frame.draw(context, x, y, width, height, theme);
                    })
                    .child(IKey.lang("matterblueprints.wireless.network_status").asWidget().left(7).top(5)
                        .size(194, 9).alignment(Alignment.CenterLeft).color(0xffa8adaf))
                    .child(IKey.dynamic(() -> displayStatus(status.getStringValue())).asWidget().left(7).top(16)
                        .size(194, 9).alignment(Alignment.CenterLeft)))
                .child(Flow.row().coverChildren().childPadding(3)
                    .child(new TextFieldWidget().value(entry).size(88, 18).setMaxLength(48)
                        .background(new WirelessMEFrame(0xff090b0c, 0xff14191b, true)))
                    .child(action("matterblueprints.wireless.create", player, () -> {
                        if (!binding.canManage(player) || binding.owner() != null
                            && !WirelessMEData.sameTeam(binding.owner(), player.getUniqueID()))
                            throw new IllegalArgumentException("matterblueprints.wireless.no_permission");
                        WirelessMEData.Channel created = WirelessMEData.get(player.worldObj).create(player.getUniqueID(), name[0]);
                        binding.join(player, created.id);
                    }).width(58))
                    .child(action("matterblueprints.wireless.disconnect", player, () -> binding.disconnect(player)).width(56)))
                .child(Flow.row().coverChildren().childPadding(2)
                    .child(IKey.dynamic(() -> StatCollector.translateToLocal("matterblueprints.wireless.available")
                        + " (" + totalChannels.getIntValue() + ")")
                        .asWidget().size(152, 16).alignment(Alignment.CenterLeft))
                    .child(action("", player, () -> page[0] = Math.max(0, page[0] - 1)).size(24, 16).overlay(IKey.str("<"))
                        .addTooltipLine(IKey.lang("matterblueprints.wireless.previous")))
                    .child(action("", player, () -> page[0] = Math.min(page[0] + 1,
                        Math.max(0, (WirelessMEData.get(player.worldObj).visible(player.getUniqueID()).size() - 1) / CHANNELS_PER_PAGE)))
                        .size(24, 16).overlay(IKey.str(">"))
                        .addTooltipLine(IKey.lang("matterblueprints.wireless.next"))))
                .child(new ParentWidget<>().size(208, 98).background(new WirelessMEFrame(0xff4b5154, 0xff555c60, true))
                    .child(new DynamicSyncedWidget<>().size(202, 92).left(3).top(3).syncHandler(rows))));
    }
    static com.cleanroommc.modularui.api.widget.IWidget channelRows(String snapshot, WirelessMEChannelActions channelActions) {
        ChannelList list = new ChannelList().size(202, 92);
        JsonArray array = new JsonParser().parse(snapshot.isEmpty() ? "[]" : snapshot).getAsJsonArray();
        for (JsonElement element : array) {
            JsonObject channel = element.getAsJsonObject();
            String id = channel.get("id").getAsString();
            String label = channel.get("name").getAsString();
            boolean favorite = channel.get("favorite").getAsBoolean();
            boolean selected = channel.get("selected").getAsBoolean();
            ParentWidget<?> row = new ParentWidget<>().size(196, 30).marginBottom(3);
            ButtonWidget<?> join = channelAction(channelActions, WirelessMEChannelActions.JOIN, id, selected)
                .size(196, 30).left(0).top(0)
                .addTooltipLine(IKey.lang(selected ? "matterblueprints.wireless.connected_hint" : "matterblueprints.wireless.connect_hint", label));
            join.tooltip().showUpTimer(6);
            row.child(join);
            row.child(IKey.str((favorite ? "★ " : "") + rowName(label, favorite ? 118 : 130)).asWidget()
                .left(6).top(4).size(136, 10).alignment(Alignment.CenterLeft).color(selected ? 0xff65ffc5 : 0xffeeeeee));
            row.child(IKey.lang("matterblueprints.wireless.count", channel.get("sources").getAsInt(), channel.get("terminals").getAsInt())
                .asWidget().left(6).top(17).size(136, 9).alignment(Alignment.CenterLeft).color(0xffaeb9b9));
            row.child(channelAction(channelActions, WirelessMEChannelActions.FAVORITE, id, false)
                .size(22).left(148).top(4).background(com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY)
                .overlay(IKey.str(favorite ? "§e★" : "§7★"))
                .addTooltipLine(IKey.lang(favorite ? "matterblueprints.wireless.unfavorite" : "matterblueprints.wireless.favorite")));
            row.child(channelAction(channelActions, WirelessMEChannelActions.DELETE, id, false)
                .size(20).left(172).top(4).background(com.cleanroommc.modularui.api.drawable.IDrawable.EMPTY).overlay(IKey.str("§7×"))
                .addTooltipLine(IKey.lang("matterblueprints.wireless.delete_hint", label)));
            list.child(row);
        }
        return list;
    }
    private static String displayStatus(String value) {
        String[] fields = value.split("\n", 2);
        String state = fields[0].isEmpty() ? "standalone" : fields[0];
        String color = state.equals("online") ? "§a" : "§c";
        if (state.equals("online") && fields.length > 1 && !fields[1].isEmpty()) return color + fields[1];
        return color + StatCollector.translateToLocal("matterblueprints.wireless.state." + state)
            + (fields.length < 2 || fields[1].isEmpty() ? "" : " · " + fields[1]);
    }
    private static String rowName(String name, int budget) {
        int width = 0;
        for (int end = 0; end < name.length();) {
            int cp = name.codePointAt(end);
            int next = end + Character.charCount(cp);
            width += cp < 128 ? 6 : 9;
            if (width > budget - 9 && next < name.length()) return name.substring(0, end) + "…";
            end = next;
        }
        return name;
    }
    private static String shortName(String name) {
        return name.codePointCount(0, name.length()) <= 16 ? name : name.substring(0, name.offsetByCodePoints(0, 15)) + "…";
    }
    private static String snapshot(WirelessMEEndpoint endpoint, EntityPlayer player, int[] page) {
        if (endpoint.wirelessTile().getWorldObj().isRemote) return "[]";
        WirelessMEData data = WirelessMEData.get(player.worldObj);
        JsonArray array = new JsonArray();
        List<WirelessMEData.Channel> visible = data.visible(player.getUniqueID());
        page[0] = Math.min(page[0], Math.max(0, (visible.size() - 1) / CHANNELS_PER_PAGE));
        for (WirelessMEData.Channel channel : visible.subList(page[0] * CHANNELS_PER_PAGE,
            Math.min(visible.size(), (page[0] + 1) * CHANNELS_PER_PAGE))) {
            JsonObject entry = new JsonObject(); entry.addProperty("id", channel.id); entry.addProperty("name", channel.name);
            entry.addProperty("favorite", channel.id.equals(data.favorite(player.getUniqueID())));
            entry.addProperty("selected", channel.id.equals(endpoint.wireless().channelId()));
            int[] count = WirelessMEService.count(channel.id); entry.addProperty("sources", count[0]); entry.addProperty("terminals", count[1]);
            array.add(entry);
        }
        return array.toString();
    }
}
