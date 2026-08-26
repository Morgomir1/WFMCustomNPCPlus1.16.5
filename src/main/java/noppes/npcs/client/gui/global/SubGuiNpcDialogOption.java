package noppes.npcs.client.gui.global;

import noppes.npcs.client.CustomNpcResourceListener;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screen.Screen;
import noppes.npcs.client.gui.SubGuiColorSelector;
import noppes.npcs.client.gui.SubGuiNpcAvailability;
import noppes.npcs.client.gui.select.GuiDialogSelection;
import noppes.npcs.client.gui.util.DialogCommandLines;
import noppes.npcs.controllers.DialogController;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.DialogOption;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

public class SubGuiNpcDialogOption extends GuiBasic implements ITextfieldListener {
    private static final int CMD_FIELD_BASE = 100;
    private static final int CMD_REMOVE_BASE = 200;
    private static final int CMD_ADD_BUTTON = 50;
    private static final int AVAILABILITY_BUTTON = 51;

    private final DialogOption option;
    private final List<String> commandLines = new ArrayList<String>();
    public static int LastColor = 0xFFFFB6;

    public SubGuiNpcDialogOption(final DialogOption option) {
        this.option = option;
        this.commandLines.addAll(DialogCommandLines.split(option.command));
        this.setBackground("menubg.png");
        this.imageWidth = 256;
        this.imageHeight = 216;
    }

    @Override
    public void init() {
        if (this.option.optionType == 4) {
            DialogCommandLines.ensureEditable(this.commandLines);
            final int extraRows = Math.max(0, this.commandLines.size() - 1);
            this.imageHeight = 216 + extraRows * 22 + 48;
        }
        else {
            this.imageHeight = 216;
        }

        super.init();
        this.addLabel(new GuiLabel(66, "dialog.editoption", this.guiLeft, this.guiTop + 4, this.imageWidth, 0));
        this.addLabel(new GuiLabel(0, "gui.title", this.guiLeft + 4, this.guiTop + 20));
        this.addTextField(new GuiTextFieldNop(0, this, this.guiLeft + 40, this.guiTop + 15, 196, 20, this.option.title));
        String color;
        for (color = Integer.toHexString(this.option.optionColor); color.length() < 6; color = 0 + color) {
        }
        this.addLabel(new GuiLabel(2, "gui.color", this.guiLeft + 4, this.guiTop + 45));
        this.addButton(new GuiButtonNop(this, 2, this.guiLeft + 62, this.guiTop + 40, 92, 20, color));
        this.getButton(2).setFGColor(this.option.optionColor);
        this.addLabel(new GuiLabel(1, "dialog.optiontype", this.guiLeft + 4, this.guiTop + 67));
        this.addButton(new GuiButtonNop(this, 1, this.guiLeft + 62, this.guiTop + 62, 92, 20,
            new String[] { "gui.close", "dialog.dialog", "gui.disabled", "menu.role", "block.minecraft.command_block" },
            this.option.optionType));

        if (this.option.optionType == 1) {
            this.addButton(new GuiButtonNop(this, 3, this.guiLeft + 4, this.guiTop + 84, "availability.selectdialog"));
            if (this.option.dialogId >= 0) {
                final Dialog dialog = DialogController.instance.dialogs.get(this.option.dialogId);
                if (dialog != null) {
                    this.getButton(3).setDisplayText(dialog.title);
                }
            }
        }

        int doneY = this.guiTop + 190;
        if (this.option.optionType == 4) {
            this.addLabel(new GuiLabel(4, "advMode.command", this.guiLeft + 4, this.guiTop + 84));
            int y = this.guiTop + 98;
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
            doneY = y + 54;
            this.addButton(new GuiButtonNop(this, AVAILABILITY_BUTTON, this.guiLeft + 53, doneY, 150, 20, "availability.available"));
            doneY += 24;
        }
        else {
            this.addButton(new GuiButtonNop(this, AVAILABILITY_BUTTON, this.guiLeft + 53, doneY - 24, 150, 20, "availability.available"));
        }

        this.addButton(new GuiButtonNop(this, 66, this.guiLeft + 82, doneY, 98, 20, "gui.done"));
    }

    @Override
    public void buttonEvent(final GuiButtonNop guibutton) {
        final int id = guibutton.id;
        if (id == 1) {
            this.saveCommandsFromFields();
            this.option.optionType = guibutton.getValue();
            if (this.option.optionType == 4) {
                DialogCommandLines.ensureEditable(this.commandLines);
            }
            this.init();
            return;
        }
        if (id == 2) {
            this.setSubGui(new SubGuiColorSelector(this.option.optionColor));
            return;
        }
        if (id == 3) {
            this.setSubGui(new GuiDialogSelection(this.option.dialogId));
            return;
        }
        if (id == AVAILABILITY_BUTTON) {
            this.saveCommandsFromFields();
            this.applyCommandsToOption();
            this.setSubGui(new SubGuiNpcAvailability(this.option.availability));
            return;
        }
        if (id == CMD_ADD_BUTTON) {
            this.saveCommandsFromFields();
            if (this.commandLines.size() < DialogCommandLines.MAX_COMMANDS) {
                this.commandLines.add("");
            }
            this.applyCommandsToOption();
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
            this.applyCommandsToOption();
            this.init();
            return;
        }
        if (id == 66) {
            this.saveCommandsFromFields();
            this.applyCommandsToOption();
            this.close();
        }
    }

    @Override
    public void unFocused(final GuiTextFieldNop textfield) {
        if (textfield.id == 0) {
            if (textfield.isEmpty()) {
                textfield.setValue(this.option.title);
            }
            else {
                this.option.title = textfield.getValue();
            }
            return;
        }
        if (textfield.id >= CMD_FIELD_BASE && textfield.id < CMD_FIELD_BASE + DialogCommandLines.MAX_COMMANDS) {
            final int index = textfield.id - CMD_FIELD_BASE;
            if (index >= 0 && index < this.commandLines.size()) {
                this.commandLines.set(index, textfield.getValue());
                this.applyCommandsToOption();
            }
        }
    }

    @Override
    public void subGuiClosed(final Screen subgui) {
        if (subgui instanceof SubGuiColorSelector) {
            final int color = ((SubGuiColorSelector)subgui).color;
            this.option.optionColor = color;
            SubGuiNpcDialogOption.LastColor = color;
        }
        if (subgui instanceof GuiDialogSelection) {
            final Dialog dialog = ((GuiDialogSelection)subgui).selectedDialog;
            if (dialog != null) {
                this.option.dialogId = dialog.id;
            }
        }
        this.init();
    }

    private void saveCommandsFromFields() {
        for (int i = 0; i < this.commandLines.size(); ++i) {
            final GuiTextFieldNop field = this.getTextField(CMD_FIELD_BASE + i);
            if (field != null) {
                this.commandLines.set(i, field.getValue());
            }
        }
    }

    private void applyCommandsToOption() {
        this.option.command = DialogCommandLines.join(this.commandLines);
    }
}
