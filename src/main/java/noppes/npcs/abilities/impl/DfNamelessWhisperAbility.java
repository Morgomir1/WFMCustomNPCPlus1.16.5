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

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Phase 3: three expanding rings from the boss (blind + wither), 2s between waves. */
public final class DfNamelessWhisperAbility implements CnpcAbility {
    public static final String ID = "df_nameless_whisper";
    private static final int DEFAULT_RING_COUNT = 3;
    private static final int DEFAULT_RING_INTERVAL = 40;
    private static final int SUB_EXPAND = 0;
    private static final int SUB_WAIT = 1;
    private static final ConcurrentHashMap<UUID, UUID> ZONE_BY_NPC = new ConcurrentHashMap<>();

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
        active.markers.add(new double[]{0.0, SUB_EXPAND});
        active.meter = 0.0F;
        active.elapsedTicks = 0;

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
        if (subState(active) == SUB_WAIT) {
            active.ticksLeft--;
            if (active.ticksLeft > 0) {
                return TickResult.CONTINUE;
            }
            final int nextWave = (int) active.meter + 1;
            beginWave(active, ctx, nextWave);
            return TickResult.CONTINUE;
        }
        return tickExpand(active, ctx);
    }

    private TickResult tickExpand(final ActiveAbility active, final AbilityContext ctx) {
        final int total = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 60));
        final double arenaR = ctx.params.getDouble(AbilityParamKeys.RADIUS, 12.0);
        final double thickness = ctx.params.getDouble(AbilityParamKeys.INNER_RADIUS, 1.333);
        active.elapsedTicks++;
        final double progress = Math.min(1.0, active.elapsedTicks / (double) total);
        final double inner = progress * arenaR;
        final double outer = Math.min(arenaR + thickness, inner + thickness);
        updateZoneRadii(active, ctx, outer, inner);
        tickHit(active, ctx, inner, outer);
        AbilityVfx.spawnDarkSoulRing(ctx.world, active.sx, active.sy + 0.05, active.sz, inner, outer);
        if (active.elapsedTicks < total) {
            return TickResult.CONTINUE;
        }
        return finishWave(active, ctx);
    }

    private TickResult finishWave(final ActiveAbility active, final AbilityContext ctx) {
        clearZone(active, ctx);
        final int wave = (int) active.meter;
        active.meter = wave + 1.0F;
        if (active.meter >= ringCount(ctx)) {
            ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.wither.shoot", 0.7F, 1.3F);
            return TickResult.FINISHED;
        }
        active.hitUuids.clear();
        setSubState(active, SUB_WAIT);
        active.ticksLeft = ringInterval(ctx);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.wither.ambient", 0.55F, 1.1F);
        return TickResult.CONTINUE;
    }

    private void beginWave(final ActiveAbility active, final AbilityContext ctx, final int waveIndex) {
        active.meter = waveIndex;
        active.elapsedTicks = 0;
        active.hitUuids.clear();
        setSubState(active, SUB_EXPAND);
        spawnRing(active, ctx);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.wither.shoot", 0.65F, 0.85F + waveIndex * 0.15F);
    }

    private void tickHit(
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
            if (active.hitUuids.contains(id)) {
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
            active.hitUuids.add(id);
        }
    }

    private void spawnRing(final ActiveAbility active, final AbilityContext ctx) {
        clearZone(active, ctx);
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
        ZONE_BY_NPC.put(active.npcUuid, zone.getUUID());
    }

    private void updateZoneRadii(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double outer,
            final double inner) {
        final UUID zoneId = ZONE_BY_NPC.get(active.npcUuid);
        if (zoneId == null) {
            return;
        }
        final EntityAbilityZone zone = resolveZone(ctx, zoneId);
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

    private static void clearZone(final ActiveAbility active, final AbilityContext ctx) {
        final UUID zoneId = ZONE_BY_NPC.remove(active.npcUuid);
        if (zoneId == null) {
            return;
        }
        final EntityAbilityZone zone = resolveZone(ctx, zoneId);
        ZoneAPI.remove(zone);
    }

    private static int ringCount(final AbilityContext ctx) {
        return Math.max(1, ctx.params.getInt(AbilityParamKeys.SUMMON_COUNT, DEFAULT_RING_COUNT));
    }

    private static int ringInterval(final AbilityContext ctx) {
        return Math.max(1, ctx.params.getInt(AbilityParamKeys.SHOT_INTERVAL, DEFAULT_RING_INTERVAL));
    }

    private static int subState(final ActiveAbility active) {
        return active.markers.isEmpty() ? SUB_EXPAND : (int) active.markers.get(0)[1];
    }

    private static void setSubState(final ActiveAbility active, final int state) {
        if (active.markers.isEmpty()) {
            active.markers.add(new double[]{0.0, state});
            return;
        }
        active.markers.get(0)[1] = state;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        clearZone(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        DrachenfelsEncounterHelper.onAbilityEnded(ctx.npc, ID);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        clearZone(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        DrachenfelsEncounterHelper.onAbilityEnded(ctx.npc, ID);
    }
}
