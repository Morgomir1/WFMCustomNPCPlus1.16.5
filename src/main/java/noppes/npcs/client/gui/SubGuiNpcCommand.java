package noppes.npcs.client.gui;

import java.util.ArrayList;
import java.util.List;

import noppes.npcs.client.gui.util.DialogCommandLines;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

public class SubGuiNpcCommand extends GuiBasic implements ITextfieldListener {
    private static final int CMD_FIELD_BASE = 100;
    private static final int CMD_REMOVE_BASE = 200;
    private static final int CMD_ADD_BUTTON = 50;

    public String command;
    private final List<String> commandLines = new ArrayList<String>();

    public SubGuiNpcCommand(final String command) {
        this.command = command == null ? "" : command;
        this.commandLines.addAll(DialogCommandLines.split(this.command));
        this.setBackground("menubg.png");
        this.imageWidth = 256;
        this.imageHeight = 216;
    }

    @Override
    public void init() {
        DialogCommandLines.ensureEditable(this.commandLines);
        final int extraRows = Math.max(0, this.commandLines.size() - 1);
        this.imageHeight = 216 + extraRows * 22 + 24;

        super.init();
        this.addLabel(new GuiLabel(4, "advMode.command", this.guiLeft + 4, this.guiTop + 20));

        int y = this.guiTop + 36;
        for (int i = 0; i < this.commandLines.size(); ++i) {
            final GuiTextFieldNop field = new GuiTextFieldNop(CMD_FIELD_BASE + i, this, this.guiLeft + 4, y, 226, 20, this.commandLines.get(i));
            field.setMaxLength(32767);
            this.addTextField(field);
            this.addButton(new GuiButtonNop(this, CMD_REMOVE_BASE + i, this.guiLeft + 232, y, 20, 20, "X"));
            y += 22;
        }
        if (this.commandLines.size() < DialogCommandLines.MAX_COMMANDS) {
            this.addButton(new GuiButtonNop(this, CMD_ADD_BUTTON, this.guiLeft + 4, y, 98, 20, "gui.add"));
            y += 24;
        }

        this.addLabel(new GuiLabel(5, "advMode.nearestPlayer", this.guiLeft + 4, y));
        this.addLabel(new GuiLabel(6, "advMode.randomPlayer", this.guiLeft + 4, y + 12));
        this.addLabel(new GuiLabel(7, "advMode.allPlayers", this.guiLeft + 4, y + 24));
        this.addLabel(new GuiLabel(8, "dialog.commandoptionplayer", this.guiLeft + 4, y + 36));
        this.addButton(new GuiButtonNop(this, 66, this.guiLeft + 82, y + 54, 98, 20, "gui.done"));
    }

    @Override
    public void buttonEvent(final GuiButtonNop guibutton) {
        final int id = guibutton.id;
        if (id == CMD_ADD_BUTTON) {
            this.saveCommandsFromFields();
            if (this.commandLines.size() < DialogCommandLines.MAX_COMMANDS) {
                this.commandLines.add("");
            }
            this.applyCommands();
            this.init();
            return;
        }
        if (id >= CMD_REMOVE_BASE && id < CMD_REMOVE_BASE + DialogCommandLines.MAX_COMMANDS) {
            this.saveCommandsFromFields();
            final int index = id - CMD_REMOVE_BASE;
            if (index >= 0 && index < this.commandLines.size()) {
                this.commandLines.remove(index);
            }
            DialogCommandLines.ensureEditable(this.commandLines);
            this.applyCommands();
            this.init();
            return;
        }
        if (id == 66) {
            this.saveCommandsFromFields();
            this.applyCommands();
            this.onClose();
        }
    }

    @Override
    public void unFocused(final GuiTextFieldNop textfield) {
        if (textfield.id >= CMD_FIELD_BASE && textfield.id < CMD_FIELD_BASE + DialogCommandLines.MAX_COMMANDS) {
            final int index = textfield.id - CMD_FIELD_BASE;
            if (index >= 0 && index < this.commandLines.size()) {
                this.commandLines.set(index, textfield.getValue());
                this.applyCommands();
            }
        }
    }

    private void saveCommandsFromFields() {
        for (int i = 0; i < this.commandLines.size(); ++i) {
            final GuiTextFieldNop field = this.getTextField(CMD_FIELD_BASE + i);
            if (field != null) {
                this.commandLines.set(i, field.getValue());
            }
        }
    }

    private void applyCommands() {
        this.command = DialogCommandLines.join(this.commandLines);
    }
}
