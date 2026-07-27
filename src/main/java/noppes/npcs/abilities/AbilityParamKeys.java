package noppes.npcs.abilities;

public final class AbilityParamKeys {
    public static final String DISTANCE = "distance";
    public static final String CHARGE_TICKS = "chargeTicks";
    public static final String ACTIVE_TICKS = "activeTicks";
    public static final String DAMAGE = "damage";
    public static final String KNOCKBACK = "knockback";
    public static final String KNOCKBACK_Y = "knockbackY";
    public static final String HIT_RADIUS = "hitRadius";
    public static final String LAND_RADIUS = "landRadius";
    public static final String ARC_HEIGHT = "arcHeight";
    public static final String ACCURACY = "accuracy";
    public static final String MAX_RANGE = "maxRange";
    public static final String PROJECTILE_ITEM = "projectileItem";
    public static final String EFFECT_DURATION = "effectDuration";
    public static final String EFFECT_AMPLIFIER = "effectAmplifier";
    public static final String EFFECT_TYPE = "effectType";
    public static final String UNDEAD_BONUS_MULTIPLIER = "undeadBonusMultiplier";
    public static final String RETREAT = "retreat";
    public static final String AURA_RADIUS = "auraRadius";
    public static final String DAMAGE_PER_TICK = "damagePerTick";
    public static final String CONE_HALF_ANGLE = "coneHalfAngle";
    public static final String RADIUS = "radius";
    public static final String SHOTS = "shots";
    public static final String SHOT_INTERVAL = "shotInterval";
    public static final String FIRST_SHOT_TICK = "firstShotTick";
    public static final String BULLET_VELOCITY = "bulletVelocity";
    public static final String LIFE_STEAL_PER_HIT = "lifeStealPerHit";
    public static final String HEAL_PER_TICK = "healPerTick";
    public static final String SUMMON_COUNT = "summonCount";
    public static final String SUMMON_RADIUS = "summonRadius";
    public static final String MAX_SUMMONED_NEAR_BOSS = "maxSummonedNearBoss";
    public static final String CLONE_TAB = "cloneTab";
    public static final String CLONE_NAME = "cloneName";
    public static final String EXECUTE_HP_THRESHOLD = "executeHpThreshold";
    public static final String EXECUTE_BONUS_DAMAGE = "executeBonusDamage";
    public static final String TELEGRAPH = "telegraph";
    public static final String TELEGRAPH_COLOR = "telegraphColor";
    public static final String TELEGRAPH_FORWARD = "telegraphForward";
    public static final String SPREAD_RADIUS = "spreadRadius";
    /** Полный угол фронтального блока щитом (градусы). */
    public static final String BLOCK_ANGLE = "blockAngle";
    /** Lifetime hazard-зоны (тики). */
    public static final String ZONE_TICKS = "zoneTicks";
    /** Интервал урона hazard-зоны (тики между хитами). */
    public static final String DAMAGE_INTERVAL = "damageInterval";
    /** ARGB цвет Ability Zone. */
    public static final String ZONE_COLOR = "zoneColor";
    /** Registry id эффекта зоны (несколько через `;`). */
    public static final String EFFECT_ID = "effectId";
    /** Партиклы полёта сгустка (через `,`). */
    public static final String BLOB_PARTICLES = "blobParticles";
    /** Партиклы приземления (через `,`). */
    public static final String LAND_PARTICLES = "landParticles";
    /** Сколько партиклов каждого типа на тик полёта. */
    public static final String PARTICLE_COUNT = "particleCount";
    /** Секунды горения цели после попадания. */
    public static final String FIRE_SECONDS = "fireSeconds";
    /** Длительность фазы разлёта (тики), напр. fire bomb scatter. */
    public static final String SCATTER_TICKS = "scatterTicks";
    /** Item id основного оружия (рапира) для restore после swap. */
    public static final String MELEE_ITEM = "meleeItem";
    /** Item id оружия дальнего боя (арбалет). */
    public static final String RANGED_ITEM = "rangedItem";
    /** Порог meter для прерывания абилки (напр. урон в спину). */
    public static final String BREAK_DAMAGE = "breakDamage";
    /** Порог melee-хитов для прерывания (напр. devour spit). */
    public static final String HIT_COUNT = "hitCount";

    private AbilityParamKeys() {
    }
}
