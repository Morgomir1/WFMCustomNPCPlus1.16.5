package noppes.npcs.entity;

import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import software.bernie.geckolib3.core.AnimationState;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.IAnimationTickable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

public class EntityCustomModel extends CreatureEntity implements IAnimatable, IAnimationTickable {
    private AnimationFactory factory = new AnimationFactory(this);
    public ResourceLocation modelResLoc = new ResourceLocation("geckolib3", "geo/bike.geo.json");
    public ResourceLocation animResLoc = new ResourceLocation("geckolib3", "bike.animation.json");
    public ResourceLocation textureResLoc = new ResourceLocation("geckolib3", "textures/model/entity/bike.png");
    public String idleAnim = "";
    public String walkAnim = "";
    public String geckoHurtAnim = "";
    public String geckoAttackAnim = "";
    public AnimationBuilder dialogAnim = null;
    public AnimationBuilder manualAnim = null;
    private AnimationBuilder appliedDialogAnim = null;
    private AnimationBuilder appliedManualAnim = null;
    public ItemStack leftHeldItem;
    private boolean attackSwingConsumed;

    private boolean isMeleeAttacking() {
        return this.swinging || this.getAttackAnim(1.0f) > 0.001f;
    }

    private <E extends IAnimatable> PlayState handleAttackAnimation(AnimationEvent<E> event) {
        if (manualAnim != null || dialogAnim != null || geckoAttackAnim.isEmpty()) {
            if (!isMeleeAttacking()) {
                attackSwingConsumed = false;
            }
            return null;
        }

        AnimationController<?> controller = event.getController();
        AnimationState state = controller.getAnimationState();

        if (!isMeleeAttacking()) {
            attackSwingConsumed = false;
            return null;
        }

        if (!attackSwingConsumed) {
            controller.markNeedsReload();
            controller.setAnimation(new AnimationBuilder().playOnce(geckoAttackAnim));
            attackSwingConsumed = true;
            return PlayState.CONTINUE;
        }

        if (state != AnimationState.Stopped) {
            return PlayState.CONTINUE;
        }
        return null;
    }

    private <E extends IAnimatable> PlayState predicateMovement(AnimationEvent<E> event) {
        if (manualAnim != null) {
            if (event.getController().getAnimationState() == AnimationState.Stopped) {
                manualAnim = null;
                appliedManualAnim = null;
            } else {
                if (appliedManualAnim != manualAnim) {
                    event.getController().markNeedsReload();
                    event.getController().setAnimation(manualAnim);
                    appliedManualAnim = manualAnim;
                }
                return PlayState.CONTINUE;
            }
        } else {
            appliedManualAnim = null;
        }
        if (dialogAnim != null) {
            if (event.getController().getAnimationState() == AnimationState.Stopped) {
                dialogAnim = null;
                appliedDialogAnim = null;
            } else {
                if (appliedDialogAnim != dialogAnim) {
                    event.getController().markNeedsReload();
                    event.getController().setAnimation(dialogAnim);
                    appliedDialogAnim = dialogAnim;
                }
                return PlayState.CONTINUE;
            }
        } else {
            appliedDialogAnim = null;
        }

        PlayState attackState = handleAttackAnimation(event);
        if (attackState != null) {
            return attackState;
        }

        if (!event.isMoving() || walkAnim.isEmpty()) {
            if (!idleAnim.isEmpty()) {
                event.getController().setAnimation(new AnimationBuilder().loop(idleAnim));
            } else {
                return PlayState.STOP;
            }
        } else {
            event.getController().setAnimation(new AnimationBuilder().loop(walkAnim));
        }
        return PlayState.CONTINUE;
    }

    public EntityCustomModel(EntityType<? extends CreatureEntity> type, World worldIn) {
        super(type, worldIn);
        this.noCulling = true;
    }

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<>(this, "movement", 0, this::predicateMovement));
    }

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }

    @Override
    public int tickTimer() {
        return tickCount;
    }

    @Override
    public void tick() {
        super.tick();
    }
}
