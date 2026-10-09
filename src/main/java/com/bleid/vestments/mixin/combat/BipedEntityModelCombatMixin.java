package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.combat.CombatPose;
import com.bleid.vestments.client.combat.Locomotion;
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

/** Стойка/ходьба/бег паладина и поверх — поза приёма (плавно смешивается). */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelCombatMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void vestments$combatPose(LivingEntity e, float limbAngle, float limbDistance, float progress, float headYaw,
                                      float headPitch, CallbackInfo ci) {
        if (!(e instanceof PlayerEntity)) return;
        // вызов для руки от первого лица (все нули) не трогаем
        if (progress == 0 && limbAngle == 0 && limbDistance == 0 && headYaw == 0 && headPitch == 0) return;
        BipedEntityModel<?> m = (BipedEntityModel<?>) (Object) this;
        float td = MathHelper.clamp(progress - e.age, 0f, 1f);
        CombatPose.Pose p = CombatPose.compute(e, td);
        float wc = p == null ? 0f : (p.clip.offOnly ? 0f : p.w);

        // ---------- стойка, ходьба, бег
        Locomotion.Loco L = Locomotion.cached(e);
        if (L != null) {
            float k = 1f - wc;
            m.body.yaw = MathHelper.lerp(k, m.body.yaw, L.body);
            m.rightLeg.pitch = MathHelper.lerp(k, m.rightLeg.pitch, L.rLeg);
            m.leftLeg.pitch = MathHelper.lerp(k, m.leftLeg.pitch, L.lLeg);
            m.rightLeg.roll = MathHelper.lerp(k, m.rightLeg.roll, L.rLegR);
            m.leftLeg.roll = MathHelper.lerp(k, m.leftLeg.roll, L.lLegR);
            m.rightLeg.pivotY = MathHelper.lerp(k, m.rightLeg.pivotY, 12f - L.rLift);
            m.leftLeg.pivotY = MathHelper.lerp(k, m.leftLeg.pivotY, 12f - L.lLift);
            boolean swinging = m.handSwingProgress > 0;
            boolean rightMain = e.getMainArm() == Arm.RIGHT;
            if (L.armR && !(swinging && rightMain)) {
                m.rightArm.pitch = MathHelper.lerp(k, m.rightArm.pitch, L.rP);
                m.rightArm.yaw = MathHelper.lerp(k, m.rightArm.yaw, L.rY);
                m.rightArm.roll = MathHelper.lerp(k, m.rightArm.roll, L.rR);
            }
            if (L.armL && !(swinging && !rightMain)) {
                m.leftArm.pitch = MathHelper.lerp(k, m.leftArm.pitch, L.lP);
                m.leftArm.yaw = MathHelper.lerp(k, m.leftArm.yaw, L.lY);
                m.leftArm.roll = MathHelper.lerp(k, m.leftArm.roll, L.lR);
            }
            float bt = m.body.yaw;
            m.rightArm.pivotZ = MathHelper.sin(bt) * 5f;
            m.rightArm.pivotX = -MathHelper.cos(bt) * 5f;
            m.leftArm.pivotZ = -MathHelper.sin(bt) * 5f;
            m.leftArm.pivotX = MathHelper.cos(bt) * 5f;
            // голова смотрит прямо, несмотря на наклон и разворот корпуса
            m.head.pitch -= (float) Math.toRadians(L.lean) * k;
            m.head.yaw -= (float) Math.toRadians(L.turn) * k;
        }

        // ---------- приём
        if (p != null) {
            float w = p.w;
            boolean right = e.getMainArm() == Arm.RIGHT;
            ModelPart main = right ? m.rightArm : m.leftArm;
            ModelPart off = right ? m.leftArm : m.rightArm;
            float s = right ? 1f : -1f;
            if (!p.clip.offOnly) {
                m.body.yaw = MathHelper.lerp(w, m.body.yaw, p.bodyYaw * s);
                float bt = m.body.yaw;
                m.rightArm.pivotZ = MathHelper.lerp(w, m.rightArm.pivotZ, MathHelper.sin(bt) * 5f);
                m.rightArm.pivotX = MathHelper.lerp(w, m.rightArm.pivotX, -MathHelper.cos(bt) * 5f);
                m.leftArm.pivotZ = MathHelper.lerp(w, m.leftArm.pivotZ, -MathHelper.sin(bt) * 5f);
                m.leftArm.pivotX = MathHelper.lerp(w, m.leftArm.pivotX, MathHelper.cos(bt) * 5f);
                main.pitch = MathHelper.lerp(w, main.pitch, p.armPitch);
                main.yaw = MathHelper.lerp(w, main.yaw, p.armYaw * s);
                main.roll = MathHelper.lerp(w, main.roll, p.armRoll * s);
                m.rightLeg.pitch = MathHelper.lerp(w, m.rightLeg.pitch, p.rLeg * s);
                m.leftLeg.pitch = MathHelper.lerp(w, m.leftLeg.pitch, p.lLeg * s);
            }
            if (!(e.isUsingItem() && e.getActiveHand() == net.minecraft.util.Hand.OFF_HAND)) {
                off.pitch = MathHelper.lerp(w, off.pitch, p.offPitch);
                off.yaw = MathHelper.lerp(w, off.yaw, p.offYaw * s);
                off.roll = MathHelper.lerp(w, off.roll, p.offRoll * s);
            }
        }
        m.hat.copyTransform(m.head);
    }
}
