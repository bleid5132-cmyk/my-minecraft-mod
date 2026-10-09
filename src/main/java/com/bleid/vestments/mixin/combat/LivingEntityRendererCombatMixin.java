package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.combat.CombatPose;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Движение всего тела во время приёма: подскок, разворот, наклон (вокруг бёдер). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererCombatMixin {
    @Inject(method = "setupTransforms", at = @At("TAIL"))
    private void vestments$combatRoot(LivingEntity e, MatrixStack matrices, float progress, float bodyYaw, float tickDelta,
                                      CallbackInfo ci) {
        if (!(e instanceof PlayerEntity)) return;
        CombatPose.Pose p = CombatPose.compute(e, tickDelta);
        if (p == null) return;
        matrices.translate(0, p.lift * p.w, 0);
        matrices.translate(0, 0.9, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-p.spin * p.w));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-p.lean * p.w));
        matrices.translate(0, -0.9, 0);
    }
}
