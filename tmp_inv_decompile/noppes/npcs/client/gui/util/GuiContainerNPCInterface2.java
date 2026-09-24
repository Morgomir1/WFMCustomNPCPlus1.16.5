/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.matrix.MatrixStack
 *  net.minecraft.entity.player.PlayerInventory
 *  net.minecraft.inventory.container.Container
 *  net.minecraft.util.ResourceLocation
 *  net.minecraft.util.text.ITextComponent
 */
package noppes.npcs.client.gui.util;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import noppes.npcs.client.gui.util.GuiNpcMenu;
import noppes.npcs.entity.EntityNPCInterface;

public abstract class GuiContainerNPCInterface2<T extends Container>
extends GuiContainerNPCInterface<T> {
    private ResourceLocation background = new ResourceLocation("customnpcs", "textures/gui/menubg.png");
    private final ResourceLocation defaultBackground = new ResourceLocation("customnpcs", "textures/gui/menubg.png");
    private GuiNpcMenu menu;
    public int menuYOffset = 0;

    public GuiContainerNPCInterface2(EntityNPCInterface npc, T cont, PlayerInventory inv, ITextComponent titleIn) {
        this(npc, cont, inv, titleIn, -1);
    }

    public GuiContainerNPCInterface2(EntityNPCInterface npc, T cont, PlayerInventory inv, ITextComponent titleIn, int activeMenu) {
        super(npc, cont, inv, titleIn);
        this.imageWidth = 420;
        this.menu = new GuiNpcMenu(this, activeMenu, npc);
        this.title = "";
    }

    public void setBackground(String texture) {
        this.background = new ResourceLocation("customnpcs", "textures/gui/" + texture);
    }

    @Override
    public ResourceLocation getResource(String texture) {
        return new ResourceLocation("customnpcs", "textures/gui/" + texture);
    }

    @Override
    public void init() {
        super.init();
        this.menu.initGui(this.guiLeft, this.guiTop + this.menuYOffset, this.imageWidth);
    }

    @Override
    public boolean mouseClicked(double i, double j, int k) {
        if (!this.hasSubGui()) {
            this.menu.mouseClicked(i, j, k);
        }
        return super.mouseClicked(i, j, k);
    }

    public void delete() {
        this.npc.delete();
        this.setScreen(null);
        this.minecraft.mouseHandler.grabMouse();
    }

    @Override
    protected void renderBg(MatrixStack matrixStack, float partialTicks, int x, int y) {
        this.renderBackground(matrixStack);
        this.minecraft.getTextureManager().bind(this.background);
        this.blit(matrixStack, this.guiLeft, this.guiTop, 0, 0, 256, 256);
        this.minecraft.getTextureManager().bind(this.defaultBackground);
        this.blit(matrixStack, this.guiLeft + this.imageWidth - 200, this.guiTop, 26, 0, 200, 220);
        this.menu.drawElements(matrixStack, this.font, x, y, this.minecraft, partialTicks);
        super.renderBg(matrixStack, partialTicks, x, y);
    }
}
