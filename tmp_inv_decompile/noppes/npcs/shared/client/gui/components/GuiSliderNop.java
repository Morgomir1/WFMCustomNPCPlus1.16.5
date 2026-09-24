/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.matrix.MatrixStack
 *  com.mojang.blaze3d.systems.RenderSystem
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.audio.SoundHandler
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.gui.widget.Widget
 *  net.minecraft.util.math.MathHelper
 *  net.minecraft.util.text.ITextComponent
 *  net.minecraft.util.text.TranslationTextComponent
 */
package noppes.npcs.shared.client.gui.components;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import noppes.npcs.shared.client.gui.listeners.ISliderListener;

public class GuiSliderNop
extends Widget {
    private ISliderListener listener;
    public int id;
    public float sliderValue = 1.0f;
    public float startValue = 1.0f;

    public GuiSliderNop(Screen parent, int id, int xPos, int yPos, String displayString, float sliderValue) {
        super(xPos, yPos, 150, 20, (ITextComponent)new TranslationTextComponent(displayString));
        this.id = id;
        this.sliderValue = sliderValue;
        this.startValue = sliderValue;
        this.listener = (ISliderListener)parent;
    }

    public GuiSliderNop(Screen parent, int id, int xPos, int yPos, float sliderValue) {
        this(parent, id, xPos, yPos, "", sliderValue);
        this.listener.mouseDragged(this);
    }

    public GuiSliderNop(Screen parent, int id, int xPos, int yPos, int width, int height, float sliderValue) {
        this(parent, id, xPos, yPos, "", sliderValue);
        this.width = width;
        this.height = height;
        this.listener.mouseDragged(this);
    }

    public void playDownSound(SoundHandler p_146113_1_) {
    }

    protected int getYImage(boolean p_getYImage_1_) {
        return 0;
    }

    public void setString(String str) {
        this.setMessage((ITextComponent)new TranslationTextComponent(str));
    }

    private void setSliderValue(float value) {
        if ((value = MathHelper.clamp((float)value, (float)0.0f, (float)1.0f)) == this.sliderValue) {
            return;
        }
        this.sliderValue = value;
        this.listener.mouseDragged(this);
    }

    public void onClick(double x, double y) {
        if (!this.visible || !this.active) {
            return;
        }
        this.setSliderValue((float)(x - (double)(this.x + 4)) / (float)(this.width - 8));
        super.onClick(x, y);
    }

    protected void onDrag(double x, double y, double p_onDrag_5_, double p_onDrag_7_) {
        this.setSliderValue((float)(x - (double)(this.x + 4)) / (float)(this.width - 8));
        super.onDrag(x, y, p_onDrag_5_, p_onDrag_7_);
    }

    public void onRelease(double x, double y) {
        if (this.sliderValue == this.startValue) {
            return;
        }
        super.playDownSound(Minecraft.getInstance().getSoundManager());
        this.listener.mouseReleased(this);
        this.startValue = this.sliderValue;
    }

    public void renderBg(MatrixStack matrixStack, Minecraft mc, int p_146119_2_, int p_146119_3_) {
        if (!this.visible) {
            return;
        }
        mc.getTextureManager().bind(WIDGETS_LOCATION);
        RenderSystem.color4f((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
        int lvt_4_1_ = (this.isHovered() ? 2 : 1) * 20;
        this.blit(matrixStack, this.x + (int)((double)this.sliderValue * (double)(this.width - 8)), this.y, 0, 46 + lvt_4_1_, 4, 20);
        this.blit(matrixStack, this.x + (int)((double)this.sliderValue * (double)(this.width - 8)) + 4, this.y, 196, 46 + lvt_4_1_, 4, 20);
    }
}
