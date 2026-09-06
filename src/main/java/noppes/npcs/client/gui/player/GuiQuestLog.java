package noppes.npcs.client.gui.player;

import noppes.npcs.client.gui.util.*;
import net.minecraft.util.*;
import noppes.npcs.controllers.data.*;
import net.minecraft.entity.player.*;
import net.minecraft.client.*;
import noppes.npcs.controllers.*;
import micdoodle8.mods.galacticraft.api.client.tabs.*;
import noppes.npcs.shared.common.util.*;
import noppes.npcs.shared.client.gui.listeners.*;
import net.minecraft.client.gui.screen.*;
import noppes.npcs.shared.client.gui.components.*;
import java.util.*;
import com.mojang.blaze3d.matrix.*;
import net.minecraft.util.text.*;
import net.minecraft.client.gui.*;
import noppes.npcs.api.handler.data.*;
import noppes.npcs.client.*;
import net.minecraft.util.math.*;
import net.minecraft.client.gui.widget.button.*;
import net.minecraft.client.gui.widget.*;

public class GuiQuestLog extends GuiNPCInterface implements ITopButtonListener, ICustomScrollListener
{
    private final ResourceLocation resource;
    public HashMap<String, List<Quest>> activeQuests;
    private HashMap<String, Quest> categoryQuests;
    public Quest selectedQuest;
    public ITextComponent selectedCategory;
    private PlayerEntity player;
    private GuiCustomScroll scroll;
    private HashMap<Integer, GuiMenuSideButton> sideButtons;
    private boolean noQuests;
    private final int maxLines = 10;
    private int currentPage;
    private int maxPages;
    TextBlockClient textblock;
    private Minecraft mc;
    
    public GuiQuestLog(final PlayerEntity player) {
        this.resource = new ResourceLocation("customnpcs", "textures/gui/standardbg.png");
        this.activeQuests = new HashMap<String, List<Quest>>();
        this.categoryQuests = new HashMap<String, Quest>();
        this.selectedQuest = null;
        this.selectedCategory = StringTextComponent.EMPTY;
        this.sideButtons = new HashMap<Integer, GuiMenuSideButton>();
        this.noQuests = false;
        this.currentPage = 0;
        this.maxPages = 1;
        this.textblock = null;
        this.mc = Minecraft.getInstance();
        this.player = player;
        this.imageWidth = 256;
        this.imageHeight = 256;
        this.drawDefaultBackground = false;
    }
    
    @Override
    public void init() {
        super.init();
        for (final Quest quest : PlayerQuestController.getActiveQuests(this.player)) {
            final String category = quest.category.title;
            if (!this.activeQuests.containsKey(category)) {
                this.activeQuests.put(category, new ArrayList<Quest>());
            }
            final List<Quest> list = this.activeQuests.get(category);
            list.add(quest);
        }
        this.sideButtons.clear();
        this.guiTop += 10;
        TabRegistry.updateTabValues(this.guiLeft, this.guiTop, InventoryTabQuests.class);
        TabRegistry.addTabsToList(button -> this.addButton(button));
        this.noQuests = false;
        if (this.activeQuests.isEmpty()) {
            this.noQuests = true;
            return;
        }
        final List<String> categories = new ArrayList<String>();
        categories.addAll(this.activeQuests.keySet());
        Collections.sort(categories, new NaturalOrderComparator());
        int i = 0;
        for (final String category2 : categories) {
            if (this.selectedCategory == StringTextComponent.EMPTY) {
                this.selectedCategory = (ITextComponent)new TranslationTextComponent(category2);
            }
            this.sideButtons.put(i, new GuiMenuSideButton(this, i, this.guiLeft - 69, this.guiTop + 2 + i * 21, 70, 22, category2));
            ++i;
        }
        this.sideButtons.get(categories.indexOf(this.selectedCategory.getString())).active = true;
        if (this.scroll == null) {
            this.scroll = new GuiCustomScroll(this, 0);
        }
        final HashMap<String, Quest> categoryQuests = new HashMap<String, Quest>();
        for (final Quest q : this.activeQuests.get(this.selectedCategory.getString())) {
            categoryQuests.put(q.title, q);
        }
        this.categoryQuests = categoryQuests;
        this.scroll.setList(new ArrayList<String>(categoryQuests.keySet()));
        this.scroll.setSize(134, 174);
        this.scroll.guiLeft = this.guiLeft + 5;
        this.scroll.guiTop = this.guiTop + 15;
        this.addScroll(this.scroll);
        this.addButton(new GuiButtonNextPage(this, 1, this.guiLeft + 286, this.guiTop + 114, true, b -> {
            ++this.currentPage;
            this.init();
        }));
        this.addButton(new GuiButtonNextPage(this, 2, this.guiLeft + 144, this.guiTop + 114, false, b -> {
            --this.currentPage;
            this.init();
        }));
        this.getButton(1).visible = (this.selectedQuest != null && this.currentPage < this.maxPages - 1);
        this.getButton(2).visible = (this.selectedQuest != null && this.currentPage > 0);
    }
    
    @Override
    public void render(final MatrixStack matrixStack, final int mouseX, final int mouseY, final float partialTicks) {
        if (this.scroll != null) {
            this.scroll.visible = !this.noQuests;
        }
        this.renderBackground(matrixStack);
        this.minecraft.getTextureManager().bind(this.resource);
        this.blit(matrixStack, this.guiLeft, this.guiTop, 0, 0, 256, 256);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
        if (this.noQuests) {
            this.mc.font.draw(matrixStack, (ITextComponent)new TranslationTextComponent("quest.noquests"), (float)(this.guiLeft + 84), (float)(this.guiTop + 80), CustomNpcResourceListener.DefaultTextColor);
            return;
        }
        for (final GuiMenuSideButton button : this.sideButtons.values().toArray(new GuiMenuSideButton[this.sideButtons.size()])) {
            button.render(matrixStack, mouseX, mouseY, partialTicks);
        }
        this.mc.font.draw(matrixStack, this.selectedCategory, (float)(this.guiLeft + 5), (float)(this.guiTop + 5), CustomNpcResourceListener.DefaultTextColor);
        if (this.selectedQuest == null) {
            return;
        }
        this.drawProgress(matrixStack);
        this.drawQuestText(matrixStack);
        matrixStack.pushPose();
        matrixStack.translate((double)(this.guiLeft + 148), (double)this.guiTop, 0.0);
        matrixStack.scale(1.24f, 1.24f, 1.24f);
        final TranslationTextComponent title = new TranslationTextComponent(this.selectedQuest.title);
        this.font.draw(matrixStack, (ITextComponent)title, (float)((130 - this.font.width((ITextProperties)title)) / 2), 4.0f, CustomNpcResourceListener.DefaultTextColor);
        matrixStack.popPose();
        this.hLine(matrixStack, this.guiLeft + 142, this.guiLeft + 312, this.guiTop + 17, -16777216 + CustomNpcResourceListener.DefaultTextColor);
    }
    
    private void drawQuestText(final MatrixStack matrixStack) {
        if (this.textblock == null) {
            return;
        }
        final int yoffset = this.guiTop + 5;
        for (int i = 0; i < 10; ++i) {
            final int index = i + this.currentPage * 10;
            if (index < this.textblock.lines.size()) {
                final ITextComponent text = this.textblock.lines.get(index);
                final FontRenderer font = this.font;
                final ITextComponent textComponent = text;
                final float n = (float)(this.guiLeft + 142);
                final int n2 = this.guiTop + 20;
                final int n3 = i;
                this.font.getClass();
                font.draw(matrixStack, textComponent, n, (float)(n2 + n3 * 9), CustomNpcResourceListener.DefaultTextColor);
            }
        }
    }
    
    private void drawProgress(final MatrixStack matrixStack) {
        final ITextComponent title = (ITextComponent)new TranslationTextComponent("quest.objectives").append(":");
        this.mc.font.draw(matrixStack, title, (float)(this.guiLeft + 142), (float)(this.guiTop + 130), CustomNpcResourceListener.DefaultTextColor);
        this.hLine(matrixStack, this.guiLeft + 142, this.guiLeft + 312, this.guiTop + 140, -16777216 + CustomNpcResourceListener.DefaultTextColor);
        int yoffset = this.guiTop + 144;
        for (final IQuestObjective objective : this.selectedQuest.questInterface.getObjectives(this.player)) {
            this.mc.font.draw(matrixStack, (ITextComponent)new StringTextComponent("- ").append(objective.getMCText()), (float)(this.guiLeft + 142), (float)yoffset, CustomNpcResourceListener.DefaultTextColor);
            yoffset += 10;
        }
        this.hLine(matrixStack, this.guiLeft + 142, this.guiLeft + 312, this.guiTop + 178, -16777216 + CustomNpcResourceListener.DefaultTextColor);
        final String complete = this.selectedQuest.getNpcName();
        if (complete != null && !complete.isEmpty()) {
            this.mc.font.draw(matrixStack, (ITextComponent)new TranslationTextComponent("quest.completewith", new Object[] { complete }), (float)(this.guiLeft + 142), (float)(this.guiTop + 182), CustomNpcResourceListener.DefaultTextColor);
        }
    }
    
    @Override
    public boolean mouseClicked(final double i, final double j, final int k) {
        super.mouseClicked(i, j, k);
        if (k == 0) {
            if (this.scroll != null) {
                this.scroll.mouseClicked(i, j, k);
            }
            for (final GuiMenuSideButton button : new ArrayList<GuiMenuSideButton>(this.sideButtons.values())) {
                if (button.mouseClicked(i, j, k)) {
                    this.sideButtonPressed(button);
                    return true;
                }
            }
        }
        return false;
    }
    
    private void sideButtonPressed(final GuiMenuSideButton button) {
        if (button.active) {
            return;
        }
        NoppesUtil.clickSound();
        this.selectedCategory = button.getMessage();
        this.selectedQuest = null;
        this.init();
    }
    
    @Override
    public void scrollClicked(final double i, final double j, final int k, final GuiCustomScroll scroll) {
        if (!scroll.hasSelected()) {
            return;
        }
        this.selectedQuest = this.categoryQuests.get(scroll.getSelected());
        this.textblock = new TextBlockClient(this.selectedQuest.getLogText(), 172, true, new Object[] { this.player });
        if (this.textblock.lines.size() > 10) {
            this.maxPages = MathHelper.ceil(1.0f * this.textblock.lines.size() / 10.0f);
        }
        this.currentPage = 0;
        this.init();
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    
    @Override
    public void save() {
    }
    
    @Override
    public void scrollDoubleClicked(final String selection, final GuiCustomScroll scroll) {
    }
}
