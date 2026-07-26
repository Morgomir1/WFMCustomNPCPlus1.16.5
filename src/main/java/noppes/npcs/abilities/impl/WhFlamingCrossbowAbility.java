package noppes.npcs.abilities.impl;

import noppes.npcs.abilities.*;
import noppes.npcs.api.entity.IProjectile;
import noppes.npcs.api.entity.data.INPCInventory;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.entity.EntityProjectile;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Огненный арбалет: swap на crossbow → узкий line telegraph → выстрел + поджог → restore рапиры.
 */
public final class WhFlamingCrossbowAbility implements CnpcAbility {
    public static final String ID = "wh_flaming_crossbow";
    private static final String DEFAULT_RANGED = "minecraft:crossbow";
    private static final String DEFAULT_MELEE = "wfm:empire_witch_hunter_rapier";
    private static final String DEFAULT_PROJECTILE = "minecraft:fire_charge";

    private static final ConcurrentHashMap<UUID, IItemStack> SAVED_RIGHT = new ConcurrentHashMap<>();

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
        return AbilityDefaults.whFlamingCrossbow();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.DISTANCE,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.MAX_RANGE,
                AbilityParamKeys.ACCURACY,
                AbilityParamKeys.FIRE_SECONDS,
                AbilityParamKeys.PROJECTILE_ITEM,
                AbilityParamKeys.RANGED_ITEM,
                AbilityParamKeys.MELEE_ITEM,
                AbilityParamKeys.TELEGRAPH,
                AbilityParamKeys.TELEGRAPH_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        final double maxRange = ctx.params.getDouble(AbilityParamKeys.MAX_RANGE, 24.0);
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        if (AbilityCombatHelper.distanceToTarget(ctx) > maxRange) {
            return false;
        }

        active.jumpStyle = false;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();

        final double dx = ctx.target.getX() - active.sx;
        final double dz = ctx.target.getZ() - active.sz;
        active.yaw = AbilityCombatHelper.computeYaw(dx, dz);

        // Конечная точка = длина telegraph (distance), не позиция цели
        final double dist = ctx.params.getDouble(AbilityParamKeys.DISTANCE, 18.0);
        final double len = Math.sqrt(dx * dx + dz * dz);
        final double nx = len > 0.05 ? dx / len : 0.0;
        final double nz = len > 0.05 ? dz / len : 1.0;
        active.ex = active.sx + nx * dist;
        active.ez = active.sz + nz * dist;
        active.ey = AbilityCombatHelper.findGroundY(ctx.world, active.ex, active.ez, active.sy);

        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 10);
        active.hitUuids.clear();
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);

        equipCrossbow(active, ctx);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:item.crossbow.loading_middle", 0.9F, 1.0F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return fireShot(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        // Направление зафиксировано в onStart вместе с telegraph — не следовать за целью
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnMuzzleFlash(
                    ctx.world,
                    ctx.npc.getX(),
                    ctx.npc.getY() + 1.2,
                    ctx.npc.getZ());
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = 1;
        return TickResult.CONTINUE;
    }

    private TickResult fireShot(final ActiveAbility active, final AbilityContext ctx) {
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 10.0);
        final double halfWidth = ctx.params.getDouble(AbilityParamKeys.RADIUS, 0.7);
        final int fireSeconds = ctx.params.getInt(AbilityParamKeys.FIRE_SECONDS, 5);
        final int accuracy = ctx.params.getInt(AbilityParamKeys.ACCURACY, 3);
        final String projId = ctx.params.getString(AbilityParamKeys.PROJECTILE_ITEM, DEFAULT_PROJECTILE);

        final double ox = ctx.npc.getX();
        final double oy = ctx.npc.getY() + 1.0;
        final double oz = ctx.npc.getZ();
        final double aimX = active.ex;
        final double aimY = active.ey + 1.0;
        final double aimZ = active.ez;

        AbilityVfx.spawnMuzzleFlash(ctx.world, ox, oy + 0.2, oz);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:item.crossbow.shoot", 1.0F, 1.05F);

        // Урон по зафиксированному коридору telegraph
        active.hitUuids.clear();
        AbilityCombatHelper.damageInCorridor(
                active, ctx,
                ox, oy, oz,
                aimX, aimY, aimZ,
                halfWidth, damage, 0.4, 0.1,
                fireSeconds, null, 0, 0);

        // Снаряд вдоль линии зоны, не homing в цель
        try {
            final IItemStack item = ctx.world.createItem(projId, 1);
            if (item != null) {
                final IProjectile proj = ctx.npc.shootItem(aimX, aimY, aimZ, item, accuracy);
                if (proj != null) {
                    try {
                        final Object mc = proj.getMCEntity();
                        if (mc instanceof EntityProjectile) {
                            ((EntityProjectile) mc).damage = (float) damage;
                        }
                    } catch (final Exception ignored) {
                    }
                }
            }
        } catch (final Exception ignored) {
        }

        restoreMelee(active, ctx);
        return TickResult.FINISHED;
    }

    private void equipCrossbow(final ActiveAbility active, final AbilityContext ctx) {
        try {
            final INPCInventory inv = ctx.npc.getInventory();
            if (inv == null) {
                return;
            }
            final UUID uuid = UUID.fromString(String.valueOf(ctx.npc.getUUID()));
            final IItemStack current = inv.getRightHand();
            if (current != null) {
                SAVED_RIGHT.put(uuid, current);
            }
            final String rangedId = ctx.params.getString(AbilityParamKeys.RANGED_ITEM, DEFAULT_RANGED);
            final IItemStack crossbow = ctx.world.createItem(rangedId, 1);
            if (crossbow != null) {
                inv.setRightHand(crossbow);
            }
        } catch (final Exception ignored) {
        }
    }

    private void restoreMelee(final ActiveAbility active, final AbilityContext ctx) {
        try {
            final INPCInventory inv = ctx.npc.getInventory();
            if (inv == null) {
                return;
            }
            final UUID uuid = UUID.fromString(String.valueOf(ctx.npc.getUUID()));
            IItemStack saved = SAVED_RIGHT.remove(uuid);
            if (saved == null) {
                final String meleeId = ctx.params.getString(AbilityParamKeys.MELEE_ITEM, DEFAULT_MELEE);
                saved = ctx.world.createItem(meleeId, 1);
            }
            if (saved != null) {
                inv.setRightHand(saved);
            }
        } catch (final Exception ignored) {
        }
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        restoreMelee(active, ctx);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        restoreMelee(active, ctx);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
