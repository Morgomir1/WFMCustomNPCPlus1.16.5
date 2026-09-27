package noppes.npcs.abilities.impl;

import net.minecraft.entity.LivingEntity;
import noppes.npcs.abilities.*;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/**
 * Кручение с оружием: biped крутится (тело + руки + предмет), бьёт вокруг себя.
 * Во время спина летит по прямой (направление целится в момент старта, без самонаведения).
 * Красный круг под ногами на весь каст.
 */
public final class WeaponSpinAbility implements CnpcAbility {
    public static final String ID = "weapon_spin";

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
        return AbilityDefaults.weaponSpin();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.DAMAGE_PER_TICK,
                AbilityParamKeys.DAMAGE_INTERVAL,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.KNOCKBACK,
                AbilityParamKeys.KNOCKBACK_Y,
                AbilityParamKeys.ORBIT_SPEED,
                AbilityParamKeys.APPROACH_SPEED,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 16);
        active.hitUuids.clear();
        active.telegraphIds.clear();

        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        AbilityCombatHelper.zeroHorizontalMotion(ctx.npc);

        if (ctx.target != null) {
            final double dx = ctx.target.getX() - ctx.npc.getX();
            final double dz = ctx.target.getZ() - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
        } else {
            active.yaw = ctx.npc.getRotation();
        }
        applyBipedYaw(ctx.npc, active.yaw);

        spawnFollowCircle(active, ctx);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.player.attack.sweep", 0.8F, 0.65F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickSpin(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        AbilityCombatHelper.zeroHorizontalMotion(ctx.npc);
        applyBipedYaw(ctx.npc, active.yaw);

        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }

        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 40);
        active.hitUuids.clear();
        lockMoveDir(active, ctx);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.player.attack.sweep", 1.05F, 0.9F);
        return TickResult.CONTINUE;
    }

    private TickResult tickSpin(final ActiveAbility active, final AbilityContext ctx) {
        if (active.ticksLeft <= 0) {
            return TickResult.FINISHED;
        }

        AbilityCombatHelper.stopNavigation(ctx.npc);
        flyStraight(active, ctx);

        final float spinSpeed = (float) ctx.params.getDouble(AbilityParamKeys.ORBIT_SPEED, 42.0);
        active.yaw = active.yaw + spinSpeed;
        applyBipedYaw(ctx.npc, active.yaw);

        final int interval = Math.max(1, ctx.params.getInt(AbilityParamKeys.DAMAGE_INTERVAL, 5));
        if (active.ticksLeft % interval == 0) {
            pulseDamage(active, ctx);
        }

        active.ticksLeft--;
        return TickResult.CONTINUE;
    }

    private static void lockMoveDir(final ActiveAbility active, final AbilityContext ctx) {
        double dx = 0.0;
        double dz = 1.0;
        if (ctx.target != null && ctx.target.isAlive()) {
            dx = ctx.target.getX() - ctx.npc.getX();
            dz = ctx.target.getZ() - ctx.npc.getZ();
        }
        final double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.05) {
            final double rad = (ctx.npc.getRotation() + 90.0) * 0.0174532925;
            active.ex = Math.cos(rad);
            active.ez = Math.sin(rad);
            return;
        }
        active.ex = dx / len;
        active.ez = dz / len;
    }

    private static void flyStraight(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.npc == null) {
            return;
        }
        final double speed = Math.max(0.05, ctx.params.getDouble(AbilityParamKeys.APPROACH_SPEED, 0.4));
        final double nx = ctx.npc.getX();
        final double ny = ctx.npc.getY();
        final double nz = ctx.npc.getZ();
        final double cx = nx + active.ex * speed;
        final double cz = nz + active.ez * speed;
        final double cy = AbilityCombatHelper.findGroundY(ctx.world, cx, cz, ny);
        ctx.npc.setPosition(cx, cy, cz);
    }

    private static void pulseDamage(final ActiveAbility active, final AbilityContext ctx) {
        final double x = ctx.npc.getX();
        final double y = ctx.npc.getY();
        final double z = ctx.npc.getZ();
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 3.5);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE_PER_TICK, 4.0);
        final double knockback = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK, 0.45);
        final double knockbackY = ctx.params.getDouble(AbilityParamKeys.KNOCKBACK_Y, 0.12);

        active.hitUuids.clear();
        AbilityCombatHelper.damageNearby(
                active, ctx, x, y + 0.5, z,
                radius, damage, 0, 0, knockback, knockbackY, false);
        AbilityVfx.spawnSwordSweep(ctx.world, x, y, z, active.yaw);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.player.attack.sweep", 0.7F, 1.15F);
    }

    private static void spawnFollowCircle(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.params.getInt(AbilityParamKeys.TELEGRAPH, 1) == 0) {
            return;
        }
        final int charge = Math.max(1, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 16));
        final int activeTicks = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 40));
        final int duration = charge + activeTicks + 2;
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 3.5);
        final int color = ctx.params.getInt(AbilityParamKeys.TELEGRAPH_COLOR, 0xC0FF3030);
        try {
            final String tid = TelegraphAPI.circleFollow(
                    ctx.npc,
                    ctx.npc.getX(),
                    ctx.npc.getY(),
                    ctx.npc.getZ(),
                    radius,
                    duration,
                    color);
            if (tid != null && !tid.isEmpty()) {
                active.telegraphIds.add(tid);
            }
        } catch (final Exception ignored) {
        }
    }

    /**
     * Крутит весь biped: yRot + body + head, иначе WFM BipedModel вертит только взгляд,
     * а руки с оружием остаются.
     */
    private static void applyBipedYaw(final ICustomNpc npc, final float yaw) {
        npc.setRotation(yaw);
        try {
            final Object mc = npc.getMCEntity();
            if (mc instanceof LivingEntity) {
                final LivingEntity living = (LivingEntity) mc;
                living.yRot = yaw;
                living.yRotO = yaw;
                living.yBodyRot = yaw;
                living.yBodyRotO = yaw;
                living.yHeadRot = yaw;
                living.yHeadRotO = yaw;
            }
        } catch (final Exception ignored) {
        }
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityTelegraph.clear(active, ctx);
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
