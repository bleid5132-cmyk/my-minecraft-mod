package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.combat.CombatPose;
import com.bleid.vestments.paladin.PaladinSwordItem;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.ModelWithArms;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Во время приёма меч в руке перехватывается «по линии руки» (клинок продолжает руку),
 * переход от обычного хвата плавный: смешиваем положение и поворот двух хватов.
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    @Shadow @Final private HeldItemRenderer heldItemRenderer;

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void vestments$combatGrip(LivingEntity e, ItemStack stack, ModelTransformationMode mode, Arm arm,
                                      MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        if (!(e instanceof PlayerEntity) || !(stack.getItem() instanceof PaladinSwordItem) || arm != e.getMainArm()) return;
        CombatPose.Pose p = CombatPose.compute(e, net.minecraft.client.MinecraftClient.getInstance().getTickDelta());
        if (p == null || p.clip.offOnly) return;
        boolean left = arm == Arm.LEFT;
        float s = left ? -1f : 1f;
        matrices.push();
        ((ModelWithArms) ((FeatureRenderer<?, ?>) (Object) this).getContextModel()).setArmAngle(arm, matrices);
        // обычный хват (как у ванильного меча с нашей display-трансформацией)
        Matrix4f van = new Matrix4f()
                .rotateX((float) Math.toRadians(-90)).rotateY((float) Math.PI)
                .translate(s / 16f, 0.125f, -0.625f)
                .translate(0, 4f / 16f, 0.5f / 16f)
                .rotateXYZ(0, (float) Math.toRadians(-90 * s), (float) Math.toRadians(10 * s))
                .scale(0.85f);
        // боевой хват: рукоять в кулаке, клинок вдоль руки
        Matrix4f fight = new Matrix4f()
                .translate(-s / 16f, 0.6f, -0.05f)
                .scale(0.85f)
                .translate(0, 0.53f, 0);
        Vector3f tv = van.getTranslation(new Vector3f()), tf = fight.getTranslation(new Vector3f());
        Quaternionf qv = van.getNormalizedRotation(new Quaternionf()), qf = fight.getNormalizedRotation(new Quaternionf());
        float w = p.w;
        Vector3f t = tv.lerp(tf, w);
        Quaternionf q = qv.slerp(qf, w);
        matrices.translate(t.x, t.y, t.z);
        matrices.multiply(q);
        matrices.scale(0.85f, 0.85f, 0.85f);
        this.heldItemRenderer.renderItem(e, stack, ModelTransformationMode.NONE, left, matrices, vcp, light);
        matrices.pop();
        ci.cancel();
    }
}
