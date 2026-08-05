package noppes.npcs.entity;

import net.minecraftforge.fml.common.registry.*;
import noppes.npcs.util.*;
import net.minecraftforge.common.util.*;
import noppes.npcs.entity.data.*;
import net.minecraft.entity.ai.attributes.*;
import noppes.npcs.api.event.*;
import noppes.npcs.client.*;
import noppes.npcs.api.constants.*;
import net.minecraft.entity.monster.*;
import noppes.npcs.packets.*;
import net.minecraft.util.text.*;
import net.minecraft.entity.player.*;
import net.minecraft.item.*;
import noppes.npcs.controllers.data.*;
import noppes.npcs.api.wrapper.*;
import noppes.npcs.items.*;
import noppes.npcs.api.entity.*;
import noppes.npcs.api.*;
import net.minecraft.world.server.*;
import noppes.npcs.ai.selector.*;
import java.util.function.*;
import noppes.npcs.ai.target.*;
import net.minecraft.entity.ai.controller.*;
import net.minecraft.entity.ai.goal.*;
import noppes.npcs.ai.*;
import net.minecraft.world.*;
import net.minecraftforge.event.*;
import net.minecraftforge.common.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraft.nbt.*;
import net.minecraft.entity.*;
import net.minecraft.util.math.*;
import noppes.npcs.api.item.*;
import net.minecraft.inventory.*;
import net.minecraft.util.math.shapes.*;
import net.minecraft.entity.item.*;
import noppes.npcs.controllers.*;
import net.minecraft.potion.*;
import noppes.npcs.*;
import net.minecraft.command.*;
import net.minecraft.block.*;
import net.minecraft.block.material.*;
import noppes.npcs.packets.client.*;
import net.minecraft.network.play.server.*;
import net.minecraft.network.*;
import java.util.*;
import noppes.npcs.roles.*;
import net.minecraft.util.math.vector.*;
import net.minecraft.util.*;
import net.minecraft.network.datasync.*;
import net.minecraft.pathfinding.*;

public abstract class EntityNPCInterface extends CreatureEntity implements IEntityAdditionalSpawnData, IRangedAttackMob
{
    public static final DataParameter<Boolean> Attacking;
    protected static final DataParameter<Integer> Animation;
    private static final DataParameter<String> RoleData;
    private static final DataParameter<String> JobData;
    private static final DataParameter<Integer> FactionData;
    private static final DataParameter<Boolean> Walking;
    private static final DataParameter<Boolean> Interacting;
    private static final DataParameter<Boolean> IsDead;
    public static final GameProfileAlt CommandProfile;
    public static final GameProfileAlt ChatEventProfile;
    public static final GameProfileAlt GenericProfile;
    public static FakePlayer ChatEventPlayer;
    public static FakePlayer CommandPlayer;
    public static FakePlayer GenericPlayer;
    public ICustomNpc wrappedNPC;
    public final DataAbilities abilities;
    public DataDisplay display;
    public DataStats stats;
    public DataInventory inventory;
    public final DataAI ais;
    public final DataAdvanced advanced;
    public final DataScript script;
    public final DataTransform transform;
    public final DataTimers timers;
    public CombatHandler combatHandler;
    public String linkedName;
    public long linkedLast;
    public LinkedNpcController.LinkedData linkedData;
    public EntitySize baseSize;
    private static final EntitySize sizeSleep;
    public float scaleX;
    public float scaleY;
    public float scaleZ;
    private boolean wasKilled;
    public RoleInterface role;
    public JobInterface job;
    public HashMap<Integer, DialogOption> dialogs;
    public boolean hasDied;
    public long killedtime;
    public long totalTicksAlive;
    private int taskCount;
    public int lastInteract;
    public Faction faction;
    private EntityAIRangedAttack aiRange;
    private Goal aiAttackTarget;
    public EntityAILook lookAi;
    public EntityAIAnimation animateAi;
    public List<LivingEntity> interactingEntities;
    public ResourceLocation textureLocation;
    public ResourceLocation textureGlowLocation;
    public ResourceLocation textureCloakLocation;
    public int currentAnimation;
    public int animationStart;
    public int npcVersion;
    public IChatMessages messages;
    public boolean updateClient;
    public boolean updateAI;
    public final ServerBossInfo bossInfo;
    public final HashSet<Integer> tracking;
    public double prevChasingPosX;
    public double prevChasingPosY;
    public double prevChasingPosZ;
    public double chasingPosX;
    public double chasingPosY;
    public double chasingPosZ;
    private double startYPos;
    
    public EntityNPCInterface(final EntityType<? extends CreatureEntity> type, final World world) {
        super((EntityType)type, world);
        this.abilities = new DataAbilities(this);
        this.display = new DataDisplay(this);
        this.stats = new DataStats(this);
        this.inventory = new DataInventory(this);
        this.ais = new DataAI(this);
        this.advanced = new DataAdvanced(this);
        this.script = new DataScript(this);
        this.transform = new DataTransform(this);
        this.timers = new DataTimers(this);
        this.combatHandler = new CombatHandler(this);
        this.linkedName = "";
        this.linkedLast = 0L;
        this.baseSize = new EntitySize(0.6f, 1.8f, false);
        this.wasKilled = false;
        this.role = RoleInterface.NONE;
        this.job = JobInterface.NONE;
        this.hasDied = false;
        this.killedtime = 0L;
        this.totalTicksAlive = 0L;
        this.taskCount = 1;
        this.lastInteract = 0;
        this.interactingEntities = new ArrayList<LivingEntity>();
        this.textureLocation = null;
        this.textureGlowLocation = null;
        this.textureCloakLocation = null;
        this.currentAnimation = 0;
        this.animationStart = 0;
        this.npcVersion = VersionCompatibility.ModRev;
        this.updateClient = false;
        this.updateAI = false;
        this.tracking = new HashSet<Integer>();
        this.startYPos = -1.0;
        if (!this.isClientSide()) {
            this.wrappedNPC = new NPCWrapper(this);
        }
        this.registerBaseAttributes();
        this.dialogs = new HashMap<Integer, DialogOption>();
        if (!CustomNpcs.DefaultInteractLine.isEmpty()) {
            this.advanced.interactLines.lines.put(0, new Line(CustomNpcs.DefaultInteractLine));
        }
        this.xpReward = 0;
        final float scaleX = 0.9375f;
        this.scaleZ = scaleX;
        this.scaleY = scaleX;
        this.scaleX = scaleX;
        this.faction = this.getFaction();
        this.setFaction(this.faction.id);
        this.updateAI = true;
        (this.bossInfo = new ServerBossInfo(this.getDisplayName(), BossInfo.Color.PURPLE, BossInfo.Overlay.PROGRESS)).setVisible(false);
    }
    
    public boolean canBreatheUnderwater() {
        return this.ais.movementType == 2;
    }
    
    public boolean isPushedByFluid() {
        return this.ais.movementType != 2;
    }
    
    private void registerBaseAttributes() {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue((double)this.stats.maxHealth);
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue((double)CustomNpcs.NpcNavRange);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((double)this.getSpeed());
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue((double)this.stats.melee.getStrength());
        this.getAttribute(Attributes.FLYING_SPEED).setBaseValue((double)(this.getSpeed() * 2.0f));
    }
    
    public static AttributeModifierMap.MutableAttribute createMobAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.ATTACK_DAMAGE).add(Attributes.FLYING_SPEED).add(Attributes.FOLLOW_RANGE);
    }
    
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define((DataParameter)EntityNPCInterface.RoleData, (Object)String.valueOf(""));
        this.entityData.define((DataParameter)EntityNPCInterface.JobData, (Object)String.valueOf(""));
        this.entityData.define((DataParameter)EntityNPCInterface.FactionData, (Object)0);
        this.entityData.define((DataParameter)EntityNPCInterface.Animation, (Object)0);
        this.entityData.define((DataParameter)EntityNPCInterface.Walking, (Object)false);
        this.entityData.define((DataParameter)EntityNPCInterface.Interacting, (Object)false);
        this.entityData.define((DataParameter)EntityNPCInterface.IsDead, (Object)false);
        this.entityData.define((DataParameter)EntityNPCInterface.Attacking, (Object)false);
    }
    
    public boolean isAlive() {
        return super.isAlive() && !this.isKilled();
    }
    
    public void tick() {
        super.tick();
        if (this.tickCount % 10 == 0) {
            this.startYPos = this.calculateStartYPos(this.ais.startPos()) + 1.0;
            if (this.startYPos < 0.0 && !this.isClientSide()) {
                this.remove();
            }
            EventHooks.onNPCTick(this);
        }
        this.timers.update();
        if (this.level.isClientSide && this.wasKilled != this.isKilled() && this.wasKilled) {
            this.deathTime = 0;
            this.refreshDimensions();
        }
        this.wasKilled = this.isKilled();
        if (this.currentAnimation == 14) {
            this.deathTime = 19;
        }
    }
    
    public boolean doHurtTarget(final Entity par1Entity) {
        float f = (float)this.stats.melee.getStrength();
        if (this.stats.melee.getDelay() < 10) {
            par1Entity.invulnerableTime = 0;
        }
        if (par1Entity instanceof LivingEntity) {
            final NpcEvent.MeleeAttackEvent event = new NpcEvent.MeleeAttackEvent(this.wrappedNPC, (LivingEntity)par1Entity, f);
            if (EventHooks.onNPCAttacksMelee(this, event)) {
                return false;
            }
            f = event.damage;
        }
        final boolean var4 = par1Entity.hurt((DamageSource)new NpcDamageSource("mob", (Entity)this), f);
        if (var4) {
            if (this.getOwner() instanceof PlayerEntity) {
                EntityUtil.setRecentlyHit((LivingEntity)par1Entity);
            }
            if (this.stats.melee.getKnockback() > 0) {
                par1Entity.push((double)(-MathHelper.sin(this.yRot * 3.1415927f / 180.0f) * this.stats.melee.getKnockback() * 0.5f), 0.1, (double)(MathHelper.cos(this.yRot * 3.1415927f / 180.0f) * this.stats.melee.getKnockback() * 0.5f));
                final Vector3d motion = this.getDeltaMovement();
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1.0, 0.6));
            }
            if (this.role.getType() == 6) {
                ((RoleCompanion)this.role).attackedEntity(par1Entity);
            }
        }
        if (this.stats.melee.getEffectType() != 0) {
            if (this.stats.melee.getEffectType() != 666) {
                ((LivingEntity)par1Entity).addEffect(new EffectInstance(PotionEffectType.getMCType(this.stats.melee.getEffectType()), this.stats.melee.getEffectTime() * 20, this.stats.melee.getEffectStrength()));
            }
            else {
                par1Entity.setRemainingFireTicks(this.stats.melee.getEffectTime() * 20);
            }
        }
        return var4;
    }
    
    public void aiStep() {
        if (CustomNpcs.FreezeNPCs) {
            return;
        }
        if (this.isNoAi()) {
            super.aiStep();
            return;
        }
        ++this.totalTicksAlive;
        this.updateSwingTime();
        if (this.tickCount % 20 == 0) {
            this.faction = this.getFaction();
        }
        if (!this.level.isClientSide) {
            if (!this.isKilled() && this.tickCount % 20 == 0) {
                this.advanced.scenes.update();
                if (this.getHealth() < this.getMaxHealth()) {
                    if (this.stats.healthRegen > 0 && !this.isAttacking()) {
                        this.heal((float)this.stats.healthRegen);
                    }
                    if (this.stats.combatRegen > 0 && this.isAttacking()) {
                        this.heal((float)this.stats.combatRegen);
                    }
                }
                if (this.faction.getsAttacked && !this.isAttacking()) {
                    final List<MonsterEntity> list = (List<MonsterEntity>)this.level.getEntitiesOfClass((Class)MonsterEntity.class, this.getBoundingBox().inflate(16.0, 16.0, 16.0));
                    for (final MonsterEntity mob : list) {
                        if (mob.getTarget() == null && this.canNpcSee((Entity)mob)) {
                            mob.setTarget((LivingEntity)this);
                        }
                    }
                }
                if (this.linkedData != null && this.linkedData.time > this.linkedLast) {
                    LinkedNpcController.Instance.loadNpcData(this);
                }
                if (this.updateClient) {
                    this.updateClient();
                }
                if (this.updateAI) {
                    this.updateTasks();
                    this.updateAI = false;
                }
            }
            if (this.getHealth() <= 0.0f && !this.isKilled()) {
                this.removeAllEffects();
                this.entityData.set((DataParameter)EntityNPCInterface.IsDead, (Object)true);
                this.updateTasks();
                this.refreshDimensions();
            }
            if (this.display.getBossbar() == 2) {
                this.bossInfo.setVisible(this.getTarget() != null);
            }
            this.entityData.set((DataParameter)EntityNPCInterface.Walking, (Object)!this.getNavigation().isDone());
            this.entityData.set((DataParameter)EntityNPCInterface.Interacting, (Object)this.isInteracting());
            this.combatHandler.update();
            this.onCollide();
        }
        if (this.wasKilled != this.isKilled() && this.wasKilled) {
            this.reset();
        }
        if (this.level.isDay() && !this.level.isClientSide && this.stats.burnInSun) {
            final float f = this.getBrightness();
            if (f > 0.5f && this.random.nextFloat() * 30.0f < (f - 0.4f) * 2.0f && this.level.canSeeSky(this.blockPosition())) {
                this.setRemainingFireTicks(160);
            }
        }
        super.aiStep();
        if (this.level.isClientSide) {
            this.role.clientUpdate();
            if (this.textureCloakLocation != null) {
                this.cloakUpdate();
            }
            if (this.currentAnimation != (int)this.entityData.get((DataParameter)EntityNPCInterface.Animation)) {
                this.currentAnimation = (int)this.entityData.get((DataParameter)EntityNPCInterface.Animation);
                this.animationStart = this.tickCount;
                this.refreshDimensions();
            }
            if (this.job.getType() == 1) {
                ((JobBard)this.job).aiStep();
            }
        }
        if (this.display.getBossbar() > 0) {
            this.bossInfo.setPercent(this.getHealth() / this.getMaxHealth());
        }
    }
    
    public void updateClient() {
        Packets.sendNearby((Entity)this, new PacketNpcUpdate(this.getId(), this.writeSpawnData()));
        this.updateClient = false;
    }
    
    protected ActionResultType mobInteract(final PlayerEntity player, final Hand hand) {
        if (this.level.isClientSide) {
            return this.isAttacking() ? ActionResultType.SUCCESS : ActionResultType.FAIL;
        }
        if (hand != Hand.MAIN_HAND) {
            return ActionResultType.PASS;
        }
        final ItemStack stack = player.getItemInHand(hand);
        if (stack != null) {
            final Item item = stack.getItem();
            if (item == CustomItems.cloner || item == CustomItems.wand || item == CustomItems.mount || item == CustomItems.scripter) {
                this.setTarget(null);
                this.setLastHurtByMob((LivingEntity)null);
                return ActionResultType.SUCCESS;
            }
            if (item == CustomItems.moving) {
                this.setTarget(null);
                stack.addTagElement("NPCID", (INBT)IntNBT.valueOf(this.getId()));
                player.sendMessage((ITextComponent)new TranslationTextComponent("message.pather.register", new Object[] { this.getName() }), this.getUUID());
                return ActionResultType.SUCCESS;
            }
        }
        if (EventHooks.onNPCInteract(this, player)) {
            return ActionResultType.FAIL;
        }
        if (this.getFaction().isAggressiveToPlayer(player)) {
            return ActionResultType.FAIL;
        }
        this.addInteract((LivingEntity)player);
        final Dialog dialog = this.getDialog(player);
        final QuestData data = PlayerData.get(player).questData.getQuestCompletion(player, this);
        if (data != null) {
            Packets.send((ServerPlayerEntity)player, new PacketQuestCompletion(data.quest.id));
        }
        else if (dialog != null) {
            NoppesUtilServer.openDialog(player, this, dialog);
        }
        else if (this.role.getType() != 0) {
            this.role.interact(player);
        }
        else {
            this.say(player, this.advanced.getInteractLine());
        }
        return ActionResultType.PASS;
    }
    
    public void addInteract(final LivingEntity entity) {
        if (!this.ais.stopAndInteract || this.isAttacking() || !entity.isAlive() || this.isNoAi()) {
            return;
        }
        if (this.tickCount - this.lastInteract < 180) {
            this.interactingEntities.clear();
        }
        this.getNavigation().stop();
        this.lastInteract = this.tickCount;
        if (!this.interactingEntities.contains(entity)) {
            this.interactingEntities.add(entity);
        }
    }
    
    public boolean isInteracting() {
        return this.tickCount - this.lastInteract < 40 || (this.isClientSide() && (boolean)this.entityData.get((DataParameter)EntityNPCInterface.Interacting)) || (this.ais.stopAndInteract && !this.interactingEntities.isEmpty() && this.tickCount - this.lastInteract < 180);
    }
    
    private Dialog getDialog(final PlayerEntity player) {
        for (final DialogOption option : this.dialogs.values()) {
            if (option == null) {
                continue;
            }
            if (!option.hasDialog()) {
                continue;
            }
            final Dialog dialog = option.getDialog();
            if (dialog.availability.isAvailable(player)) {
                return dialog;
            }
        }
        return null;
    }
    
    public boolean hurt(final DamageSource damagesource, float i) {
        if (this.level.isClientSide || CustomNpcs.FreezeNPCs || damagesource.msgId.equals("inWall")) {
            return false;
        }
        if (damagesource.msgId.equals("outOfWorld") && this.isKilled()) {
            this.reset();
        }
        i = this.stats.resistances.applyResistance(damagesource, i);
        final float n = (float)this.invulnerableTime;
        this.getClass();
        if (n > 20.0f / 2.0f && i <= this.lastHurt) {
            return false;
        }
        final Entity entity = NoppesUtilServer.GetDamageSourcee(damagesource);
        LivingEntity attackingEntity = null;
        if (entity instanceof LivingEntity) {
            attackingEntity = (LivingEntity)entity;
        }
        if (attackingEntity != null && attackingEntity == this.getOwner()) {
            return false;
        }
        if (attackingEntity instanceof EntityNPCInterface) {
            final EntityNPCInterface npc = (EntityNPCInterface)attackingEntity;
            if (npc.faction.id == this.faction.id) {
                return false;
            }
            if (npc.getOwner() instanceof PlayerEntity) {
                this.hurtTime = 100;
            }
        }
        else if (attackingEntity instanceof PlayerEntity && this.faction.isFriendlyToPlayer((PlayerEntity)attackingEntity)) {
            ForgeHooks.onLivingAttack((LivingEntity)this, damagesource, i);
            return false;
        }
        final NpcEvent.DamagedEvent event = new NpcEvent.DamagedEvent(this.wrappedNPC, entity, i, damagesource);
        if (EventHooks.onNPCDamaged(this, event)) {
            ForgeHooks.onLivingAttack((LivingEntity)this, damagesource, i);
            return false;
        }
        i = event.damage;
        if (this.isKilled()) {
            return false;
        }
        if (attackingEntity == null) {
            return super.hurt(damagesource, i);
        }
        try {
            if (this.isAttacking()) {
                if (this.getTarget() != null && this.distanceToSqr((Entity)this.getTarget()) > this.distanceToSqr((Entity)attackingEntity)) {
                    this.setTarget(attackingEntity);
                }
                return super.hurt(damagesource, i);
            }
            if (i > 0.0f) {
                final List<EntityNPCInterface> inRange = (List<EntityNPCInterface>)this.level.getEntitiesOfClass((Class)EntityNPCInterface.class, this.getBoundingBox().inflate(32.0, 16.0, 32.0));
                for (final EntityNPCInterface npc2 : inRange) {
                    if (!npc2.isKilled() && npc2.advanced.defendFaction) {
                        if (npc2.faction.id != this.faction.id) {
                            continue;
                        }
                        if (!npc2.canNpcSee((Entity)this) && !npc2.ais.directLOS && !npc2.canNpcSee((Entity)attackingEntity)) {
                            continue;
                        }
                        npc2.onAttack(attackingEntity);
                    }
                }
                this.setTarget(attackingEntity);
            }
            return super.hurt(damagesource, i);
        }
        finally {
            if (event.clearTarget) {
                this.setTarget(null);
                this.setLastHurtByMob((LivingEntity)null);
            }
        }
    }
    
    protected void actuallyHurt(final DamageSource damageSrc, final float damageAmount) {
        super.actuallyHurt(damageSrc, damageAmount);
        this.combatHandler.damage(damageSrc, damageAmount);
    }
    
    public void onAttack(final LivingEntity entity) {
        if (entity == null || entity == this || this.isAttacking() || this.ais.onAttack == 3 || entity == this.getOwner()) {
            return;
        }
        super.setTarget(entity);
    }
    
    public void setTarget(LivingEntity entity) {
        if ((entity instanceof PlayerEntity && ((PlayerEntity)entity).abilities.invulnerable) || (entity != null && entity == this.getOwner()) || this.getTarget() == entity) {
            return;
        }
        if (entity != null) {
            final NpcEvent.TargetEvent event = new NpcEvent.TargetEvent(this.wrappedNPC, entity);
            if (EventHooks.onNPCTarget(this, event)) {
                return;
            }
            if (event.entity == null) {
                entity = null;
            }
            else {
                entity = event.entity.getMCEntity();
            }
        }
        else {
            for (final PrioritizedGoal en : this.targetSelector.availableGoals) {
                en.stop();
            }
            if (EventHooks.onNPCTargetLost(this, this.getTarget())) {
                return;
            }
        }
        if (entity != null && entity != this && this.ais.onAttack != 3 && !this.isAttacking() && !this.isClientSide()) {
            final Line line = this.advanced.getAttackLine();
            if (line != null) {
                this.saySurrounding(Line.formatTarget(line, entity));
            }
        }
        super.setTarget(entity);
    }
    
    public void performRangedAttack(final LivingEntity entity, final float f) {
        final ItemStack proj = ItemStackWrapper.MCItem(this.inventory.getProjectile());
        if (proj == null) {
            this.updateAI = true;
            return;
        }
        final NpcEvent.RangedLaunchedEvent event = new NpcEvent.RangedLaunchedEvent(this.wrappedNPC, entity, (float)this.stats.ranged.getStrength());
        for (int i = 0; i < this.stats.ranged.getShotCount(); ++i) {
            final EntityProjectile projectile2 = this.shoot(entity, this.stats.ranged.getAccuracy(), proj, f == 1.0f);
            projectile2.damage = event.damage;
            final ItemStack stack;
            Entity e;
            projectile2.callback = ((projectile1, pos, entity1) -> {
                if (stack.getItem() == CustomItems.soulstoneFull) {
                    e = ItemSoulstoneFilled.Spawn(null, stack, this.level, pos);
                    if (e instanceof LivingEntity && entity1 instanceof LivingEntity) {
                        if (e instanceof MobEntity) {
                            ((MobEntity)e).setTarget((LivingEntity)entity1);
                        }
                        else {
                            ((LivingEntity)e).setLastHurtByMob((LivingEntity)entity1);
                        }
                    }
                }
                projectile1.playSound(this.stats.ranged.getSoundEvent((entity1 != null) ? 1 : 2), 1.0f, 1.2f / (this.getRandom().nextFloat() * 0.2f + 0.9f));
                return false;
            });
            this.playSound(this.stats.ranged.getSoundEvent(0), 2.0f, 1.0f);
            event.projectiles.add((IProjectile)NpcAPI.Instance().getIEntity((Entity)projectile2));
        }
        EventHooks.onNPCRangedLaunched(this, event);
    }
    
    public EntityProjectile shoot(final LivingEntity entity, final int accuracy, final ItemStack proj, final boolean indirect) {
        return this.shoot(entity.getX(), entity.getBoundingBox().minY + entity.getBbHeight() / 2.0f, entity.getZ(), accuracy, proj, indirect);
    }
    
    public EntityProjectile shoot(final double x, final double y, final double z, final int accuracy, final ItemStack proj, final boolean indirect) {
        final EntityProjectile projectile = new EntityProjectile(this.level, (LivingEntity)this, proj.copy(), true);
        final double varX = x - this.getX();
        final double varY = y - (this.getY() + this.getEyeHeight());
        final double varZ = z - this.getZ();
        final float varF = projectile.hasGravity() ? MathHelper.sqrt(varX * varX + varZ * varZ) : 0.0f;
        final float angle = projectile.getAngleForXYZ(varX, varY, varZ, varF, indirect);
        final float acc = 20.0f - MathHelper.floor(accuracy / 5.0f);
        projectile.shoot(varX, varY, varZ, angle, acc);
        this.level.addFreshEntity((Entity)projectile);
        return projectile;
    }
    
    private void clearTasks(final GoalSelector tasks) {
        final List<PrioritizedGoal> list = new ArrayList<PrioritizedGoal>(tasks.availableGoals);
        for (final PrioritizedGoal entityaitaskentry : list) {
            tasks.removeGoal((Goal)entityaitaskentry);
        }
        tasks.availableGoals.clear();
        tasks.lockedFlags.clear();
        tasks.disabledFlags.clear();
    }
    
    private void updateTasks() {
        if (this.level == null || this.level.isClientSide || !(this.level instanceof ServerWorld)) {
            return;
        }
        final ServerWorld sWorld = (ServerWorld)this.level;
        this.clearTasks(this.goalSelector);
        this.clearTasks(this.targetSelector);
        if (this.isKilled()) {
            return;
        }
        this.targetSelector.addGoal(0, (Goal)new EntityAIClearTarget(this));
        this.targetSelector.addGoal(1, (Goal)new HurtByTargetGoal((CreatureEntity)this, new Class[0]));
        this.targetSelector.addGoal(2, (Goal)new NpcNearestAttackableTargetGoal(this, LivingEntity.class, 4, this.ais.directLOS, false, (Predicate<LivingEntity>)new NPCAttackSelector(this)));
        this.targetSelector.addGoal(3, (Goal)new EntityAIOwnerHurtByTarget(this));
        this.targetSelector.addGoal(4, (Goal)new EntityAIOwnerHurtTarget(this));
        sWorld.navigations.remove(this.getNavigation());
        if (this.ais.movementType == 1) {
            this.moveControl = new FlyingMoveHelper(this);
            this.navigation = (PathNavigator)new FlyingPathNavigator((MobEntity)this, this.level);
        }
        else if (this.ais.movementType == 2) {
            this.moveControl = new FlyingMoveHelper(this);
            this.navigation = (PathNavigator)new SwimmerPathNavigator((MobEntity)this, this.level);
        }
        else {
            this.moveControl = new MovementController((MobEntity)this);
            this.navigation = (PathNavigator)new GroundPathNavigator((MobEntity)this, this.level);
            this.goalSelector.addGoal(0, (Goal)new EntityAIWaterNav(this));
        }
        sWorld.navigations.add(this.getNavigation());
        this.taskCount = 1;
        this.addRegularEntries();
        this.doorInteractType();
        this.seekShelter();
        this.setResponse();
        this.setMoveType();
    }
    
    private void setResponse() {
        final EntityAIRangedAttack entityAIRangedAttack = null;
        this.aiRange = entityAIRangedAttack;
        this.aiAttackTarget = entityAIRangedAttack;
        if (this.ais.canSprint) {
            this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAISprintToTarget(this));
        }
        if (this.ais.onAttack == 1) {
            this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAIPanic(this, 1.2f));
        }
        else if (this.ais.onAttack == 2) {
            this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAIAvoidTarget(this));
        }
        else if (this.ais.onAttack == 0) {
            if (this.ais.canLeap) {
                this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAIPounceTarget(this));
            }
            this.goalSelector.addGoal(this.taskCount, this.aiAttackTarget = new EntityAIAttackTarget(this));
            if (this.inventory.getProjectile() != null) {
                this.goalSelector.addGoal(this.taskCount++, (Goal)(this.aiRange = new EntityAIRangedAttack((IRangedAttackMob)this)));
            }
        }
        else if (this.ais.onAttack == 3) {}
    }
    
    public boolean canFly() {
        return this.navigation instanceof FlyingPathNavigator;
    }
    
    public void setMoveType() {
        if (this.ais.getMovingType() == 1) {
            this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAIWander(this));
        }
        if (this.ais.getMovingType() == 2) {
            this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAIMovingPath(this));
        }
    }
    
    public void doorInteractType() {
        if (this.navigation instanceof GroundPathNavigator) {
            Goal aiDoor = null;
            if (this.ais.doorInteract == 1) {
                this.goalSelector.addGoal(this.taskCount++, aiDoor = (Goal)new OpenDoorGoal((MobEntity)this, true));
            }
            else if (this.ais.doorInteract == 0) {
                this.goalSelector.addGoal(this.taskCount++, aiDoor = (Goal)new EntityAIBustDoor((MobEntity)this));
            }
            ((GroundPathNavigator)this.navigation).setCanOpenDoors(aiDoor != null);
        }
    }
    
    public void seekShelter() {
        if (this.ais.findShelter == 0) {
            this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAIMoveIndoors(this));
        }
        else if (this.ais.findShelter == 1) {
            if (!this.canFly()) {
                this.goalSelector.addGoal(this.taskCount++, (Goal)new RestrictSunGoal((CreatureEntity)this));
            }
            this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAIFindShade(this));
        }
    }
    
    public void addRegularEntries() {
        this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAIReturn(this));
        this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAIFollow(this));
        if (this.ais.getStandingType() != 1 && this.ais.getStandingType() != 3) {
            this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAIWatchClosest(this, LivingEntity.class, 5.0f));
        }
        this.goalSelector.addGoal(this.taskCount++, (Goal)(this.lookAi = new EntityAILook(this)));
        this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAIWorldLines(this));
        this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAIJob(this));
        this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAIRole(this));
        this.goalSelector.addGoal(this.taskCount++, (Goal)(this.animateAi = new EntityAIAnimation(this)));
        if (this.transform.isValid()) {
            this.goalSelector.addGoal(this.taskCount++, (Goal)new EntityAITransform(this));
        }
    }
    
    public float getSpeed() {
        return this.ais.getWalkingSpeed() / 20.0f;
    }
    
    public float getWalkTargetValue(final BlockPos pos) {
        if (this.ais.movementType == 2) {
            return (this.level.getBlockState(pos).getMaterial() == Material.WATER) ? 10.0f : 0.0f;
        }
        float weight = this.level.getLightEmission(pos) - 0.5f;
        if (this.level.getBlockState(pos).isSolidRender((IBlockReader)this.level, pos)) {
            weight += 10.0f;
        }
        return weight;
    }
    
    protected int decreaseAirSupply(final int par1) {
        if (!this.stats.canDrown) {
            return par1;
        }
        return super.decreaseAirSupply(par1);
    }
    
    public CreatureAttribute getMobType() {
        return (this.stats == null) ? null : this.stats.creatureType;
    }
    
    public int getAmbientSoundInterval() {
        return 160;
    }
    
    public void playAmbientSound() {
        if (!this.isAlive()) {
            return;
        }
        this.advanced.playSound((this.getTarget() != null) ? 1 : 0, this.getSoundVolume(), this.getVoicePitch());
    }
    
    protected void playHurtSound(final DamageSource source) {
        this.advanced.playSound(2, this.getSoundVolume(), this.getVoicePitch());
    }
    
    public SoundEvent getDeathSound() {
        return null;
    }
    
    protected float getVoicePitch() {
        if (this.advanced.disablePitch) {
            return 1.0f;
        }
        return super.getVoicePitch();
    }
    
    protected void playStepSound(final BlockPos pos, final BlockState state) {
        if (this.advanced.getSound(4) != null) {
            this.advanced.playSound(4, 0.15f, 1.0f);
        }
        else {
            super.playStepSound(pos, state);
        }
    }
    
    public ServerPlayerEntity getFakeChatPlayer() {
        if (this.level.isClientSide) {
            return null;
        }
        EntityUtil.Copy((LivingEntity)this, (LivingEntity)EntityNPCInterface.ChatEventPlayer);
        EntityNPCInterface.ChatEventProfile.npc = this;
        EntityNPCInterface.ChatEventPlayer.setLevel(this.level);
        EntityNPCInterface.ChatEventPlayer.setPos(this.getX(), this.getY(), this.getZ());
        return (ServerPlayerEntity)EntityNPCInterface.ChatEventPlayer;
    }
    
    public void saySurrounding(final Line line) {
        if (line == null) {
            return;
        }
        if (line.getShowText() && !line.getText().isEmpty()) {
            final ServerChatEvent event = new ServerChatEvent(this.getFakeChatPlayer(), line.getText(), (ITextComponent)new TranslationTextComponent(line.getText().replace("%", "%%")));
            if (CustomNpcs.NpcSpeachTriggersChatEvent && (MinecraftForge.EVENT_BUS.post((Event)event) || event.getComponent() == null)) {
                return;
            }
            line.setText(event.getComponent().getString().replace("%%", "%"));
        }
        final List<PlayerEntity> inRange = (List<PlayerEntity>)this.level.getEntitiesOfClass((Class)PlayerEntity.class, this.getBoundingBox().inflate(20.0, 20.0, 20.0));
        for (final PlayerEntity player : inRange) {
            this.say(player, line);
        }
    }
    
    public void say(final PlayerEntity player, final Line line) {
        if (line == null || !this.canNpcSee((Entity)player)) {
            return;
        }
        if (!line.getSound().isEmpty()) {
            final BlockPos pos = this.blockPosition();
            Packets.send((ServerPlayerEntity)player, new PacketPlaySound(line.getSound(), pos, this.getSoundVolume(), this.getVoicePitch()));
        }
        if (!line.getText().isEmpty()) {
            Packets.send((ServerPlayerEntity)player, new PacketChatBubble(this.getId(), (ITextComponent)new TranslationTextComponent(line.getText()), line.getShowText()));
        }
    }
    
    public boolean shouldShowName() {
        return true;
    }
    
    public void push(final double d, final double d1, final double d2) {
        if (this.isWalking() && !this.isKilled()) {
            super.push(d, d1, d2);
        }
    }
    
    public void readAdditionalSaveData(final CompoundNBT compound) {
        super.readAdditionalSaveData(compound);
        this.npcVersion = compound.getInt("ModRev");
        VersionCompatibility.CheckNpcCompatibility(this, compound);
        this.display.readToNBT(compound);
        this.stats.readToNBT(compound);
        this.ais.readToNBT(compound);
        this.script.load(compound);
        this.timers.load(compound);
        this.advanced.readToNBT(compound);
        this.role.load(compound);
        this.job.load(compound);
        this.inventory.load(compound);
        this.transform.readToNBT(compound);
        this.killedtime = compound.getLong("KilledTime");
        this.totalTicksAlive = compound.getLong("TotalTicksAlive");
        this.linkedName = compound.getString("LinkedNpcName");
        if (!this.isClientSide()) {
            LinkedNpcController.Instance.loadNpcData(this);
        }
        this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue((double)CustomNpcs.NpcNavRange);
        this.updateAI = true;
    }
    
    public void addAdditionalSaveData(final CompoundNBT compound) {
        super.addAdditionalSaveData(compound);
        this.display.save(compound);
        this.stats.save(compound);
        this.ais.save(compound);
        this.script.save(compound);
        this.timers.save(compound);
        this.advanced.save(compound);
        this.role.save(compound);
        this.job.save(compound);
        this.inventory.save(compound);
        this.transform.save(compound);
        compound.putLong("KilledTime", this.killedtime);
        compound.putLong("TotalTicksAlive", this.totalTicksAlive);
        compound.putInt("ModRev", this.npcVersion);
        compound.putString("LinkedNpcName", this.linkedName);
    }
    
    public EntitySize getDimensions(final Pose poseIn) {
        EntitySize size = this.baseSize;
        if (this.currentAnimation == 2 || this.currentAnimation == 7 || this.deathTime > 0) {
            size = EntityNPCInterface.sizeSleep;
        }
        else if (this.isPassenger() || this.currentAnimation == 1) {
            size = this.baseSize.scale(1.0f, 0.77f);
        }
        size = size.scale(this.display.getSize() * 0.2f);
        if (this.display.getHitboxState() == 1 || (this.isKilled() && this.stats.hideKilledBody)) {
            size = EntitySize.scalable(1.0E-5f, size.height);
        }
        if (size.width / 2.0f > this.level.getMaxEntityRadius()) {
            this.level.increaseMaxEntityRadius((double)(size.width / 2.0f));
        }
        return size;
    }
    
    public void tickDeath() {
        if (this.stats.spawnCycle == 3 || this.stats.spawnCycle == 4) {
            super.tickDeath();
            return;
        }
        ++this.deathTime;
        if (this.level.isClientSide) {
            return;
        }
        if (!this.hasDied) {
            this.remove();
        }
        if (this.killedtime < System.currentTimeMillis() && (this.stats.spawnCycle == 0 || (this.level.isDay() && this.stats.spawnCycle == 1) || (!this.level.isDay() && this.stats.spawnCycle == 2))) {
            this.reset();
        }
    }
    
    public void reset() {
        this.hasDied = false;
        this.removed = false;
        this.dead = false;
        this.revive();
        this.setSprinting(this.wasKilled = false);
        this.setHealth(this.getMaxHealth());
        this.entityData.set((DataParameter)EntityNPCInterface.Animation, (Object)0);
        this.entityData.set((DataParameter)EntityNPCInterface.Walking, (Object)false);
        this.entityData.set((DataParameter)EntityNPCInterface.IsDead, (Object)false);
        this.entityData.set((DataParameter)EntityNPCInterface.Interacting, (Object)false);
        this.interactingEntities.clear();
        this.combatHandler.reset();
        this.setTarget(null);
        this.setLastHurtByMob((LivingEntity)null);
        this.deathTime = 0;
        if (this.ais.returnToStart && !this.hasOwner() && !this.isClientSide() && !this.isPassenger()) {
            this.moveTo((double)this.getStartXPos(), this.getStartYPos(), (double)this.getStartZPos(), this.yRot, this.xRot);
        }
        this.killedtime = 0L;
        this.clearFire();
        this.removeAllEffects();
        this.travel(Vector3d.ZERO);
        final float n = 0.0f;
        this.walkDist = n;
        this.walkDistO = n;
        this.getNavigation().stop();
        this.currentAnimation = 0;
        this.refreshDimensions();
        this.updateAI = true;
        this.ais.movingPos = 0;
        if (this.getOwner() != null) {
            this.getOwner().setLastHurtMob((Entity)null);
        }
        this.bossInfo.setVisible(this.display.getBossbar() == 1);
        this.job.reset();
        EventHooks.onNPCInit(this);
    }
    
    public void onCollide() {
        if (!this.isAlive() || this.tickCount % 4 != 0 || this.level.isClientSide) {
            return;
        }
        AxisAlignedBB axisalignedbb = null;
        if (this.getVehicle() != null && this.getVehicle().isAlive()) {
            axisalignedbb = this.getBoundingBox().minmax(this.getVehicle().getBoundingBox()).inflate(1.0, 0.0, 1.0);
        }
        else {
            axisalignedbb = this.getBoundingBox().inflate(1.0, 0.5, 1.0);
        }
        final List list = this.level.getEntitiesOfClass((Class)LivingEntity.class, axisalignedbb);
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); ++i) {
            final Entity entity = list.get(i);
            if (entity != this && entity.isAlive()) {
                EventHooks.onNPCCollide(this, entity);
            }
        }
    }
    
    public void handleInsidePortal(final BlockPos pos) {
    }
    
    public void cloakUpdate() {
        this.prevChasingPosX = this.chasingPosX;
        this.prevChasingPosY = this.chasingPosY;
        this.prevChasingPosZ = this.chasingPosZ;
        final double d0 = this.getX() - this.chasingPosX;
        final double d2 = this.getY() - this.chasingPosY;
        final double d3 = this.getZ() - this.chasingPosZ;
        final double d4 = 10.0;
        if (d0 > 10.0) {
            this.chasingPosX = this.getX();
            this.prevChasingPosX = this.chasingPosX;
        }
        if (d3 > 10.0) {
            this.chasingPosZ = this.getZ();
            this.prevChasingPosZ = this.chasingPosZ;
        }
        if (d2 > 10.0) {
            this.chasingPosY = this.getY();
            this.prevChasingPosY = this.chasingPosY;
        }
        if (d0 < -10.0) {
            this.chasingPosX = this.getX();
            this.prevChasingPosX = this.chasingPosX;
        }
        if (d3 < -10.0) {
            this.chasingPosZ = this.getZ();
            this.prevChasingPosZ = this.chasingPosZ;
        }
        if (d2 < -10.0) {
            this.chasingPosY = this.getY();
            this.prevChasingPosY = this.chasingPosY;
        }
        this.chasingPosX += d0 * 0.25;
        this.chasingPosZ += d3 * 0.25;
        this.chasingPosY += d2 * 0.25;
    }
    
    public boolean removeWhenFarAway(final double distanceToPlayer) {
        return this.stats != null && this.stats.spawnCycle == 4;
    }
    
    public ItemStack getMainHandItem() {
        IItemStack item = null;
        if (this.isAttacking()) {
            item = this.inventory.getRightHand();
        }
        else if (this.role.getType() == 6) {
            item = ((RoleCompanion)this.role).getItemInHand();
        }
        else if (this.job.overrideMainHand) {
            item = this.job.getMainhand();
        }
        else {
            item = this.inventory.getRightHand();
        }
        return ItemStackWrapper.MCItem(item);
    }
    
    public ItemStack getOffhandItem() {
        IItemStack item = null;
        if (this.isAttacking()) {
            item = this.inventory.getLeftHand();
        }
        else if (this.job.overrideOffHand) {
            item = this.job.getOffhand();
        }
        else {
            item = this.inventory.getLeftHand();
        }
        return ItemStackWrapper.MCItem(item);
    }
    
    public ItemStack getItemBySlot(final EquipmentSlotType slot) {
        if (slot == EquipmentSlotType.MAINHAND) {
            return this.getMainHandItem();
        }
        if (slot == EquipmentSlotType.OFFHAND) {
            return this.getOffhandItem();
        }
        return ItemStackWrapper.MCItem(this.inventory.getArmor(3 - slot.getIndex()));
    }
    
    public void setItemSlot(final EquipmentSlotType slot, final ItemStack item) {
        if (slot == EquipmentSlotType.MAINHAND) {
            this.inventory.weapons.put(0, NpcAPI.Instance().getIItemStack(item));
        }
        else if (slot == EquipmentSlotType.OFFHAND) {
            this.inventory.weapons.put(2, NpcAPI.Instance().getIItemStack(item));
        }
        else {
            this.inventory.armor.put(3 - slot.getIndex(), NpcAPI.Instance().getIItemStack(item));
        }
    }
    
    public Iterable<ItemStack> getArmorSlots() {
        final ArrayList<ItemStack> list = new ArrayList<ItemStack>();
        for (int i = 0; i < 4; ++i) {
            list.add(ItemStackWrapper.MCItem(this.inventory.armor.get(3 - i)));
        }
        return list;
    }
    
    public Iterable<ItemStack> getAllSlots() {
        final ArrayList list = new ArrayList();
        list.add(ItemStackWrapper.MCItem(this.inventory.weapons.get(0)));
        list.add(ItemStackWrapper.MCItem(this.inventory.weapons.get(2)));
        return (Iterable<ItemStack>)list;
    }
    
    protected void dropCustomDeathLoot(final DamageSource source, final int looting, final boolean recentlyHitIn) {
    }
    
    protected void dropFromLootTable(final DamageSource damageSourceIn, final boolean attackedRecently) {
    }
    
    public void die(final DamageSource damagesource) {
        this.setSprinting(false);
        this.getNavigation().stop();
        this.clearFire();
        this.removeAllEffects();
        if (!this.isClientSide()) {
            this.advanced.playSound(3, this.getSoundVolume(), this.getVoicePitch());
            final Entity attackingEntity = NoppesUtilServer.GetDamageSourcee(damagesource);
            final NpcEvent.DiedEvent event = new NpcEvent.DiedEvent(this.wrappedNPC, damagesource, attackingEntity);
            event.droppedItems = this.inventory.getItemsRNG();
            event.expDropped = this.inventory.getExpRNG();
            event.line = this.advanced.getKilledLine();
            EventHooks.onNPCDied(this, event);
            this.bossInfo.setVisible(false);
            this.inventory.dropStuff(event, attackingEntity, damagesource);
            if (event.line != null) {
                this.saySurrounding(Line.formatTarget((Line)event.line, (attackingEntity instanceof LivingEntity) ? attackingEntity : null));
            }
        }
        super.die(damagesource);
    }
    
    public void startSeenByPlayer(final ServerPlayerEntity player) {
        super.startSeenByPlayer(player);
        this.bossInfo.addPlayer(player);
    }
    
    public void stopSeenByPlayer(final ServerPlayerEntity player) {
        super.stopSeenByPlayer(player);
        this.bossInfo.removePlayer(player);
    }
    
    public void remove() {
        this.hasDied = true;
        this.ejectPassengers();
        this.stopRiding();
        if (this.level.isClientSide || this.stats.spawnCycle == 3 || this.stats.spawnCycle == 4) {
            this.delete();
        }
        else {
            this.setHealth(-1.0f);
            this.setSprinting(false);
            this.getNavigation().stop();
            this.setCurrentAnimation(2);
            this.refreshDimensions();
            if (this.killedtime <= 0L) {
                this.killedtime = this.stats.respawnTime * 1000 + System.currentTimeMillis();
            }
            this.role.killed();
            this.job.killed();
        }
    }
    
    public void delete() {
        VisibilityController.instance.remove(this);
        this.role.delete();
        this.job.delete();
        super.remove();
    }
    
    public float getStartXPos() {
        return this.ais.startPos().getX() + this.ais.bodyOffsetX / 10.0f;
    }
    
    public float getStartZPos() {
        return this.ais.startPos().getZ() + this.ais.bodyOffsetZ / 10.0f;
    }
    
    public boolean isVeryNearAssignedPlace() {
        final double xx = this.getX() - this.getStartXPos();
        final double zz = this.getZ() - this.getStartZPos();
        return xx >= -0.2 && xx <= 0.2 && zz >= -0.2 && zz <= 0.2;
    }
    
    public double getStartYPos() {
        if (this.startYPos < 0.0) {
            return this.calculateStartYPos(this.ais.startPos());
        }
        return this.startYPos;
    }
    
    private double calculateStartYPos(BlockPos pos) {
        final BlockPos startPos = this.ais.startPos();
        while (pos.getY() > 0) {
            final BlockState state = this.level.getBlockState(pos);
            final VoxelShape shape = state.getShape((IBlockReader)this.level, pos);
            if (shape.isEmpty()) {
                pos = pos.below();
            }
            else {
                final AxisAlignedBB bb = shape.bounds().move(pos);
                if (this.ais.movementType != 2 || startPos.getY() > pos.getY() || state.getMaterial() != Material.WATER) {
                    return bb.maxY;
                }
                pos = pos.below();
            }
        }
        return 0.0;
    }
    
    private BlockPos calculateTopPos(final BlockPos pos) {
        for (BlockPos check = pos; check.getY() > 0; check = check.below()) {
            final BlockState state = this.level.getBlockState(pos);
            final VoxelShape shape = state.getShape((IBlockReader)this.level, pos);
            if (!shape.isEmpty()) {
                final AxisAlignedBB bb = shape.bounds().move(pos);
                if (bb != null) {
                    return check;
                }
            }
        }
        return pos;
    }
    
    public boolean isInRange(final Entity entity, final double range) {
        return this.isInRange(entity.getX(), entity.getY(), entity.getZ(), range);
    }
    
    public boolean isInRange(final double posX, final double posY, final double posZ, final double range) {
        final double y = Math.abs(this.getY() - posY);
        if (posY >= 0.0 && y > range) {
            return false;
        }
        final double x = Math.abs(this.getX() - posX);
        final double z = Math.abs(this.getZ() - posZ);
        return x <= range && z <= range;
    }
    
    public void givePlayerItem(final PlayerEntity player, ItemStack item) {
        if (this.level.isClientSide) {
            return;
        }
        item = item.copy();
        final float f = 0.7f;
        final double d = this.level.random.nextFloat() * f + (double)(1.0f - f);
        final double d2 = this.level.random.nextFloat() * f + (double)(1.0f - f);
        final double d3 = this.level.random.nextFloat() * f + (double)(1.0f - f);
        final ItemEntity entityitem = new ItemEntity(this.level, this.getX() + d, this.getY() + d2, this.getZ() + d3, item);
        entityitem.setPickUpDelay(2);
        this.level.addFreshEntity((Entity)entityitem);
        final int i = item.getCount();
        if (player.inventory.add(item)) {
            this.level.playSound((PlayerEntity)null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f, ((this.random.nextFloat() - this.random.nextFloat()) * 0.7f + 1.0f) * 2.0f);
            player.take((Entity)entityitem, i);
            if (item.getCount() <= 0) {
                entityitem.remove();
            }
        }
    }
    
    public boolean isSleeping() {
        return this.currentAnimation == 2 && !this.isAttacking();
    }
    
    public boolean isWalking() {
        return this.ais.getMovingType() != 0 || this.isAttacking() || this.isFollower() || (boolean)this.entityData.get((DataParameter)EntityNPCInterface.Walking);
    }
    
    public boolean isCrouching() {
        return this.currentAnimation == 4;
    }
    
    public void knockback(final float strength, final double ratioX, final double ratioZ) {
        super.knockback(strength * (2.0f - this.stats.resistances.knockback), ratioX, ratioZ);
    }
    
    public Faction getFaction() {
        final Faction fac = FactionController.instance.getFaction((int)this.entityData.get((DataParameter)EntityNPCInterface.FactionData));
        if (fac == null) {
            return FactionController.instance.getFaction(FactionController.instance.getFirstFactionId());
        }
        return fac;
    }
    
    public boolean isClientSide() {
        return this.level == null || this.level.isClientSide;
    }
    
    public void setFaction(final int id) {
        if (id < 0 || this.isClientSide()) {
            return;
        }
        this.entityData.set((DataParameter)EntityNPCInterface.FactionData, (Object)id);
    }
    
    public boolean canBeAffected(final EffectInstance effect) {
        return !this.stats.potionImmune && (this.getMobType() != CreatureAttribute.ARTHROPOD || effect.getEffect() != Effects.POISON) && super.canBeAffected(effect);
    }
    
    public boolean isAttacking() {
        return (boolean)this.entityData.get((DataParameter)EntityNPCInterface.Attacking);
    }
    
    public boolean isKilled() {
        return this.removed || (boolean)this.entityData.get((DataParameter)EntityNPCInterface.IsDead);
    }
    
    public void writeSpawnData(final PacketBuffer buffer) {
        buffer.writeNbt(this.writeSpawnData());
    }
    
    public CompoundNBT writeSpawnData() {
        final CompoundNBT compound = new CompoundNBT();
        this.display.save(compound);
        compound.putInt("MaxHealth", this.stats.maxHealth);
        compound.put("Armor", (INBT)NBTTags.nbtIItemStackMap(this.inventory.armor));
        compound.put("Weapons", (INBT)NBTTags.nbtIItemStackMap(this.inventory.weapons));
        compound.putInt("Speed", this.ais.getWalkingSpeed());
        compound.putBoolean("DeadBody", this.stats.hideKilledBody);
        compound.putInt("StandingState", this.ais.getStandingType());
        compound.putInt("MovingState", this.ais.getMovingType());
        compound.putInt("Orientation", this.ais.orientation);
        compound.putFloat("PositionXOffset", this.ais.bodyOffsetX);
        compound.putFloat("PositionYOffset", this.ais.bodyOffsetY);
        compound.putFloat("PositionZOffset", this.ais.bodyOffsetZ);
        compound.putInt("Role", this.role.getType());
        compound.putInt("Job", this.job.getType());
        if (this.job.getType() == 1) {
            final CompoundNBT bard = new CompoundNBT();
            this.job.save(bard);
            compound.put("Bard", (INBT)bard);
        }
        if (this.job.getType() == 9) {
            final CompoundNBT bard = new CompoundNBT();
            this.job.save(bard);
            compound.put("Puppet", (INBT)bard);
        }
        if (this.role.getType() == 6) {
            final CompoundNBT bard = new CompoundNBT();
            this.role.save(bard);
            compound.put("Companion", (INBT)bard);
        }
        if (this instanceof EntityCustomNpc) {
            compound.put("ModelData", (INBT)((EntityCustomNpc)this).modelData.save());
        }
        return compound;
    }
    
    public void readSpawnData(final PacketBuffer buf) {
        this.readSpawnData(buf.readNbt());
    }
    
    public void readSpawnData(final CompoundNBT compound) {
        this.stats.setMaxHealth(compound.getInt("MaxHealth"));
        this.ais.setWalkingSpeed(compound.getInt("Speed"));
        this.stats.hideKilledBody = compound.getBoolean("DeadBody");
        this.ais.setStandingType(compound.getInt("StandingState"));
        this.ais.setMovingType(compound.getInt("MovingState"));
        this.ais.orientation = compound.getInt("Orientation");
        this.ais.bodyOffsetX = compound.getFloat("PositionXOffset");
        this.ais.bodyOffsetY = compound.getFloat("PositionYOffset");
        this.ais.bodyOffsetZ = compound.getFloat("PositionZOffset");
        this.inventory.armor = NBTTags.getIItemStackMap(compound.getList("Armor", 10));
        this.inventory.weapons = NBTTags.getIItemStackMap(compound.getList("Weapons", 10));
        this.advanced.setRole(compound.getInt("Role"));
        this.advanced.setJob(compound.getInt("Job"));
        if (this.job.getType() == 1) {
            final CompoundNBT bard = compound.getCompound("Bard");
            this.job.load(bard);
        }
        if (this.job.getType() == 9) {
            final CompoundNBT puppet = compound.getCompound("Puppet");
            this.job.load(puppet);
        }
        if (this.role.getType() == 6) {
            final CompoundNBT puppet = compound.getCompound("Companion");
            this.role.load(puppet);
        }
        if (this instanceof EntityCustomNpc) {
            ((EntityCustomNpc)this).modelData.load(compound.getCompound("ModelData"));
        }
        this.display.readToNBT(compound);
        this.refreshDimensions();
    }
    
    public CommandSource createCommandSourceStack() {
        if (this.level.isClientSide) {
            return super.createCommandSourceStack();
        }
        EntityUtil.Copy((LivingEntity)this, (LivingEntity)EntityNPCInterface.CommandPlayer);
        EntityNPCInterface.CommandPlayer.setLevel(this.level);
        EntityNPCInterface.CommandPlayer.setPos(this.getX(), this.getY(), this.getZ());
        return new CommandSource((ICommandSource)this, this.position(), this.getRotationVector(), (ServerWorld)((this.level instanceof ServerWorld) ? this.level : null), this.getPermissionLevel(), this.getName().getString(), this.getDisplayName(), this.level.getServer(), (Entity)this);
    }
    
    public ITextComponent getName() {
        return (ITextComponent)new TranslationTextComponent(this.display.getName());
    }
    
    public void setImmuneToFire(final boolean immuneToFire) {
        this.stats.immuneToFire = immuneToFire;
    }
    
    public boolean fireImmune() {
        return this.stats.immuneToFire;
    }
    
    public boolean causeFallDamage(final float distance, final float modifier) {
        return !this.stats.noFallDamage && super.causeFallDamage(distance, modifier);
    }
    
    public void makeStuckInBlock(final BlockState state, final Vector3d motionMultiplierIn) {
        if ((state != null && !state.is(Blocks.COBWEB)) || !this.stats.ignoreCobweb) {
            super.makeStuckInBlock(state, motionMultiplierIn);
        }
    }
    
    public boolean canBeCollidedWith() {
        return !this.isKilled() && this.display.getHitboxState() == 2;
    }
    
    protected void pushEntities() {
        if (this.display.getHitboxState() != 0) {
            return;
        }
        super.pushEntities();
    }
    
    public boolean isPushable() {
        return this.isWalking() && !this.isKilled();
    }
    
    public PushReaction getPistonPushReaction() {
        return (this.display.getHitboxState() == 0) ? super.getPistonPushReaction() : PushReaction.IGNORE;
    }
    
    public EntityAIRangedAttack getRangedTask() {
        return this.aiRange;
    }
    
    public String getRoleData() {
        return (String)this.entityData.get((DataParameter)EntityNPCInterface.RoleData);
    }
    
    public void setRoleData(final String s) {
        this.entityData.set((DataParameter)EntityNPCInterface.RoleData, (Object)s);
    }
    
    public String getJobData() {
        return (String)this.entityData.get((DataParameter)EntityNPCInterface.RoleData);
    }
    
    public void setJobData(final String s) {
        this.entityData.set((DataParameter)EntityNPCInterface.RoleData, (Object)s);
    }
    
    public World getCommandSenderWorld() {
        return this.level;
    }
    
    public boolean isInvisibleTo(final PlayerEntity player) {
        return this.display.getVisible() == 1 && player.getMainHandItem().getItem() != CustomItems.wand && !this.display.availability.hasOptions();
    }
    
    public boolean isInvisible() {
        return this.display.getVisible() != 0 && !this.display.availability.hasOptions();
    }
    
    public void setInvisible(final ServerPlayerEntity playerMP) {
        if (this.tracking.contains(playerMP.getId())) {
            this.tracking.remove(playerMP.getId());
            Packets.send(playerMP, new PacketNpcVisibleFalse(this.getId()));
        }
    }
    
    public void setVisible(final ServerPlayerEntity playerMP) {
        if (!this.tracking.contains(playerMP.getId())) {
            this.tracking.add(playerMP.getId());
            Packets.send(playerMP, new PacketNpcVisibleTrue((Entity)this));
            playerMP.connection.send((IPacket)new SEntityMetadataPacket(this.getId(), this.getEntityData(), true));
        }
        Packets.send(playerMP, new PacketNpcUpdate(this.getId(), this.writeSpawnData()));
    }
    
    public void sendMessage(final ITextComponent var1, final UUID sender) {
    }
    
    public void setCurrentAnimation(final int animation) {
        this.currentAnimation = animation;
        this.entityData.set((DataParameter)EntityNPCInterface.Animation, (Object)animation);
    }
    
    public boolean canNpcSee(final Entity entity) {
        return this.getSensing().canSee(entity);
    }
    
    public boolean isFollower() {
        return this.advanced.scenes.getOwner() != null || this.role.isFollowing() || this.job.isFollowing();
    }
    
    public LivingEntity getOwner() {
        if (this.advanced.scenes.getOwner() != null) {
            return this.advanced.scenes.getOwner();
        }
        if (this.role.getType() == 2 && this.role instanceof RoleFollower) {
            return (LivingEntity)((RoleFollower)this.role).owner;
        }
        if (this.role.getType() == 6 && this.role instanceof RoleCompanion) {
            return (LivingEntity)((RoleCompanion)this.role).owner;
        }
        if (this.job.getType() == 5 && this.job instanceof JobFollower) {
            return (LivingEntity)((JobFollower)this.job).following;
        }
        return null;
    }
    
    public boolean hasOwner() {
        return this.advanced.scenes.getOwner() != null || (this.role.getType() == 2 && ((RoleFollower)this.role).hasOwner()) || (this.role.getType() == 6 && ((RoleCompanion)this.role).hasOwner()) || (this.job.getType() == 5 && ((JobFollower)this.job).hasOwner());
    }
    
    public int followRange() {
        if (this.advanced.scenes.getOwner() != null) {
            return 4;
        }
        if (this.role.getType() == 2 && this.role.isFollowing()) {
            return 6;
        }
        if (this.role.getType() == 6 && this.role.isFollowing()) {
            return 4;
        }
        if (this.job.getType() == 5 && this.job.isFollowing()) {
            return 4;
        }
        return 15;
    }
    
    protected float getDamageAfterArmorAbsorb(final DamageSource source, float damage) {
        if (this.role.getType() == 6) {
            damage = ((RoleCompanion)this.role).getDamageAfterArmorAbsorb(source, damage);
        }
        return damage;
    }
    
    public boolean isAlliedTo(final Entity entity) {
        if (!this.isClientSide()) {
            if (entity instanceof PlayerEntity && this.getFaction().isFriendlyToPlayer((PlayerEntity)entity)) {
                return true;
            }
            if (entity == this.getOwner()) {
                return true;
            }
            if (entity instanceof EntityNPCInterface && ((EntityNPCInterface)entity).faction.id == this.faction.id) {
                return true;
            }
        }
        return super.isAlliedTo(entity);
    }
    
    public void setDataWatcher(final EntityDataManager entityData) {
        this.entityData.assignValues(entityData.getAll());
    }
    
    public void travel(final Vector3d travelVector) {
        final BlockPos pos = this.blockPosition();
        super.travel(travelVector);
        if (this.role.getType() == 6 && !this.isClientSide()) {
            final BlockPos delta = this.blockPosition().subtract((Vector3i)pos);
            ((RoleCompanion)this.role).addMovementStat(delta.getX(), delta.getY(), delta.getZ());
        }
    }
    
    public boolean canBeLeashed(final PlayerEntity player) {
        return false;
    }
    
    public boolean isLeashed() {
        return false;
    }
    
    public boolean nearPosition(final BlockPos pos) {
        final BlockPos npcpos = this.blockPosition();
        final float x = (float)(npcpos.getX() - pos.getX());
        final float z = (float)(npcpos.getZ() - pos.getZ());
        final float y = (float)(npcpos.getY() - pos.getY());
        final float height = (float)(MathHelper.ceil(this.getBbHeight() + 1.0f) * MathHelper.ceil(this.getBbHeight() + 1.0f));
        return x * x + z * z < 2.5 && y * y < height + 2.5;
    }
    
    public void tpTo(final LivingEntity owner) {
        if (owner == null) {
            return;
        }
        final Direction facing = owner.getDirection().getOpposite();
        BlockPos pos = new BlockPos(owner.getX(), owner.getBoundingBox().minY, owner.getZ());
        pos = pos.offset(facing.getStepX(), 0, facing.getStepZ());
        pos = this.calculateTopPos(pos);
        for (int i = -1; i < 2; ++i) {
            for (int j = 0; j < 3; ++j) {
                BlockPos check;
                if (facing.getStepX() == 0) {
                    check = pos.offset(i, 0, j * facing.getStepZ());
                }
                else {
                    check = pos.offset(j * facing.getStepX(), 0, i);
                }
                check = this.calculateTopPos(check);
                if (!this.level.getBlockState(check).isSolidRender((IBlockReader)this.level, check) && !this.level.getBlockState(check.above()).isSolidRender((IBlockReader)this.level, check.above())) {
                    this.moveTo((double)(check.getX() + 0.5f), (double)check.getY(), (double)(check.getZ() + 0.5f), this.yRot, this.xRot);
                    this.getNavigation().stop();
                    break;
                }
            }
        }
    }
    
    public boolean canBeRiddenInWater(final Entity rider) {
        return false;
    }
    
    public void onSyncedDataUpdated(final DataParameter<?> para) {
        super.onSyncedDataUpdated((DataParameter)para);
        if (EntityNPCInterface.Animation.equals((Object)para)) {
            this.refreshDimensions();
        }
    }
    
    static {
        Attacking = EntityDataManager.defineId((Class)EntityNPCInterface.class, DataSerializers.BOOLEAN);
        Animation = EntityDataManager.defineId((Class)EntityNPCInterface.class, DataSerializers.INT);
        RoleData = EntityDataManager.defineId((Class)EntityNPCInterface.class, DataSerializers.STRING);
        JobData = EntityDataManager.defineId((Class)EntityNPCInterface.class, DataSerializers.STRING);
        FactionData = EntityDataManager.defineId((Class)EntityNPCInterface.class, DataSerializers.INT);
        Walking = EntityDataManager.defineId((Class)EntityNPCInterface.class, DataSerializers.BOOLEAN);
        Interacting = EntityDataManager.defineId((Class)EntityNPCInterface.class, DataSerializers.BOOLEAN);
        IsDead = EntityDataManager.defineId((Class)EntityNPCInterface.class, DataSerializers.BOOLEAN);
        CommandProfile = new GameProfileAlt();
        ChatEventProfile = new GameProfileAlt();
        GenericProfile = new GameProfileAlt();
        sizeSleep = new EntitySize(0.8f, 0.4f, false);
    }
}
