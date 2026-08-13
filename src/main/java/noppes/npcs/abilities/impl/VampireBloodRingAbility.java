package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import noppes.npcs.abilities.*;
import noppes.npcs.entity.EntityAbilityZone;
import noppes.npcs.zone.ZoneAPI;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charge with ring telegraph, then a following hazard ring (10s):
 * damage ticks in the annulus, dark smoke + soul particles. Boss is not locked after spawn.
 */
public final class VampireBloodRingAbility implements CnpcAbility {
    public static final String ID = "vampire_blood_ring";
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
        return AbilityDefaults.vampireBloodRing();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.INNER_RADIUS,
                AbilityParamKeys.DAMAGE,
                AbilityParamKeys.ZONE_TICKS,
                AbilityParamKeys.DAMAGE_INTERVAL,
                AbilityParamKeys.ZONE_COLOR);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        active.jumpStyle = false;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY();
        active.sz = ctx.npc.getZ();
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 8);
        active.hitUuids.clear();
        AbilityCombatHelper.freezeAiForCast(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (ctx.target != null) {
            final double dx = ctx.target.getX() - ctx.npc.getX();
            final double dz = ctx.target.getZ() - ctx.npc.getZ();
            active.yaw = AbilityCombatHelper.computeYaw(dx, dz);
            ctx.npc.setRotation(active.yaw);
        } else {
            active.yaw = ctx.npc.getRotation();
        }
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.wither.ambient", 0.55F, 0.7F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            spawnFollowingRing(active, ctx);
            return TickResult.FINISHED;
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.holdInPlace(ctx.npc, active.sx, active.sy, active.sz);
        ctx.npc.setRotation(active.yaw);
        if (active.ticksLeft % 3 == 0) {
            AbilityVfx.spawnDarkCharge(ctx.world, ctx.npc.getX(), ctx.npc.getY(), ctx.npc.getZ());
            AbilityVfx.spawnDarkSoulRing(
                    ctx.world,
                    ctx.npc.getX(),
                    ctx.npc.getY(),
                    ctx.npc.getZ(),
                    ctx.params.getDouble(AbilityParamKeys.INNER_RADIUS, 4.5),
                    ctx.params.getDouble(AbilityParamKeys.RADIUS, 7.0));
        }
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = 1;
        return TickResult.CONTINUE;
    }

    private void spawnFollowingRing(final ActiveAbility active, final AbilityContext ctx) {
        clearZone(active, ctx);
        final double x = ctx.npc.getX();
        final double z = ctx.npc.getZ();
        final double y = AbilityCombatHelper.findGroundY(ctx.world, x, z, ctx.npc.getY()) + 0.05;
        final double outer = ctx.params.getDouble(AbilityParamKeys.RADIUS, 7.0);
        final double inner = ctx.params.getDouble(AbilityParamKeys.INNER_RADIUS, 4.5);
        final int zoneTicks = ctx.params.getInt(AbilityParamKeys.ZONE_TICKS, 200);
        final double damage = ctx.params.getDouble(AbilityParamKeys.DAMAGE, 3.0);
        final int interval = ctx.params.getInt(AbilityParamKeys.DAMAGE_INTERVAL, 10);
        final int color = ctx.params.getInt(AbilityParamKeys.ZONE_COLOR, 0xC0180810);

        final EntityAbilityZone zone = ZoneAPI.hazardRing(
                ctx.npc, x, y, z, outer, inner, zoneTicks, damage, interval);
        if (zone == null) {
            return;
        }
        zone.setColor(color);
        zone.setZoneHeight(2.4f);
        zone.setFollowOwner(true);
        zone.setVisible(true);
        zone.setGroundFill(true);
        zone.setBorder(true);
        ZONE_BY_NPC.put(active.npcUuid, zone.getUUID());
        AbilityVfx.spawnDarkSoulRing(ctx.world, x, y, z, inner, outer);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.wither.shoot", 0.7F, 0.55F);
    }

    private static void clearZone(final ActiveAbility active, final AbilityContext ctx) {
        final UUID zoneId = ZONE_BY_NPC.remove(active.npcUuid);
        if (zoneId == null) {
            return;
        }
        try {
            final Object mc = ctx.npc.getMCEntity();
            if (!(mc instanceof Entity)) {
                return;
            }
            final World world = ((Entity) mc).level;
            if (!(world instanceof ServerWorld)) {
                return;
            }
            final Entity entity = ((ServerWorld) world).getEntity(zoneId);
            if (entity instanceof EntityAbilityZone) {
                ZoneAPI.remove((EntityAbilityZone) entity);
            }
        } catch (final Exception ignored) {
        }
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.unfreezeAi(active, ctx.npc);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }
}
