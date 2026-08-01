package noppes.npcs.client.gui.player;

import noppes.npcs.client.gui.util.*;
import noppes.npcs.shared.client.gui.listeners.*;
import noppes.npcs.entity.*;
import noppes.npcs.mixin.*;
import net.minecraft.client.*;
import net.minecraft.client.util.*;
import com.mojang.blaze3d.matrix.*;
import net.minecraft.entity.*;
import net.minecraft.util.text.*;
import java.util.*;
import noppes.npcs.controllers.data.*;
import net.minecraft.entity.player.*;
import noppes.npcs.shared.client.util.*;
import noppes.npcs.packets.*;
import noppes.npcs.client.*;
import noppes.npcs.packets.server.*;
import noppes.npcs.client.controllers.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.nbt.*;

public class GuiDialogInteract extends GuiNPCInterface implements IGuiClose
{
    private Dialog dialog;
    private int selected;
    private List<TextBlockClient> lines;
    private List<Integer> options;
    private int rowStart;
    private int rowTotal;
    private int dialogHeight;
    private ResourceLocation wheel;
    private ResourceLocation[] wheelparts;
    private ResourceLocation indicator;
    private boolean isGrabbed;
    private double selectedX;
    private double selectedY;
    
    public GuiDialogInteract(final EntityNPCInterface npc, final Dialog dialog) {
        super(npc);
        this.selected = 0;
        this.lines = new ArrayList<TextBlockClient>();
        this.options = new ArrayList<Integer>();
        this.rowStart = 0;
        this.rowTotal = 0;
        this.dialogHeight = 180;
        this.isGrabbed = false;
        this.selectedX = 0.0;
        this.selectedY = 0.0;
        this.appendDialog(this.dialog = dialog);
        this.imageHeight = 238;
        this.wheel = this.getResource("wheel.png");
        this.indicator = this.getResource("indicator.png");
        this.wheelparts = new ResourceLocation[] { this.getResource("wheel1.png"), this.getResource("wheel2.png"), this.getResource("wheel3.png"), this.getResource("wheel4.png"), this.getResource("wheel5.png"), this.getResource("wheel6.png") };
    }
    
    @Override
    public void init() {
        super.init();
        this.isGrabbed = false;
        this.grabMouse(this.dialog.showWheel);
        this.guiTop = this.height - this.imageHeight;
        this.calculateRowHeight();
    }
    
    public void grabMouse(final boolean grab) {
        if (grab && !this.isGrabbed) {
            final MouseHelperMixin mouse = (MouseHelperMixin)Minecraft.getInstance().mouseHandler;
            mouse.setGrabbed(false);
            final double xpos = 0.0;
            final double ypos = 0.0;
            mouse.setX(xpos);
            mouse.setY(ypos);
            InputMappings.grabOrReleaseMouse(this.minecraft.getWindow().getWindow(), 212995, xpos, ypos);
            this.isGrabbed = true;
        }
        else if (!grab && this.isGrabbed) {
            Minecraft.getInstance().mouseHandler.releaseMouse();
            this.isGrabbed = false;
        }
    }
    
    @Override
    public void render(final MatrixStack matrixStack, final int mouseX, final int mouseY, final float partialTicks) {
        this.fillGradient(matrixStack, 0, 0, this.width, this.height, -587202560, -587202560);
        if (!this.dialog.hideNPC) {
            final int l = -70;
            final int i1 = this.imageHeight;
            this.drawNpc((LivingEntity)this.npc, l, i1, 1.4f, 0);
        }
        super.render(matrixStack, mouseX, mouseY, partialTicks);
        matrixStack.pushPose();
        matrixStack.translate(0.0, 0.5, 100.06500244140625);
        int count = 0;
        for (final TextBlockClient block : new ArrayList<TextBlockClient>(this.lines)) {
            final int size = ClientProxy.Font.width(block.getName() + ": ");
            this.drawString(matrixStack, block.getName() + ": ", -4 - size, block.color, count);
            for (final ITextComponent line : block.lines) {
                this.drawString(matrixStack, line.getString(), 0, block.color, count);
                ++count;
            }
            ++count;
        }
        if (!this.options.isEmpty()) {
            if (!this.dialog.showWheel) {
                this.drawLinedOptions(matrixStack, mouseY);
            }
            else {
                this.drawWheel(matrixStack);
            }
        }
        matrixStack.popPose();
    }
    
    private void drawWheel(final MatrixStack matrixStack) {
        final int yoffset = this.guiTop + this.dialogHeight + 14;
        this.minecraft.getTextureManager().bind(this.wheel);
        this.blit(matrixStack, this.width / 2 - 31, yoffset, 0, 0, 63, 40);
        this.selectedX = this.minecraft.mouseHandler.xpos() * 0.5;
        this.selectedY = -this.minecraft.mouseHandler.ypos() * 0.5;
        final int limit = 80;
        if (this.selectedX > limit) {
            this.selectedX = limit;
        }
        if (this.selectedX < -limit) {
            this.selectedX = -limit;
        }
        if (this.selectedY > limit) {
            this.selectedY = limit;
        }
        if (this.selectedY < -limit) {
            this.selectedY = -limit;
        }
        this.selected = 1;
        if (this.selectedY < -20.0) {
            ++this.selected;
        }
        if (this.selectedY > 54.0) {
            --this.selected;
        }
        if (this.selectedX < 0.0) {
            this.selected += 3;
        }
        this.minecraft.getTextureManager().bind(this.wheelparts[this.selected]);
        this.blit(matrixStack, this.width / 2 - 31, yoffset, 0, 0, 85, 55);
        for (final int slot : this.dialog.options.keySet()) {
            final DialogOption option = this.dialog.options.get(slot);
            if (option != null && option.optionType != 2) {
                if (!option.isAvailable((PlayerEntity)this.player)) {
                    continue;
                }
                int color = option.optionColor;
                if (slot == this.selected) {
                    color = 8622040;
                }
                final int height = ClientProxy.Font.height(option.title);
                if (slot == 0) {
                    drawString(matrixStack, this.font, option.title, this.width / 2 + 13, yoffset - height, color);
                }
                if (slot == 1) {
                    drawString(matrixStack, this.font, option.title, this.width / 2 + 33, yoffset - height / 2 + 14, color);
                }
                if (slot == 2) {
                    drawString(matrixStack, this.font, option.title, this.width / 2 + 27, yoffset + 27, color);
                }
                if (slot == 3) {
                    drawString(matrixStack, this.font, option.title, this.width / 2 - 13 - ClientProxy.Font.width(option.title), yoffset - height, color);
                }
                if (slot == 4) {
                    drawString(matrixStack, this.font, option.title, this.width / 2 - 33 - ClientProxy.Font.width(option.title), yoffset - height / 2 + 14, color);
                }
                if (slot != 5) {
                    continue;
                }
                drawString(matrixStack, this.font, option.title, this.width / 2 - 27 - ClientProxy.Font.width(option.title), yoffset + 27, color);
            }
        }
        this.minecraft.getTextureManager().bind(this.indicator);
        this.blit(matrixStack, this.width / 2 + (int)this.selectedX / 4 - 2, yoffset + 16 - (int)this.selectedY / 6, 0, 0, 8, 8);
    }
    
    private void drawLinedOptions(final MatrixStack matrixStack, final int j) {
        this.hLine(matrixStack, this.guiLeft - 60, this.guiLeft + this.imageWidth + 120, this.guiTop + this.dialogHeight - ClientProxy.Font.height(null) / 3, -1);
        final int offset = this.dialogHeight;
        if (j >= this.guiTop + offset) {
            final int selected = (j - (this.guiTop + offset)) / ClientProxy.Font.height(null);
            if (selected < this.options.size()) {
                this.selected = selected;
            }
        }
        if (this.selected >= this.options.size()) {
            this.selected = 0;
        }
        if (this.selected < 0) {
            this.selected = 0;
        }
        for (int k = 0; k < this.options.size(); ++k) {
            final int id = this.options.get(k);
            final DialogOption option = this.dialog.options.get(id);
            final int y = this.guiTop + offset + k * ClientProxy.Font.height(null);
            if (this.selected == k) {
                drawString(matrixStack, this.font, ">", this.guiLeft - 60, y, 14737632);
            }
            drawString(matrixStack, this.font, NoppesStringUtils.formatText(option.title, this.player, this.npc), this.guiLeft - 30, y, option.optionColor);
        }
    }
    
    private void drawString(final MatrixStack matrixStack, final String text, final int left, final int color, final int count) {
        final int height = count - this.rowStart;
        ClientProxy.Font.draw(matrixStack, text, this.guiLeft + left, this.guiTop + height * ClientProxy.Font.height(null), color);
    }
    
    private int getSelected() {
        if (this.selected <= 0) {
            return 0;
        }
        if (this.selected < this.options.size()) {
            return this.selected;
        }
        return this.options.size() - 1;
    }
    
    @Override
    public boolean keyPressed(final int key, final int p_keyPressed_2_, final int p_keyPressed_3_) {
        if (key == this.minecraft.options.keyUp.getKey().getValue() || key == InputMappings.getKey("key.keyboard.up").getValue()) {
            --this.selected;
        }
        if (key == this.minecraft.options.keyDown.getKey().getValue() || key == InputMappings.getKey("key.keyboard.down").getValue()) {
            ++this.selected;
        }
        if (key == InputMappings.getKey("key.keyboard.enter").getValue() || key == InputMappings.getKey("key.keyboard.keypad.enter").getValue()) {
            this.handleDialogSelection();
        }
        if (this.closeOnEsc && (key == InputMappings.getKey("key.keyboard.escape").getValue() || this.isInventoryKey(key))) {
            Packets.sendServer(new SPacketDialogSelected(this.dialog.id, -1));
            this.closed();
            this.onClose();
        }
        return true;
    }
    
    @Override
    public boolean mouseClicked(final double i, final double j, final int k) {
        if (((this.selected == -1 && this.options.isEmpty()) || this.selected >= 0) && k == 0) {
            this.handleDialogSelection();
        }
        return true;
    }
    
    private void handleDialogSelection() {
        int optionId = -1;
        if (this.dialog.showWheel) {
            optionId = this.selected;
        }
        else if (!this.options.isEmpty()) {
            optionId = this.options.get(this.selected);
        }
        Packets.sendServer(new SPacketDialogSelected(this.dialog.id, optionId));
        if (this.dialog == null || !this.dialog.hasOtherOptions() || this.options.isEmpty()) {
            if (this.closeOnEsc) {
                this.closed();
                this.onClose();
            }
            return;
        }
        final DialogOption option = this.dialog.options.get(optionId);
        if (option == null || option.optionType != 1) {
            if (this.closeOnEsc) {
                this.closed();
                this.onClose();
            }
            return;
        }
        this.lines.add(new TextBlockClient(this.player.getDisplayName().getString(), option.title, 280, option.optionColor, new Object[] { this.player, this.npc }));
        this.calculateRowHeight();
        NoppesUtil.clickSound();
    }
    
    private void closed() {
        this.grabMouse(false);
        Packets.sendServer(new SPacketQuestCompletionCheckAll());
    }
    
    public void appendDialog(final Dialog dialog) {
        this.closeOnEsc = !dialog.disableEsc;
        this.dialog = dialog;
        this.options = new ArrayList<Integer>();
        if (dialog.sound != null && !dialog.sound.isEmpty()) {
            MusicController.Instance.stopMusic();
            final BlockPos pos = this.npc.blockPosition();
            MusicController.Instance.playSound(SoundCategory.VOICE, dialog.sound, pos, 1.0f, 1.0f);
        }
        this.lines.add(new TextBlockClient(this.npc.createCommandSourceStack(), dialog.text, 280, 14737632, new Object[] { this.player, this.npc }));
        for (final int slot : dialog.options.keySet()) {
            final DialogOption option = dialog.options.get(slot);
            if (option != null) {
                if (!option.isAvailable((PlayerEntity)this.player)) {
                    continue;
                }
                this.options.add(slot);
            }
        }
        this.calculateRowHeight();
        this.grabMouse(dialog.showWheel);
    }
    
    private void calculateRowHeight() {
        if (this.dialog.showWheel) {
            this.dialogHeight = this.imageHeight - 58;
        }
        else {
            this.dialogHeight = this.imageHeight - 3 * ClientProxy.Font.height(null) - 4;
            if (this.dialog.options.size() > 3) {
                this.dialogHeight -= (this.dialog.options.size() - 3) * ClientProxy.Font.height(null);
            }
        }
        this.rowTotal = 0;
        for (final TextBlockClient block : this.lines) {
            this.rowTotal += block.lines.size() + 1;
        }
        final int max = this.dialogHeight / ClientProxy.Font.height(null);
        this.rowStart = this.rowTotal - max;
        if (this.rowStart < 0) {
            this.rowStart = 0;
        }
    }
    
    @Override
    public void setClose(final CompoundNBT data) {
        this.grabMouse(false);
    }
    
    @Override
    public void save() {
    }
}
