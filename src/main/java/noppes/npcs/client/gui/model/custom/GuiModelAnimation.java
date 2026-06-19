package noppes.npcs.client.gui.model.custom;

import net.minecraft.util.ResourceLocation;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;
import noppes.npcs.util.AnimationFileUtil;
import software.bernie.geckolib3.resource.GeckoLibCache;

public class GuiModelAnimation extends GuiNPCInterface implements ITextfieldListener {

    private static final int HINT_COLOR = 0xA0A0A0;
    private static final int ROW_HEIGHT = 34;

    @Override
    public void init() {
        super.init();
        int y = guiTop + 30;
        this.addLabel(new GuiLabel(100, "Анимации GeckoLib", guiLeft - 130, y, 0xffffff));
        y += 18;
        addSelectionBlock(1, y, "Файл анимаций", "Путь к .animation.json (все клипы модели)", npc.display.customModelData.getAnimFile());
        addSelectionBlock(2, y += ROW_HEIGHT, "Idle", "Стоит на месте — включается автоматически", npc.display.customModelData.getIdleAnim());
        addSelectionBlock(3, y += ROW_HEIGHT, "Walk", "Идёт — включается автоматически", npc.display.customModelData.getWalkAnim());
        addSelectionBlock(4, y += ROW_HEIGHT, "Attack", "При ударе в ближнем бою (авто)", npc.display.customModelData.getAttackAnim());
        addSelectionBlock(5, y += ROW_HEIGHT, "Hurt", "Пока не подключено — оставьте пустым", npc.display.customModelData.getHurtAnim());
        this.addButton(new GuiButtonNop(this, 670, width - 22, 2, 20, 20, "X"));
    }

    private void addSelectionBlock(int id, int y, String label, String desc, String value) {
        this.addLabel(new GuiLabel(id, label, guiLeft - 130, y + 2, 0xffffff));
        this.addLabel(new GuiLabel(id + 100, desc, guiLeft - 130, y + 12, HINT_COLOR));
        addTextField(new GuiTextFieldNop(id, this, guiLeft - 40, y + 4, 200, 20, value));
        this.addButton(new GuiButtonNop(this, id, guiLeft + 163, y + 4, 80, 20, "mco.template.button.select"));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if(button.id == 670){
            close();
        }
        if(button.id==1){
            setSubGui(new GuiStringSelection(this,"Selecting geckolib animation file:",
                    AnimationFileUtil.getAnimationFileList(), (name)-> npc.display.customModelData.setAnimFile(name)));
        }
        if(button.id==2){
            setSubGui(new GuiStringSelection(this,"Selecting geckolib idle animation:",
                    AnimationFileUtil.getAnimationList(npc.display.customModelData.getAnimFile()),
                    (name)-> npc.display.customModelData.setIdleAnim(name)));
        }
        if(button.id==3){
            setSubGui(new GuiStringSelection(this,"Selecting geckolib walk animation:",
                    AnimationFileUtil.getAnimationList(npc.display.customModelData.getAnimFile()),
                    (name)-> npc.display.customModelData.setWalkAnim(name)));
        }
        if(button.id==4){
            setSubGui(new GuiStringSelection(this,"Selecting geckolib attack animation:",
                    AnimationFileUtil.getAnimationList(npc.display.customModelData.getAnimFile()),
                    (name)-> npc.display.customModelData.setAttackAnim(name)));
        }
        if(button.id==5){
            setSubGui(new GuiStringSelection(this,"Selecting geckolib hurt animation:",
                    AnimationFileUtil.getAnimationList(npc.display.customModelData.getAnimFile()),
                    (name)-> npc.display.customModelData.setHurtAnim(name)));
        }
    }

    public boolean isValidAnimFile(String name){
        return GeckoLibCache.getInstance().getAnimations().containsKey(new ResourceLocation(name));
    }

    public boolean isValidAnimation(String name){
        return AnimationFileUtil.getAnimationList(npc.display.customModelData.getAnimFile()).contains(name);
    }

    @Override
    public void unFocused(GuiTextFieldNop textfield) {
        if(textfield.id == 1 && isValidAnimFile(textfield.getValue())){
            if(!textfield.isEmpty())
                npc.display.customModelData.setAnimFile(textfield.getValue());
            else
                textfield.setValue(npc.display.customModelData.getAnimFile());
        }
        if(textfield.id == 2 && isValidAnimation(textfield.getValue())){
            if(!textfield.isEmpty())
                npc.display.customModelData.setIdleAnim(textfield.getValue());
            else
                textfield.setValue(npc.display.customModelData.getIdleAnim());
        }
        if(textfield.id == 3 && isValidAnimation(textfield.getValue())){
            if(!textfield.isEmpty())
                npc.display.customModelData.setWalkAnim(textfield.getValue());
            else
                textfield.setValue(npc.display.customModelData.getWalkAnim());
        }
        if(textfield.id == 4 && isValidAnimation(textfield.getValue())){
            if(!textfield.isEmpty())
                npc.display.customModelData.setAttackAnim(textfield.getValue());
            else
                textfield.setValue(npc.display.customModelData.getAttackAnim());
        }
        if(textfield.id == 5 && isValidAnimation(textfield.getValue())){
            if(!textfield.isEmpty())
                npc.display.customModelData.setHurtAnim(textfield.getValue());
            else
                textfield.setValue(npc.display.customModelData.getHurtAnim());
        }
    }
}
