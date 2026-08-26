package noppes.npcs.shared.client.gui.components;

import noppes.npcs.client.CustomNpcResourceListener;

import net.minecraft.util.*;
import noppes.npcs.shared.client.gui.listeners.*;
import com.mojang.blaze3d.matrix.*;
import net.minecraft.client.*;
import com.mojang.blaze3d.systems.*;

public class GuiButtonBiDirectional extends GuiButtonNop
{
    public static final ResourceLocation resource;
    private int color;
    
    public GuiButtonBiDirectional(final IGuiInterface gui, final int id, final int x, final int y, final int width, final int height, final String[] arr, final int current) {
        super(gui, id, x, y, width, height, arr, current);
        this.color = 16777215;
    }
    
    public GuiButtonBiDirectional(final IGuiInterface gui, final int id, final int x, final int y, final int width, final int height, final int current, final String... arr) {
        super(gui, id, x, y, width, height, arr, current);
        this.color = 16777215;
    }
    
    @Override
    public void render(final MatrixStack matrixStack, final int mouseX, final int mouseY, final float partialTicks) {
        if (!this.visible) {
            return;
        }
        final boolean hover = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
        final boolean hoverL = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + 14 && mouseY < this.y + this.height;
        final boolean hoverR = !hoverL && mouseX >= this.x + this.width - 14 && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
        final Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().bind(GuiButtonBiDirectional.resource);
        RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);
        this.blit(matrixStack, this.x, this.y, 0, hoverL ? 40 : 20, 11, 20);
        this.blit(matrixStack, this.x + this.width - 11, this.y, 11, ((hover && !hoverL) || hoverR) ? 40 : 20, 11, 20);
        int l = this.color;
        if (this.packedFGColor != 0) {
            l = this.packedFGColor;
        }
        else if (!this.active) {
            l = CustomNpcResourceListener.DefaultTextColor;
        }
        else if (hover) {
            l = 16777120;
        }
        String text = "";
        final float maxWidth = (float)(this.width - 36);
        final String displayString = this.getMessage().getString();
        if (mc.font.width(displayString) > maxWidth) {
            for (int h = 0; h < displayString.length(); ++h) {
                final char c = displayString.charAt(h);
                text += c;
                if (mc.font.width(text) > maxWidth) {
                    break;
                }
            }
            text += "...";
        }
        else {
            text = displayString;
        }
        if (hover) {
            text = "§n" + text;
        }
        drawCenteredString(matrixStack, mc.font, text, this.x + this.width / 2, this.y + (this.height - 8) / 2, l);
    }
    
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        int value = this.getValue();
        if (this.isMouseOver(mouseX, mouseY) && this.display != null && this.display.length != 0) {
            final boolean hoverL = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + 14 && mouseY < this.y + this.height;
            final boolean hoverR = !hoverL && mouseX >= this.x + 14 && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            if (hoverR) {
                value = (value + 1) % this.display.length;
            }
            if (hoverL) {
                if (value <= 0) {
                    value = this.display.length;
                }
                --value;
            }
            this.setDisplay(value);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public void onClick(final double x, final double y) {
        if (this.gui.hasSubGui()) {
            return;
        }
        this.gui.buttonEvent(this);
    }
    
    static {
        resource = new ResourceLocation("customnpcs", "textures/gui/arrowbuttons.png");
    }
}
