package com.gtnhblueprints.client.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.gtnhblueprints.inventory.ContainerBlueprintConfigurator;
import com.gtnhblueprints.io.BlueprintIO;
import com.gtnhblueprints.model.Blueprint;
import com.gtnhblueprints.model.CompatibilityReport;
import com.gtnhblueprints.network.BlueprintNetwork;
import com.gtnhblueprints.service.BlueprintLibrary;
import com.gtnhblueprints.service.BlueprintReplacementService;
import com.gtnhblueprints.service.BlueprintReplacementService.BlockGroup;
import com.recursive_pineapple.matter_manipulator.common.building.BlockSpec;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class GuiBlueprintConfigurator extends GuiContainer {

    private static final int ROWS = 3;
    private static final long DELETE_CONFIRMATION_MS = 5000L;
    private static final long SAVE_CONFIRMATION_MS = 5000L;
    private static List<String> cachedServerNames = Collections.emptyList();

    private final ContainerBlueprintConfigurator container;
    private boolean serverTab;
    private boolean configuring;
    private int page;
    private int selected = -1;
    private Blueprint source;
    private List<BlockGroup> groups = Collections.emptyList();
    private final Map<String, BlockSpec> replacements = new LinkedHashMap<>();
    private String outputValue = "";
    private String searchValue = "";
    private String status = "";
    private int statusColour = 0xC0C0C0;
    private String pendingDeleteName;
    private long pendingDeleteUntil;
    private String pendingSaveName;
    private long pendingSaveUntil;
    private GuiTextField outputName;
    private GuiTextField searchField;

    public GuiBlueprintConfigurator(ContainerBlueprintConfigurator container) {
        super(container);
        this.container = container;
        xSize = 390;
        ySize = 270;
    }

    public static void receiveServerList(List<String> names) {
        cachedServerNames = new ArrayList<>(names);
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof GuiBlueprintConfigurator) {
            GuiBlueprintConfigurator gui = (GuiBlueprintConfigurator) screen;
            if (gui.serverTab && !gui.configuring) {
                gui.page = 0;
                gui.selected = -1;
                gui.status = "已刷新服务器蓝图列表，共 " + names.size() + " 个";
                gui.rebuildButtons();
            }
        }
    }

    public static void localLibraryChanged() {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof GuiBlueprintConfigurator) {
            GuiBlueprintConfigurator gui = (GuiBlueprintConfigurator) screen;
            if (!gui.configuring) {
                gui.serverTab = false;
                gui.searchValue = "";
                gui.page = 0;
                gui.selected = -1;
                gui.status = "下载完成，已切换到本地蓝图";
                gui.rebuildButtons();
            }
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        rebuildButtons();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    private void rebuildButtons() {
        if (outputName != null) outputValue = outputName.getText();
        buttonList.clear();
        int left = (width - xSize) / 2;
        int top = (height - ySize) / 2;
        searchField = new GuiTextField(fontRendererObj, left + 10, top + 34, xSize - 20, 16);
        searchField.setMaxStringLength(96);
        searchField.setText(searchValue);
        if (!configuring) {
            buttonList.add(new GuiButton(1, left + 10, top + 8, 88, 20, serverTab ? "本地蓝图" : "§a本地蓝图"));
            buttonList.add(new GuiButton(2, left + 102, top + 8, 88, 20, serverTab ? "§a服务器蓝图" : "服务器蓝图"));
            buttonList.add(new GuiButton(3, left + 194, top + 8, 58, 20, "刷新"));
            addRows(libraryNames(), left, top);
            buttonList.add(new GuiButton(4, left + 10, top + 126, 50, 20, "上一页"));
            buttonList.add(new GuiButton(5, left + 64, top + 126, 50, 20, "下一页"));
            if (serverTab) {
                buttonList.add(new GuiButton(6, left + 268, top + 126, 112, 20, "下载到本地"));
            } else {
                buttonList.add(new GuiButton(6, left + 170, top + 126, 60, 20, "导入"));
                buttonList.add(new GuiButton(13, left + 234, top + 126, 92, 20, "配置替换"));
                buttonList.add(
                    new GuiButton(
                        11,
                        left + 330,
                        top + 126,
                        50,
                        20,
                        pendingDeleteName == null ? "删除" : "确认"));
            }
        } else {
            buttonList.add(new GuiButton(7, left + 10, top + 8, 58, 20, "返回"));
            buttonList.add(new GuiButton(4, left + 72, top + 8, 50, 20, "上一页"));
            buttonList.add(new GuiButton(5, left + 126, top + 8, 50, 20, "下一页"));
            addRows(visibleGroups(), left, top);
            buttonList.add(new GuiButton(8, left + 196, top + 126, 116, 20, "用替换槽物品替换"));
            buttonList.add(new GuiButton(9, left + 316, top + 126, 40, 20, "清除"));
            buttonList.add(new GuiButton(10, left + 208, top + 150, 76, 16, "载入 MM"));
            buttonList.add(
                new GuiButton(
                    12,
                    left + 288,
                    top + 150,
                    92,
                    16,
                    pendingSaveName == null ? "保存副本" : "确认保存"));
            outputName = new GuiTextField(fontRendererObj, left + 10, top + 150, 194, 16);
            outputName.setMaxStringLength(96);
            outputName.setText(outputValue);
        }
    }

    private void addRows(List<?> entries, int left, int top) {
        int start = page * ROWS;
        for (int row = 0; row < ROWS && start + row < entries.size(); row++) {
            int index = start + row;
            String label;
            if (configuring) {
                BlockGroup group = (BlockGroup) entries.get(index);
                BlockSpec target = replacements.get(group.key);
                label = group.displayName + " ×" + group.count;
                if (target != null) label += "  →  " + BlueprintReplacementService.displayName(target);
            } else label = entries.get(index).toString();
            if (index == selected) label = "▶ " + label;
            label = fontRendererObj.trimStringToWidth(label, xSize - 28);
            buttonList.add(new GuiButton(100 + row, left + 10, top + 56 + row * 18, xSize - 20, 17, label));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        try {
            if (button.id == 1) switchLibrary(false);
            else if (button.id == 2) switchLibrary(true);
            else if (button.id == 3) refreshLibrary();
            else if (button.id == 4) changePage(-1);
            else if (button.id == 5) changePage(1);
            else if (button.id == 6) libraryAction();
            else if (button.id == 7) leaveConfiguration();
            else if (button.id == 8) setReplacement();
            else if (button.id == 9) clearReplacement();
            else if (button.id == 10) loadIntoManipulator();
            else if (button.id == 11) deleteLocalBlueprint();
            else if (button.id == 12) saveConfiguredBlueprint();
            else if (button.id == 13) configureSelectedBlueprint();
            else if (button.id >= 100 && button.id < 100 + ROWS) {
                int index = page * ROWS + button.id - 100;
                int size = configuring ? visibleGroups().size() : libraryNames().size();
                if (index < size) {
                    if (index != selected) clearDeleteConfirmation();
                    selected = index;
                    status = "";
                }
            }
        } catch (IOException | RuntimeException exception) {
            status = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            statusColour = 0xFF5555;
        }
        rebuildButtons();
    }

    private void switchLibrary(boolean server) {
        clearDeleteConfirmation();
        serverTab = server;
        searchValue = "";
        page = 0;
        selected = -1;
        status = "";
        if (server) BlueprintNetwork.requestServerBlueprints();
    }

    private void refreshLibrary() {
        clearDeleteConfirmation();
        page = 0;
        selected = -1;
        if (serverTab) {
            status = "正在读取服务器蓝图…";
            BlueprintNetwork.requestServerBlueprints();
        } else {
            status = "已刷新本地蓝图";
        }
    }

    private void changePage(int delta) {
        clearDeleteConfirmation();
        int size = configuring ? visibleGroups().size() : libraryNames().size();
        int pages = Math.max(1, (size + ROWS - 1) / ROWS);
        page = Math.max(0, Math.min(pages - 1, page + delta));
        selected = -1;
    }

    private void libraryAction() throws IOException {
        clearDeleteConfirmation();
        clearSaveConfirmation();
        List<String> names = libraryNames();
        if (selected < 0 || selected >= names.size()) throw new IOException("请先选择一个蓝图");
        String name = names.get(selected);
        if (serverTab) {
            BlueprintNetwork.requestDownload(name);
            status = "正在下载 " + name + "…";
            return;
        }
        byte[] bytes = BlueprintIO.read(BlueprintLibrary.directory(), name);
        Blueprint decoded = BlueprintIO.decode(bytes);
        CompatibilityReport report = CompatibilityReport.inspect(decoded);
        if (!report.canUse()) throw new IOException(report.errors.get(0));
        BlueprintNetwork.upload(name, bytes);
        mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("§a[GTBP] 正在导入 " + name));
        mc.thePlayer.closeScreen();
    }

    private void configureSelectedBlueprint() throws IOException {
        clearDeleteConfirmation();
        List<String> names = libraryNames();
        if (selected < 0 || selected >= names.size()) throw new IOException("请先选择一个蓝图");
        String name = names.get(selected);
        byte[] bytes = BlueprintIO.read(BlueprintLibrary.directory(), name);
        Blueprint decoded = BlueprintIO.decode(bytes);
        CompatibilityReport report = CompatibilityReport.inspect(decoded);
        if (!report.canUse()) throw new IOException(report.errors.get(0));
        source = decoded;
        groups = BlueprintReplacementService.groups(source);
        replacements.clear();
        configuring = true;
        searchValue = "";
        page = 0;
        selected = -1;
        outputValue = BlueprintIO.safeName(source.name + "-配置");
        status = "已列出 " + groups.size() + " 种方块；选择一项并把目标方块放入右侧替换槽";
        statusColour = 0x55FF55;
    }

    private void deleteLocalBlueprint() throws IOException {
        if (serverTab) throw new IOException("服务器蓝图只能先下载到本地");
        List<String> names = libraryNames();
        if (selected < 0 || selected >= names.size()) throw new IOException("请先选择一个本地蓝图");
        String name = names.get(selected);
        long now = System.currentTimeMillis();
        if (!name.equals(pendingDeleteName) || now > pendingDeleteUntil) {
            pendingDeleteName = name;
            pendingDeleteUntil = now + DELETE_CONFIRMATION_MS;
            status = "再次点击“确认”删除本地蓝图 " + name;
            statusColour = 0xFFAA00;
            return;
        }
        Files.delete(BlueprintIO.resolve(BlueprintLibrary.directory(), name).toPath());
        clearDeleteConfirmation();
        selected = -1;
        status = "已删除本地蓝图 " + name;
        statusColour = 0x55FF55;
    }

    private void clearDeleteConfirmation() {
        pendingDeleteName = null;
        pendingDeleteUntil = 0L;
    }

    private void leaveConfiguration() {
        clearSaveConfirmation();
        configuring = false;
        source = null;
        groups = Collections.emptyList();
        replacements.clear();
        page = 0;
        selected = -1;
        outputName = null;
        searchValue = "";
        status = "";
    }

    private void setReplacement() {
        BlockGroup group = selectedGroup();
        ItemStack slot = container.configurator.getStackInSlot(0);
        BlockSpec target = BlueprintReplacementService.replacementFrom(slot);
        if (group.key.equals(BlueprintReplacementService.key(target))) {
            replacements.remove(group.key);
            status = "目标与原方块相同，已取消该项替换";
        } else {
            replacements.put(group.key, target.clone());
            status = group.displayName + " ×" + group.count + " → " + BlueprintReplacementService.displayName(target);
        }
        statusColour = 0x55FF55;
    }

    private void clearReplacement() {
        BlockGroup group = selectedGroup();
        replacements.remove(group.key);
        status = "已清除 " + group.displayName + " 的替换设置";
        statusColour = 0xC0C0C0;
    }

    private void loadIntoManipulator() throws IOException {
        if (source == null) throw new IOException("尚未载入待配置蓝图");
        String name = BlueprintIO.safeName(outputName == null ? outputValue : outputName.getText());
        Blueprint configured = configuredBlueprint(name);
        byte[] encoded = BlueprintIO.encode(configured);
        BlueprintNetwork.upload(name, encoded);
        mc.thePlayer.addChatMessage(
            new net.minecraft.util.ChatComponentText(
                "§a[GTBP] 已临时载入 " + name + "；应用 " + replacements.size() + " 项替换（未保存文件）"));
        mc.thePlayer.closeScreen();
    }

    private void saveConfiguredBlueprint() throws IOException {
        if (source == null) throw new IOException("尚未载入待配置蓝图");
        String name = BlueprintIO.safeName(outputName == null ? outputValue : outputName.getText());
        long now = System.currentTimeMillis();
        if (!name.equals(pendingSaveName) || now > pendingSaveUntil) {
            pendingSaveName = name;
            pendingSaveUntil = now + SAVE_CONFIRMATION_MS;
            status = "再次点击“确认保存”写入 " + name + ".gtbp；同名文件会覆盖";
            statusColour = 0xFFAA00;
            return;
        }
        Blueprint configured = configuredBlueprint(name);
        BlueprintIO.write(BlueprintLibrary.directory(), name, BlueprintIO.encode(configured));
        clearSaveConfirmation();
        status = "已保存本地蓝图 " + name + ".gtbp；尚未载入 MM";
        statusColour = 0x55FF55;
    }

    private Blueprint configuredBlueprint(String name) throws IOException {
        return BlueprintReplacementService.apply(
            source,
            name,
            mc.thePlayer.getCommandSenderName(),
            mc.thePlayer.getUniqueID().toString(),
            replacements);
    }

    private void clearSaveConfirmation() {
        pendingSaveName = null;
        pendingSaveUntil = 0L;
    }

    private BlockGroup selectedGroup() {
        List<BlockGroup> visible = visibleGroups();
        if (selected < 0 || selected >= visible.size()) throw new IllegalStateException("请先选择一种原方块");
        return visible.get(selected);
    }

    private List<String> libraryNames() {
        List<String> all = serverTab ? cachedServerNames : BlueprintIO.list(BlueprintLibrary.directory());
        if (searchValue.trim().isEmpty()) return all;
        List<String> filtered = new ArrayList<>();
        for (String name : all) {
            if (matchesSearch(name, "")) filtered.add(name);
        }
        return filtered;
    }

    private List<BlockGroup> visibleGroups() {
        if (searchValue.trim().isEmpty()) return groups;
        List<BlockGroup> filtered = new ArrayList<>();
        for (BlockGroup group : groups) {
            if (matchesSearch(group.displayName, group.spec.getObjectId().toString())) filtered.add(group);
        }
        return filtered;
    }

    private boolean matchesSearch(String displayName, String objectId) {
        String display = displayName.toLowerCase(Locale.ROOT);
        String object = objectId.toLowerCase(Locale.ROOT);
        String modId = object.contains(":") ? object.substring(0, object.indexOf(':')) : object;
        for (String token : searchValue.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            if (token.charAt(0) == '@') {
                if (!modId.contains(token.substring(1))) return false;
            } else if (!display.contains(token) && !object.contains(token)) return false;
        }
        return true;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (outputName != null) outputName.updateCursorCounter();
        if (pendingDeleteName != null && System.currentTimeMillis() > pendingDeleteUntil) {
            clearDeleteConfirmation();
            status = "删除确认已取消";
            statusColour = 0xC0C0C0;
            rebuildButtons();
        }
        if (pendingSaveName != null && System.currentTimeMillis() > pendingSaveUntil) {
            clearSaveConfirmation();
            status = "保存确认已取消";
            statusColour = 0xC0C0C0;
            rebuildButtons();
        }
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (outputName != null && outputName.textboxKeyTyped(character, keyCode)) return;
        if (searchField != null && searchField.textboxKeyTyped(character, keyCode)) {
            searchValue = searchField.getText();
            page = 0;
            selected = -1;
            clearDeleteConfirmation();
            rebuildButtons();
            searchField.setFocused(true);
            return;
        }
        super.keyTyped(character, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (outputName != null) outputName.mouseClicked(mouseX, mouseY, mouseButton);
        if (searchField != null) searchField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (outputName != null) outputName.drawTextBox();
        if (searchField != null) {
            searchField.drawTextBox();
            if (searchField.getText().isEmpty() && !searchField.isFocused()) {
                fontRendererObj.drawString(
                    configuring ? "搜索待替换方块（支持 @模组ID）" : "搜索蓝图",
                    searchField.xPosition + 4,
                    searchField.yPosition + 4,
                    0x777777);
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GL11.glColor4f(1, 1, 1, 1);
        drawGradientRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF20252B, 0xFF12161A);
        drawRect(guiLeft + 6, guiTop + 4, guiLeft + xSize - 6, guiTop + 166, 0xFF303840);
        drawRect(guiLeft + 110, guiTop + 175, guiLeft + 280, guiTop + 264, 0xFF252B31);
        drawPlayerInventorySlots();
        drawRect(guiLeft + 359, guiTop + 123, guiLeft + 377, guiTop + 141, 0xFF8B8B8B);
        drawRect(guiLeft + 360, guiTop + 124, guiLeft + 376, guiTop + 140, 0xFF101010);
    }

    private void drawPlayerInventorySlots() {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawInventorySlot(113 + column * 18, 178 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawInventorySlot(113 + column * 18, 236);
        }
    }

    private void drawInventorySlot(int x, int y) {
        drawRect(guiLeft + x, guiTop + y, guiLeft + x + 18, guiTop + y + 18, 0xFF9EA6B0);
        drawRect(guiLeft + x + 1, guiTop + y + 1, guiLeft + x + 17, guiTop + y + 17, 0xFF4A525C);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString("蓝图替换配置机", 272, 12, 0xFFFFFF);
        if (!configuring) {
            List<String> names = libraryNames();
            if (status.isEmpty()) {
                fontRendererObj.drawString(
                    (serverTab ? "服务器" : "本地") + "蓝图：" + names.size() + " 个，第 " + (page + 1) + "/"
                        + Math.max(1, (names.size() + ROWS - 1) / ROWS) + " 页",
                    10,
                    114,
                    0xD0D0D0);
            }
        } else {
            fontRendererObj.drawString("输出名称", 10, 140, 0xD0D0D0);
            fontRendererObj.drawString("目标", 358, 113, 0xD0D0D0);
        }
        if (!status.isEmpty()) {
            fontRendererObj.drawString(
                fontRendererObj.trimStringToWidth(status, 330),
                10,
                114,
                statusColour);
        }
        fontRendererObj.drawString("玩家物品栏", 114, 168, 0xD0D0D0);
        List<BlockGroup> visible = configuring ? visibleGroups() : Collections.emptyList();
        if (configuring && status.isEmpty() && selected >= 0 && selected < visible.size()) {
            BlockGroup group = visible.get(selected);
            String details = "已选：" + group.displayName + " ×" + group.count;
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(details, 330), 10, 114, 0x55FFFF);
        }
    }
}
