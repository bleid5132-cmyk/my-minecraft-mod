package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.bend.Bends;
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
import org.joml.Matrix3f;
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
 * Предмет в руке следует за согнутым предплечьем. Во время приёма меч перехватывается «по линии руки»
 * (клинок продолжает предплечье); при выхватывании из ножен меч ведётся по ключам DrawPose и
 * появляется в руке только в момент хвата рукояти. Переходы между хватами плавные.
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    @Shadow @Final private HeldItemRenderer heldItemRenderer;

    private static float vestments$elbow(LivingEntity e, Arm arm) {
        float[] v = Bends.of(e);
        return v == null ? 0f : (arm == Arm.RIGHT ? v[0] : v[1]);
    }

    /** Обычный хват (ванильная display-трансформация меча) в системе координат кисти. */
    private static Matrix4f vestments$vanillaGrip(float s) {
        return new Matrix4f()
                .rotateX((float) Math.toRadians(-90)).rotateY((float) Math.PI)
                .translate(s / 16f, 0.125f, -0.625f)
                .translate(0, 4f / 16f, 0.5f / 16f)
                .rotateXYZ(0, (float) Math.toRadians(-90 * s), (float) Math.toRadians(10 * s))
                .scale(0.85f);
    }

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void vestments$combatGrip(LivingEntity e, ItemStack stack, ModelTransformationMode mode, Arm arm,
                                      MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        if (!(e instanceof PlayerEntity) || arm != e.getMainArm()) return;
        // брошенный молот в полёте — в руке его нет
        if (com.bleid.vestments.client.HammerClient.flying(e) && com.bleid.vestments.client.HammerClient.isHammer(stack)) {
            ci.cancel();
            return;
        }
        CombatPose.Pose p = CombatPose.compute(e, net.minecraft.client.MinecraftClient.getInstance().getTickDelta());
        // меч убирается в ножны: пока он в руке (его рисует ScabbardFeature), новый предмет не показываем
        if (p != null && p.clip.reverse) {
            if (p.draw != null && p.draw.inHand) ci.cancel();
            return;
        }
        if (!(stack.getItem() instanceof PaladinSwordItem)) return;
        if (p == null || p.clip.offOnly || p.clip.dodge) return;
        boolean left = arm == Arm.LEFT;
        float s = left ? -1f : 1f;
        ModelWithArms model = (ModelWithArms) ((FeatureRenderer<?, ?>) (Object) this).getContextModel();
        float w = p.w;
        Matrix4f target;
        Matrix4f van = vestments$vanillaGrip(s);

        if (p.draw != null) {
            if (p.draw.inHand) {
                com.bleid.vestments.client.combat.SwordGrip.render(heldItemRenderer, e, stack, arm, model, p.draw, w,
                        matrices, vcp, light);
            }
            ci.cancel();             // до хвата меч в ножнах — его рисует ScabbardFeature
            return;
        } else {
            // боевой хват: рукоять в кулаке, клинок продолжает предплечье
            matrices.push();
            model.setArmAngle(arm, matrices);
            Bends.forearm(matrices, arm, vestments$elbow(e, arm));
            target = new Matrix4f()
                    .translate(-s / 16f, 0.6f, -0.05f)
                    .scale(0.85f)
                    .translate(0, 0.53f, 0);
        }
        Vector3f tv = van.getTranslation(new Vector3f()), tf = target.getTranslation(new Vector3f());
        Quaternionf qv = van.getNormalizedRotation(new Quaternionf()), qf = target.getNormalizedRotation(new Quaternionf());
        Vector3f t = tv.lerp(tf, w);
        Quaternionf q = qv.slerp(qf, w);
        matrices.translate(t.x, t.y, t.z);
        matrices.multiply(q);
        matrices.scale(0.85f, 0.85f, 0.85f);
        this.heldItemRenderer.renderItem(e, stack, ModelTransformationMode.NONE, left, matrices, vcp, light);
        matrices.pop();
        ci.cancel();
    }

    /** Обычные предметы (и щит) поворачиваются вместе с согнутым предплечьем. */
    @Inject(method = "renderItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/model/ModelWithArms;setArmAngle(Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;)V",
            shift = At.Shift.AFTER))
    private void vestments$forearm(LivingEntity e, ItemStack stack, ModelTransformationMode mode, Arm arm,
                                   MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        if (e instanceof PlayerEntity) Bends.forearm(matrices, arm, vestments$elbow(e, arm));
    }
}
