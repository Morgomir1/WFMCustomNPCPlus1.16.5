package noppes.npcs.client.gui.questtypes;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.global.GuiNPCManageQuest;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import noppes.npcs.containers.ContainerNpcQuestTypeItem;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.quests.QuestItem;
import noppes.npcs.quests.QuestObjectiveLimits;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

public class GuiNpcQuestTypeItem extends GuiContainerNPCInterface<ContainerNpcQuestTypeItem> implements ITextfieldListener {
    private static final ResourceLocation BACKGROUND = new ResourceLocation("customnpcs", "textures/gui/followersetup.png");
    private static final ResourceLocation SLOT_TEXTURE = new ResourceLocation("textures/gui/container/generic_54.png");

    private final Quest quest;

    public GuiNpcQuestTypeItem(final ContainerNpcQuestTypeItem container, final PlayerInventory inv, final ITextComponent titleIn) {
        super(NoppesUtil.getLastNpc(), container, inv, titleIn);
        this.quest = NoppesUtilServer.getEditingQuest(this.player);
        this.title = "";
        this.imageHeight = 202;
        this.closeOnEsc = false;
    }

    @Override
    public void init() {
        super.init();
        this.addLabel(new GuiLabel(0, "quest.takeitems", this.guiLeft + 4, this.guiTop + 8));
        this.addButton(new GuiButtonNop((IGuiInterface) this, 0, this.guiLeft + 90, this.guiTop + 3, 60, 20, new String[] { "gui.yes", "gui.no" }, ((QuestItem) this.quest.questInterface).leaveItems ? 1 : 0));
        this.addLabel(new GuiLabel(1, "gui.ignoreDamage", this.guiLeft + 4, this.guiTop + 29));
        this.addButton(new GuiButtonYesNo((IGuiInterface) this, 1, this.guiLeft + 90, this.guiTop + 24, 50, 20, ((QuestItem) this.quest.questInterface).ignoreDamage));
        this.addLabel(new GuiLabel(2, "gui.ignoreNBT", this.guiLeft + 90, this.guiTop + 51));
        this.addButton(new GuiButtonYesNo((IGuiInterface) this, 2, this.guiLeft + 148, this.guiTop + 46, 50, 20, ((QuestItem) this.quest.questInterface).ignoreNBT));
        this.addButton(new GuiButtonNop(this, 5, this.guiLeft, this.guiTop + this.imageHeight, 98, 20, "gui.back"));
    }

    @Override
    public void buttonEvent(final GuiButtonNop guibutton) {
        if (guibutton.id == 0) {
            ((QuestItem) this.quest.questInterface).leaveItems = guibutton.getValue() == 1;
        }
        if (guibutton.id == 1) {
            ((QuestItem) this.quest.questInterface).ignoreDamage = ((GuiButtonYesNo) guibutton).getBoolean();
        }
        if (guibutton.id == 2) {
            ((QuestItem) this.quest.questInterface).ignoreNBT = ((GuiButtonYesNo) guibutton).getBoolean();
        }
        if (guibutton.id == 5) {
            NoppesUtil.openGUI(this.player, GuiNPCManageQuest.Instance);
        }
    }

    @Override
    protected void renderBg(final MatrixStack matrixStack, final float partialTicks, final int mouseX, final int mouseY) {
        super.renderBackground(matrixStack);
        RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);
        this.minecraft.getTextureManager().bind(BACKGROUND);
        final int left = (this.width - this.imageWidth) / 2;
        final int top = (this.height - this.imageHeight) / 2;
        this.blit(matrixStack, left, top, 0, 0, this.imageWidth, this.imageHeight);
        this.minecraft.getTextureManager().bind(SLOT_TEXTURE);
        for (int i = 0; i < QuestObjectiveLimits.ITEM_SLOTS; ++i) {
            this.blit(matrixStack, left + QuestObjectiveLimits.itemSlotX(i) - 1, top + QuestObjectiveLimits.itemSlotY(i) - 1, 7, 17, 18, 18);
        }
    }

    @Override
    public void save() {
    }

    @Override
    public void unFocused(final GuiTextFieldNop textfield) {
        this.quest.rewardExp = textfield.getInteger();
    }
}
