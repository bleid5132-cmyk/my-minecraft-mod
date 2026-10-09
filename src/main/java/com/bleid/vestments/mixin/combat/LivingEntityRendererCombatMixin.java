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
    /** Получивший удар отшатывается по направлению отбрасывания: резко, затем плавно выпрямляется. */
    private static void vestments$flinch(LivingEntity e, MatrixStack matrices, float tickDelta) {
        if (e.hurtTime <= 0 || e.maxHurtTime <= 0 || e.deathTime > 0 || e.getHeight() > 3f) return;
        double vx = e.getVelocity().x, vz = e.getVelocity().z;
        double sp = Math.sqrt(vx * vx + vz * vz);
        if (sp < 0.04) return;
        float g = 1f - (e.hurtTime - tickDelta) / e.maxHurtTime;     // 0 → 1
        g = Math.max(0f, Math.min(1f, g));
        float amt = g < 0.2f ? g / 0.2f : 1f - (g - 0.2f) / 0.8f;
        amt = amt * amt * (3 - 2 * amt);
        float ang = (float) Math.min(1.0, sp / 0.35) * 13f * amt;
        double yaw = Math.toRadians(e.getBodyYaw());
        double fwd = (vx * -Math.sin(yaw) + vz * Math.cos(yaw)) / sp;
        double right = (vx * -Math.cos(yaw) + vz * -Math.sin(yaw)) / sp;
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) (-fwd * ang)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (-right * ang)));
    }

    @Inject(method = "setupTransforms", at = @At("TAIL"))
    private void vestments$combatRoot(LivingEntity e, MatrixStack matrices, float progress, float bodyYaw, float tickDelta,
                                      CallbackInfo ci) {
        vestments$flinch(e, matrices, tickDelta);
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
        if (L != null && L.ground > 0.001f) {
            // стопы стоят на земле: таз опускается ровно настолько, насколько согнутые ноги стали «короче»
            float rL = L.rLeg, lL = L.lLeg, kr = L.kR, kl = L.kL;
            if (p != null && !p.clip.offOnly) {
                boolean right = e.getMainArm() == net.minecraft.util.Arm.RIGHT;
                float legS = right ? 1f : -1f;
                float pkR = right || p.draw != null ? p.kneeR : p.kneeL, pkL = right || p.draw != null ? p.kneeL : p.kneeR;
                rL = rL + (p.rLeg * legS - rL) * p.w;
                lL = lL + (p.lLeg * legS - lL) * p.w;
                kr = kr + (pkR - kr) * p.w;
                kl = kl + (pkL - kl) * p.w;
            }
            float drop = Math.min(Locomotion.footDrop(rL, kr), Locomotion.footDrop(lL, kl));
            lift -= drop / 16f * L.ground;
        }
        if (p != null && !p.clip.offOnly) {
            lift += p.lift * p.w;
            spin += p.spin * p.w;
            lean += p.lean * p.w;
        } else if (p != null) {
            spin += p.spin * p.w * 0.5f;
        }
        if (L != null && L.ground > 0.001f && lean != 0) {
            // наклон корпуса вокруг таза приподнимает стопы — опускаем тело обратно на землю
            lift -= 0.9f * (1f - (float) Math.cos(Math.toRadians(lean))) * L.ground;
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
