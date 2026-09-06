package noppes.npcs.client.gui.player;

import noppes.npcs.client.gui.util.*;
import net.minecraft.util.*;
import noppes.npcs.controllers.data.*;
import net.minecraft.entity.player.*;
import noppes.npcs.controllers.*;
import micdoodle8.mods.galacticraft.api.client.tabs.*;
import noppes.npcs.shared.client.gui.listeners.*;
import noppes.npcs.shared.client.gui.components.*;
import java.util.*;
import com.mojang.blaze3d.matrix.*;
import com.mojang.blaze3d.systems.*;
import noppes.npcs.client.*;
import net.minecraft.util.text.*;
import net.minecraft.client.gui.widget.button.*;
import net.minecraft.client.gui.widget.*;

public class GuiFaction extends GuiNPCInterface
{
    private int imageWidth;
    private int imageHeight;
    private int guiLeft;
    private int guiTop;
    private ArrayList<Faction> playerFactions;
    private PlayerFactionData data;
    private int page;
    private int pages;
    private GuiButtonNextPage buttonNextPage;
    private GuiButtonNextPage buttonPreviousPage;
    private ResourceLocation indicator;
    
    public GuiFaction() {
        this.playerFactions = new ArrayList<Faction>();
        this.page = 0;
        this.pages = 1;
        this.imageWidth = 256;
        this.imageHeight = 256;
        this.drawDefaultBackground = false;
        this.title = "";
        this.indicator = this.getResource("standardbg.png");
    }
    
    @Override
    public void init() {
        super.init();
        this.data = PlayerData.get((PlayerEntity)this.player).factionData;
        this.playerFactions = new ArrayList<Faction>();
        for (final int id : this.data.factionData.keySet()) {
            final Faction faction = FactionController.instance.getFaction(id);
            if (faction != null) {
                if (faction.hideFaction) {
                    continue;
                }
                this.playerFactions.add(faction);
            }
        }
        this.pages = (this.playerFactions.size() - 1) / 5;
        ++this.pages;
        this.page = 1;
        this.guiLeft = (this.width - this.imageWidth) / 2;
        this.guiTop = (this.height - this.imageHeight) / 2 + 12;
        TabRegistry.updateTabValues(this.guiLeft, this.guiTop + 8, InventoryTabFactions.class);
        TabRegistry.addTabsToList(button -> this.addButton(button));
        this.addButton(this.buttonNextPage = new GuiButtonNextPage(this, 1, this.guiLeft + this.imageWidth - 43, this.guiTop + 180, true, button -> {
            ++this.page;
            this.updateButtons();
        }));
        this.addButton(this.buttonPreviousPage = new GuiButtonNextPage(this, 2, this.guiLeft + 20, this.guiTop + 180, false, button -> {
            --this.page;
            this.updateButtons();
        }));
        this.updateButtons();
    }
    
    @Override
    public void render(final MatrixStack matrixStack, final int mouseX, final int mouseY, final float partialTicks) {
        this.renderBackground(matrixStack);
        RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);
        this.minecraft.getTextureManager().bind(this.indicator);
        this.blit(matrixStack, this.guiLeft, this.guiTop + 8, 0, 0, 256, 256);
        if (this.playerFactions.isEmpty()) {
            final ITextComponent noFaction = (ITextComponent)new TranslationTextComponent("faction.nostanding");
            this.font.draw(matrixStack, noFaction, (float)(this.guiLeft + (this.imageWidth - this.font.width((ITextProperties)noFaction)) / 2), (float)(this.guiTop + 80), CustomNpcResourceListener.DefaultTextColor);
        }
        else {
            this.renderScreen(matrixStack);
        }
        super.render(matrixStack, mouseX, mouseY, partialTicks);
    }
    
    private void renderScreen(final MatrixStack matrixStack) {
        int size = 5;
        if (this.playerFactions.size() % 5 != 0 && this.page == this.pages) {
            size = this.playerFactions.size() % 5;
        }
        for (int id = 0; id < size; ++id) {
            this.hLine(matrixStack, this.guiLeft + 2, this.guiLeft + this.imageWidth, this.guiTop + 14 + id * 30, -16777216 + CustomNpcResourceListener.DefaultTextColor);
            final Faction faction = this.playerFactions.get((this.page - 1) * 5 + id);
            final ITextComponent name = (ITextComponent)new TranslationTextComponent(faction.name);
            final int current = this.data.factionData.get(faction.id);
            String points = " : " + current;
            ITextComponent standing = (ITextComponent)new TranslationTextComponent("faction.friendly");
            int color = 65280;
            if (current < faction.neutralPoints) {
                standing = (ITextComponent)new TranslationTextComponent("faction.unfriendly");
                color = 16711680;
                points = points + "/" + faction.neutralPoints;
            }
            else if (current < faction.friendlyPoints) {
                standing = (ITextComponent)new TranslationTextComponent("faction.neutral");
                color = 15924992;
                points = points + "/" + faction.friendlyPoints;
            }
            else {
                points += "/-";
            }
            this.font.draw(matrixStack, name, (float)(this.guiLeft + (this.imageWidth - this.font.width((ITextProperties)name)) / 2), (float)(this.guiTop + 19 + id * 30), faction.color);
            this.font.draw(matrixStack, standing, (float)(this.width / 2 - this.font.width((ITextProperties)standing) - 1), (float)(this.guiTop + 33 + id * 30), color);
            this.font.draw(matrixStack, points, (float)(this.width / 2), (float)(this.guiTop + 33 + id * 30), CustomNpcResourceListener.DefaultTextColor);
        }
        this.hLine(matrixStack, this.guiLeft + 2, this.guiLeft + this.imageWidth, this.guiTop + 14 + size * 30, -16777216 + CustomNpcResourceListener.DefaultTextColor);
        if (this.pages > 1) {
            final String s = this.page + "/" + this.pages;
            this.font.draw(matrixStack, s, (float)(this.guiLeft + (this.imageWidth - this.font.width(s)) / 2), (float)(this.guiTop + 203), CustomNpcResourceListener.DefaultTextColor);
        }
    }
    
    @Override
    public void buttonEvent(final GuiButtonNop guibutton) {
        if (!(guibutton instanceof GuiButtonNextPage)) {
            return;
        }
        final int id = guibutton.id;
        if (id == 1) {
            ++this.page;
        }
        if (id == 2) {
            --this.page;
        }
        this.updateButtons();
    }
    
    private void updateButtons() {
        this.buttonNextPage.visible = (this.page < this.pages);
        this.buttonPreviousPage.visible = (this.page > 1);
    }
    
    @Override
    public void save() {
    }
}
