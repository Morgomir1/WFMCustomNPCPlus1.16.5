package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/** Phase 1: charge then radial knockback of nearby players. */
public final class DfRepulseAbility implements CnpcAbility {
    public static final String ID = "df_repulse";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean requiresTarget() {
        return false;
    }

    @Override
    public boolean cancelsOnTargetLost() {
        return false;
    }

    @Override
    public Map<String, Object> defaultParams() {
        return AbilityDefaults.dfRepulse();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
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
        final int charge = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 30));
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 3.0);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, 0xC0FF3030);
        final String tid = TelegraphAPI.circle(
                ctx.npc, active.sx, active.sy, active.sz, radius, charge, color);
        if (tid != null && !tid.isEmpty()) {
            active.telegraphIds.add(tid);
            active.telegraphId = tid;
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = charge;
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.evoker.prepare_attack", 0.95F, 0.55F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            if (active.ticksLeft % 5 == 0) {
                AbilityVfx.spawnDarkCharge(ctx.world, active.sx, active.sy + 1.0, active.sz);
            }
            active.ticksLeft--;
            if (active.ticksLeft > 0) {
                return TickResult.CONTINUE;
            }
            AbilityTelegraph.clear(active, ctx);
            pulse(active, ctx);
            active.phase = ActiveAbility.PHASE_ACTIVE;
            active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 1));
            return TickResult.CONTINUE;
        }
        active.ticksLeft--;
        return active.ticksLeft > 0 ? TickResult.CONTINUE : TickResult.FINISHED;
    }

    private static void pulse(final ActiveAbility active, final AbilityContext ctx) {
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 3.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 1.85);
        final double lift = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.42);
        final int range = (int) Math.ceil(radius + 0.5);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(active.sx, active.sy, active.sz),
                range,
                1);
        for (final IEntity ent : list) {
            if (!(ent instanceof IPlayer) || !ent.isAlive()) {
                continue;
            }
            if (AbilityCombatHelper.flatDistance(ent.getX(), ent.getZ(), active.sx, active.sz) > radius) {
                continue;
            }
            final String id = String.valueOf(ent.getUUID());
            if (active.hitUuids.contains(id)) {
                continue;
            }
            applyRadialKnockback((IEntityLiving) ent, active.sx, active.sz, knockback, lift);
            AbilityVfx.spawnHitParticle(ctx.world, ent);
            active.hitUuids.add(id);
        }
        AbilityVfx.spawnSoulWave(ctx.world, active.sx, active.sy + 0.2, active.sz, radius);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.wither.break_block", 1.0F, 0.65F);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.elder_guardian.curse", 0.7F, 1.15F);
    }

    private static void applyRadialKnockback(
            final IEntityLiving entity,
            final double fromX,
            final double fromZ,
            final double strength,
            final double lift) {
        double dx = entity.getX() - fromX;
        double dz = entity.getZ() - fromZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) {
            dx = 1.0;
            dz = 0.0;
            len = 1.0;
        }
        entity.setMotionX((dx / len) * strength);
        entity.setMotionY(lift);
        entity.setMotionZ((dz / len) * strength);
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
