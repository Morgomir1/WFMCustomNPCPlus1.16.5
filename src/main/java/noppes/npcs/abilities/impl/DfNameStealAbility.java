package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;

import java.util.Map;
import java.util.Set;

/** Phase 3: melee name steal — instant damage + weakness + blindness. */
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
        return false;
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
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.TELEGRAPH);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
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
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = 1;
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        return TickResult.FINISHED;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        DrachenfelsEncounterHelper.onAbilityEnded(ctx.npc, ID);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        DrachenfelsEncounterHelper.onAbilityEnded(ctx.npc, ID);
    }
}
