package noppes.npcs.client.gui.questtypes;

import java.util.HashMap;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.CompoundNBT;
import noppes.npcs.client.gui.select.GuiDialogSelection;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketQuestDialogTitles;
import noppes.npcs.quests.QuestDialog;
import noppes.npcs.quests.QuestObjectiveLimits;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.GuiSelectionListener;
import noppes.npcs.shared.client.gui.listeners.IGuiData;

public class GuiNpcQuestTypeDialog extends GuiNPCInterface implements GuiSelectionListener, IGuiData {
    private static final int SELECT_ID_BASE = 100;
    private static final int REMOVE_ID_BASE = 200;

    private final Screen parent;
    private final QuestDialog quest;
    private final HashMap<Integer, String> data = new HashMap<>();
    private int selectedSlot;
    private int visibleSlots = QuestObjectiveLimits.MIN_EDITOR_SLOTS;

    public GuiNpcQuestTypeDialog(final EntityNPCInterface npc, final Quest q, final Screen parent) {
        this.npc = npc;
        this.parent = parent;
        this.title = "Quest Dialog Setup";
        this.quest = (QuestDialog) q.questInterface;
        this.setBackground("menubg.png");
        this.imageWidth = 256;
        this.imageHeight = 216;
        Packets.sendServer(new SPacketQuestDialogTitles(this.quest.dialogs));
    }

    @Override
    public void init() {
        this.visibleSlots = this.computeSlotCount();
        final int extra = Math.max(0, this.visibleSlots - QuestObjectiveLimits.MIN_EDITOR_SLOTS);
        this.imageHeight = 216 + extra * 22;
        super.init();
        for (int i = 0; i < this.visibleSlots; ++i) {
            String title = "dialog.selectoption";
            if (this.data.containsKey(i)) {
                title = this.data.get(i);
            }
            this.addButton(new GuiButtonNop(this, REMOVE_ID_BASE + i, this.guiLeft + 10, this.guiTop + 40 + i * 22, 20, 20, "X"));
            this.addButton(new GuiButtonNop(this, SELECT_ID_BASE + i, this.guiLeft + 34, this.guiTop + 40 + i * 22, 210, 20, title));
        }
        this.addButton(new GuiButtonNop(this, 0, this.guiLeft + 150, this.guiTop + 190 + extra * 22, 98, 20, "gui.back"));
    }

    private int computeSlotCount() {
        int maxKey = -1;
        for (final Integer key : this.quest.dialogs.keySet()) {
            if (key != null && key > maxKey) {
                maxKey = key;
            }
        }
        return QuestObjectiveLimits.editorSlots(maxKey + 1);
    }

    @Override
    public void buttonEvent(final GuiButtonNop button) {
        if (button.id == 0) {
            this.close();
            return;
        }
        if (button.id >= SELECT_ID_BASE && button.id < SELECT_ID_BASE + QuestObjectiveLimits.MAX) {
            this.selectedSlot = button.id - SELECT_ID_BASE;
            int id = -1;
            if (this.quest.dialogs.containsKey(this.selectedSlot)) {
                id = this.quest.dialogs.get(this.selectedSlot);
            }
            this.setSubGui(new GuiDialogSelection(id));
            return;
        }
        if (button.id >= REMOVE_ID_BASE && button.id < REMOVE_ID_BASE + QuestObjectiveLimits.MAX) {
            final int slot = button.id - REMOVE_ID_BASE;
            this.quest.dialogs.remove(slot);
            this.data.remove(slot);
            this.init();
        }
    }

    @Override
    public void save() {
    }

    @Override
    public void selected(final int id, final String name) {
        this.quest.dialogs.put(this.selectedSlot, id);
        this.data.put(this.selectedSlot, name);
        this.init();
    }

    @Override
    public void setGuiData(final CompoundNBT compound) {
        this.data.clear();
        for (int i = 0; i < QuestObjectiveLimits.MAX; ++i) {
            final String key = Integer.toString(i);
            if (compound.contains(key)) {
                this.data.put(i, compound.getString(key));
            }
        }
        this.init();
    }
}
