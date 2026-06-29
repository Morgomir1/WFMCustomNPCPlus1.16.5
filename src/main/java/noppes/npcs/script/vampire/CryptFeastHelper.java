package noppes.npcs.script.vampire;

import noppes.npcs.abilities.AbilityVfx;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.data.IData;
import noppes.npcs.api.entity.data.INPCAi;
import noppes.npcs.script.ScriptDataUtil;
import noppes.npcs.script.ScriptEntityUtil;

public final class CryptFeastHelper {
    private static final String HORROR_TAG = "crypt_horror";

    private static final int RETALIATE_NONE = 3;
    private static final int RETALIATE_REVENGE = 0;

    private static final double CORPSE_DETECT_RADIUS = 2.5;
    private static final double FEAST_STAND_XZ_MAX = 0.75;
    private static final double FEAST_STAND_Y_MIN = -0.5;
    private static final double FEAST_STAND_Y_MAX = 2.8;
    private static final int FEAST_STEPS = 5;
    private static final int FEAST_COOLDOWN_TICKS = 80;

    private static final int MAX_FEAST_STACKS = 8;
    private static final double HEAL_RATIO = 0.18;
    private static final double MELEE_PER_STACK = 0.14;
    private static final double SPEED_PER_STACK = 0.10;
    private static final int DAMAGE_TYPE_COUNT = 4;
    private static final float RESIST_PER_STACK = 0.04F;
    private static final double MAX_HP_PER_STACK = 0.10;

    private static final int FEAST_BLOOD_PER_TICK = 14;
    private static final int FINISH_BLOOD_BURST = 48;
    private static final int COMBAT_IDLE_RESET_TICKS = 1200;

    private static final String BASE_SPEED_KEY = "ch_base_speed";
    private static final String BASE_MELEE_KEY = "ch_base_melee";
    private static final String BASE_RESIST_KEY = "ch_base_resist";
    private static final String BASE_MAX_HP_KEY = "ch_base_max_hp";
    private static final String FEAST_STACKS_KEY = "ch_feast_stacks";
    private static final String FEASTING_KEY = "ch_feasting";
    private static final String FEAST_STEP_KEY = "ch_feast_step";
    private static final String FEAST_TARGET_KEY = "ch_feast_target";
    private static final String FEAST_X_KEY = "ch_feast_x";
    private static final String FEAST_Y_KEY = "ch_feast_y";
    private static final String FEAST_Z_KEY = "ch_feast_z";
    private static final String FEAST_CD_KEY = "ch_feast_cd";
    private static final String SAVED_RETALIATE_KEY = "ch_saved_retaliate";
    private static final String LAST_COMBAT_KEY = "ch_last_combat";

    private CryptFeastHelper() {
    }

    public static void init(final ICustomNpc npc) {
        if (!npc.hasTag(HORROR_TAG)) {
            npc.addTag(HORROR_TAG);
        }
        final IWorld world = npc.getWorld();
        storeBaseStats(npc);
        touchCombat(npc, world);
        applyFeastStats(npc);
    }

    public static void tick(final ICustomNpc npc) {
        final IWorld world = npc.getWorld();
        final IData data = npc.getStoreddata();

        if (!npc.isAlive()) {
            clearState(data);
            return;
        }

        CryptCorpseRegistry.purgeExpired(world);
        storeBaseStats(npc);

        if (!ScriptDataUtil.isFlag(data, FEASTING_KEY)) {
            checkCombatDecay(npc, world, data);
        }

        if (ScriptDataUtil.isFlag(data, FEASTING_KEY)) {
            doFeastTick(npc, world, data);
            return;
        }

        applyFeastStats(npc);

        final long now = world.getTotalTime();
        if (!ScriptDataUtil.isCooldownReady(data, FEAST_CD_KEY, now)) {
            return;
        }
        if (ScriptDataUtil.getInt(data, FEAST_STACKS_KEY) >= MAX_FEAST_STACKS) {
            return;
        }

        final CorpseTarget corpse = CryptCorpseRegistry.findCorpseUnderfoot(
                npc, world, CORPSE_DETECT_RADIUS, FEAST_STAND_XZ_MAX, FEAST_STAND_Y_MIN, FEAST_STAND_Y_MAX);
        if (corpse == null) {
            return;
        }

        startFeast(npc, world, data, corpse);
    }

    public static void onCombat(final ICustomNpc npc) {
        touchCombat(npc, npc.getWorld());
    }

    public static void onDeath(final ICustomNpc npc) {
        clearState(npc.getStoreddata());
    }

    private static void touchCombat(final ICustomNpc npc, final IWorld world) {
        ScriptDataUtil.putInt(npc.getStoreddata(), LAST_COMBAT_KEY, (int) world.getTotalTime());
    }

    private static void checkCombatDecay(final ICustomNpc npc, final IWorld world, final IData data) {
        if (ScriptDataUtil.getInt(data, FEAST_STACKS_KEY) <= 0) {
            return;
        }
        final long now = world.getTotalTime();
        final int lastCombat = ScriptDataUtil.getInt(data, LAST_COMBAT_KEY);
        if (lastCombat <= 0) {
            touchCombat(npc, world);
            return;
        }
        if (now - lastCombat < COMBAT_IDLE_RESET_TICKS) {
            return;
        }
        resetAllFeastBonuses(npc, world, data);
    }

    private static void resetAllFeastBonuses(final ICustomNpc npc, final IWorld world, final IData data) {
        ScriptDataUtil.putInt(data, FEAST_STACKS_KEY, 0);
        applyFeastStats(npc);
        touchCombat(npc, world);

        try {
            world.playSoundAt(
                    NpcAPI.Instance().getIPos(npc.getX(), npc.getY() + 1.0, npc.getZ()),
                    "minecraft:entity.zombie_villager.cure",
                    0.35F,
                    0.55F);
        } catch (final Exception ignored) {
        }

        AbilityVfx.spawnDecayCloud(world, npc.getX(), npc.getY(), npc.getZ(), npc.getHeight());
    }

    private static void startFeast(final ICustomNpc npc, final IWorld world, final IData data, final CorpseTarget corpse) {
        final INPCAi ai = npc.getAi();

        ScriptDataUtil.setFlag(data, FEASTING_KEY, true);
        ScriptDataUtil.putInt(data, FEAST_STEP_KEY, 0);
        ScriptDataUtil.putString(data, FEAST_TARGET_KEY, corpse.uuid);
        ScriptDataUtil.putFloat(data, FEAST_X_KEY, (float) corpse.x);
        ScriptDataUtil.putFloat(data, FEAST_Y_KEY, (float) corpse.y);
        ScriptDataUtil.putFloat(data, FEAST_Z_KEY, (float) corpse.z);

        try {
            ScriptDataUtil.putInt(data, SAVED_RETALIATE_KEY, ai.getRetaliateType());
            ai.setRetaliateType(RETALIATE_NONE);
            ai.setWalkingSpeed(0);
        } catch (final Exception ignored) {
        }

        ScriptEntityUtil.faceTarget(npc, corpse.x, corpse.z);
        ScriptEntityUtil.swingMainHand(npc);

        try {
            world.playSoundAt(
                    NpcAPI.Instance().getIPos(npc.getX(), npc.getY() + 1.0, npc.getZ()),
                    "minecraft:entity.hoglin.ambient",
                    0.65F,
                    0.55F);
            world.playSoundAt(
                    NpcAPI.Instance().getIPos(corpse.x, corpse.y + 0.5, corpse.z),
                    "minecraft:entity.zombie.attack_wooden_door",
                    0.7F,
                    0.45F);
        } catch (final Exception ignored) {
        }

        AbilityVfx.spawnFeastBloodBurst(world, corpse.x, corpse.y, corpse.z, 20);
        AbilityVfx.spawnFeastBloodBurst(world, npc.getX(), npc.getY(), npc.getZ(), 8);
    }

    private static void doFeastTick(final ICustomNpc npc, final IWorld world, final IData data) {
        final float cx = ScriptDataUtil.getFloat(data, FEAST_X_KEY);
        final float cy = ScriptDataUtil.getFloat(data, FEAST_Y_KEY);
        final float cz = ScriptDataUtil.getFloat(data, FEAST_Z_KEY);

        if (!ScriptEntityUtil.isStandingOver(npc, cx, cy, cz, FEAST_STAND_XZ_MAX, FEAST_STAND_Y_MIN, FEAST_STAND_Y_MAX)) {
            endFeastState(npc, data);
            return;
        }

        final int step = ScriptDataUtil.getInt(data, FEAST_STEP_KEY) + 1;
        ScriptDataUtil.putInt(data, FEAST_STEP_KEY, step);

        ScriptEntityUtil.faceTarget(npc, cx, cz);
        ScriptEntityUtil.swingMainHand(npc);
        AbilityVfx.spawnFeastBloodBurst(world, cx, cy, cz, FEAST_BLOOD_PER_TICK);
        AbilityVfx.spawnFeastBloodBurst(world, npc.getX(), npc.getY() + npc.getHeight() * 0.4, npc.getZ(), 6);

        if (step < FEAST_STEPS) {
            return;
        }

        finishFeast(npc, world, data);
    }

    private static void finishFeast(final ICustomNpc npc, final IWorld world, final IData data) {
        final String uuid = String.valueOf(data.get(FEAST_TARGET_KEY));
        final float cx = ScriptDataUtil.getFloat(data, FEAST_X_KEY);
        final float cy = ScriptDataUtil.getFloat(data, FEAST_Y_KEY);
        final float cz = ScriptDataUtil.getFloat(data, FEAST_Z_KEY);

        CryptCorpseRegistry.markEaten(world, uuid, cx, cy, cz);

        int stacks = ScriptDataUtil.getInt(data, FEAST_STACKS_KEY) + 1;
        if (stacks > MAX_FEAST_STACKS) {
            stacks = MAX_FEAST_STACKS;
        }
        ScriptDataUtil.putInt(data, FEAST_STACKS_KEY, stacks);

        applyFeastStats(npc);
        healFromFeast(npc);

        ScriptDataUtil.setCooldown(data, FEAST_CD_KEY, world.getTotalTime(), FEAST_COOLDOWN_TICKS);
        endFeastState(npc, data);

        try {
            world.playSoundAt(
                    NpcAPI.Instance().getIPos(npc.getX(), npc.getY() + 1.2, npc.getZ()),
                    "minecraft:entity.ravager.roar",
                    0.55F,
                    0.8F + stacks * 0.03F);
            world.playSoundAt(
                    NpcAPI.Instance().getIPos(cx, cy + 0.6, cz),
                    "minecraft:entity.player.splash",
                    0.8F,
                    0.7F);
        } catch (final Exception ignored) {
        }

        AbilityVfx.spawnFeastBloodBurst(world, cx, cy, cz, FINISH_BLOOD_BURST);
        AbilityVfx.spawnFeastBloodBurst(world, npc.getX(), npc.getY() + npc.getHeight() * 0.5, npc.getZ(), 24);
        AbilityVfx.spawnFeastFinishFlourish(world, cx, cy, cz);
    }

    private static void endFeastState(final ICustomNpc npc, final IData data) {
        final INPCAi ai = npc.getAi();

        ScriptDataUtil.setFlag(data, FEASTING_KEY, false);
        ScriptDataUtil.putInt(data, FEAST_STEP_KEY, 0);
        data.remove(FEAST_TARGET_KEY);
        data.remove(FEAST_X_KEY);
        data.remove(FEAST_Y_KEY);
        data.remove(FEAST_Z_KEY);

        try {
            final int saved = ScriptDataUtil.getInt(data, SAVED_RETALIATE_KEY);
            if (saved == RETALIATE_NONE || saved == RETALIATE_REVENGE || saved == 1 || saved == 2) {
                ai.setRetaliateType(saved);
            } else {
                ai.setRetaliateType(RETALIATE_REVENGE);
            }
        } catch (final Exception ignored) {
        }

        data.remove(SAVED_RETALIATE_KEY);
        applyFeastStats(npc);
    }

    private static void healFromFeast(final ICustomNpc npc) {
        final float maxHp = npc.getMaxHealth();
        if (maxHp <= 0) {
            return;
        }
        final float heal = maxHp * (float) HEAL_RATIO;
        float hp = npc.getHealth();
        if (hp < 0) {
            hp = 0;
        }
        float next = hp + heal;
        if (next > maxHp) {
            next = maxHp;
        }
        try {
            npc.setHealth(next);
        } catch (final Exception ignored) {
        }
    }

    private static void applyFeastStats(final ICustomNpc npc) {
        final IData data = npc.getStoreddata();
        final INPCAi ai = npc.getAi();
        final int stacks = ScriptDataUtil.getInt(data, FEAST_STACKS_KEY);

        final int baseSpeed = getBaseSpeed(data, ai);
        final float baseMelee = getBaseMelee(data, npc);
        final float baseResist = getBaseResist(data, npc);
        final float baseMaxHp = getBaseMaxHp(data, npc);

        final double speedMult = 1.0 + SPEED_PER_STACK * stacks;
        final double meleeMult = 1.0 + MELEE_PER_STACK * stacks;
        final double maxHpMult = 1.0 + MAX_HP_PER_STACK * stacks;
        final float resist = Math.min(1.0F, baseResist + RESIST_PER_STACK * stacks);

        final int speed = Math.max(1, (int) Math.round(baseSpeed * speedMult));
        final int melee = Math.max(1, Math.round(baseMelee * (float) meleeMult));
        final int maxHp = Math.max(1, Math.round(baseMaxHp * (float) maxHpMult));

        try {
            ai.setWalkingSpeed(speed);
            npc.getStats().getMelee().setStrength(melee);
            for (int damageType = 0; damageType < DAMAGE_TYPE_COUNT; damageType++) {
                npc.getStats().setResistance(damageType, resist);
            }
            npc.getStats().setMaxHealth(maxHp);
            npc.setMaxHealth(maxHp);

            final float hp = npc.getHealth();
            if (hp > maxHp) {
                npc.setHealth(maxHp);
            }
        } catch (final Exception ignored) {
        }
    }

    private static void storeBaseStats(final ICustomNpc npc) {
        final IData data = npc.getStoreddata();
        final INPCAi ai = npc.getAi();

        if (!data.has(BASE_SPEED_KEY)) {
            ScriptDataUtil.putInt(data, BASE_SPEED_KEY, ai.getWalkingSpeed());
        }
        if (!data.has(BASE_MELEE_KEY)) {
            float melee = 8.0F;
            try {
                melee = npc.getStats().getMelee().getStrength();
            } catch (final Exception ignored) {
            }
            if (melee <= 0) {
                melee = 8.0F;
            }
            ScriptDataUtil.putFloat(data, BASE_MELEE_KEY, melee);
        }
        if (!data.has(BASE_RESIST_KEY)) {
            float resist = 0.0F;
            try {
                resist = npc.getStats().getResistance(0);
            } catch (final Exception ignored) {
            }
            if (resist < 0.0F) {
                resist = 0.0F;
            }
            ScriptDataUtil.putFloat(data, BASE_RESIST_KEY, resist);
        }
        if (!data.has(BASE_MAX_HP_KEY)) {
            float maxHp = npc.getMaxHealth();
            if (maxHp <= 0) {
                try {
                    maxHp = npc.getStats().getMaxHealth();
                } catch (final Exception ignored) {
                    maxHp = 60.0F;
                }
            }
            if (maxHp <= 0) {
                maxHp = 60.0F;
            }
            ScriptDataUtil.putFloat(data, BASE_MAX_HP_KEY, maxHp);
        }
    }

    private static int getBaseSpeed(final IData data, final INPCAi ai) {
        if (data.has(BASE_SPEED_KEY)) {
            return ScriptDataUtil.getInt(data, BASE_SPEED_KEY);
        }
        return ai.getWalkingSpeed();
    }

    private static float getBaseMelee(final IData data, final ICustomNpc npc) {
        if (data.has(BASE_MELEE_KEY)) {
            return ScriptDataUtil.getFloat(data, BASE_MELEE_KEY);
        }
        try {
            return npc.getStats().getMelee().getStrength();
        } catch (final Exception ignored) {
            return 8.0F;
        }
    }

    private static float getBaseResist(final IData data, final ICustomNpc npc) {
        if (data.has(BASE_RESIST_KEY)) {
            return ScriptDataUtil.getFloat(data, BASE_RESIST_KEY);
        }
        try {
            return npc.getStats().getResistance(0);
        } catch (final Exception ignored) {
            return 0.0F;
        }
    }

    private static float getBaseMaxHp(final IData data, final ICustomNpc npc) {
        if (data.has(BASE_MAX_HP_KEY)) {
            return ScriptDataUtil.getFloat(data, BASE_MAX_HP_KEY);
        }
        final float maxHp = npc.getMaxHealth();
        return maxHp > 0 ? maxHp : 60.0F;
    }

    private static void clearState(final IData data) {
        data.remove(FEASTING_KEY);
        data.remove(FEAST_STEP_KEY);
        data.remove(FEAST_TARGET_KEY);
        data.remove(FEAST_X_KEY);
        data.remove(FEAST_Y_KEY);
        data.remove(FEAST_Z_KEY);
        data.remove(FEAST_CD_KEY);
        data.remove(SAVED_RETALIATE_KEY);
        data.remove(FEAST_STACKS_KEY);
        data.remove(LAST_COMBAT_KEY);
    }
}
