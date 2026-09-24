package noppes.npcs.client.gui.mainmenu;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Slot;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface2;
import noppes.npcs.constants.EnumMenuType;
import noppes.npcs.containers.ContainerNPCInv;
import noppes.npcs.entity.data.NpcDropInventoryLimits;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketMenuGet;
import noppes.npcs.packets.server.SPacketMenuSave;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiSliderNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGuiData;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import noppes.npcs.shared.client.gui.listeners.ISliderListener;

public class GuiNPCInv extends GuiContainerNPCInterface2<ContainerNPCInv> implements ISliderListener, IGuiData {
    private final HashMap<Integer, Integer> chances = new HashMap<>();
    private final ContainerNPCInv container;
    private final ResourceLocation slot;

    public GuiNPCInv(final ContainerNPCInv container, final PlayerInventory inv, final ITextComponent titleIn) {
        super(NoppesUtil.getLastNpc(), container, inv, titleIn, 4);
        this.setBackground("npcinv.png");
        this.container = container;
        this.imageHeight = 200;
        this.slot = this.getResource("slot.png");
        Packets.sendServer(new SPacketMenuGet(EnumMenuType.INVENTORY));
    }

    @Override
    public void init() {
        super.init();
        this.addLabel(new GuiLabel(0, "inv.minExp", this.guiLeft + 118, this.guiTop + 18));
        this.addTextField(new GuiTextFieldNop(0, (Screen) this, this.guiLeft + 108, this.guiTop + 29, 60, 20, this.npc.inventory.getExpMin() + ""));
        this.getTextField(0).numbersOnly = true;
        this.getTextField(0).setMinMaxDefault(0, Short.MAX_VALUE, 0);
        this.addLabel(new GuiLabel(1, "inv.maxExp", this.guiLeft + 118, this.guiTop + 52));
        this.addTextField(new GuiTextFieldNop(1, (Screen) this, this.guiLeft + 108, this.guiTop + 63, 60, 20, this.npc.inventory.getExpMax() + ""));
        this.getTextField(1).numbersOnly = true;
        this.getTextField(1).setMinMaxDefault(0, Short.MAX_VALUE, 0);
        this.addButton(new GuiButtonNop((IGuiInterface) this, 10, this.guiLeft + 88, this.guiTop + 88, 80, 20, new String[]{"stats.normal", "inv.auto"}, this.npc.inventory.lootMode));
        this.addLabel(new GuiLabel(2, "inv.npcInventory", this.guiLeft + NpcDropInventoryLimits.DROP_SLOT_X0, this.guiTop + 5));
        this.addLabel(new GuiLabel(3, "inv.inventory", this.guiLeft + 8, this.guiTop + 101));
        for (int i = 0; i < NpcDropInventoryLimits.DROP_SLOTS; ++i) {
            int chance = 100;
            if (this.npc.inventory.dropchance.containsKey(i)) {
                chance = this.npc.inventory.dropchance.get(i);
            }
            if (chance <= 0 || chance > 100) {
                chance = 100;
            }
            this.chances.put(i, chance);
            this.addSlider(new GuiSliderNop(
                    (Screen) this,
                    i,
                    this.guiLeft + NpcDropInventoryLimits.dropSliderX(i),
                    this.guiTop + NpcDropInventoryLimits.dropSliderY(i),
                    NpcDropInventoryLimits.DROP_SLIDER_WIDTH,
                    NpcDropInventoryLimits.DROP_SLIDER_HEIGHT,
                    (float) chance / 100.0f));
        }
    }

    @Override
    public void buttonEvent(final GuiButtonNop guibutton) {
        if (guibutton.id == 10) {
            this.npc.inventory.lootMode = guibutton.getValue();
        }
    }

    @Override
    protected void renderBg(final MatrixStack matrixStack, final float partialTicks, final int x, final int y) {
        super.renderBg(matrixStack, partialTicks, x, y);
        RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);
        this.minecraft.getTextureManager().bind(this.slot);
        for (int id = 4; id <= 6; ++id) {
            final Slot slot = this.container.getSlot(id);
            if (!slot.hasItem()) {
                continue;
            }
            this.blit(matrixStack, this.guiLeft + slot.x - 1, this.guiTop + slot.y - 1, 0, 0, 18, 18);
        }
    }

    @Override
    public void render(final MatrixStack matrixStack, final int mouseX, final int mouseY, final float partialTicks) {
        super.render(matrixStack, mouseX, mouseY, partialTicks);
        final int showname = this.npc.display.getShowName();
        this.npc.display.setShowName(1);
        this.drawNpc(50, 84);
        this.npc.display.setShowName(showname);
    }

    @Override
    public void save() {
        this.npc.inventory.dropchance = this.chances;
        this.npc.inventory.setExp(this.getTextField(0).getInteger(), this.getTextField(1).getInteger());
        Packets.sendServer(new SPacketMenuSave(EnumMenuType.INVENTORY, this.npc.inventory.save(new CompoundNBT())));
    }

    @Override
    public void setGuiData(final CompoundNBT compound) {
        this.npc.inventory.load(compound);
        this.init();
    }

    @Override
    public void mouseDragged(final GuiSliderNop guiNpcSlider) {
        guiNpcSlider.setMessage(new TranslationTextComponent("inv.dropChance").append(": " + (int) (guiNpcSlider.sliderValue * 100.0f) + "%"));
    }

    @Override
    public void mousePressed(final GuiSliderNop guiNpcSlider) {
    }

    @Override
    public void mouseReleased(final GuiSliderNop guiNpcSlider) {
        this.chances.put(guiNpcSlider.id, (int) (guiNpcSlider.sliderValue * 100.0f));
    }
}
