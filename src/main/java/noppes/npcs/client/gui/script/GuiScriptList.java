package noppes.npcs.client.gui.script;

import java.util.ArrayList;
import java.util.List;
import noppes.npcs.controllers.ScriptContainer;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScroll;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/**
 * Левый список — доступные файлы, по алфавиту.
 * Правый — подключённые. Его порядок сверху вниз и есть порядок выполнения.
 */
public class GuiScriptList extends GuiBasic {
    private static final int ADD = 1;
    private static final int REMOVE = 2;
    private static final int ADD_ALL = 3;
    private static final int REMOVE_ALL = 4;
    private static final int MOVE_UP = 5;
    private static final int MOVE_DOWN = 6;
    private static final int DONE = 66;

    private GuiCustomScroll available;
    private GuiCustomScroll loaded;
    private final ScriptContainer container;
    private final List<String> scripts;

    public GuiScriptList(final List<String> scripts, final ScriptContainer container) {
        this.container = container;
        this.setBackground("menubg.png");
        this.imageWidth = 346;
        this.imageHeight = 216;
        this.scripts = scripts != null ? scripts : new ArrayList<String>();
    }

    @Override
    public void init() {
        super.init();
        if (this.available == null) {
            this.available = new GuiCustomScroll(this, 0);
            this.available.setSize(140, 180);
        }
        this.available.guiLeft = this.guiLeft + 4;
        this.available.guiTop = this.guiTop + 14;
        this.addScroll(this.available);
        this.addLabel(new GuiLabel(1, "script.availableScripts", this.guiLeft + 4, this.guiTop + 4));

        if (this.loaded == null) {
            this.loaded = new GuiCustomScroll(this, 1);
            this.loaded.setSize(140, 180);
        }
        this.loaded.guiLeft = this.guiLeft + 200;
        this.loaded.guiTop = this.guiTop + 14;
        this.addScroll(this.loaded);
        this.addLabel(new GuiLabel(2, "script.loadedScripts", this.guiLeft + 200, this.guiTop + 4));

        final List<String> free = new ArrayList<String>(this.scripts);
        free.removeAll(this.container.scripts);
        this.available.setList(free);
        // setList сортирует переданный список на месте. Правый список так не трогаем.
        this.loaded.setUnsortedList(this.container.scripts);

        this.addButton(new GuiButtonNop(this, ADD, this.guiLeft + 145, this.guiTop + 40, 55, 20, ">"));
        this.addButton(new GuiButtonNop(this, REMOVE, this.guiLeft + 145, this.guiTop + 62, 55, 20, "<"));
        this.addButton(new GuiButtonNop(this, ADD_ALL, this.guiLeft + 145, this.guiTop + 90, 55, 20, ">>"));
        this.addButton(new GuiButtonNop(this, REMOVE_ALL, this.guiLeft + 145, this.guiTop + 112, 55, 20, "<<"));
        this.addButton(new GuiButtonNop(this, MOVE_UP, this.guiLeft + 145, this.guiTop + 140, 55, 20, "^"));
        this.addButton(new GuiButtonNop(this, MOVE_DOWN, this.guiLeft + 145, this.guiTop + 162, 55, 20, "v"));
        this.addButton(new GuiButtonNop(this, DONE, this.guiLeft + 260, this.guiTop + 194, 60, 20, "gui.done"));
    }

    @Override
    public void buttonEvent(final GuiButtonNop button) {
        final int id = button.id;
        if (id == ADD && this.available.hasSelected()) {
            this.container.scripts.add(this.available.getSelected());
            this.available.clearSelection();
            this.loaded.clearSelection();
            this.init();
            return;
        }
        if (id == REMOVE && this.loaded.hasSelected()) {
            this.container.scripts.remove(this.loaded.getSelected());
            this.loaded.clearSelection();
            this.init();
            return;
        }
        if (id == ADD_ALL) {
            this.container.scripts.clear();
            for (final String script : this.scripts) {
                this.container.scripts.add(script);
            }
            this.available.clearSelection();
            this.loaded.clearSelection();
            this.init();
            return;
        }
        if (id == REMOVE_ALL) {
            this.container.scripts.clear();
            this.available.clearSelection();
            this.loaded.clearSelection();
            this.init();
            return;
        }
        if (id == MOVE_UP) {
            this.moveLoaded(-1);
            return;
        }
        if (id == MOVE_DOWN) {
            this.moveLoaded(1);
            return;
        }
        if (id == DONE) {
            this.close();
        }
    }

    private void moveLoaded(final int delta) {
        if (this.loaded == null || !this.loaded.hasSelected()) {
            return;
        }
        final String selected = this.loaded.getSelected();
        final List<String> list = this.container.scripts;
        final int index = list.indexOf(selected);
        final int target = index + delta;
        if (index < 0 || target < 0 || target >= list.size()) {
            return;
        }
        list.remove(index);
        list.add(target, selected);
        this.init();
        this.loaded.setSelected(selected);
    }

    @Override
    public void save() {
    }
}
