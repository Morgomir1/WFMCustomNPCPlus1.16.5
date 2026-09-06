package noppes.npcs.client.gui.questtypes;

import java.util.TreeMap;

import net.minecraft.client.gui.screen.Screen;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.quests.QuestManual;
import noppes.npcs.quests.QuestObjectiveLimits;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

public class GuiNpcQuestTypeManual extends GuiNPCInterface implements ITextfieldListener {
    private final Screen parent;
    private final QuestManual quest;
    private GuiTextFieldNop lastSelected;
    private int visibleSlots = QuestObjectiveLimits.MIN_EDITOR_SLOTS;

    public GuiNpcQuestTypeManual(final EntityNPCInterface npc, final Quest q, final Screen parent) {
        this.npc = npc;
        this.parent = parent;
        this.title = "Quest Manual Setup";
        this.quest = (QuestManual) q.questInterface;
        this.setBackground("menubg.png");
        this.imageWidth = 356;
        this.imageHeight = 216;
    }

    @Override
    public void init() {
        this.visibleSlots = QuestObjectiveLimits.editorSlots(this.quest.manuals.size());
        final int extra = Math.max(0, this.visibleSlots - QuestObjectiveLimits.MIN_EDITOR_SLOTS);
        this.imageHeight = 216 + extra * 22;
        super.init();
        this.addLabel(new GuiLabel(0, "You can fill in npc or player names too", this.guiLeft + 4, this.guiTop + 50));
        int i = 0;
        for (final String name : this.quest.manuals.keySet()) {
            if (i >= this.visibleSlots) {
                break;
            }
            this.addTargetRow(i, name, String.valueOf(this.quest.manuals.get(name)));
            ++i;
        }
        while (i < this.visibleSlots) {
            this.addTargetRow(i, "", "1");
            ++i;
        }
        this.addButton(new GuiButtonNop(this, 0, this.guiLeft + 4, this.guiTop + 140 + extra * 22, 98, 20, "gui.back"));
    }

    private void addTargetRow(final int index, final String name, final String amount) {
        final int amountId = index + QuestObjectiveLimits.AMOUNT_ID_OFFSET;
        this.addTextField(new GuiTextFieldNop(index, this, this.guiLeft + 4, this.guiTop + 70 + index * 22, 180, 20, name));
        this.addTextField(new GuiTextFieldNop(amountId, this, this.guiLeft + 186, this.guiTop + 70 + index * 22, 24, 20, amount));
        this.getTextField(amountId).numbersOnly = true;
        this.getTextField(amountId).setMinMaxDefault(1, Integer.MAX_VALUE, 1);
    }

    @Override
    public void buttonEvent(final GuiButtonNop guibutton) {
        if (guibutton.id == 0) {
            this.close();
        }
    }

    @Override
    public void save() {
    }

    @Override
    public void unFocused(final GuiTextFieldNop guiNpcTextField) {
        if (guiNpcTextField.id < this.visibleSlots) {
            this.lastSelected = guiNpcTextField;
        }
        final int before = this.visibleSlots;
        this.saveTargets();
        if (QuestObjectiveLimits.editorSlots(this.quest.manuals.size()) != before) {
            this.init();
        }
    }

    private void saveTargets() {
        final TreeMap<String, Integer> map = new TreeMap<>();
        for (int i = 0; i < this.visibleSlots; ++i) {
            if (this.getTextField(i) == null || this.getTextField(i + QuestObjectiveLimits.AMOUNT_ID_OFFSET) == null) {
                continue;
            }
            final String name = this.getTextField(i).getValue();
            if (name.isEmpty()) {
                continue;
            }
            map.put(name, this.getTextField(i + QuestObjectiveLimits.AMOUNT_ID_OFFSET).getInteger());
        }
        this.quest.manuals = map;
    }
}
