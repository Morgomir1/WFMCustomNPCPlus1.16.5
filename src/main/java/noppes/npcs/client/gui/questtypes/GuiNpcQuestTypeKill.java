package noppes.npcs.client.gui.questtypes;

import java.util.ArrayList;
import java.util.TreeMap;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.world.World;
import noppes.npcs.client.EntityUtil;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.quests.QuestKill;
import noppes.npcs.quests.QuestObjectiveLimits;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScroll;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

public class GuiNpcQuestTypeKill extends GuiNPCInterface implements ITextfieldListener, ICustomScrollListener {
    private final Screen parent;
    private GuiCustomScroll scroll;
    private final QuestKill quest;
    private GuiTextFieldNop lastActive;
    private int visibleSlots = QuestObjectiveLimits.MIN_EDITOR_SLOTS;

    public GuiNpcQuestTypeKill(final EntityNPCInterface npc, final Quest q, final Screen parent) {
        this.npc = npc;
        this.parent = parent;
        this.title = "Quest Kill Setup";
        this.quest = (QuestKill) q.questInterface;
        this.setBackground("menubg.png");
        this.imageWidth = 356;
        this.imageHeight = 216;
    }

    @Override
    public void init() {
        this.visibleSlots = QuestObjectiveLimits.editorSlots(this.quest.targets.size());
        final int extra = Math.max(0, this.visibleSlots - QuestObjectiveLimits.MIN_EDITOR_SLOTS);
        this.imageHeight = 216 + extra * 22;
        super.init();
        this.addLabel(new GuiLabel(0, "You can fill in npc or player names too", this.guiLeft + 4, this.guiTop + 50));
        int i = 0;
        for (final String name : this.quest.targets.keySet()) {
            if (i >= this.visibleSlots) {
                break;
            }
            this.addTargetRow(i, name, String.valueOf(this.quest.targets.get(name)));
            ++i;
        }
        while (i < this.visibleSlots) {
            this.addTargetRow(i, "", "1");
            ++i;
        }
        final ArrayList<String> list = new ArrayList<>();
        for (final EntityType<? extends Entity> ent : EntityUtil.getAllEntitiesClassesNoNpcs((World) this.minecraft.level).keySet()) {
            list.add(ent.getRegistryName().toString());
        }
        if (this.scroll == null) {
            this.scroll = new GuiCustomScroll(this, 0);
        }
        this.scroll.setList(list);
        this.scroll.setSize(130, 198 + extra * 22);
        this.scroll.guiLeft = this.guiLeft + 220;
        this.scroll.guiTop = this.guiTop + 14;
        this.addScroll(this.scroll);
        this.addButton(new GuiButtonNop(this, 0, this.guiLeft + 4, this.guiTop + 140 + extra * 22, 98, 20, "gui.back"));
        this.scroll.visible = false;
        this.lastActive = null;
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
    public boolean mouseClicked(final double i, final double j, final int k) {
        final boolean bo = super.mouseClicked(i, j, k);
        if (GuiTextFieldNop.isActive() && GuiTextFieldNop.getActive().id < this.visibleSlots) {
            this.scroll.visible = true;
            this.lastActive = GuiTextFieldNop.getActive();
        }
        return bo;
    }

    @Override
    public void save() {
    }

    @Override
    public void unFocused(final GuiTextFieldNop guiNpcTextField) {
        final int before = this.visibleSlots;
        this.saveTargets();
        if (QuestObjectiveLimits.editorSlots(this.quest.targets.size()) != before) {
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
        this.quest.targets = map;
    }

    @Override
    public void scrollClicked(final double i, final double j, final int k, final GuiCustomScroll guiCustomScroll) {
        if (this.lastActive != null) {
            this.lastActive.setValue(guiCustomScroll.getSelected());
            this.saveTargets();
        }
    }

    @Override
    public void scrollDoubleClicked(final String selection, final GuiCustomScroll scroll) {
    }
}
