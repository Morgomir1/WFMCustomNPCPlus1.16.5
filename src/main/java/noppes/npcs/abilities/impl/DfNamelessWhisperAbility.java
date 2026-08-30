package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/** Phase 3: three simultaneous rings; hit only in ring bands. */
public final class DfNamelessWhisperAbility implements CnpcAbility {
    public static final String ID = "df_nameless_whisper";

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
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.RING1_DISTANCE,
                AbilityParamKeys.RING2_DISTANCE,
                AbilityParamKeys.RING3_DISTANCE,
                AbilityParamKeys.RING1_RADIUS,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();
        active.hitUuids.clear();
        active.telegraphIds.clear();
        final int charge = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 24));
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, 0xC0FF3030);
        final double[] inners = resolveInners(ctx);
        final double thickness = ctx.params.getDouble(AbilityParamKeys.RING1_RADIUS, 1.2);
        for (int i = 0; i < inners.length; i++) {
            final double inner = inners[i];
            final double outer = inner + thickness;
            final String id = TelegraphAPI.ring(
                    ctx.npc, active.sx, active.sy, active.sz, outer, inner, charge, color);
            if (id != null && !id.isEmpty()) {
                active.telegraphIds.add(id);
            }
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
        final double[] inners = resolveInners(ctx);
        final double thickness = ctx.params.getDouble(AbilityParamKeys.RING1_RADIUS, 1.2);
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            if (active.ticksLeft % 4 == 0) {
                for (int i = 0; i < inners.length; i++) {
                    AbilityVfx.spawnDarkSoulRing(
                            ctx.world, active.sx, active.sy + 0.05, active.sz,
                            inners[i], inners[i] + thickness);
                }
            }
            active.ticksLeft--;
            if (active.ticksLeft > 0) {
                return TickResult.CONTINUE;
            }
            AbilityTelegraph.clear(active, ctx);
            detonate(active, ctx, inners, thickness);
            active.phase = ActiveAbility.PHASE_ACTIVE;
            active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 1));
            return TickResult.CONTINUE;
        }
        active.ticksLeft--;
        return active.ticksLeft > 0 ? TickResult.CONTINUE : TickResult.FINISHED;
    }

    private void detonate(
            final ActiveAbility active,
            final AbilityContext ctx,
            final double[] inners,
            final double thickness) {
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 7.0);
        final int blindDur = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 20);
        final int range = 14;
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(active.sx, active.sy, active.sz), range, -1);
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
            if (!inAnyRing(dist, inners, thickness)) {
                continue;
            }
            if (!AbilityCombatHelper.dealPureDamage(ent, (float) damage, false)) {
                ent.damage((float) damage);
            }
            AbilityCombatHelper.applyEffect(
                    ent, AbilityEffectType.BLINDNESS.toMcEffect(), blindDur, 0);
            AbilityVfx.spawnHitParticle(ctx.world, ent);
            active.hitUuids.add(id);
        }
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.wither.shoot", 0.7F, 1.3F);
    }

    private static double[] resolveInners(final AbilityContext ctx) {
        return new double[]{
                ctx.params.getDouble(AbilityParamKeys.RING1_DISTANCE, 2.0),
                ctx.params.getDouble(AbilityParamKeys.RING2_DISTANCE, 5.0),
                ctx.params.getDouble(AbilityParamKeys.RING3_DISTANCE, 8.0)
        };
    }

    private static boolean inAnyRing(final double dist, final double[] inners, final double thickness) {
        for (int i = 0; i < inners.length; i++) {
            final double inner = inners[i];
            final double outer = inner + thickness;
            if (dist >= inner && dist <= outer) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        DrachenfelsEncounterHelper.onAbilityEnded(ctx.npc, ID);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        DrachenfelsEncounterHelper.onAbilityEnded(ctx.npc, ID);
    }
}
