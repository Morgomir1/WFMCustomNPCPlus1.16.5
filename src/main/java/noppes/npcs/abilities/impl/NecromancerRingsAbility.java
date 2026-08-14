package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.abilities.event.NecromancerCombatHandler;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

public final class NecromancerRingsAbility implements CnpcAbility {
    public static final String ID = "necro_rings";

    private static final double[][] RINGS = {
            {3.0, 5.0},
            {6.0, 8.0},
            {9.0, 11.0}
    };

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
        return AbilityDefaults.necroRings();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (NecromancerCombatHandler.isStunned(ctx.npc)) {
            return false;
        }
        active.jumpStyle = false;
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 20));
        active.meter = 0.0F;
        active.hitUuids.clear();
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (ctx.target != null) {
            final double dx = ctx.target.getX() - ctx.npc.getX();
            final double dz = ctx.target.getZ() - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
            ctx.npc.setRotation(active.yaw);
        }
        spawnCurrentTelegraph(active, ctx);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:block.beacon.power_select", 0.8F, 0.7F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        final int ringIndex = Math.max(0, Math.min(RINGS.length - 1, (int) active.meter));
        AbilityCombatHelper.holdInPlace(ctx.npc, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
        ctx.npc.setRotation(active.yaw);

        if (active.ticksLeft % 4 == 0) {
            AbilityVfx.spawnDarkCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
            AbilityVfx.spawnDarkSoulRing(
                    ctx.world,
                    ctx.npc.getX(),
                    ctx.npc.getY() + 0.05,
                    ctx.npc.getZ(),
                    RINGS[ringIndex][0],
                    RINGS[ringIndex][1]);
        }

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        detonateCurrentRing(active, ctx, ringIndex);
        active.meter = ringIndex + 1;
        if ((int) active.meter >= RINGS.length) {
            return TickResult.FINISHED;
        }

        active.hitUuids.clear();
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 20));
        spawnCurrentTelegraph(active, ctx);
        return TickResult.CONTINUE;
    }

    private void detonateCurrentRing(final ActiveAbility active, final AbilityContext ctx, final int ringIndex) {
        clearTelegraphs(active);
        final double inner = RINGS[ringIndex][0];
        final double outer = RINGS[ringIndex][1];
        final double x = ctx.npc.getX();
        final double y = AbilityCombatHelper.findGroundY(ctx.world, x, ctx.npc.getZ(), ctx.npc.getY()) + 0.05;
        final double z = ctx.npc.getZ();

        AbilityCombatHelper.damageInMagicRing(
                active,
                ctx,
                x,
                y + 0.5,
                z,
                inner,
                outer,
                (float) ctx.params.getDouble(AbilityParamKeys.DAMAGE, 10.0));
        AbilityVfx.spawnDarkSoulRing(ctx.world, x, y, z, inner, outer);
        AbilityVfx.spawnSoulBurst(ctx.world, x, y + 0.2, z, outer);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(x, y, z),
                "minecraft:entity.wither.break_block",
                0.8F,
                0.75F + ringIndex * 0.08F);
    }

    private void spawnCurrentTelegraph(final ActiveAbility active, final AbilityContext ctx) {
        clearTelegraphs(active);
        final int ringIndex = Math.max(0, Math.min(RINGS.length - 1, (int) active.meter));
        final int chargeTicks = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 20));
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, TelegraphAPI.DEFAULT_COLOR);
        final String id = TelegraphAPI.ring(
                ctx.npc,
                ctx.npc.getX(),
                ctx.npc.getY(),
                ctx.npc.getZ(),
                RINGS[ringIndex][1],
                RINGS[ringIndex][0],
                chargeTicks,
                color);
        if (id != null && !id.isEmpty()) {
            active.telegraphIds.add(id);
        }
    }

    private void clearTelegraphs(final ActiveAbility active) {
        if (active.telegraphIds.isEmpty()) {
            return;
        }
        for (final String id : active.telegraphIds) {
            if (id != null && !id.isEmpty()) {
                TelegraphAPI.remove(id);
            }
        }
        active.telegraphIds.clear();
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        clearTelegraphs(active);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        clearTelegraphs(active);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
