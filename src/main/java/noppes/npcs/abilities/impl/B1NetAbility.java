package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityContext;
import noppes.npcs.abilities.AbilityDefaults;
import noppes.npcs.abilities.AbilityParamKeys;
import noppes.npcs.abilities.AbilityParams;
import noppes.npcs.abilities.ActiveAbility;
import noppes.npcs.abilities.CnpcAbility;
import noppes.npcs.abilities.HerdstoneMount;
import noppes.npcs.abilities.TickResult;
import noppes.npcs.abilities.integration.WfmIntegration;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.Map;
import java.util.Set;

/**
 * Сеть Грула с седла. Попадание: спешиться, дойти до игрока и бросить его перед кабаном.
 */
public final class B1NetAbility implements CnpcAbility {
    public static final String ID = "b1_net";
    private static final int PHASE_HAUL = 3;
    private static final String HAUL_KEY = "b1_haul";
    private static final String BOAR_KEY = "b1_haul_boar";
    private static final String FEED_KEY = "b1_feed";
    private static final int NET_COLOR = 0xC0D8C090;

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
        return AbilityDefaults.b1Net();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.CHARGE_TICKS,
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.RADIUS,
                AbilityParamKeys.EFFECT_DURATION,
                AbilityParamKeys.TELEGRAPH);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        if (ctx.target == null || !ctx.target.isAlive()) {
            return false;
        }
        active.phase = ActiveAbility.PHASE_CHARGE;
        active.ticksLeft = Math.max(8, ctx.params.getInt(AbilityParamKeys.CHARGE_TICKS, 16));
        active.hitUuids.clear();
        aim(active, ctx);
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 2.2);
        final String id = TelegraphAPI.circleAt(
                ctx.npc, active.ex, active.ey, active.ez, radius, active.ticksLeft, NET_COLOR);
        try {
            TelegraphAPI.follow(id, ctx.target);
        } catch (final Exception ignored) {
        }
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.fishing_bobber.throw", 0.9F, 0.8F);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        if (active.phase == ActiveAbility.PHASE_CHARGE) {
            return tickCharge(active, ctx);
        }
        if (active.phase == ActiveAbility.PHASE_ACTIVE) {
            return tickFlight(active, ctx);
        }
        if (active.phase == PHASE_HAUL) {
            return tickHaul(active, ctx);
        }
        return TickResult.FINISHED;
    }

    private TickResult tickCharge(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (ctx.target != null && ctx.target.isAlive()) {
            aim(active, ctx);
        }
        ctx.npc.setRotation(active.yaw);
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        WfmIntegration.throwNetTowardPoint(ctx.npc, active.ex, active.ey + 0.8, active.ez, 1.55F, 0.2F);
        ctx.world.playSoundAt(ctx.npc.getPos(), "minecraft:entity.snowball.throw", 1.0F, 0.75F);
        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.sx = ctx.npc.getX();
        active.sy = ctx.npc.getY() + 1.2;
        active.sz = ctx.npc.getZ();
        active.ticksLeft = Math.max(6, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 12));
        return TickResult.CONTINUE;
    }

    private TickResult tickFlight(final ActiveAbility active, final AbilityContext ctx) {
        AbilityCombatHelper.stopNavigation(ctx.npc);
        ctx.npc.setRotation(active.yaw);
        active.ticksLeft--;
        if (active.ticksLeft > 0) {
            return TickResult.CONTINUE;
        }
        if (!catchTarget(active, ctx)) {
            return TickResult.FINISHED;
        }
        beginHaul(active, ctx);
        return TickResult.CONTINUE;
    }

    private TickResult tickHaul(final ActiveAbility active, final AbilityContext ctx) {
        final IEntityLiving player = caught(ctx, active);
        if (player == null || !player.isAlive()) {
            return TickResult.FINISHED;
        }
        final Entity grul = ctx.npc.getMCEntity();
        final Entity victim = player.getMCEntity();
        if (grul == null || victim == null) {
            return TickResult.FINISHED;
        }
        final double dx = victim.getX() - grul.getX();
        final double dz = victim.getZ() - grul.getZ();
        final double len = Math.sqrt(dx * dx + dz * dz);
        ctx.npc.setRotation(AbilityCombatHelper.computeYaw(dx, dz));
        AbilityCombatHelper.stopNavigation(ctx.npc);
        if (len > 2.2) {
            if (active.ticksLeft <= 0) {
                return TickResult.FINISHED;
            }
            final double step = Math.min(0.42, len);
            final double nx = grul.getX() + dx / len * step;
            final double nz = grul.getZ() + dz / len * step;
            final double ny = AbilityCombatHelper.findGroundY(ctx.world, nx, nz, grul.getY());
            grul.setPos(nx, ny, nz);
            grul.setDeltaMovement(0.0, 0.0, 0.0);
            grul.hasImpulse = true;
            grul.fallDistance = 0.0F;
            active.ticksLeft--;
            return TickResult.CONTINUE;
        }
        throwUnderRam(ctx, player, victim);
        return TickResult.FINISHED;
    }

    private void beginHaul(final ActiveAbility active, final AbilityContext ctx) {
        final Entity grul = ctx.npc.getMCEntity();
        final Entity boar = grul == null ? null : grul.getVehicle();
        if (boar != null) {
            put(ctx.npc, BOAR_KEY, String.valueOf(boar.getUUID()));
        }
        HerdstoneMount.dismount(ctx.npc);
        put(ctx.npc, "b1_mounted", "0");
        put(ctx.npc, "b1_mounting", "0");
        put(ctx.npc, HAUL_KEY, "1");
        put(ctx.npc, "b1_mount_go", "0");
        active.phase = PHASE_HAUL;
        active.ticksLeft = 90;
        try {
            ctx.npc.getAi().setWalkingSpeed(0);
            ctx.npc.getAi().setRetaliateType(3);
        } catch (final Exception ignored) {
        }
    }

    private boolean catchTarget(final ActiveAbility active, final AbilityContext ctx) {
        final double radius = ctx.params.getDouble(AbilityParamKeys.RADIUS, 2.2);
        final int duration = ctx.params.getInt(AbilityParamKeys.EFFECT_DURATION, 100);
        WfmIntegration.ensnareAroundPoint(ctx.npc, active.ex, active.ey, active.ez, radius, duration);
        ctx.world.playSoundAt(
                NpcAPI.Instance().getIPos(active.ex, active.ey, active.ez),
                "minecraft:entity.fishing_bobber.splash",
                1.0F,
                1.0F);
        final IEntity[] list = ctx.world.getNearbyEntities(
                NpcAPI.Instance().getIPos(active.ex, active.ey, active.ez),
                (int) Math.ceil(radius) + 1,
                1);
        IEntityLiving caught = null;
        double best = radius + 0.01;
        if (list != null) {
            for (int i = 0; i < list.length; i++) {
                final IEntity ent = list[i];
                if (ent == null || !ent.isAlive() || !(ent.getMCEntity() instanceof PlayerEntity)) {
                    continue;
                }
                final double dx = ent.getX() - active.ex;
                final double dz = ent.getZ() - active.ez;
                final double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius || dist >= best) {
                    continue;
                }
                if (ent instanceof IEntityLiving) {
                    caught = (IEntityLiving) ent;
                    best = dist;
                }
            }
        }
        if (caught == null && ctx.target != null && ctx.target.isAlive()) {
            final double dx = ctx.target.getX() - active.ex;
            final double dz = ctx.target.getZ() - active.ez;
            if (Math.sqrt(dx * dx + dz * dz) <= radius + 0.6) {
                caught = ctx.target;
            }
        }
        if (caught == null) {
            return false;
        }
        active.hitUuids.clear();
        active.hitUuids.add(String.valueOf(caught.getUUID()));
        return true;
    }

    private void throwUnderRam(final AbilityContext ctx, final IEntityLiving player, final Entity victim) {
        final ICustomNpc boar = findBoar(ctx);
        if (boar == null || boar.getMCEntity() == null) {
            return;
        }
        final Entity mount = boar.getMCEntity();
        final double rad = Math.toRadians(mount.yRot + 90.0F);
        final double fx = Math.cos(rad);
        final double fz = Math.sin(rad);
        final double x = mount.getX() + fx * 4.0;
        final double z = mount.getZ() + fz * 4.0;
        final double y = AbilityCombatHelper.findGroundY(ctx.world, x, z, mount.getY());
        victim.setPos(x, y, z);
        victim.setDeltaMovement(fx * 0.35, 0.15, fz * 0.35);
        victim.hasImpulse = true;
        victim.fallDistance = 0.0F;
        if (victim instanceof LivingEntity) {
            ((LivingEntity) victim).addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 40, 2));
        }
        put(boar, FEED_KEY, String.valueOf(player.getUUID()));
        final long now = ctx.world.getTotalTime();
        put(boar, "ram_at", Long.toString(now + 15L));
    }

    private IEntityLiving caught(final AbilityContext ctx, final ActiveAbility active) {
        if (active.hitUuids.isEmpty()) {
            return null;
        }
        final String id = active.hitUuids.iterator().next();
        try {
            final IEntity ent = ctx.world.getEntity(id);
            if (ent instanceof IEntityLiving) {
                return (IEntityLiving) ent;
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    private ICustomNpc findBoar(final AbilityContext ctx) {
        final String id = get(ctx.npc, BOAR_KEY);
        if (id == null || id.length() < 32) {
            return null;
        }
        try {
            final IEntity ent = ctx.world.getEntity(id);
            if (ent instanceof ICustomNpc && ent.isAlive()) {
                return (ICustomNpc) ent;
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    private void aim(final ActiveAbility active, final AbilityContext ctx) {
        final double tx = ctx.target.getX();
        final double tz = ctx.target.getZ();
        active.ex = tx;
        active.ez = tz;
        active.ey = AbilityCombatHelper.findGroundY(ctx.world, tx, tz, ctx.target.getY());
        active.yaw = AbilityCombatHelper.computeYaw(tx - ctx.npc.getX(), tz - ctx.npc.getZ());
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        finishHaul(ctx);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        finishHaul(ctx);
    }

    private void finishHaul(final AbilityContext ctx) {
        final boolean hauling = "1".equals(get(ctx.npc, HAUL_KEY));
        put(ctx.npc, HAUL_KEY, "0");
        put(ctx.npc, BOAR_KEY, "");
        if (!hauling) {
            return;
        }
        put(ctx.npc, "b1_mount_go", "1");
        try {
            int speed = 4;
            final Object raw = ctx.npc.getStoreddata().get("base_speed");
            if (raw != null) {
                final int parsed = (int) Double.parseDouble(String.valueOf(raw));
                if (parsed > 0) {
                    speed = parsed;
                }
            }
            ctx.npc.getAi().setWalkingSpeed(speed);
            ctx.npc.getAi().setRetaliateType(0);
        } catch (final Exception ignored) {
        }
    }

    private static void put(final ICustomNpc npc, final String key, final String value) {
        if (npc == null) {
            return;
        }
        try {
            npc.getStoreddata().put(key, value);
        } catch (final Exception ignored) {
        }
    }

    private static String get(final ICustomNpc npc, final String key) {
        try {
            final Object value = npc.getStoreddata().get(key);
            return value == null ? "" : String.valueOf(value);
        } catch (final Exception ignored) {
            return "";
        }
    }
}
