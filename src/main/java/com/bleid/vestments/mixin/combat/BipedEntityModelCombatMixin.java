package com.bleid.vestments.mixin.combat;

import com.bleid.vestments.client.bend.Bends;
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

/** Живое тело (стойка/ходьба/бег) и поверх — поза приёма; считаются и сгибы локтей/коленей. */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelCombatMixin {
    private static boolean vestments$freeArm(BipedEntityModel.ArmPose pose) {
        return pose == BipedEntityModel.ArmPose.EMPTY || pose == BipedEntityModel.ArmPose.ITEM;
    }

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void vestments$combatPose(LivingEntity e, float limbAngle, float limbDistance, float progress, float headYaw,
                                      float headPitch, CallbackInfo ci) {
        if (!(e instanceof PlayerEntity)) return;
        // вызов для руки от первого лица (все нули): рука прямая, без сгибов
        if (progress == 0 && limbAngle == 0 && limbDistance == 0 && headYaw == 0 && headPitch == 0) {
            Bends.current = null;
            return;
        }
        BipedEntityModel<?> m = (BipedEntityModel<?>) (Object) this;
        float td = MathHelper.clamp(progress - e.age, 0f, 1f);
        CombatPose.Pose p = CombatPose.compute(e, td);
        float wc = p == null ? 0f : (p.clip.offOnly ? 0f : p.w);
        boolean rightMain = e.getMainArm() == Arm.RIGHT;
        boolean freeR = vestments$freeArm(m.rightArmPose), freeL = vestments$freeArm(m.leftArmPose);
        float eR = 0, eL = 0, kR = 0, kL = 0;

        // ---------- стойка, ходьба, бег
        Locomotion.Loco L = Locomotion.cached(e);
        if (L != null) {
            float k = 1f - wc;
            m.body.yaw = MathHelper.lerp(k, m.body.yaw, L.body);
            m.rightLeg.pitch = MathHelper.lerp(k, m.rightLeg.pitch, L.rLeg);
            m.leftLeg.pitch = MathHelper.lerp(k, m.leftLeg.pitch, L.lLeg);
            m.rightLeg.roll = MathHelper.lerp(k, m.rightLeg.roll, L.rLegR);
            m.leftLeg.roll = MathHelper.lerp(k, m.leftLeg.roll, L.lLegR);
            kR = L.kR;
            kL = L.kL;
            boolean swinging = m.handSwingProgress > 0;
            boolean armR = L.armR && freeR && !(swinging && rightMain);
            boolean armL = L.armL && freeL && !(swinging && !rightMain);
            if (armR) {
                m.rightArm.pitch = MathHelper.lerp(k, m.rightArm.pitch, L.rP);
                m.rightArm.yaw = MathHelper.lerp(k, m.rightArm.yaw, L.rY);
                m.rightArm.roll = MathHelper.lerp(k, m.rightArm.roll, L.rR);
                eR = L.eR;
            } else if (swinging && rightMain && freeR) {
                eR = 0.3f;
            }
            if (armL) {
                m.leftArm.pitch = MathHelper.lerp(k, m.leftArm.pitch, L.lP);
                m.leftArm.yaw = MathHelper.lerp(k, m.leftArm.yaw, L.lY);
                m.leftArm.roll = MathHelper.lerp(k, m.leftArm.roll, L.lR);
                eL = L.eL;
            } else if (swinging && !rightMain && freeL) {
                eL = 0.3f;
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
            ModelPart main = rightMain ? m.rightArm : m.leftArm;
            ModelPart off = rightMain ? m.leftArm : m.rightArm;
            // удары считаются для правши и зеркалятся; выхватывание уже посчитано в пространстве модели
            float s = p.draw != null ? 1f : (rightMain ? 1f : -1f);
            float legS = rightMain ? 1f : -1f;
            float eMain = rightMain ? eR : eL, eOff = rightMain ? eL : eR;
            if (!p.clip.offOnly) {
                m.body.yaw = MathHelper.lerp(w, m.body.yaw, p.bodyYaw * s);
                float bt = m.body.yaw;
                m.rightArm.pivotZ = MathHelper.lerp(w, m.rightArm.pivotZ, MathHelper.sin(bt) * 5f);
                m.rightArm.pivotX = MathHelper.lerp(w, m.rightArm.pivotX, -MathHelper.cos(bt) * 5f);
                m.leftArm.pivotZ = MathHelper.lerp(w, m.leftArm.pivotZ, -MathHelper.sin(bt) * 5f);
                m.leftArm.pivotX = MathHelper.lerp(w, m.leftArm.pivotX, MathHelper.cos(bt) * 5f);
                if (p.draw != null) {
                    // плечи подаются вперёд/внутрь, чтобы дотянуться до рукояти
                    main.pivotX = MathHelper.lerp(w, main.pivotX, p.shX);
                    main.pivotY = MathHelper.lerp(w, main.pivotY, p.shY);
                    main.pivotZ = MathHelper.lerp(w, main.pivotZ, p.shZ);
                    off.pivotX = MathHelper.lerp(w, off.pivotX, p.offShX);
                    off.pivotY = MathHelper.lerp(w, off.pivotY, p.offShY);
                    off.pivotZ = MathHelper.lerp(w, off.pivotZ, p.offShZ);
                }
                main.pitch = vestments$lerpAngle(w, main.pitch, p.armPitch);
                main.yaw = vestments$lerpAngle(w, main.yaw, p.armYaw * s);
                main.roll = vestments$lerpAngle(w, main.roll, p.armRoll * s);
                eMain = MathHelper.lerp(w, eMain, p.mainElbow);
                m.rightLeg.pitch = MathHelper.lerp(w, m.rightLeg.pitch, p.rLeg * legS);
                m.leftLeg.pitch = MathHelper.lerp(w, m.leftLeg.pitch, p.lLeg * legS);
                float pkR = rightMain ? p.kneeR : p.kneeL, pkL = rightMain ? p.kneeL : p.kneeR;
                if (p.draw != null) { pkR = p.kneeR; pkL = p.kneeL; }
                kR = MathHelper.lerp(w, kR, pkR);
                kL = MathHelper.lerp(w, kL, pkL);
            }
            if (!(e.isUsingItem() && e.getActiveHand() == net.minecraft.util.Hand.OFF_HAND)) {
                off.pitch = vestments$lerpAngle(w, off.pitch, p.offPitch);
                off.yaw = vestments$lerpAngle(w, off.yaw, p.offYaw * s);
                off.roll = vestments$lerpAngle(w, off.roll, p.offRoll * s);
                eOff = MathHelper.lerp(w, eOff, p.offElbow);
            }
            if (rightMain) { eR = eMain; eL = eOff; } else { eL = eMain; eR = eOff; }
        }
        m.hat.copyTransform(m.head);
        Bends.store(e, new float[] { Math.max(0, eR), Math.max(0, eL), Math.max(0, kR), Math.max(0, kL) });
    }

    private static float vestments$lerpAngle(float t, float a, float b) {
        return a + MathHelper.wrapDegrees((float) Math.toDegrees(b - a)) * (float) (Math.PI / 180.0) * t;
    }
}
