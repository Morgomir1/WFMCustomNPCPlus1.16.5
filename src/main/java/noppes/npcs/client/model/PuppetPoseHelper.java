package noppes.npcs.client.model;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.util.math.MathHelper;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobPuppet;

/**
 * Applies JobPuppet part rotations onto any {@link BipedModel}.
 * Used for entity-cloned models (e.g. WFM LOTRBipedModel) where
 * {@code BipedBodyMixin} does not see {@code EntityCustomNpc}.
 */
public final class PuppetPoseHelper {
    private static final float PI = 3.1415927f;
    private static final Map<Class<?>, WearFields> WEAR_CACHE = new HashMap<Class<?>, WearFields>();

    private PuppetPoseHelper() {
    }

    public static void apply(EntityNPCInterface npc, BipedModel<?> bipedModel, float ageInTicks) {
        if (npc == null || bipedModel == null || npc.job == null || npc.job.getType() != 9) {
            return;
        }
        JobPuppet job = (JobPuppet) npc.job;
        if (!job.isActive()) {
            return;
        }

        float partialTicks = Minecraft.getInstance().getDeltaFrameTime();

        if (!job.head.disabled) {
            float x = job.getRotationX(job.head, job.head2, partialTicks) * PI;
            float y = job.getRotationY(job.head, job.head2, partialTicks) * PI;
            float z = job.getRotationZ(job.head, job.head2, partialTicks) * PI;
            bipedModel.head.xRot = x;
            bipedModel.head.yRot = y;
            bipedModel.head.zRot = z;
            bipedModel.hat.xRot = x;
            bipedModel.hat.yRot = y;
            bipedModel.hat.zRot = z;
        }
        if (!job.body.disabled) {
            bipedModel.body.xRot = job.getRotationX(job.body, job.body2, partialTicks) * PI;
            bipedModel.body.yRot = job.getRotationY(job.body, job.body2, partialTicks) * PI;
            bipedModel.body.zRot = job.getRotationZ(job.body, job.body2, partialTicks) * PI;
        }
        if (!job.larm.disabled) {
            bipedModel.leftArm.xRot = job.getRotationX(job.larm, job.larm2, partialTicks) * PI;
            bipedModel.leftArm.yRot = job.getRotationY(job.larm, job.larm2, partialTicks) * PI;
            bipedModel.leftArm.zRot = job.getRotationZ(job.larm, job.larm2, partialTicks) * PI;
            if (npc.display.getHasLivingAnimation()) {
                bipedModel.leftArm.zRot -= MathHelper.cos(ageInTicks * 0.09f) * 0.05f + 0.05f;
                bipedModel.leftArm.xRot -= MathHelper.sin(ageInTicks * 0.067f) * 0.05f;
            }
        }
        if (!job.rarm.disabled) {
            bipedModel.rightArm.xRot = job.getRotationX(job.rarm, job.rarm2, partialTicks) * PI;
            bipedModel.rightArm.yRot = job.getRotationY(job.rarm, job.rarm2, partialTicks) * PI;
            bipedModel.rightArm.zRot = job.getRotationZ(job.rarm, job.rarm2, partialTicks) * PI;
            if (npc.display.getHasLivingAnimation()) {
                bipedModel.rightArm.zRot += MathHelper.cos(ageInTicks * 0.09f) * 0.05f + 0.05f;
                bipedModel.rightArm.xRot += MathHelper.sin(ageInTicks * 0.067f) * 0.05f;
            }
        }
        if (!job.rleg.disabled) {
            bipedModel.rightLeg.xRot = job.getRotationX(job.rleg, job.rleg2, partialTicks) * PI;
            bipedModel.rightLeg.yRot = job.getRotationY(job.rleg, job.rleg2, partialTicks) * PI;
            bipedModel.rightLeg.zRot = job.getRotationZ(job.rleg, job.rleg2, partialTicks) * PI;
        }
        if (!job.lleg.disabled) {
            bipedModel.leftLeg.xRot = job.getRotationX(job.lleg, job.lleg2, partialTicks) * PI;
            bipedModel.leftLeg.yRot = job.getRotationY(job.lleg, job.lleg2, partialTicks) * PI;
            bipedModel.leftLeg.zRot = job.getRotationZ(job.lleg, job.lleg2, partialTicks) * PI;
        }

        syncWearLayers(bipedModel);
    }

    /**
     * LOTRBipedModel (and similar) keep wear sleeves/pants as sibling ModelRenderers
     * copied from limbs at the end of setupAnim — refresh them after puppet overrides.
     */
    private static void syncWearLayers(BipedModel<?> model) {
        WearFields wear = WEAR_CACHE.get(model.getClass());
        if (wear == null) {
            wear = WearFields.resolve(model.getClass());
            WEAR_CACHE.put(model.getClass(), wear);
        }
        if (wear.empty) {
            return;
        }
        copyWear(wear.leftArmwear, model, model.leftArm);
        copyWear(wear.rightArmwear, model, model.rightArm);
        copyWear(wear.leftLegwear, model, model.leftLeg);
        copyWear(wear.rightLegwear, model, model.rightLeg);
        copyWear(wear.bodywear, model, model.body);
    }

    private static void copyWear(Field field, BipedModel<?> model, ModelRenderer source) {
        if (field == null || source == null) {
            return;
        }
        try {
            ModelRenderer wear = (ModelRenderer) field.get(model);
            if (wear != null) {
                wear.copyFrom(source);
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    private static final class WearFields {
        final Field leftArmwear;
        final Field rightArmwear;
        final Field leftLegwear;
        final Field rightLegwear;
        final Field bodywear;
        final boolean empty;

        WearFields(Field leftArmwear, Field rightArmwear, Field leftLegwear, Field rightLegwear, Field bodywear) {
            this.leftArmwear = leftArmwear;
            this.rightArmwear = rightArmwear;
            this.leftLegwear = leftLegwear;
            this.rightLegwear = rightLegwear;
            this.bodywear = bodywear;
            this.empty = leftArmwear == null && rightArmwear == null && leftLegwear == null
                    && rightLegwear == null && bodywear == null;
        }

        static WearFields resolve(Class<?> clazz) {
            return new WearFields(
                    findModelRendererField(clazz, "bipedLeftArmwear", "leftArmwear"),
                    findModelRendererField(clazz, "bipedRightArmwear", "rightArmwear"),
                    findModelRendererField(clazz, "bipedLeftLegwear", "leftLegwear"),
                    findModelRendererField(clazz, "bipedRightLegwear", "rightLegwear"),
                    findModelRendererField(clazz, "bipedBodywear", "bodywear"));
        }

        private static Field findModelRendererField(Class<?> clazz, String... names) {
            Class<?> c = clazz;
            while (c != null && c != Object.class) {
                for (String name : names) {
                    try {
                        Field f = c.getDeclaredField(name);
                        if (ModelRenderer.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            return f;
                        }
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                c = c.getSuperclass();
            }
            return null;
        }
    }
}
