package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.combat.CameraTilt;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Крен камеры: наклон в сторону бега и толчок при попадании. */
@Mixin(GameRenderer.class)
public abstract class GameRendererTiltMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
    private void vestments$tilt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        float r = CameraTilt.roll(tickDelta);
        if (Math.abs(r) > 0.001f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(r));
    }
}
