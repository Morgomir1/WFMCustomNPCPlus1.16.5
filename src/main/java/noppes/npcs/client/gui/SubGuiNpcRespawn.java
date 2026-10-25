package noppes.npcs.client.gui;

import noppes.npcs.entity.data.*;
import noppes.npcs.shared.client.gui.listeners.*;
import noppes.npcs.shared.client.gui.components.*;
import net.minecraft.client.gui.screen.*;

public class SubGuiNpcRespawn extends GuiBasic implements ITextfieldListener
{
    private DataStats stats;
    
    public SubGuiNpcRespawn(final DataStats stats) {
        this.stats = stats;
        this.setBackground("menubg.png");
        this.imageWidth = 256;
        this.imageHeight = 216;
    }
    
    @Override
    public void init() {
        super.init();
        this.addLabel(new GuiLabel(0, "stats.respawn", this.guiLeft + 5, this.guiTop + 35));
        this.addButton(new GuiButtonBiDirectional(this, 0, this.guiLeft + 122, this.guiTop + 30, 80, 20, new String[] { "gui.yes", "gui.day", "gui.night", "gui.no", "stats.naturally" }, this.stats.spawnCycle));
        if (this.stats.respawnTime > 0) {
            this.addLabel(new GuiLabel(3, "gui.time", this.guiLeft + 5, this.guiTop + 57));
            this.addTextField(new GuiTextFieldNop(2, this, this.guiLeft + 122, this.guiTop + 53, 50, 18, this.stats.respawnTime + ""));
            this.getTextField(2).numbersOnly = true;
            this.getTextField(2).setMinMaxDefault(1, Integer.MAX_VALUE, 20);
            this.addLabel(new GuiLabel(4, "stats.deadbody", this.guiLeft + 4, this.guiTop + 79));
            this.addButton(new GuiButtonNop(this, 4, this.guiLeft + 122, this.guiTop + 74, 60, 20, new String[] { "gui.no", "gui.yes" }, (int)(this.stats.hideKilledBody ? 1 : 0)));
        }
        this.addButton(new GuiButtonNop(this, 66, this.guiLeft + 82, this.guiTop + 190, 98, 20, "gui.done"));
    }
    
    @Override
    public void buttonEvent(final GuiButtonNop guibutton) {
        final int id = guibutton.id;
        final GuiButtonNop button = guibutton;
        if (button.id == 0) {
            this.stats.spawnCycle = button.getValue();
            if (this.stats.spawnCycle == 3 || this.stats.spawnCycle == 4) {
                this.stats.respawnTime = 0;
            }
            else {
                this.stats.respawnTime = 20;
            }
            this.init();
        }
        else if (button.id == 4) {
            this.stats.hideKilledBody = (button.getValue() == 1);
        }
        if (id == 66) {
            this.close();
        }
    }
    
    @Override
    public void unFocused(final GuiTextFieldNop textfield) {
        if (textfield.id == 2) {
            this.stats.respawnTime = textfield.getInteger();
        }
    }
}
