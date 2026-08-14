package noppes.npcs.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.NetworkHooks;
import noppes.npcs.CustomEntities;
import noppes.npcs.abilities.AbilityCombatHelper;
import noppes.npcs.abilities.AbilityVfx;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.telegraph.TelegraphAPI;

import java.util.List;
import java.util.UUID;

/**
 * Passive rotating rectangular beam hazards around the necromancer.
 * Visual: elongated ground rectangles (see {@link noppes.npcs.client.renderer.RenderNecroBeam}).
 * Spacing: {@code 360 / beamCount} degrees (exactly 120° for 3 beams).
 */
public class EntityNecroBeam extends Entity {
    private static final DataParameter<String> DATA_OWNER_UUID =
            EntityDataManager.defineId(EntityNecroBeam.class, DataSerializers.STRING);
    private static final DataParameter<Integer> DATA_OWNER_ID =
            EntityDataManager.defineId(EntityNecroBeam.class, DataSerializers.INT);
    private static final DataParameter<Integer> DATA_BEAM_COUNT =
            EntityDataManager.defineId(EntityNecroBeam.class, DataSerializers.INT);
    private static final DataParameter<Float> DATA_BASE_YAW =
            EntityDataManager.defineId(EntityNecroBeam.class, DataSerializers.FLOAT);

    /** Corridor length (blocks) along beam yaw. */
    public static final double LENGTH = 16.0;
    /** Full width of rectangular zone (blocks). */
    public static final double ZONE_WIDTH = 1.6;
    private static final double HALF_WIDTH = ZONE_WIDTH * 0.5;
    private static final double HIT_HEIGHT = 2.2;
    private static final float DAMAGE = 4.0F;
    private static final int DAMAGE_INTERVAL = 10;
    /** Degrees per tick — ~0.75° ≈ full spin in ~24s (readable dodge). Was 2.5°. */
    private static final float YAW_PER_TICK = 0.75F;
    private static final int PARTICLE_INTERVAL = 2;
    private static final int PARTICLES_PER_BEAM = 5;

    private float prevBaseYaw;

    public EntityNecroBeam(final World level) {
        this(CustomEntities.entityNecroBeam, level);
    }

    public EntityNecroBeam(final EntityType<?> type, final World level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_OWNER_UUID, "");
        this.entityData.define(DATA_OWNER_ID, -1);
        this.entityData.define(DATA_BEAM_COUNT, 1);
        this.entityData.define(DATA_BASE_YAW, 0.0F);
    }

    @Override
    public void tick() {
        this.noPhysics = true;
        this.prevBaseYaw = getBaseYaw();
        super.tick();
        this.setNoGravity(true);
        this.setDeltaMovement(Vector3d.ZERO);

        final LivingEntity owner = resolveOwnerEntity();
        if (!this.level.isClientSide) {
            if (owner == null || !owner.isAlive()) {
                if (this.tickCount > 10) {
                    this.remove();
                }
                return;
            }
            this.entityData.set(DATA_OWNER_ID, owner.getId());
            followOwner(owner);
            float nextYaw = getBaseYaw() + YAW_PER_TICK;
            if (nextYaw >= 360.0F) {
                nextYaw -= 360.0F;
            }
            setBaseYaw(nextYaw);
            if (this.tickCount % DAMAGE_INTERVAL == 0) {
                damageVictims(owner, this.getX(), this.getY(), this.getZ());
            }
            if (this.tickCount % PARTICLE_INTERVAL == 0) {
                spawnZoneParticles(owner);
            }
            return;
        }

        if (owner != null && owner.isAlive()) {
            followOwner(owner);
        }
    }

    private void followOwner(final LivingEntity owner) {
        final double groundY = TelegraphAPI.resolveGroundY(
                this.level, owner.getX(), owner.getY(), owner.getZ());
        this.moveTo(owner.getX(), groundY, owner.getZ(), 0, 0);
    }

    public float getInterpolatedYaw(final float partialTicks) {
        return this.prevBaseYaw + MathHelper.wrapDegrees(getBaseYaw() - this.prevBaseYaw) * partialTicks;
    }

    /**
     * Yaw of beam {@code index} so beams are evenly spaced on a full circle.
     * For 3 beams the step is exactly 120°.
     */
    public static float beamYaw(final float baseYaw, final int index, final int beamCount) {
        final int n = Math.max(1, beamCount);
        return baseYaw + index * (360.0F / n);
    }

    /**
     * Forward XZ for Minecraft / WFMTelegraph LINE yaw (local +Z after {@code -yaw} Y rotation).
     */
    public static void directionFromYaw(final float yawDeg, final double[] outXZ) {
        final double rad = yawDeg * (Math.PI / 180.0);
        outXZ[0] = -Math.sin(rad);
        outXZ[1] = Math.cos(rad);
    }

    private void damageVictims(
            final LivingEntity ownerMc,
            final double ox,
            final double oy,
            final double oz) {
        final IEntity ownerWrapped;
        try {
            ownerWrapped = NpcAPI.Instance().getIEntity(ownerMc);
        } catch (final Exception ignored) {
            return;
        }
        if (!(ownerWrapped instanceof IEntityLiving)) {
            return;
        }
        final AxisAlignedBB box = new AxisAlignedBB(
                ox - LENGTH,
                oy - 0.5,
                oz - LENGTH,
                ox + LENGTH,
                oy + HIT_HEIGHT,
                oz + LENGTH);
        final List<LivingEntity> list = this.level.getEntitiesOfClass(LivingEntity.class, box);
        for (final LivingEntity living : list) {
            if (living == ownerMc) {
                continue;
            }
            final IEntity wrapped;
            try {
                wrapped = NpcAPI.Instance().getIEntity(living);
            } catch (final Exception ignored) {
                continue;
            }
            if (!AbilityCombatHelper.isHostileToBoss((IEntityLiving) ownerWrapped, wrapped)) {
                continue;
            }
            final double tx = living.getX();
            final double tz = living.getZ();
            if (!isHitByAnyBeam(tx, tz)) {
                continue;
            }
            final double midY = living.getY() + living.getBbHeight() * 0.5;
            if (midY < oy - 0.25 || midY > oy + HIT_HEIGHT) {
                continue;
            }
            living.hurt(DamageSource.MAGIC, DAMAGE);
            AbilityVfx.spawnHitParticle(((IEntityLiving) ownerWrapped).getWorld(), wrapped);
        }
    }

    private boolean isHitByAnyBeam(final double tx, final double tz) {
        final double dx = tx - this.getX();
        final double dz = tz - this.getZ();
        final int beams = getBeamCount();
        final double[] dir = new double[2];
        for (int i = 0; i < beams; i++) {
            final float yaw = beamYaw(getBaseYaw(), i, beams);
            directionFromYaw(yaw, dir);
            final double fx = dir[0];
            final double fz = dir[1];
            final double proj = dx * fx + dz * fz;
            if (proj < 0.0 || proj > LENGTH) {
                continue;
            }
            final double perp = Math.abs((-fz * dx) + (fx * dz));
            if (perp <= HALF_WIDTH) {
                return true;
            }
        }
        return false;
    }

    private void spawnZoneParticles(final LivingEntity ownerMc) {
        final IEntity ownerWrapped;
        try {
            ownerWrapped = NpcAPI.Instance().getIEntity(ownerMc);
        } catch (final Exception ignored) {
            return;
        }
        if (!(ownerWrapped instanceof IEntityLiving)) {
            return;
        }
        final noppes.npcs.api.IWorld world = ((IEntityLiving) ownerWrapped).getWorld();
        final int beams = getBeamCount();
        final double[] dir = new double[2];
        final double ox = this.getX();
        final double oy = this.getY() + 0.35;
        final double oz = this.getZ();
        for (int i = 0; i < beams; i++) {
            directionFromYaw(beamYaw(getBaseYaw(), i, beams), dir);
            AbilityVfx.spawnNecroBeamZone(
                    world, ox, oy, oz, dir[0], dir[1], LENGTH, HALF_WIDTH, PARTICLES_PER_BEAM);
        }
    }

    private LivingEntity resolveOwnerEntity() {
        final int ownerId = this.entityData.get(DATA_OWNER_ID);
        if (ownerId >= 0) {
            final Entity byId = this.level.getEntity(ownerId);
            if (byId instanceof LivingEntity && byId.isAlive()) {
                return (LivingEntity) byId;
            }
        }
        final UUID ownerUuid = getOwnerUuid();
        if (ownerUuid == null) {
            return null;
        }
        if (this.level instanceof ServerWorld) {
            final Entity entity = ((ServerWorld) this.level).getEntity(ownerUuid);
            if (entity instanceof LivingEntity) {
                return (LivingEntity) entity;
            }
        }
        final List<LivingEntity> nearby = this.level.getEntitiesOfClass(
                LivingEntity.class, this.getBoundingBox().inflate(16.0));
        for (final LivingEntity living : nearby) {
            if (ownerUuid.equals(living.getUUID())) {
                return living;
            }
        }
        return null;
    }

    public void setOwnerUuid(final UUID ownerUuid) {
        this.entityData.set(DATA_OWNER_UUID, ownerUuid == null ? "" : ownerUuid.toString());
    }

    public UUID getOwnerUuid() {
        final String raw = this.entityData.get(DATA_OWNER_UUID);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (final Exception ignored) {
            return null;
        }
    }

    public void setOwnerEntityId(final int entityId) {
        this.entityData.set(DATA_OWNER_ID, entityId);
    }

    public void setBeamCount(final int beamCount) {
        this.entityData.set(DATA_BEAM_COUNT, Math.max(1, Math.min(3, beamCount)));
    }

    public int getBeamCount() {
        return Math.max(1, Math.min(3, this.entityData.get(DATA_BEAM_COUNT)));
    }

    public void setBaseYaw(final float yaw) {
        this.entityData.set(DATA_BASE_YAW, yaw);
    }

    public float getBaseYaw() {
        return this.entityData.get(DATA_BASE_YAW);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(final double distance) {
        final double range = LENGTH + 48.0;
        return distance < range * range;
    }

    @Override
    public AxisAlignedBB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(LENGTH + 1.0);
    }

    @Override
    protected void readAdditionalSaveData(final CompoundNBT nbt) {
        setOwnerUuid(nbt.hasUUID("Owner") ? nbt.getUUID("Owner") : null);
        setBeamCount(nbt.getInt("BeamCount"));
        setBaseYaw(nbt.getFloat("BaseYaw"));
    }

    @Override
    protected void addAdditionalSaveData(final CompoundNBT nbt) {
        final UUID owner = getOwnerUuid();
        if (owner != null) {
            nbt.putUUID("Owner", owner);
        }
        nbt.putInt("BeamCount", getBeamCount());
        nbt.putFloat("BaseYaw", getBaseYaw());
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
