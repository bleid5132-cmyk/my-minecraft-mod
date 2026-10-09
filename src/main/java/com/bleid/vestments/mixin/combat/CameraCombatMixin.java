package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.combat.CameraShake;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Тряска камеры при тяжёлых ударах. */
@Mixin(Camera.class)
public abstract class CameraCombatMixin {
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow public abstract float getYaw();
    @Shadow public abstract float getPitch();

    @Inject(method = "update", at = @At("TAIL"))
    private void vestments$shake(BlockView area, Entity focused, boolean thirdPerson, boolean inverseView, float tickDelta,
                                 CallbackInfo ci) {
        float[] o = CameraShake.offset();
        if (o != null) setRotation(getYaw() + o[0], getPitch() + o[1]);
    }
}
