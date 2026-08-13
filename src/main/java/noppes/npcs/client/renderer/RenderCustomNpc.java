package noppes.npcs.client.renderer;

import java.util.List;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.entity.NPCRendererHelper;
import net.minecraft.client.renderer.entity.layers.BipedArmorLayer;
import net.minecraft.client.renderer.entity.layers.HeadLayer;
import net.minecraft.client.renderer.entity.layers.HeldItemLayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.model.Model;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.NpcSoftVisibility;
import noppes.npcs.client.layer.LayerArms;
import noppes.npcs.client.layer.LayerBody;
import noppes.npcs.client.layer.LayerEyes;
import noppes.npcs.client.layer.LayerGlow;
import noppes.npcs.client.layer.LayerHead;
import noppes.npcs.client.layer.LayerHeadwear;
import noppes.npcs.client.layer.LayerLegs;
import noppes.npcs.client.layer.LayerNpcCloak;
import noppes.npcs.client.layer.LayerPreRender;
import noppes.npcs.client.model.PuppetPoseHelper;
import noppes.npcs.controllers.PixelmonHelper;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.mixin.ArmorLayerMixin;

@SuppressWarnings({"rawtypes", "unchecked"})
public class RenderCustomNpc<T extends EntityCustomNpc, M extends BipedModel<T>> extends RenderNPCInterface<T, M> {
    private float partialTicks;
    private LivingEntity entity;
    private EntityNPCInterface npc;
    private LivingRenderer renderEntity;
    public M npcmodel;
    public Model otherModel;
    public ArmorLayerMixin armorLayer;
    public final List<LayerRenderer<T, M>> npclayers;
    private LayerRenderer renderLayer;
    private BipedModel renderModel;

    public RenderCustomNpc(final EntityRendererManager manager, final M model) {
        super(manager, model, 0.5f);
        this.npclayers = Lists.newArrayList();
        this.renderLayer = new LayerRenderer((IEntityRenderer) null) {
            public void render(final MatrixStack mStack, final IRenderTypeBuffer typeBuffer, final int lightmapUV, final Entity p_225628_4_, final float limbSwing, final float limbSwingAmount, final float partialTicks, final float age, final float netHeadYaw, final float headPitch) {
                for (final Object layer : RenderCustomNpc.this.renderEntity.layers) {
                    ((LayerRenderer) layer).render(mStack, typeBuffer, lightmapUV, (Entity) RenderCustomNpc.this.entity, limbSwing, limbSwingAmount, partialTicks, age, netHeadYaw, headPitch);
                }
            }
        };
        this.renderModel = new BipedModel(0.0f) {
            public void renderToBuffer(final MatrixStack mStack, final IVertexBuilder iVertex, final int lightmapUV, final int packedOverlayIn, float red, float green, float blue, final float alpha) {
                final int color = RenderCustomNpc.this.npc.display.getTint();
                if (color < 16777215) {
                    red = (color >> 16 & 0xFF) / 255.0f;
                    green = (color >> 8 & 0xFF) / 255.0f;
                    blue = (color & 0xFF) / 255.0f;
                }
                RenderCustomNpc.this.otherModel.renderToBuffer(mStack, iVertex, lightmapUV, packedOverlayIn, red, green, blue, alpha);
            }

            public void setupAnim(final Entity entityIn, final float limbSwing, final float limbSwingAmount, final float ageInTicks, final float netHeadYaw, final float headPitch) {
                if (RenderCustomNpc.this.otherModel instanceof EntityModel) {
                    final EntityModel em = (EntityModel) RenderCustomNpc.this.otherModel;
                    em.setupAnim((Entity) RenderCustomNpc.this.entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
                    // Cloned entity models animate a foreign LivingEntity, so BipedBodyMixin
                    // never applies JobPuppet to the model that is actually rendered.
                    if (RenderCustomNpc.this.otherModel instanceof BipedModel && RenderCustomNpc.this.npc != null) {
                        PuppetPoseHelper.apply(RenderCustomNpc.this.npc, (BipedModel) RenderCustomNpc.this.otherModel, ageInTicks);
                    }
                }
            }

            public void prepareMobModel(final Entity npcEntity, final float animationPos, final float animationSpeed, final float partialTicks) {
                if (PixelmonHelper.isPixelmon((Entity) RenderCustomNpc.this.entity)) {
                    final Model pixModel = (Model) PixelmonHelper.getModel(RenderCustomNpc.this.entity);
                    if (pixModel != null) {
                        RenderCustomNpc.this.otherModel = pixModel;
                        PixelmonHelper.setupModel(RenderCustomNpc.this.entity, pixModel);
                    }
                }
                if (RenderCustomNpc.this.otherModel instanceof BipedModel) {
                    final BipedModel bm = (BipedModel) RenderCustomNpc.this.otherModel;
                    bm.swimAmount = ((EntityCustomNpc) npcEntity).getSwimAmount(partialTicks);
                    bm.crouching = RenderCustomNpc.this.npcmodel.crouching;
                }
                if (RenderCustomNpc.this.otherModel instanceof EntityModel) {
                    final EntityModel em = (EntityModel) RenderCustomNpc.this.otherModel;
                    em.riding = (RenderCustomNpc.this.entity.isPassenger() && RenderCustomNpc.this.entity.getVehicle() != null && RenderCustomNpc.this.entity.getVehicle().shouldRiderSit());
                    em.young = RenderCustomNpc.this.entity.isBaby();
                    em.attackTime = RenderCustomNpc.this.getAttackAnim((T) npcEntity, partialTicks);
                    em.prepareMobModel((Entity) RenderCustomNpc.this.entity, animationPos, animationSpeed, partialTicks);
                }
            }
        };
        this.npcmodel = model;
        this.addLayer(new LayerEyes(this));
        this.addLayer(new LayerHeadwear(this));
        this.addLayer(new LayerHead(this));
        this.addLayer(new LayerArms(this));
        this.addLayer(new LayerLegs(this));
        this.addLayer(new LayerBody(this));
        this.addLayer(new LayerNpcCloak(this));
        this.addLayer(new HeldItemLayer(this));
        this.addLayer(new HeadLayer(this));
        this.addLayer(new LayerGlow(this));
        final BipedArmorLayer armorLayer = new BipedArmorLayer(this, new BipedModel(0.5f), new BipedModel(1.0f));
        this.addLayer(armorLayer);
        this.armorLayer = (ArmorLayerMixin) armorLayer;
    }

    public Vector3d getRenderOffset(final T npc, final float partialTicks) {
        float xOffset = 0.0f;
        float yOffset = (npc.currentAnimation == 0) ? (npc.ais.bodyOffsetY / 10.0f - 0.5f) : 0.0f;
        float zOffset = 0.0f;
        if (npc.isAlive()) {
            if (npc.isSleeping()) {
                xOffset = (float) (-Math.cos(Math.toRadians(180 - npc.ais.orientation)));
                zOffset = (float) (-Math.sin(Math.toRadians(npc.ais.orientation)));
                yOffset += 0.14f;
            } else if (npc.currentAnimation == 1 || npc.isPassenger()) {
                yOffset -= 0.5f - npc.modelData.getLegsY() * 0.8f;
            } else if (npc.isCrouching()) {
                yOffset -= 0.125;
            }
        }
        return new Vector3d(xOffset, yOffset * (npc.display.getSize() / 5.0f), zOffset);
    }

    @Override
    public void render(final T npc, final float entityYaw, final float partialTicks, final MatrixStack matrixStack, final IRenderTypeBuffer buffer, final int packedLight) {
        // simpleRender returns before super.render — must gate soft-hide here too.
        final PlayerEntity localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null && NpcSoftVisibility.isInvisibleTo(npc, localPlayer)) {
            this.shadowRadius = 0.0f;
            return;
        }
        this.npc = npc;
        this.partialTicks = partialTicks;
        final Entity prevEntity = this.entity;
        this.entity = npc.modelData.getEntity(npc);
        if (prevEntity != null && this.entity == null) {
            this.model = this.npcmodel;
            this.renderEntity = null;
            this.layers.clear();
            this.layers.addAll(this.npclayers);
        }
        if (this.entity != null) {
            final EntityRenderer render = this.entityRenderDispatcher.getRenderer(this.entity);
            if (npc.modelData.simpleRender) {
                this.renderEntity = null;
                matrixStack.pushPose();
                render.render(this.entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
                this.renderNameTag(npc, StringTextComponent.EMPTY, matrixStack, buffer, packedLight);
                matrixStack.popPose();
                return;
            }
            if (render instanceof LivingRenderer) {
                this.renderEntity = (LivingRenderer) render;
                this.otherModel = this.renderEntity.getModel();
                this.model = (M) this.renderModel;
                this.layers.clear();
                this.layers.add(this.renderLayer);
                if (render instanceof RenderCustomNpc) {
                    for (final Object layer : this.renderEntity.layers) {
                        if (layer instanceof LayerPreRender) {
                            ((LayerPreRender) layer).preRender((EntityCustomNpc) this.entity);
                        }
                    }
                }
            } else {
                this.renderEntity = null;
                this.entity = null;
                this.model = this.npcmodel;
                this.layers.clear();
                this.layers.addAll(this.npclayers);
            }
        } else {
            for (final LayerRenderer<T, M> layer2 : this.layers) {
                if (layer2 instanceof LayerPreRender) {
                    ((LayerPreRender) layer2).preRender(npc);
                }
            }
        }
        this.npcmodel.rightArmPose = this.getPose(npc, npc.getMainHandItem());
        this.npcmodel.leftArmPose = this.getPose(npc, npc.getOffhandItem());
        super.render(npc, entityYaw, partialTicks, matrixStack, buffer, packedLight);
    }

    protected RenderType getRenderType(final T entity, final boolean p_230496_2_, final boolean p_230496_3_, final boolean p_230496_4_) {
        final ResourceLocation resourcelocation = this.getTextureLocation(entity);
        if (p_230496_2_ && this.model == this.renderModel) {
            return this.otherModel.renderType(resourcelocation);
        }
        return super.getRenderType(entity, p_230496_2_, p_230496_3_, p_230496_4_);
    }

    public BipedModel.ArmPose getPose(final T npc, final ItemStack item) {
        if (NoppesUtilServer.IsItemStackNull(item)) {
            return BipedModel.ArmPose.EMPTY;
        }
        if (npc.getUseItemRemainingTicks() > 0) {
            final UseAction enumaction = item.getUseAnimation();
            if (enumaction == UseAction.BLOCK) {
                return BipedModel.ArmPose.BLOCK;
            }
            if (enumaction == UseAction.BOW) {
                return BipedModel.ArmPose.BOW_AND_ARROW;
            }
            if (enumaction == UseAction.CROSSBOW) {
                return BipedModel.ArmPose.CROSSBOW_HOLD;
            }
        }
        return BipedModel.ArmPose.ITEM;
    }

    @Override
    protected void scale(final T npc, final MatrixStack matrixScale, final float f) {
        if (this.renderEntity != null) {
            this.renderColor(npc);
            final int size = npc.display.getSize();
            if (this.entity instanceof EntityNPCInterface) {
                ((EntityNPCInterface) this.entity).display.setSize(5);
            }
            NPCRendererHelper.scale(this.entity, f, matrixScale, this.renderEntity);
            npc.display.setSize(size);
            matrixScale.scale(0.2f * npc.display.getSize(), 0.2f * npc.display.getSize(), 0.2f * npc.display.getSize());
        } else {
            super.scale(npc, matrixScale, f);
        }
    }

    @Override
    protected float getBob(final T par1LivingEntity, final float limbSwingAmount) {
        if (this.renderEntity != null) {
            return NPCRendererHelper.getBob(this.entity, limbSwingAmount, this.renderEntity);
        }
        return super.getBob(par1LivingEntity, limbSwingAmount);
    }
}
