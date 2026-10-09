package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.combat.CombatPose;
import com.bleid.vestments.client.combat.Locomotion;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Движение всего тела: покачивание шага, наклон, разворот в стойке; поверх — приёмы. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererCombatMixin {
    @Inject(method = "setupTransforms", at = @At("TAIL"))
    private void vestments$combatRoot(LivingEntity e, MatrixStack matrices, float progress, float bodyYaw, float tickDelta,
                                      CallbackInfo ci) {
        if (!(e instanceof PlayerEntity)) return;
        CombatPose.Pose p = CombatPose.compute(e, tickDelta);
        float wc = p == null || p.clip.offOnly ? 0f : p.w;
        Locomotion.Loco L = Locomotion.compute(e, tickDelta);
        float lift = 0, spin = 0, lean = 0, roll = 0;
        if (L != null) {
            float k = 1f - wc;
            lift += L.bob * k;
            spin += L.turn * k;
            lean += L.lean * k;
            roll += L.roll * k;
        }
        if (p != null && !p.clip.offOnly) {
            lift += p.lift * p.w;
            spin += p.spin * p.w;
            lean += p.lean * p.w;
        } else if (p != null) {
            spin += p.spin * p.w * 0.5f;
        }
        if (lift == 0 && spin == 0 && lean == 0 && roll == 0) return;
        matrices.translate(0, lift, 0);
        matrices.translate(0, 0.9, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-spin));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-lean));
        matrices.translate(0, -0.9, 0);
        if (roll != 0) {                       // покачивание таза — вокруг точки между стопами
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        }
    }
}
