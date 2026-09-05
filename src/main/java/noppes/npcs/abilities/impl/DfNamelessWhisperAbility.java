package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.telegraph.TelegraphAPI;
import noppes.npcs.zone.ZoneAPI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Phase 3: three overlapping expanding rings from the boss (blind + wither). */
public final class DfNamelessWhisperAbility implements CnpcAbility {
    public static final String ID = "df_nameless_whisper";
    private static final int DEFAULT_RING_COUNT = 3;
    private static final int DEFAULT_RING_INTERVAL = 40;
    private static final ConcurrentHashMap<UUID, CastState> STATE_BY_NPC = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }

    @Override
    public boolean cancelsOnTargetLost() {
        return false;
    }

    @Override
    public Map<String, Object> defaultParams() {
        return AbilityDefaults.dfNamelessWhisper();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.INNER_RADIUS,
                AbilityParamKeys.ARC_HEIGHT,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.EFFECT_AMPLIFIER,
                AbilityParamKeys.TRAIL_TICKS,
                AbilityParamKeys.SHOT_INTERVAL,
                AbilityParamKeys.SUMMON_COUNT,
                AbilityParamKeys.ZONE_COLOR,
                AbilityParamKeys.TELEGRAPH_COLOR,
                AbilityParamKeys.TELEGRAPH);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();
        active.hitUuids.clear();
        active.markers.clear();
        active.meter = 0.0F;
        active.elapsedTicks = 0;
        STATE_BY_NPC.put(active.npcUuid, new CastState());

        final int charge = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 24));
        final double arenaR = ctx.params.getDouble(AbilityParamKeys.RADIUS, 12.0);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, 0xC0FF3030);
        final String warnId = TelegraphAPI.circle(
                ctx.npc, active.sx, active.sy, active.sz, arenaR, charge, color);
        if (warnId != null && !warnId.isEmpty()) {
            active.telegraphId = warnId;
            active.telegraphIds.add(warnId);
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = charge;
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.vex.ambient", 0.95F, 0.5F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            active.ticksLeft--;
            if (active.ticksLeft > 0) {
                return TickResult.CONTINUE;
            }
            AbilityTelegraph.clear(active, ctx);
            beginWave(active, ctx, 0);
            active.phase = ActiveAbility.PHASE_ACTIVE;
            return TickResult.CONTINUE;
        }
        return tickWaves(active, ctx);
    }

    private TickResult tickWaves(final ActiveAbility active, final AbilityContext ctx) {
        final CastState state = STATE_BY_NPC.computeIfAbsent(active.npcUuid, ignored -> new CastState());
        state.elapsedTicks++;
        while (state.nextWave < ringCount(ctx)
                && state.elapsedTicks >= state.nextWave * ringInterval(ctx)) {
            beginWave(active, ctx, state.nextWave);
        }

        final int total = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 60));
        final double arenaR = ctx.params.getDouble(AbilityParamKeys.RADIUS, 12.0);
        final double thickness = ctx.params.getDouble(AbilityParamKeys.INNER_RADIUS, 1.333);
        for (final WaveState wave : state.waves) {
            if (wave.finished) {
                continue;
            }
            wave.elapsedTicks++;
            final double progress = Math.min(1.0, wave.elapsedTicks / (double) total);
            final double inner = progress * arenaR;
            final double outer = Math.min(arenaR + thickness, inner + thickness);
            updateZoneRadii(wave, ctx, active, outer, inner);
            tickHit(wave, active, ctx, inner, outer);
            AbilityVfx.spawnDarkSoulRing(ctx.world, active.sx, active.sy + 0.05, active.sz, inner, outer);
            if (wave.elapsedTicks >= total) {
                clearZone(wave, ctx);
                wave.finished = true;
            }
        }
        if (state.nextWave >= ringCount(ctx) && allWavesFinished(state)) {
            ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.wither.shoot", 0.7F, 1.3F);
            return TickResult.FINISHED;
        }
        return TickResult.CONTINUE;
    }

    private void beginWave(final ActiveAbility active, final AbilityContext ctx, final int waveIndex) {
        final CastState state = STATE_BY_NPC.computeIfAbsent(active.npcUuid, ignored -> new CastState());
        final WaveState wave = new WaveState();
        state.waves.add(wave);
        state.nextWave = waveIndex + 1;
        active.meter = waveIndex;
        spawnRing(wave, active, ctx);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.wither.shoot", 0.65F, 0.85F + waveIndex * 0.15F);
    }

    private void tickHit(
            final WaveState wave,
            final ActiveAbility active,
            final AbilityContext ctx,
            final double inner,
            final double outer) {
        final int blindDur = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 40);
        final int witherDur = ctx.params.getInt(AbilityParamKeys.TRAIL_TICKS, 60);
        final int witherAmp = ctx.params.getInt(AbilityParamKeys.EFFECT_AMPLIFIER, 0);
        final double hitHeight = Math.max(0.25, ctx.params.getDouble(AbilityParamKeys.ARC_HEIGHT, 1.0));
        final double groundY =
                AbilityCombatHelper.findGroundY(ctx.world, active.sx, active.sz, active.sy);
        final int range = (int) Math.ceil(outer + 1.0);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(active.sx, active.sy, active.sz),
                range,
                -1);
        for (final IEntity ent : list) {
            if (!AbilityCombatHelper.isHostileToBoss(ctx.npc, ent)) {
                continue;
            }
            final String id = String.valueOf(ent.getUUID());
            if (wave.hitUuids.contains(id)) {
                continue;
            }
            final double dist = AbilityCombatHelper.flatDistance(
                    ent.getX(), ent.getZ(), active.sx, active.sz);
            if (dist < inner || dist > outer) {
                continue;
            }
            if (ent.getY() - groundY > hitHeight) {
                continue;
            }
            AbilityCombatHelper.applyEffect(
                    ent, AbilityEffectType.BLINDNESS.toMcEffect(), blindDur, 0);
            AbilityCombatHelper.applyEffect(
                    ent, AbilityEffectType.WITHER.toMcEffect(), witherDur, witherAmp);
            AbilityVfx.spawnHitParticle(ctx.world, ent);
            wave.hitUuids.add(id);
        }
    }

    private void spawnRing(
            final WaveState wave,
            final ActiveAbility active,
            final AbilityContext ctx) {
        final double y = AbilityCombatHelper.findGroundY(ctx.world, active.sx, active.sz, active.sy) + 0.05;
        final int color = ctx.params.getInt(AbilityParamKeys.ZONE_COLOR, 0xC0FF3030);
        final float hitHeight =
                (float) Math.max(0.25, ctx.params.getDouble(AbilityParamKeys.ARC_HEIGHT, 1.0));
        final EntityAbilityZone zone = ZoneAPI.hazardRing(
                ctx.npc, active.sx, y, active.sz, 2.0, 0.05, 40, 0, 999);
        if (zone == null) {
            return;
        }
        zone.setColor(color);
        zone.setZoneHeight(hitHeight);
        zone.setVisible(true);
        zone.setGroundFill(true);
        zone.setBorder(true);
        wave.zoneId = zone.getUUID();
    }

    private void updateZoneRadii(
            final WaveState wave,
            final AbilityContext ctx,
            final ActiveAbility active,
            final double outer,
            final double inner) {
        if (wave.zoneId == null) {
            return;
        }
        final EntityAbilityZone zone = resolveZone(ctx, wave.zoneId);
        if (zone == null) {
            return;
        }
        zone.setRadius((float) outer);
        zone.setInnerRadius((float) Math.max(0.05, inner));
        final double y = AbilityCombatHelper.findGroundY(ctx.world, active.sx, active.sz, active.sy) + 0.05;
        zone.moveTo(active.sx, y, active.sz, 0, 0);
    }

    private static EntityAbilityZone resolveZone(final AbilityContext ctx, final UUID zoneId) {
        try {
            final Object mc = ctx.npc.getMCEntity();
            if (!(mc instanceof Entity)) {
                return null;
            }
            final World world = ((Entity) mc).level;
            if (!(world instanceof ServerWorld)) {
                return null;
            }
            final Entity entity = ((ServerWorld) world).getEntity(zoneId);
            return entity instanceof EntityAbilityZone ? (EntityAbilityZone) entity : null;
        } catch (final Exception e) {
            return null;
        }
    }

    private static void clearZone(final WaveState wave, final AbilityContext ctx) {
        if (wave.zoneId == null) {
            return;
        }
        final EntityAbilityZone zone = resolveZone(ctx, wave.zoneId);
        ZoneAPI.remove(zone);
        wave.zoneId = null;
    }

    private static void clearState(final ActiveAbility active, final AbilityContext ctx) {
        final CastState state = STATE_BY_NPC.remove(active.npcUuid);
        if (state == null) {
            return;
        }
        for (final WaveState wave : state.waves) {
            clearZone(wave, ctx);
        }
    }

    private static boolean allWavesFinished(final CastState state) {
        for (final WaveState wave : state.waves) {
            if (!wave.finished) {
                return false;
            }
        }
        return true;
    }

    private static int ringCount(final AbilityContext ctx) {
        return Math.max(1, ctx.params.getInt(AbilityParamKeys.SUMMON_COUNT, DEFAULT_RING_COUNT));
    }

    private static int ringInterval(final AbilityContext ctx) {
        return Math.max(1, ctx.params.getInt(AbilityParamKeys.SHOT_INTERVAL, DEFAULT_RING_INTERVAL));
    }

    private static final class CastState {
        private final List<WaveState> waves = new ArrayList<>();
        private int elapsedTicks;
        private int nextWave;
    }

    private static final class WaveState {
        private final Set<String> hitUuids = new HashSet<>();
        private UUID zoneId;
        private int elapsedTicks;
        private boolean finished;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        clearState(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        DrachenfelsEncounterHelper.onAbilityEnded(ctx.npc, ID);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        clearState(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        DrachenfelsEncounterHelper.onAbilityEnded(ctx.npc, ID);
    }
}
