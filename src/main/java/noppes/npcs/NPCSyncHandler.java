package noppes.npcs;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.controllers.VisibilityController;

import java.util.HashSet;
import java.util.Set;

/**
 * Обработчик событий для синхронизации NPC между клиентом и сервером
 * Исправляет баг, когда клиент выгружает сущности, а сервер не отправляет их повторно
 */
@Mod.EventBusSubscriber(modid = CustomNpcs.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NPCSyncHandler {
    
    private static final Set<ServerPlayerEntity> playersToSync = new HashSet<>();
    
    /**
     * Обработчик загрузки чанка - синхронизирует NPC для игроков
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getWorld() instanceof ServerWorld)) {
            return;
        }
        
        ServerWorld serverWorld = (ServerWorld) event.getWorld();
        
        // Получаем всех игроков в мире и планируем синхронизацию
        for (ServerPlayerEntity player : serverWorld.getPlayers(p -> true)) {
            scheduleNPCSync(player);
        }
    }
    
    /**
     * Обработчик тика сервера - выполняет запланированную синхронизацию NPC
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || playersToSync.isEmpty()) {
            return;
        }
        
        // Синхронизируем NPC для всех игроков в очереди
        Set<ServerPlayerEntity> toSync = new HashSet<>(playersToSync);
        playersToSync.clear();
        
        for (ServerPlayerEntity player : toSync) {
            if (player != null && player.isAlive() && player.level instanceof ServerWorld) {
                syncNPCsForPlayer(player);
            }
        }
    }
    
    /**
     * Планирует синхронизацию NPC для игрока
     */
    private static void scheduleNPCSync(ServerPlayerEntity player) {
        if (player != null && player.isAlive()) {
            playersToSync.add(player);
        }
    }
    
    /**
     * Синхронизирует NPC для игрока - обновляет видимость через VisibilityController
     */
    private static void syncNPCsForPlayer(ServerPlayerEntity player) {
        if (!(player.level instanceof ServerWorld)) {
            return;
        }
        
        // Используем VisibilityController для обновления видимости NPC
        // Это исправляет проблему, когда клиент выгрузил сущности,
        // а сервер думает что они еще у клиента
        if (VisibilityController.instance != null) {
            VisibilityController.instance.onUpdate(player);
        }
    }
}
