package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/** Phase 3: charge circle on target, then melee name steal. */
public final class DfNameStealAbility implements CnpcAbility {
    public static final String ID = "df_name_steal";

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
        return true;
    }

    @Override
    public Map<String, Object> defaultParams() {
        return AbilityDefaults.dfNameSteal();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        active.jumpStyle = false;
        active.telegraphIds.clear();
        final int charge = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 14));
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 1.5);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, 0xC0FF3030);
        final String id = TelegraphAPI.circle(
                ctx.npc,
                ctx.target.getX(),
                ctx.target.getY(),
                ctx.target.getZ(),
                radius,
                charge,
                color);
        if (id != null && !id.isEmpty()) {
            active.telegraphId = id;
            active.telegraphIds.add(id);
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = charge;
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.illusioner.prepare_blindness", 0.85F, 0.8F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.holdInPlace(ctx.npc, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
        if (active.phase != ActiveAbility.PHASE_CHARGE) {
            return TickResult.FINISHED;
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        AbilityTelegraph.clear(active, ctx);
        applySteal(active, ctx);
        return TickResult.FINISHED;
    }

    private static void applySteal(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return;
        }
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 6.0);
        final int weakDur = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 40);
        final int blindDur = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 40);
        if (!AbilityCombatHelper.dealPureDamage(ctx.target, (float) damage, true)) {
            ctx.target.damage((float) damage);
        }
        AbilityCombatHelper.applyEffect(
                ctx.target, AbilityEffectType.WEAKNESS.toMcEffect(), weakDur, 0);
        AbilityCombatHelper.applyEffect(
                ctx.target, AbilityEffectType.BLINDNESS.toMcEffect(), blindDur, 0);
        AbilityVfx.spawnHitParticle(ctx.world, ctx.target);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.illusioner.cast_spell", 0.9F, 0.6F);
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
