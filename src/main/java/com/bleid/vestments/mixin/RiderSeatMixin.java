package com.bleid.vestments.mixin;

import com.bleid.vestments.client.horse.RealHorseModel;
import com.bleid.vestments.client.horse.RiderPose;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Всадник сидит ровно в седле лошади мода: его модель переносится в точку сиденья (с качанием корпуса лошади)
 * и наклоняется вместе со спиной лошади; на рыси привстаёт в такт. Позиция игрока в мире не меняется.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class RiderSeatMixin {
    /** Высота таза над ступнями у модели игрока (12 пикс. × 0,9375), в блоках. */
    private static final float HIP = 0.703f;

    @Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V",
            at = @At("HEAD"))
    private void vestments$seatMove(AbstractClientPlayerEntity player, MatrixStack ms, float progress, float bodyYaw, float td, CallbackInfo ci) {
        AbstractHorseEntity h = RiderPose.horse(player);
        if (h == null) return;
        RealHorseModel.State s = RealHorseModel.state(h);
        RiderPose.Pose p = RiderPose.compute(player);
        if (s == null || p == null) return;
        float yaw = MathHelper.lerpAngleDegrees(td, h.prevBodyYaw, h.bodyYaw);
        float k = RealHorseModel.SCALE / 16f;
        Vector3f seat = new Vector3f(s.seatX * k, s.seatY * k, s.seatZ * k).rotateY((float) Math.toRadians(-yaw));
        double hx = MathHelper.lerp(td, h.lastRenderX, h.getX()), hy = MathHelper.lerp(td, h.lastRenderY, h.getY()),
                hz = MathHelper.lerp(td, h.lastRenderZ, h.getZ());
        double px = MathHelper.lerp(td, player.lastRenderX, player.getX()), py = MathHelper.lerp(td, player.lastRenderY, player.getY()),
                pz = MathHelper.lerp(td, player.lastRenderZ, player.getZ());
        ms.translate(hx + seat.x - px, hy + seat.y - HIP + p.rise - py, hz + seat.z - pz);
    }

    @Inject(method = "setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V",
            at = @At("TAIL"))
    private void vestments$seatTilt(AbstractClientPlayerEntity player, MatrixStack ms, float progress, float bodyYaw, float td, CallbackInfo ci) {
        AbstractHorseEntity h = RiderPose.horse(player);
        if (h == null) return;
        RealHorseModel.State s = RealHorseModel.state(h);
        RiderPose.Pose p = RiderPose.compute(player);
        if (s == null || p == null) return;
        // здесь оси корпуса всадника: вперёд −z, вправо +x; поворот вокруг таза
        ms.translate(0, HIP, 0);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(s.seatPitch));
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-s.seatRoll + p.sway));
        ms.translate(0, -HIP, 0);
    }
}
