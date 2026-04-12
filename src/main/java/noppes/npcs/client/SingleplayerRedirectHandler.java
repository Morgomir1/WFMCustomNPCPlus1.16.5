package noppes.npcs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MultiplayerScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.WorldSelectionScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Костыль: перехватывает открытие экрана одиночной игры и заменяет его на экран сетевой игры
 */
@Mod.EventBusSubscriber(modid = noppes.npcs.CustomNpcs.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class SingleplayerRedirectHandler {
    
    /**
     * Перехватывает открытие экрана выбора мира (одиночная игра) и заменяет его на экран сетевой игры
     */
    @SubscribeEvent
    public static void onGuiOpen(GuiOpenEvent event) {
        // Проверяем, открывается ли экран выбора мира (одиночная игра)
        //if (event.getGui() instanceof WorldSelectionScreen) {
        //    // Получаем текущий экран (родительский экран, обычно главное меню)
        //    Screen parentScreen = Minecraft.getInstance().screen;
        //    // Заменяем на экран сетевой игры, передавая родительский экран
        //    event.setGui(new MultiplayerScreen(parentScreen));
        //}
    }
}
