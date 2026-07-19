package noppes.npcs.abilities.impl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityContext;
import noppes.npcs.abilities.AbilityDefaults;
import noppes.npcs.abilities.AbilityParamKeys;
import noppes.npcs.abilities.AbilityParams;
import noppes.npcs.abilities.ActiveAbility;
import noppes.npcs.abilities.CnpcAbility;
import noppes.npcs.abilities.TickResult;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.item.IItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/**
 * Держит щит NPC поднятым {@code activeTicks} тиков.
 * Урон спереди гасит {@link noppes.npcs.abilities.event.ShieldBlockDamageHandler}.
 */
public final class ShieldBlockAbility implements CnpcAbility {
    public static final String ID = "shield_block";

    private static final float DEFAULT_NPC_SHIELD_HP = 10000.0F;
    private static final String VANILLA_BLOCK_SOUND = "minecraft:item.shield.block";
    private static final String WFM_BLOCK_SOUND = "wfm:block.shield.hit";

    private static Boolean wfmShieldCapAvailable;
    private static Object wfmShieldCapability;
    private static Method wfmGetCapability;
    private static Method wfmIsPresent;
    private static Method wfmOrElse;
    private static Method wfmSetMax;
    private static Method wfmSetHealth;
    private static Method wfmSetBreakTime;

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
        return AbilityDefaults.shieldBlock();
    }

    @Override
    public Set<String> knownParamKeys() {
        return AbilityParams.keys(
                AbilityParamKeys.ACTIVE_TICKS,
                AbilityParamKeys.BLOCK_ANGLE,
                AbilityParamKeys.TELEGRAPH);
    }

    @Override
    public boolean onStart(final ActiveAbility active, final AbilityContext ctx) {
        final LivingEntity mc = toLiving(ctx.npc);
        if (mc == null) {
            return false;
        }
        final Hand hand = findShieldHand(mc, ctx.npc);
        if (hand == null) {
            return false;
        }

        active.phase = ActiveAbility.PHASE_ACTIVE;
        active.ticksLeft = Math.max(1, ctx.params.getInt(AbilityParamKeys.ACTIVE_TICKS, 20));
        active.jumpStyle = hand == Hand.OFF_HAND;
        active.yaw = ctx.npc.getRotation();

        fillWfmShieldHp(mc);
        raiseShield(mc, hand);
        playBlockSound(ctx, mc);
        return true;
    }

    @Override
    public TickResult tick(final ActiveAbility active, final AbilityContext ctx) {
        final LivingEntity mc = toLiving(ctx.npc);
        if (mc == null || !mc.isAlive()) {
            return TickResult.FINISHED;
        }

        Hand hand = active.jumpStyle ? Hand.OFF_HAND : Hand.MAIN_HAND;
        if (!isShieldInHand(mc, ctx.npc, hand)) {
            final Hand found = findShieldHand(mc, ctx.npc);
            if (found == null) {
                return TickResult.FINISHED;
            }
            hand = found;
            active.jumpStyle = hand == Hand.OFF_HAND;
        }

        fillWfmShieldHp(mc);
        raiseShield(mc, hand);

        active.ticksLeft--;
        return active.ticksLeft > 0 ? TickResult.CONTINUE : TickResult.FINISHED;
    }

    @Override
    public void onEnd(final ActiveAbility active, final AbilityContext ctx) {
        stopShield(ctx);
    }

    @Override
    public void onCancel(final ActiveAbility active, final AbilityContext ctx) {
        stopShield(ctx);
        AbilityCombatHelper.stopNavigation(ctx.npc);
    }

    public static boolean isFrontalHit(
            final LivingEntity npc,
            final DamageSource source,
            final double blockAngleDegrees) {
        if (npc == null || source == null) {
            return true;
        }
        final Vector3d srcPos = source.getSourcePosition();
        final Entity attacker = source.getEntity();
        final double sx;
        final double sz;
        if (srcPos != null) {
            sx = srcPos.x;
            sz = srcPos.z;
        } else if (attacker != null) {
            sx = attacker.getX();
            sz = attacker.getZ();
        } else {
            return true;
        }

        final double dx = sx - npc.getX();
        final double dz = sz - npc.getZ();
        if (dx * dx + dz * dz < 1.0E-4) {
            return true;
        }

        final double angle = Math.atan2(dx, dz) * (180.0 / Math.PI);
        double diff = Math.abs(angle - npc.yRot) % 360.0;
        if (diff > 180.0) {
            diff = 360.0 - diff;
        }
        final double half = Math.max(1.0, blockAngleDegrees) * 0.5;
        return diff <= half;
    }

    public static void onBlockedHit(final AbilityContext ctx, final LivingEntity mc) {
        if (ctx == null || mc == null) {
            return;
        }
        fillWfmShieldHp(mc);
        final Hand hand = findShieldHand(mc, ctx.npc);
        if (hand != null) {
            raiseShield(mc, hand);
        }
        playBlockSound(ctx, mc);
    }

    private static void stopShield(final AbilityContext ctx) {
        final LivingEntity mc = toLiving(ctx == null ? null : ctx.npc);
        if (mc != null && mc.isUsingItem()) {
            mc.stopUsingItem();
        }
    }

    private static void raiseShield(final LivingEntity mc, final Hand hand) {
        if (mc == null || hand == null) {
            return;
        }
        final ItemStack stack = mc.getItemInHand(hand);
        if (stack.isEmpty() || !isShieldStack(stack, mc)) {
            return;
        }
        if (mc.isUsingItem() && mc.getUsedItemHand() == hand) {
            return;
        }
        if (mc.isUsingItem()) {
            mc.stopUsingItem();
        }
        mc.startUsingItem(hand);
    }

    private static Hand findShieldHand(final LivingEntity mc, final ICustomNpc npc) {
        if (isShieldInHand(mc, npc, Hand.OFF_HAND)) {
            return Hand.OFF_HAND;
        }
        if (isShieldInHand(mc, npc, Hand.MAIN_HAND)) {
            return Hand.MAIN_HAND;
        }
        return null;
    }

    private static boolean isShieldInHand(final LivingEntity mc, final ICustomNpc npc, final Hand hand) {
        if (hand == Hand.OFF_HAND && npc != null) {
            try {
                final IItemStack left = npc.getInventory().getLeftHand();
                if (left != null && !left.isEmpty() && isShieldStack(left.getMCItemStack(), mc)) {
                    return true;
                }
            } catch (final Exception ignored) {
            }
        }
        if (hand == Hand.MAIN_HAND && npc != null) {
            try {
                final IItemStack right = npc.getInventory().getRightHand();
                if (right != null && !right.isEmpty() && isShieldStack(right.getMCItemStack(), mc)) {
                    return true;
                }
            } catch (final Exception ignored) {
            }
        }
        if (mc == null) {
            return false;
        }
        return isShieldStack(mc.getItemInHand(hand), mc);
    }

    private static boolean isShieldStack(final ItemStack stack, final LivingEntity entity) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof ShieldItem) {
            return true;
        }
        try {
            if (stack.isShield(entity)) {
                return true;
            }
        } catch (final Exception ignored) {
        }
        final ResourceLocation reg = stack.getItem().getRegistryName();
        return reg != null && reg.getPath().toLowerCase().contains("shield");
    }

    private static void playBlockSound(final AbilityContext ctx, final LivingEntity mc) {
        if (ctx == null || ctx.world == null || ctx.npc == null) {
            return;
        }
        String sound = VANILLA_BLOCK_SOUND;
        try {
            final ItemStack off = mc.getOffhandItem();
            final ItemStack main = mc.getMainHandItem();
            final ItemStack shield = off.getItem() instanceof ShieldItem ? off
                    : (main.getItem() instanceof ShieldItem ? main : ItemStack.EMPTY);
            final ResourceLocation reg = shield.isEmpty() ? null : shield.getItem().getRegistryName();
            if (reg != null && "wfm".equals(reg.getNamespace())) {
                sound = WFM_BLOCK_SOUND;
            }
        } catch (final Exception ignored) {
        }
        ctx.world.playSoundAt(ctx.npc.getPos(), sound, 1.0F, 1.0F);
    }

    private static LivingEntity toLiving(final ICustomNpc npc) {
        if (npc == null) {
            return null;
        }
        try {
            final Object mc = npc.getMCEntity();
            if (mc instanceof LivingEntity) {
                return (LivingEntity) mc;
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    private static void fillWfmShieldHp(final LivingEntity entity) {
        ensureWfmShieldCap();
        if (!Boolean.TRUE.equals(wfmShieldCapAvailable) || entity == null) {
            return;
        }
        try {
            final Object optional = wfmGetCapability.invoke(entity, wfmShieldCapability);
            if (optional == null || !Boolean.TRUE.equals(wfmIsPresent.invoke(optional))) {
                return;
            }
            final Object data = wfmOrElse.invoke(optional, (Object) null);
            if (data == null) {
                return;
            }
            wfmSetMax.invoke(data, DEFAULT_NPC_SHIELD_HP);
            wfmSetHealth.invoke(data, DEFAULT_NPC_SHIELD_HP);
            if (wfmSetBreakTime != null) {
                wfmSetBreakTime.invoke(data, 0L);
            }
        } catch (final Exception ignored) {
        }
    }

    private static void ensureWfmShieldCap() {
        if (wfmShieldCapAvailable != null) {
            return;
        }
        try {
            final Class<?> provider = Class.forName("wfm.common.entity.capabilities.ShieldBlockDataProvider");
            final Class<?> dataClass = Class.forName("wfm.common.entity.capabilities.ShieldBlockData");
            wfmShieldCapability = provider.getField("CAPABILITY").get(null);
            wfmGetCapability = Entity.class.getMethod("getCapability",
                    Class.forName("net.minecraftforge.common.capabilities.Capability"));
            final Class<?> lazyOptional = Class.forName("net.minecraftforge.common.util.LazyOptional");
            wfmIsPresent = lazyOptional.getMethod("isPresent");
            wfmOrElse = lazyOptional.getMethod("orElse", Object.class);
            wfmSetMax = dataClass.getMethod("setMaxShieldHealth", float.class);
            wfmSetHealth = dataClass.getMethod("setShieldHealth", float.class);
            try {
                wfmSetBreakTime = dataClass.getMethod("setLastShieldBreakTime", long.class);
            } catch (final NoSuchMethodException ignored) {
                wfmSetBreakTime = null;
            }
            wfmShieldCapAvailable = true;
        } catch (final Exception e) {
            wfmShieldCapAvailable = false;
        }
    }
}
