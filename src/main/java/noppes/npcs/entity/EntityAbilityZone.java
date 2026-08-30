package noppes.npcs.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.ICustomNpc;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityVfx;

import java.util.List;
import java.util.UUID;

public class EntityAbilityZone extends Entity {
    public enum ZoneShape {
        CIRCLE, SQUARE, RING;

        public static ZoneShape byId(final int id) {
            final ZoneShape[] v = values();
            if (id < 0 || id >= v.length) {
                return CIRCLE;
            }
            return v[id];
        }
    }

    public enum ZoneType {
        HAZARD, TRAP;

        public static ZoneType byId(final int id) {
            final ZoneType[] v = values();
            if (id < 0 || id >= v.length) {
                return HAZARD;
            }
            return v[id];
        }
    }

    private static final DataParameter<Integer> DATA_SHAPE =
            EntityDataManager.defineId(EntityAbilityZone.class, DataSerializers.INT);
    private static final DataParameter<Integer> DATA_ZONE_TYPE =
            EntityDataManager.defineId(EntityAbilityZone.class, DataSerializers.INT);
    private static final DataParameter<Float> DATA_RADIUS =
            EntityDataManager.defineId(EntityAbilityZone.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> DATA_INNER_RADIUS =
            EntityDataManager.defineId(EntityAbilityZone.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> DATA_HEIGHT =
            EntityDataManager.defineId(EntityAbilityZone.class, DataSerializers.FLOAT);
    private static final DataParameter<Integer> DATA_COLOR =
            EntityDataManager.defineId(EntityAbilityZone.class, DataSerializers.INT);
    private static final DataParameter<Boolean> DATA_VISIBLE =
            EntityDataManager.defineId(EntityAbilityZone.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> DATA_GROUND_FILL =
            EntityDataManager.defineId(EntityAbilityZone.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Boolean> DATA_BORDER =
            EntityDataManager.defineId(EntityAbilityZone.class, DataSerializers.BOOLEAN);

    private int lifetimeTicks = 100;
    private float damage = 2.0f;
    private int damageInterval = 20;
    private float knockback = 0.0f;
    /** Seconds on fire applied with each damage tick (0 = off). */
    private int fireSeconds;
    private int damageCooldown;
    private UUID ownerUuid;
    private int ownerEntityId = -1;
    private String effectId = "";
    private int effectDuration;
    private int effectAmplifier;
    private boolean trapTriggered;
    private int triggerFlashTick = -1;
    private boolean followOwner;

    private float healOwner;

    public EntityAbilityZone(final EntityType<?> type, final World level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SHAPE, ZoneShape.CIRCLE.ordinal());
        this.entityData.define(DATA_ZONE_TYPE, ZoneType.HAZARD.ordinal());
        this.entityData.define(DATA_RADIUS, 3.0f);
        this.entityData.define(DATA_INNER_RADIUS, 0.0f);
        this.entityData.define(DATA_HEIGHT, 2.0f);
        this.entityData.define(DATA_COLOR, 0x80FF3030);
        this.entityData.define(DATA_VISIBLE, true);
        this.entityData.define(DATA_GROUND_FILL, true);
        this.entityData.define(DATA_BORDER, true);
    }

    @Override
    public void onSyncedDataUpdated(final DataParameter<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_RADIUS.equals(key) || DATA_HEIGHT.equals(key)) {
            refreshZoneDimensions();
        }
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        super.tick();
        this.setDeltaMovement(Vector3d.ZERO);
        this.setInvulnerable(true);

        if (this.level.isClientSide) {
            return;
        }

        this.lifetimeTicks--;
        if (this.lifetimeTicks <= 0) {
            this.remove();
            return;
        }

        if (this.followOwner) {
            if (!tickFollowOwner()) {
                this.remove();
                return;
            }
        }

        if (this.getZoneType() == ZoneType.TRAP) {
            if (!this.trapTriggered) {
                final LivingEntity victim = findFirstVictim();
                if (victim != null) {
                    this.trapTriggered = true;
                    this.triggerFlashTick = this.tickCount;
                    this.entityData.set(DATA_VISIBLE, true);
                    applyHit(victim);
                    this.lifetimeTicks = Math.min(this.lifetimeTicks, 10);
                }
            }
            return;
        }

        if (healOwnerIfInside()) {
            this.remove();
            return;
        }

        if (this.damageCooldown > 0) {
            this.damageCooldown--;
            return;
        }
        this.damageCooldown = Math.max(1, this.damageInterval);
        damageVictims();
    }

    private void damageVictims() {
        final IEntityLiving owner = resolveOwner();
        final float r = getRadius();
        final float h = getZoneHeight();
        final AxisAlignedBB box = new AxisAlignedBB(
                this.getX() - r - 0.5,
                this.getY() - 0.5,
                this.getZ() - r - 0.5,
                this.getX() + r + 0.5,
                this.getY() + h + 0.5,
                this.getZ() + r + 0.5);
        final List<LivingEntity> list = this.level.getEntitiesOfClass(LivingEntity.class, box);
        for (final LivingEntity living : list) {
            if (!isInside(living)) {
                continue;
            }
            if (owner != null) {
                try {
                    final IEntity wrapped = NpcAPI.Instance().getIEntity(living);
                    if (!AbilityCombatHelper.isHostileToBoss(owner, wrapped)) {
                        continue;
                    }
                } catch (final Exception e) {
                    if (living.getUUID().equals(this.ownerUuid)) {
                        continue;
                    }
                }
            } else if (this.ownerUuid != null && living.getUUID().equals(this.ownerUuid)) {
                continue;
            }
            applyHit(living);
        }
    }

    /**
     * @return true if the owner picked up this zone (heal + VFX); caller should remove it
     */
    private boolean healOwnerIfInside() {
        if (this.healOwner <= 0.001f) {
            return false;
        }
        final IEntityLiving owner = resolveOwner();
        if (owner == null || !owner.isAlive()) {
            return false;
        }
        try {
            final Object mc = owner.getMCEntity();
            if (!(mc instanceof LivingEntity)) {
                return false;
            }
            final LivingEntity living = (LivingEntity) mc;
            if (!isInside(living)) {
                return false;
            }
            final double dy = living.getY() - this.getY();
            if (dy < -0.5 || dy > getZoneHeight() + 0.5) {
                return false;
            }
            AbilityCombatHelper.healLiving(living, this.healOwner);
            AbilityVfx.spawnBloodHeal(owner.getWorld(), owner.getX(), owner.getY() + 1.0, owner.getZ());
            AbilityVfx.spawnBloodBurst(owner.getWorld(), this.getX(), this.getY() + 0.2, this.getZ(), getRadius());
            try {
                owner.getWorld().playSoundAt(owner.getPos(), "minecraft:entity.generic.drink", 1.0F, 0.75F);
                owner.getWorld().playSoundAt(owner.getPos(), "minecraft:item.honey_bottle.drink", 0.9F, 0.7F);
                owner.getWorld().playSoundAt(owner.getPos(), "minecraft:block.beacon.power_select", 0.65F, 1.55F);
            } catch (final Exception ignoredSound) {
            }
            return true;
        } catch (final Exception ignored) {
            return false;
        }
    }

    private LivingEntity findFirstVictim() {
        final IEntityLiving owner = resolveOwner();
        final float r = getRadius();
        final float h = getZoneHeight();
        final AxisAlignedBB box = new AxisAlignedBB(
                this.getX() - r,
                this.getY() - 0.25,
                this.getZ() - r,
                this.getX() + r,
                this.getY() + h,
                this.getZ() + r);
        for (final LivingEntity living : this.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!isInside(living)) {
                continue;
            }
            if (owner != null) {
                try {
                    final IEntity wrapped = NpcAPI.Instance().getIEntity(living);
                    if (!AbilityCombatHelper.isHostileToBoss(owner, wrapped)) {
                        continue;
                    }
                } catch (final Exception ignored) {
                    continue;
                }
            }
            return living;
        }
        return null;
    }

    private void applyHit(final LivingEntity living) {
        // Pure MAGIC via shared helper; ignore i-frames so DoT interval is reliable.
        if (this.damage > 0.001f) {
            AbilityCombatHelper.dealPureDamage(living, this.damage, true);
        }
        if (this.fireSeconds > 0) {
            living.setSecondsOnFire(this.fireSeconds);
        }
        if (this.knockback > 0.001f) {
            final double dx = living.getX() - this.getX();
            final double dz = living.getZ() - this.getZ();
            final double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.05) {
                living.setDeltaMovement(
                        living.getDeltaMovement().add(dx / len * this.knockback, 0.15, dz / len * this.knockback));
                living.hurtMarked = true;
            }
        }
        if (this.effectId != null && !this.effectId.isEmpty() && this.effectDuration > 0) {
            final String[] ids = this.effectId.split("[;|]");
            for (int i = 0; i < ids.length; i++) {
                String id = ids[i].trim();
                if (id.isEmpty()) {
                    continue;
                }
                if (id.indexOf(':') < 0) {
                    id = "minecraft:" + id;
                }
                final Effect effect = ForgeRegistries.POTIONS.getValue(new ResourceLocation(id));
                if (effect == null) {
                    continue;
                }
                try {
                    living.addEffect(new EffectInstance(effect, this.effectDuration, this.effectAmplifier));
                } catch (final Exception ignored) {
                    // WFM/other mods may throw from PotionApplicableEvent handlers (e.g. bad setCanceled).
                }
            }
        }
    }

    private boolean isInside(final LivingEntity living) {
        final double dx = living.getX() - this.getX();
        final double dz = living.getZ() - this.getZ();
        final double dist = Math.sqrt(dx * dx + dz * dz);
        final float r = getRadius();
        final float inner = getInnerRadius();
        switch (getShape()) {
            case SQUARE:
                return Math.abs(dx) <= r && Math.abs(dz) <= r;
            case RING:
                return dist <= r && dist >= inner;
            case CIRCLE:
            default:
                return dist <= r;
        }
    }

    private boolean tickFollowOwner() {
        final IEntityLiving owner = resolveOwner();
        if (owner == null || !owner.isAlive()) {
            return false;
        }
        final double x = owner.getX();
        final double z = owner.getZ();
        final double y = AbilityCombatHelper.findFeetGroundY(owner.getWorld(), x, z, owner.getY()) + 0.05;
        this.moveTo(x, y, z, 0, 0);
        if (getShape() == ZoneShape.RING && this.tickCount % 2 == 0) {
            AbilityVfx.spawnDarkSoulRing(
                    owner.getWorld(),
                    x,
                    y,
                    z,
                    getInnerRadius(),
                    getRadius());
        }
        return true;
    }

    public void setFollowOwner(final boolean follow) {
        this.followOwner = follow;
    }

    public boolean isFollowOwner() {
        return this.followOwner;
    }

    private IEntityLiving resolveOwner() {
        try {
            if (this.ownerEntityId >= 0) {
                final Entity e = this.level.getEntity(this.ownerEntityId);
                if (e != null) {
                    final IEntity wrapped = NpcAPI.Instance().getIEntity(e);
                    if (wrapped instanceof IEntityLiving) {
                        return (IEntityLiving) wrapped;
                    }
                }
            }
            if (this.ownerUuid != null && this.level instanceof net.minecraft.world.server.ServerWorld) {
                final Entity e = ((net.minecraft.world.server.ServerWorld) this.level).getEntity(this.ownerUuid);
                if (e != null) {
                    final IEntity wrapped = NpcAPI.Instance().getIEntity(e);
                    if (wrapped instanceof IEntityLiving) {
                        this.ownerEntityId = e.getId();
                        return (IEntityLiving) wrapped;
                    }
                }
            }
        } catch (final Exception ignored) {
        }
        return null;
    }

    public void configureHazard(
            final ICustomNpc owner,
            final ZoneShape shape,
            final float radius,
            final float innerRadius,
            final int lifetimeTicks,
            final float damage,
            final int damageInterval) {
        setShape(shape);
        setZoneType(ZoneType.HAZARD);
        setRadius(radius);
        setInnerRadius(innerRadius);
        this.lifetimeTicks = Math.max(1, lifetimeTicks);
        this.damage = damage;
        this.damageInterval = Math.max(1, damageInterval);
        this.damageCooldown = 0;
        if (owner != null) {
            try {
                this.ownerUuid = UUID.fromString(String.valueOf(owner.getUUID()));
                this.ownerEntityId = owner.getMCEntity().getId();
            } catch (final Exception ignored) {
            }
        }
        setVisible(true);
        setGroundFill(true);
        setBorder(true);
        refreshZoneDimensions();
    }

    public void configureTrap(
            final ICustomNpc owner,
            final ZoneShape shape,
            final float radius,
            final int lifetimeTicks,
            final float damage) {
        configureHazard(owner, shape, radius, 0, lifetimeTicks, damage, 999999);
        setZoneType(ZoneType.TRAP);
        setVisible(false);
    }

    public ZoneShape getShape() {
        return ZoneShape.byId(this.entityData.get(DATA_SHAPE));
    }

    public void setShape(final ZoneShape shape) {
        this.entityData.set(DATA_SHAPE, shape.ordinal());
    }

    public ZoneType getZoneType() {
        return ZoneType.byId(this.entityData.get(DATA_ZONE_TYPE));
    }

    public void setZoneType(final ZoneType type) {
        this.entityData.set(DATA_ZONE_TYPE, type.ordinal());
    }

    public float getRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public void setRadius(final float radius) {
        this.entityData.set(DATA_RADIUS, Math.max(0.1f, radius));
        refreshZoneDimensions();
    }

    public float getInnerRadius() {
        return this.entityData.get(DATA_INNER_RADIUS);
    }

    public void setInnerRadius(final float inner) {
        this.entityData.set(DATA_INNER_RADIUS, Math.max(0f, inner));
    }

    public float getZoneHeight() {
        return this.entityData.get(DATA_HEIGHT);
    }

    public void setZoneHeight(final float height) {
        this.entityData.set(DATA_HEIGHT, Math.max(0.5f, height));
        refreshZoneDimensions();
    }

    /**
     * Ваниль считает дистанцию рендера от размера AABB (для 0.1×0.1 ≈ 6 блоков).
     * Зона рисуется с радиусом до нескольких блоков — расширяем лимит.
     */
    @Override
    public boolean shouldRenderAtSqrDistance(final double distance) {
        final double range = Math.max(64.0, getRadius() * 2.0 + 48.0);
        return distance < range * range;
    }

    @Override
    public net.minecraft.entity.EntitySize getDimensions(final net.minecraft.entity.Pose pose) {
        final float diameter = Math.max(1.0f, getRadius() * 2.0f);
        final float height = Math.max(1.0f, getZoneHeight());
        return net.minecraft.entity.EntitySize.scalable(diameter, height);
    }

    private void refreshZoneDimensions() {
        // Vanilla Entity.refreshDimensions() recenters by moving when width grows;
        // for a ground marker that must stay pinned, restore X/Y/Z after resize.
        final double x = this.getX();
        final double y = this.getY();
        final double z = this.getZ();
        this.refreshDimensions();
        this.setPos(x, y, z);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        final float diameter = Math.max(1.0f, getRadius() * 2.0f);
        final float height = Math.max(1.0f, getZoneHeight());
        final double half = diameter * 0.5;
        this.setBoundingBox(new AxisAlignedBB(
                x - half, y, z - half,
                x + half, y + height, z + half));
    }

    public int getColor() {
        return this.entityData.get(DATA_COLOR);
    }

    public void setColor(final int color) {
        this.entityData.set(DATA_COLOR, color);
    }

    public boolean isZoneVisible() {
        return this.entityData.get(DATA_VISIBLE);
    }

    public void setVisible(final boolean visible) {
        this.entityData.set(DATA_VISIBLE, visible);
    }

    public boolean isGroundFill() {
        return this.entityData.get(DATA_GROUND_FILL);
    }

    public void setGroundFill(final boolean fill) {
        this.entityData.set(DATA_GROUND_FILL, fill);
    }

    public boolean isBorder() {
        return this.entityData.get(DATA_BORDER);
    }

    public void setBorder(final boolean border) {
        this.entityData.set(DATA_BORDER, border);
    }

    public void setEffect(final String effectId, final int duration, final int amplifier) {
        this.effectId = effectId == null ? "" : effectId;
        this.effectDuration = duration;
        this.effectAmplifier = amplifier;
    }

    public void setKnockback(final float knockback) {
        this.knockback = knockback;
    }

    public void setDamage(final float damage) {
        this.damage = Math.max(0f, damage);
    }

    public float getDamage() {
        return this.damage;
    }

    public void setHealOwner(final float heal) {
        this.healOwner = Math.max(0f, heal);
    }

    public float getHealOwner() {
        return this.healOwner;
    }

    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public void setLifetimeTicks(final int ticks) {
        this.lifetimeTicks = Math.max(1, ticks);
    }

    public void setDamageInterval(final int interval) {
        this.damageInterval = Math.max(1, interval);
    }

    public void setFireSeconds(final int seconds) {
        this.fireSeconds = Math.max(0, seconds);
    }

    public int getFireSeconds() {
        return this.fireSeconds;
    }

    public int getTriggerFlashTick() {
        return this.triggerFlashTick;
    }

    public boolean isTrapTriggered() {
        return this.trapTriggered;
    }

    @Override
    protected void readAdditionalSaveData(final CompoundNBT nbt) {
        setShape(ZoneShape.byId(nbt.getInt("Shape")));
        setZoneType(ZoneType.byId(nbt.getInt("ZoneType")));
        setRadius(nbt.getFloat("Radius"));
        setInnerRadius(nbt.getFloat("InnerRadius"));
        setZoneHeight(nbt.getFloat("Height"));
        setColor(nbt.getInt("Color"));
        setVisible(nbt.getBoolean("Visible"));
        setGroundFill(nbt.getBoolean("GroundFill"));
        setBorder(nbt.getBoolean("Border"));
        this.lifetimeTicks = nbt.getInt("Lifetime");
        this.damage = nbt.getFloat("Damage");
        this.damageInterval = nbt.getInt("DamageInterval");
        this.knockback = nbt.getFloat("Knockback");
        this.fireSeconds = nbt.getInt("FireSeconds");
        this.effectId = nbt.getString("EffectId");
        this.effectDuration = nbt.getInt("EffectDuration");
        this.effectAmplifier = nbt.getInt("EffectAmplifier");
        if (nbt.hasUUID("Owner")) {
            this.ownerUuid = nbt.getUUID("Owner");
        }
        this.followOwner = nbt.getBoolean("FollowOwner");
        this.healOwner = nbt.getFloat("HealOwner");
    }

    @Override
    protected void addAdditionalSaveData(final CompoundNBT nbt) {
        nbt.putInt("Shape", getShape().ordinal());
        nbt.putInt("ZoneType", getZoneType().ordinal());
        nbt.putFloat("Radius", getRadius());
        nbt.putFloat("InnerRadius", getInnerRadius());
        nbt.putFloat("Height", getZoneHeight());
        nbt.putInt("Color", getColor());
        nbt.putBoolean("Visible", isZoneVisible());
        nbt.putBoolean("GroundFill", isGroundFill());
        nbt.putBoolean("Border", isBorder());
        nbt.putInt("Lifetime", this.lifetimeTicks);
        nbt.putFloat("Damage", this.damage);
        nbt.putInt("DamageInterval", this.damageInterval);
        nbt.putFloat("Knockback", this.knockback);
        nbt.putInt("FireSeconds", this.fireSeconds);
        nbt.putString("EffectId", this.effectId == null ? "" : this.effectId);
        nbt.putInt("EffectDuration", this.effectDuration);
        nbt.putInt("EffectAmplifier", this.effectAmplifier);
        if (this.ownerUuid != null) {
            nbt.putUUID("Owner", this.ownerUuid);
        }
        nbt.putBoolean("FollowOwner", this.followOwner);
        nbt.putFloat("HealOwner", this.healOwner);
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
