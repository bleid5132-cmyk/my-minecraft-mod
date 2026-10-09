package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.combat.CombatPose;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Поза приёма паладина поверх обычной позы игрока (плавно смешивается). */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelCombatMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void vestments$combatPose(LivingEntity e, float limbAngle, float limbDistance, float progress, float headYaw,
                                      float headPitch, CallbackInfo ci) {
        if (!(e instanceof PlayerEntity)) return;
        float td = MathHelper.clamp(progress - e.age, 0f, 1f);
        CombatPose.Pose p = CombatPose.compute(e, td);
        if (p == null) return;
        BipedEntityModel<?> m = (BipedEntityModel<?>) (Object) this;
        float w = p.w;
        boolean right = e.getMainArm() == Arm.RIGHT;
        ModelPart main = right ? m.rightArm : m.leftArm;
        ModelPart off = right ? m.leftArm : m.rightArm;
        float s = right ? 1f : -1f;
        // корпус и плечи
        m.body.yaw = MathHelper.lerp(w, m.body.yaw, p.bodyYaw * s);
        float bt = m.body.yaw;
        m.rightArm.pivotZ = MathHelper.lerp(w, m.rightArm.pivotZ, MathHelper.sin(bt) * 5f);
        m.rightArm.pivotX = MathHelper.lerp(w, m.rightArm.pivotX, -MathHelper.cos(bt) * 5f);
        m.leftArm.pivotZ = MathHelper.lerp(w, m.leftArm.pivotZ, -MathHelper.sin(bt) * 5f);
        m.leftArm.pivotX = MathHelper.lerp(w, m.leftArm.pivotX, MathHelper.cos(bt) * 5f);
        // рука с мечом
        main.pitch = MathHelper.lerp(w, main.pitch, p.armPitch);
        main.yaw = MathHelper.lerp(w, main.yaw, p.armYaw * s);
        main.roll = MathHelper.lerp(w, main.roll, p.armRoll * s);
        // вторая рука
        off.pitch = MathHelper.lerp(w, off.pitch, p.offPitch);
        off.yaw = MathHelper.lerp(w, off.yaw, p.offYaw * s);
        off.roll = MathHelper.lerp(w, off.roll, p.offRoll * s);
        // ноги
        m.rightLeg.pitch = MathHelper.lerp(w, m.rightLeg.pitch, p.rLeg * s);
        m.leftLeg.pitch = MathHelper.lerp(w, m.leftLeg.pitch, p.lLeg * s);
    }
}
